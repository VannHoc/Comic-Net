package com.comicnet.notification.service;

import com.comicnet.identity.api.*;
import com.comicnet.common.web.*;
import com.comicnet.notification.dto.NotificationDtos.*;
import com.comicnet.observability.api.AuditWriter;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {
  static final Set<String> TYPES =
      Set.of("NEW_CHAPTER", "COMMENT_REPLY", "COMMENT_REACTION", "SYSTEM_ANNOUNCEMENT");

  private final JdbcTemplate jdbc;
  private final IdentityService identities;
  private final AuditWriter audit;

  public NotificationService(JdbcTemplate jdbc, IdentityService identities, AuditWriter audit) {
    this.jdbc = jdbc;
    this.identities = identities;
    this.audit = audit;
  }

  private void self(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    if (!identities.permitted(actor, "notification.self")) throw DomainException.forbidden();
  }

  private void broadcaster(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    if (!identities.permitted(actor, "notification.broadcast")) throw DomainException.forbidden();
  }

  private NotificationView map(java.sql.ResultSet row, int index) throws java.sql.SQLException {
    long link = row.getLong("LinkId");
    Long linkId = row.wasNull() ? null : link;
    return new NotificationView(
        row.getLong("Id") + "",
        row.getString("Type"),
        row.getString("Title"),
        row.getString("Body"),
        row.getString("LinkType"),
        linkId == null ? null : linkId + "",
        row.getTimestamp("ReadAt") != null,
        row.getTimestamp("CreatedAt").toInstant());
  }

  public PageResult<NotificationView> inbox(
      Identity actor, int page, int size, boolean unreadOnly) {
    self(actor);
    PageResult.validate(page, size);
    String filter = " WHERE UserId=?" + (unreadOnly ? " AND ReadAt IS NULL" : "");
    Object[] args = {actor.userId()};
    long total = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Notification" + filter, Long.class, args);
    var items =
        jdbc.query(
            "SELECT * FROM dbo.Notification"
                + filter
                + " ORDER BY Id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            this::map,
            actor.userId(),
            PageResult.offset(page, size),
            size);
    return PageResult.of(items, page, size, total);
  }

  public UnreadSummary summary(Identity actor) {
    self(actor);
    long unread =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.Notification WHERE UserId=? AND ReadAt IS NULL",
            Long.class,
            actor.userId());
    long total =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.Notification WHERE UserId=?", Long.class, actor.userId());
    return new UnreadSummary(unread, total);
  }

  @Transactional
  public NotificationView read(Identity actor, long id, boolean read) {
    self(actor);
    int changed =
        read
            ? jdbc.update(
                "UPDATE dbo.Notification SET ReadAt=SYSUTCDATETIME() WHERE Id=? AND UserId=? AND"
                    + " ReadAt IS NULL",
                id,
                actor.userId())
            : jdbc.update(
                "UPDATE dbo.Notification SET ReadAt=NULL WHERE Id=? AND UserId=? AND ReadAt IS NOT"
                    + " NULL",
                id,
                actor.userId());
    var rows =
        jdbc.query(
            "SELECT * FROM dbo.Notification WHERE Id=? AND UserId=?", this::map, id, actor.userId());
    if (rows.isEmpty()) throw DomainException.missing();
    if (changed != 0) audit.record(actor.userId(), read ? "NOTIFICATION_READ" : "NOTIFICATION_UNREAD",
        "Notification", id + "", "SUCCESS");
    return rows.getFirst();
  }

  @Transactional
  public UnreadSummary readAll(Identity actor) {
    self(actor);
    int updated =
        jdbc.update(
            "UPDATE dbo.Notification SET ReadAt=SYSUTCDATETIME() WHERE UserId=? AND ReadAt IS NULL",
            actor.userId());
    if (updated > 0) audit.record(actor.userId(), "NOTIFICATIONS_READ_ALL", "Notification",
        "bulk", "SUCCESS", Map.of("count", updated));
    return summary(actor);
  }

  public List<PreferenceView> preferences(Identity actor) {
    self(actor);
    var stored =
        jdbc.query(
            "SELECT Type,InAppEnabled,EmailEnabled FROM dbo.NotificationPreference WHERE UserId=?",
            (row, index) ->
                Map.entry(
                    row.getString("Type"),
                    new PreferenceView(
                        row.getString("Type"),
                        row.getBoolean("InAppEnabled"),
                        row.getBoolean("EmailEnabled"))),
            actor.userId());
    Map<String, PreferenceView> merged = new TreeMap<>();
    for (String type : TYPES) merged.put(type, new PreferenceView(type, true, false));
    for (var entry : stored) merged.put(entry.getKey(), entry.getValue());
    return List.copyOf(merged.values());
  }

  @Transactional
  public PreferenceView preference(Identity actor, String type, PreferenceInput input) {
    self(actor);
    String code = type == null ? "" : type.strip().toUpperCase(Locale.ROOT);
    if (!TYPES.contains(code))
      throw new DomainException(422, "INVALID_NOTIFICATION_TYPE", "Loại thông báo không hợp lệ.");
    if (!input.inApp() && input.email())
      throw new DomainException(
          422, "INVALID_NOTIFICATION_PREFERENCE", "Bật email cần bật thông báo trong ứng dụng.");
    int updated =
        jdbc.update(
            "UPDATE dbo.NotificationPreference SET InAppEnabled=?,EmailEnabled=?,"
                + " UpdatedAt=SYSUTCDATETIME() WHERE UserId=? AND Type=?",
            input.inApp(),
            input.email(),
            actor.userId(),
            code);
    if (updated == 0)
      jdbc.update(
          "INSERT dbo.NotificationPreference(UserId,Type,InAppEnabled,EmailEnabled) VALUES(?,?,?,?)",
          actor.userId(),
          code,
          input.inApp(),
          input.email());
    audit.record(actor.userId(), "NOTIFICATION_PREFERENCE_CHANGED", "NotificationPreference", code,
        "SUCCESS", Map.of("inApp", input.inApp(), "email", input.email()));
    return new PreferenceView(code, input.inApp(), input.email());
  }

  /** System announcement: one durable record, then a set-based batch fan-out to active accounts. */
  @Transactional
  public AnnouncementView announce(Identity actor, AnnouncementInput input) {
    broadcaster(actor);
    String title = title(input.title());
    String body = text(input.body(), 2000);
    long id =
        jdbc.queryForObject(
            "INSERT dbo.Announcement(Title,Body,CreatedBy) OUTPUT INSERTED.Id VALUES(?,?,?)",
            Long.class,
            title,
            body,
            actor.userId());
    int recipients =
        jdbc.update(
            "INSERT dbo.Notification(UserId,Type,Title,Body,LinkType,LinkId,DedupKey)"
                + " SELECT u.Id,'SYSTEM_ANNOUNCEMENT',?,?,NULL,NULL,? FROM dbo.[User] u WHERE"
                + " u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)"
                + " AND NOT EXISTS(SELECT 1 FROM dbo.NotificationPreference p WHERE p.UserId=u.Id"
                + " AND p.Type='SYSTEM_ANNOUNCEMENT' AND p.InAppEnabled=0)",
            title,
            body.length() > 1000 ? body.substring(0, 997) + "..." : body,
            "announcement:" + id);
    jdbc.update("UPDATE dbo.Announcement SET RecipientCount=? WHERE Id=?", recipients, id);
    audit.record(actor.userId(), "ANNOUNCEMENT_PUBLISHED", "Announcement", id + "", "SUCCESS",
        Map.of("recipients", recipients));
    return new AnnouncementView(
        id + "", title, body, actor.userId() + "", Instant.now(), recipients);
  }

  public PageResult<AnnouncementView> announcements(Identity actor, int page, int size) {
    broadcaster(actor);
    PageResult.validate(page, size);
    long total = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Announcement", Long.class);
    var items =
        jdbc.query(
            "SELECT * FROM dbo.Announcement ORDER BY Id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            (row, index) ->
                new AnnouncementView(
                    row.getLong("Id") + "",
                    row.getString("Title"),
                    row.getString("Body"),
                    row.getLong("CreatedBy") + "",
                    row.getTimestamp("CreatedAt").toInstant(),
                    row.getInt("RecipientCount")),
            PageResult.offset(page, size),
            size);
    return PageResult.of(items, page, size, total);
  }

  static String title(String value) {
    String clean = value == null ? "" : value.strip();
    if (clean.isEmpty()) throw new DomainException(422, "INVALID_TITLE", "Tiêu đề không được trống.");
    return clean.length() > 200 ? clean.substring(0, 200) : clean;
  }

  static String text(String value, int max) {
    String clean = value == null ? "" : value.strip();
    if (clean.isEmpty())
      throw new DomainException(422, "INVALID_CONTENT", "Nội dung không được trống.");
    return clean.length() > max ? clean.substring(0, max - 3) + "..." : clean;
  }
}
