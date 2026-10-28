package com.comicnet.notification.service;

import com.comicnet.identity.api.*;
import com.comicnet.library.api.ComicAudience;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Durable notification fan-out. The publishing and community modules only append domain events to
 * the existing outbox inside their own transaction; this worker turns those events into in-app
 * notifications and, when a reader opted in, queued email. Events are retried with backoff and stop
 * after five failed attempts instead of blocking later events.
 */
@Service
public class NotificationWorker {
  private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);
  private static final int MAX_ATTEMPTS = 5;

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final ComicAudience audience;
  private final AccountNotifier mail;
  private final Environment env;
  private final JsonMapper json = JsonMapper.builder().build();
  private long claimed;

  public NotificationWorker(
      JdbcTemplate jdbc,
      TransactionTemplate transactions,
      ComicAudience audience,
      AccountNotifier mail,
      Environment env) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.audience = audience;
    this.mail = mail;
    this.env = env;
  }

  @Scheduled(fixedDelayString = "${comicnet.notification.poll-delay:3000}")
  public void poll() {
    if (!env.getProperty("comicnet.notification.worker-enabled", Boolean.class, true)) return;
    for (int processed = 0; processed < 50; processed++) if (!processNext()) return;
  }

  public boolean processNext() {
    claimed = 0;
    try {
      return Boolean.TRUE.equals(transactions.execute(status -> handle()));
    } catch (RuntimeException error) {
      if (claimed > 0) fail(claimed, error);
      return false;
    }
  }

  private boolean handle() {
    var rows =
        jdbc.queryForList(
            "SELECT TOP (1) Id,Type,Payload FROM dbo.OutboxEvent WITH (UPDLOCK,READPAST) WHERE"
                + " PublishedAt IS NULL AND Attempts<? AND NextAttemptAt<=SYSUTCDATETIME() AND"
                + " Type IN ('ChapterPublished','CommentReply','CommentReaction') ORDER BY Id",
            MAX_ATTEMPTS);
    if (rows.isEmpty()) return false;
    var row = rows.getFirst();
    claimed = ((Number) row.get("Id")).longValue();
    String type = (String) row.get("Type");
    JsonNode payload = json.readTree((String) row.get("Payload"));
    switch (type) {
      case "ChapterPublished" -> chapterPublished(payload);
      case "CommentReply" -> reply(payload);
      case "CommentReaction" -> reaction(payload);
      default -> throw new IllegalStateException("Unsupported notification event " + type);
    }
    jdbc.update(
        "UPDATE dbo.OutboxEvent SET PublishedAt=SYSUTCDATETIME(),Attempts=Attempts+1,"
            + "ErrorCode=NULL WHERE Id=?",
        claimed);
    claimed = 0;
    return true;
  }

  private void fail(long id, RuntimeException error) {
    log.warn("Notification fan-out failed for outbox event {}: {}", id, error.toString());
    transactions.executeWithoutResult(
        status -> {
          jdbc.update(
              "UPDATE dbo.OutboxEvent SET Attempts=Attempts+1,ErrorCode=?,NextAttemptAt="
                  + "CASE WHEN Attempts+1>=? THEN DATEADD(year,1,SYSUTCDATETIME()) ELSE"
                  + " DATEADD(second,?*10,SYSUTCDATETIME()) END WHERE Id=? AND PublishedAt IS NULL",
              error.getClass().getSimpleName(),
              MAX_ATTEMPTS,
              MAX_ATTEMPTS,
              id);
        });
  }

  private void chapterPublished(JsonNode payload) {
    long comic = payload.path("comicId").asLong(0);
    long chapter = payload.path("chapterId").asLong(0);
    if (comic == 0 || chapter == 0) return;
    String comicTitle =
        jdbc.queryForObject("SELECT Title FROM dbo.Comic WHERE Id=?", String.class, comic);
    String label =
        jdbc.queryForObject(
            "SELECT DisplayNumber+N' · '+Title FROM dbo.Chapter WHERE Id=?", String.class, chapter);
    String title = "Chương mới: " + comicTitle;
    String body = label + " đã được phát hành.";
    List<Long> followers = audience.followers(comic);
    if (followers.isEmpty()) return;
    List<Object[]> rows = new ArrayList<>(followers.size());
    for (long follower : followers)
      rows.add(
          new Object[] {
            follower,
            "NEW_CHAPTER",
            title,
            body,
            "CHAPTER",
            chapter,
            "chapter:" + chapter,
            follower,
            "NEW_CHAPTER",
            follower,
            "NEW_CHAPTER",
            "chapter:" + chapter
          });
    insertBatch(rows);
    var emails =
        jdbc.queryForList(
            "SELECT u.Email FROM dbo.ComicFollow f JOIN dbo.[User] u ON u.Id=f.UserId JOIN"
                + " dbo.NotificationPreference p ON p.UserId=f.UserId AND p.Type='NEW_CHAPTER' AND"
                + " p.EmailEnabled=1 WHERE f.ComicId=? AND u.Status='ACTIVE' AND"
                + " u.EmailVerifiedAt IS NOT NULL",
            String.class,
            comic);
    for (String recipient : emails)
      mail.queueMail(recipient, title, body + System.lineSeparator() + "Mở Comic Net để đọc ngay.");
  }

  private void reply(JsonNode payload) {
    long comment = payload.path("commentId").asLong(0);
    long recipient = payload.path("recipientId").asLong(0);
    if (comment == 0 || recipient == 0) return;
    var author =
        jdbc.queryForObject(
            "SELECT c.AuthorId,u.DisplayName FROM dbo.Comment c JOIN dbo.[User] u ON"
                + " u.Id=c.AuthorId WHERE c.Id=?",
            (row, index) -> Map.entry(row.getLong(1), row.getString(2)),
            comment);
    if (author.getKey() == recipient) return;
    insert(
        recipient,
        "COMMENT_REPLY",
        "Phản hồi mới",
        author.getValue() + " đã trả lời bình luận của bạn.",
        "COMMENT",
        comment,
        "reply:" + comment);
    emailIfOptedIn(
        recipient,
        "COMMENT_REPLY",
        "Phản hồi mới trên Comic Net",
        author.getValue() + " đã trả lời bình luận của bạn.");
  }

  private void reaction(JsonNode payload) {
    long comment = payload.path("commentId").asLong(0);
    long recipient = payload.path("recipientId").asLong(0);
    long actor = payload.path("actorId").asLong(0);
    if (comment == 0 || recipient == 0 || actor == recipient) return;
    String name =
        jdbc.queryForObject("SELECT DisplayName FROM dbo.[User] WHERE Id=?", String.class, actor);
    insert(
        recipient,
        "COMMENT_REACTION",
        "Cảm xúc mới",
        name + " đã bày tỏ cảm xúc về bình luận của bạn.",
        "COMMENT",
        comment,
        "reaction:" + comment + ":" + actor);
  }

  private void insert(
      long recipient,
      String type,
      String title,
      String body,
      String linkType,
      long linkId,
      String dedup) {
    jdbc.update(
        "INSERT dbo.Notification(UserId,Type,Title,Body,LinkType,LinkId,DedupKey)"
            + " SELECT ?,?,?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM dbo.NotificationPreference p"
            + " WHERE p.UserId=? AND p.Type=? AND p.InAppEnabled=0) AND NOT EXISTS(SELECT 1 FROM"
            + " dbo.Notification n WHERE n.UserId=? AND n.Type=? AND n.DedupKey=?)",
        recipient,
        type,
        title,
        body,
        linkType,
        linkId,
        dedup,
        recipient,
        type,
        recipient,
        type,
        dedup);
  }

  private void insertBatch(List<Object[]> rows) {
    jdbc.batchUpdate(
        "INSERT dbo.Notification(UserId,Type,Title,Body,LinkType,LinkId,DedupKey)"
            + " SELECT ?,?,?,?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM dbo.NotificationPreference p"
            + " WHERE p.UserId=? AND p.Type=? AND p.InAppEnabled=0) AND NOT EXISTS(SELECT 1 FROM"
            + " dbo.Notification n WHERE n.UserId=? AND n.Type=? AND n.DedupKey=?)",
        rows,
        new int[] {
          java.sql.Types.BIGINT,
          java.sql.Types.VARCHAR,
          java.sql.Types.NVARCHAR,
          java.sql.Types.NVARCHAR,
          java.sql.Types.VARCHAR,
          java.sql.Types.BIGINT,
          java.sql.Types.VARCHAR,
          java.sql.Types.BIGINT,
          java.sql.Types.VARCHAR,
          java.sql.Types.BIGINT,
          java.sql.Types.VARCHAR,
          java.sql.Types.VARCHAR
        });
  }

  private void emailIfOptedIn(long recipient, String type, String subject, String body) {
    var emails =
        jdbc.queryForList(
            "SELECT u.Email FROM dbo.[User] u JOIN dbo.NotificationPreference p ON p.UserId=u.Id"
                + " AND p.Type=? AND p.EmailEnabled=1 WHERE u.Id=? AND u.Status='ACTIVE' AND"
                + " u.EmailVerifiedAt IS NOT NULL",
            String.class,
            type,
            recipient);
    for (String email : emails) mail.queueMail(email, subject, body);
  }
}
