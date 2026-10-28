package com.comicnet.community.service;

import com.comicnet.identity.api.*;
import com.comicnet.catalog.api.CatalogAccess;
import com.comicnet.common.web.*;
import com.comicnet.community.dto.CommentDtos.*;
import com.comicnet.entitlement.api.ReadingAccess;
import com.comicnet.observability.api.*;
import com.comicnet.publishing.api.PublishedChapters;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommentService {
  private final JdbcTemplate jdbc;
  private final CatalogAccess catalog;
  private final PublishedChapters chapters;
  private final ReadingAccess access;
  private final IdentityService identities;
  private final AuditWriter audit;
  private final Idempotency idempotency;
  private final EventOutbox events;
  private final Set<String> blockedWords;

  public CommentService(
      JdbcTemplate jdbc,
      CatalogAccess catalog,
      PublishedChapters chapters,
      ReadingAccess access,
      IdentityService identities,
      AuditWriter audit,
      Idempotency idempotency,
      EventOutbox events,
      Environment env) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.chapters = chapters;
    this.access = access;
    this.identities = identities;
    this.audit = audit;
    this.idempotency = idempotency;
    this.events = events;
    blockedWords = new HashSet<>();
    for (String word : env.getProperty("comicnet.community.blocked-terms", "").split(","))
      if (!word.isBlank()) blockedWords.add(word.strip().toLowerCase(Locale.ROOT));
  }

  private void self(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    if (!identities.permitted(actor, "community.self")) throw DomainException.forbidden();
  }

  private void moderator(Identity actor) {
    self(actor);
    if (!identities.permitted(actor, "community.moderate")) throw DomainException.forbidden();
  }

  private String body(String value) {
    String text = CommentText.clean(value, 4000);
    for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
      if (blockedWords.contains(word))
        throw new DomainException(422, "COMMENT_FILTERED", "Nội dung vi phạm bộ lọc từ ngữ.");
    return text;
  }

  private boolean context(Identity actor, long comic, Long chapter, Long page, boolean creating) {
    if (!catalog.publicComic(comic)) throw DomainException.missing();
    if (chapter == null) {
      if (page != null)
        throw new DomainException(
            422, "INVALID_COMMENT_ANCHOR", "Cần chọn chương cho bình luận trang.");
      return true;
    }
    var snapshot = chapters.current(chapter).orElseThrow(DomainException::missing);
    if (snapshot.comicId() != comic) throw DomainException.missing();
    if (!access.decide(actor, chapter, snapshot.chapter().accessPolicy()).allowed())
      throw DomainException.forbidden();
    boolean available =
        page == null || snapshot.pages().stream().anyMatch(p -> Ids.parse(p.pageId()) == page);
    if (creating && !available)
      throw new DomainException(
          422, "COMMENT_ANCHOR_UNAVAILABLE", "Trang không còn trong bản xuất bản hiện tại.");
    return available;
  }

  private Long id(String value) {
    return value == null || value.isBlank() ? null : Ids.parse(value);
  }

  public PageResult<CommentView> list(
      Identity actor, long comic, Long chapter, Long pageId, int page, int size) {
    boolean available = context(actor, comic, chapter, pageId, false);
    PageResult.validate(page, size);
    long viewer = actor == null ? 0 : actor.userId();
    String filter =
        " FROM dbo.Comment c JOIN dbo.[User] u ON u.Id=c.AuthorId WHERE c.ComicId=?"
            + " AND ISNULL(c.ChapterId,0)=? AND ISNULL(c.PageId,0)=? AND NOT EXISTS"
            + " (SELECT 1 FROM dbo.UserBlock b WHERE (b.BlockerId=? AND b.BlockedId=c.AuthorId)"
            + " OR (b.BlockedId=? AND b.BlockerId=c.AuthorId))";
    Object[] values = {
      comic, chapter == null ? 0 : chapter, pageId == null ? 0 : pageId, viewer, viewer
    };
    long count = jdbc.queryForObject("SELECT COUNT_BIG(*)" + filter, Long.class, values);
    var args = new ArrayList<Object>();
    args.add(viewer);
    args.addAll(Arrays.asList(values));
    args.add(PageResult.offset(page, size));
    args.add(size);
    var rows =
        jdbc.query(
            select() + filter + " ORDER BY c.Id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            (row, n) -> map(row, available),
            args.toArray());
    return PageResult.of(rows, page, size, count);
  }

  private String select() {
    return "SELECT c.*,u.DisplayName,(SELECT COALESCE(SUM(CAST(v.Value AS bigint)),0) FROM"
               + " dbo.CommentReaction v WHERE v.CommentId=c.Id) AS Votes, (SELECT"
               + " COALESCE(MAX(v.Value),0) FROM dbo.CommentReaction v WHERE v.CommentId=c.Id AND"
               + " v.UserId=?) AS MyVote";
  }

  public CommentView get(Identity actor, long comment) {
    long viewer = actor == null ? 0 : actor.userId();
    boolean staff = actor != null && identities.permitted(actor, "community.moderate");
    long blockViewer = staff ? 0 : viewer;
    var rows =
        jdbc.query(
            select()
                + " FROM dbo.Comment c JOIN dbo.[User] u ON u.Id=c.AuthorId WHERE c.Id=? AND NOT"
                + " EXISTS(SELECT 1 FROM dbo.UserBlock b WHERE (b.BlockerId=? AND"
                + " b.BlockedId=c.AuthorId) OR (b.BlockedId=? AND b.BlockerId=c.AuthorId))",
            (row, n) -> map(row, true, staff),
            viewer,
            comment,
            blockViewer,
            blockViewer);
    if (rows.isEmpty()) throw DomainException.missing();
    var row = rows.getFirst();
    boolean available =
        context(actor, Ids.parse(row.comicId()), id(row.chapterId()), id(row.pageId()), false);
    return new CommentView(
        row.id(),
        row.comicId(),
        row.chapterId(),
        row.pageId(),
        row.relativeOffset(),
        row.parentId(),
        row.authorId(),
        row.displayName(),
        row.body(),
        row.spoiler(),
        row.deleted(),
        row.hidden(),
        available,
        row.votes(),
        row.myVote(),
        row.updatedAt(),
        row.etag());
  }

  @Transactional
  public CommentView create(Identity actor, CommentInput input, String key) {
    self(actor);
    long comic = Ids.parse(input.comicId());
    Long chapter = id(input.chapterId()), page = id(input.pageId()), parent = id(input.parentId());
    context(actor, comic, chapter, page, true);
    if (page != null
        && (input.relativeOffset() == null
            || input.relativeOffset().compareTo(BigDecimal.ZERO) < 0
            || input.relativeOffset().compareTo(BigDecimal.ONE) > 0))
      throw new DomainException(422, "INVALID_COMMENT_ANCHOR", "Vị trí tương đối phải từ 0 đến 1.");
    if (page == null && input.relativeOffset() != null)
      throw new DomainException(
          422, "INVALID_COMMENT_ANCHOR", "Vị trí chỉ áp dụng cho bình luận trang.");
    String content = body(input.body());
    long result =
        idempotency.create(
            actor.userId(),
            "createComment",
            comic + ":" + chapter + ":" + page,
            key,
            input,
            () -> {
              String parentAuthor = null;
              if (parent != null) {
                var p = get(actor, parent);
                if (!p.comicId().equals(comic + "")
                    || !Objects.equals(id(p.chapterId()), chapter)
                    || !Objects.equals(id(p.pageId()), page)
                    || p.deleted()
                    || p.hidden())
                  throw new DomainException(
                      422,
                      "INVALID_COMMENT_PARENT",
                      "Bình luận cha không thuộc ngữ cảnh hiện tại.");
                parentAuthor = p.authorId();
              }
              long recent =
                  jdbc.queryForObject(
                      "SELECT COUNT_BIG(*) FROM dbo.Comment WITH (UPDLOCK,HOLDLOCK)"
                          + " WHERE AuthorId=? AND CreatedAt>DATEADD(minute,-1,SYSUTCDATETIME())",
                      Long.class,
                      actor.userId());
              if (recent >= 10)
                throw new DomainException(
                    429, "COMMENT_RATE_LIMIT", "Tối đa 10 bình luận mỗi phút.");
              var ids =
                  jdbc.queryForList(
                      "INSERT"
                          + " dbo.Comment(ComicId,ChapterId,PageId,RelativeOffset,ParentId,AuthorId,Body,Spoiler)"
                          + " OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?,?)",
                      Long.class,
                      comic,
                      chapter,
                      page,
                      input.relativeOffset(),
                      parent,
                      actor.userId(),
                      content,
                      input.spoiler());
              audit.record(
                  actor.userId(), "COMMENT_CREATED", "Comment", ids.getFirst() + "", "SUCCESS");
              if (parentAuthor != null && !parentAuthor.equals(actor.userId() + ""))
                events.append(
                    "CommentReply",
                    ids.getFirst() + "",
                    Map.of(
                        "commentId",
                        ids.getFirst(),
                        "parentCommentId",
                        parent,
                        "recipientId",
                        Long.parseLong(parentAuthor),
                        "comicId",
                        comic,
                        "chapterId",
                        chapter == null ? 0L : chapter));
              return ids.getFirst();
            });
    return get(actor, result);
  }

  @Transactional
  public CommentView edit(Identity actor, long comment, EditInput input, String etag) {
    self(actor);
    var row = get(actor, comment);
    if (!row.authorId().equals(actor.userId() + "")) throw DomainException.missing();
    if (row.deleted() || row.hidden()) throw DomainException.missing();
    Etags.require(etag, token(comment));
    int changed =
        jdbc.update(
            "UPDATE dbo.Comment SET Body=?,Spoiler=?,UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND"
                + " RowVersion=?",
            body(input.body()),
            input.spoiler(),
            comment,
            tokenFrom(etag));
    if (changed != 1) throw new DomainException(412, "STALE_VERSION", "Bình luận đã thay đổi.");
    audit.record(actor.userId(), "COMMENT_EDITED", "Comment", comment + "", "SUCCESS");
    return get(actor, comment);
  }

  @Transactional
  public void delete(Identity actor, long comment, String etag) {
    self(actor);
    var row = get(actor, comment);
    if (!row.authorId().equals(actor.userId() + "")) throw DomainException.missing();
    Etags.require(etag, token(comment));
    int changed =
        jdbc.update(
            "UPDATE dbo.Comment SET Body=NULL,DeletedAt=SYSUTCDATETIME(),UpdatedAt=SYSUTCDATETIME()"
                + " WHERE Id=? AND RowVersion=? AND DeletedAt IS NULL",
            comment,
            tokenFrom(etag));
    if (changed != 1) throw new DomainException(412, "STALE_VERSION", "Bình luận đã thay đổi.");
    audit.record(actor.userId(), "COMMENT_DELETED", "Comment", comment + "", "SUCCESS");
  }

  private byte[] token(long id) {
    return jdbc.queryForObject("SELECT RowVersion FROM dbo.Comment WHERE Id=?", byte[].class, id);
  }

  private byte[] tokenFrom(String etag) {
    return Base64.getUrlDecoder().decode(etag.substring(1, etag.length() - 1));
  }

  @Transactional
  public CommentView vote(Identity actor, long comment, Integer value) {
    self(actor);
    var row = get(actor, comment);
    if (row.deleted() || row.hidden()) throw DomainException.missing();
    if (value != null && value != 1 && value != -1)
      throw new DomainException(422, "INVALID_VOTE", "Chỉ chấp nhận +1 hoặc -1.");
    var existing =
        jdbc.queryForList(
            "SELECT Value FROM dbo.CommentReaction WITH(UPDLOCK,HOLDLOCK) WHERE CommentId=? AND"
                + " UserId=?",
            comment,
            actor.userId());
    if (value == null)
      jdbc.update(
          "DELETE dbo.CommentReaction WHERE CommentId=? AND UserId=?", comment, actor.userId());
    else if (existing.isEmpty())
      jdbc.update(
          "INSERT dbo.CommentReaction(CommentId,UserId,Value) VALUES(?,?,?)",
          comment,
          actor.userId(),
          value);
    else
      jdbc.update(
          "UPDATE dbo.CommentReaction SET Value=? WHERE CommentId=? AND UserId=?",
          value,
          comment,
          actor.userId());
    if (value != null
        && value == 1
        && existing.isEmpty()
        && !row.authorId().equals(actor.userId() + ""))
      events.append(
          "CommentReaction",
          comment + "",
          Map.of(
              "commentId",
              comment,
              "recipientId",
              Long.parseLong(row.authorId()),
              "actorId",
              actor.userId(),
              "value",
              1));
    return get(actor, comment);
  }

  @Transactional
  public void report(Identity actor, long comment, String reason) {
    self(actor);
    get(actor, comment);
    String clean = CommentText.clean(reason, 1000);
    if (jdbc.queryForList(
            "SELECT Id FROM dbo.CommentReport WITH(UPDLOCK,HOLDLOCK) WHERE CommentId=? AND"
                + " ReporterId=?",
            comment,
            actor.userId())
        .isEmpty())
      jdbc.update(
          "INSERT dbo.CommentReport(CommentId,ReporterId,Reason) VALUES(?,?,?)",
          comment,
          actor.userId(),
          clean);
  }

  @Transactional
  public void block(Identity actor, long user, boolean enabled) {
    self(actor);
    if (user == actor.userId())
      throw new DomainException(422, "SELF_BLOCK", "Không thể tự chặn chính mình.");
    identities.user(user);
    var existing =
        jdbc.queryForList(
            "SELECT BlockedId FROM dbo.UserBlock WITH(UPDLOCK,HOLDLOCK) WHERE BlockerId=? AND"
                + " BlockedId=?",
            actor.userId(),
            user);
    if (enabled && existing.isEmpty())
      jdbc.update("INSERT dbo.UserBlock(BlockerId,BlockedId) VALUES(?,?)", actor.userId(), user);
    if (!enabled)
      jdbc.update("DELETE dbo.UserBlock WHERE BlockerId=? AND BlockedId=?", actor.userId(), user);
  }

  public PageResult<BlockView> blocks(Identity actor, int page, int size) {
    self(actor);
    PageResult.validate(page, size);
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.UserBlock WHERE BlockerId=?", Long.class, actor.userId());
    var rows =
        jdbc.query(
            "SELECT b.BlockedId,u.DisplayName FROM dbo.UserBlock b JOIN dbo.[User] u ON"
                + " u.Id=b.BlockedId WHERE b.BlockerId=? ORDER BY b.BlockedId OFFSET ? ROWS FETCH"
                + " NEXT ? ROWS ONLY",
            (row, n) -> new BlockView(row.getLong(1) + "", row.getString(2)),
            actor.userId(),
            PageResult.offset(page, size),
            size);
    return PageResult.of(rows, page, size, count);
  }

  public PageResult<ReportView> reports(Identity actor, int page, int size) {
    moderator(actor);
    PageResult.validate(page, size);
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.CommentReport WHERE ResolvedAt IS NULL", Long.class);
    var rows =
        jdbc.query(
            "SELECT * FROM dbo.CommentReport WHERE ResolvedAt IS NULL ORDER BY Id OFFSET ? ROWS"
                + " FETCH NEXT ? ROWS ONLY",
            (row, n) ->
                new ReportView(
                    row.getLong("Id") + "",
                    row.getLong("CommentId") + "",
                    row.getString("Reason"),
                    row.getLong("ReporterId") + "",
                    row.getTimestamp("CreatedAt").toInstant()),
            PageResult.offset(page, size),
            size);
    return PageResult.of(rows, page, size, count);
  }

  @Transactional
  public CommentView moderate(Identity actor, long comment, ModerationInput input, String etag) {
    moderator(actor);
    get(actor, comment);
    Etags.require(etag, token(comment));
    String reason = CommentText.clean(input.reason(), 1000);
    int changed =
        jdbc.update(
            "UPDATE dbo.Comment SET IsHidden=?,UpdatedAt=SYSUTCDATETIME() WHERE Id=? AND"
                + " RowVersion=?",
            input.hidden(),
            comment,
            tokenFrom(etag));
    if (changed != 1) throw new DomainException(412, "STALE_VERSION", "Bình luận đã thay đổi.");
    jdbc.update(
        "UPDATE dbo.CommentReport SET ResolvedAt=SYSUTCDATETIME(),ResolvedBy=?,Resolution=? WHERE"
            + " CommentId=? AND ResolvedAt IS NULL",
        actor.userId(),
        reason,
        comment);
    audit.record(actor.userId(), "COMMENT_MODERATED", "Comment", comment + "", "SUCCESS");
    return get(actor, comment);
  }

  private CommentView map(ResultSet row, boolean available) throws SQLException {
    return map(row, available, false);
  }

  private CommentView map(ResultSet row, boolean available, boolean staff) throws SQLException {
    boolean deleted = row.getTimestamp("DeletedAt") != null, hidden = row.getBoolean("IsHidden");
    return new CommentView(
        row.getLong("Id") + "",
        row.getLong("ComicId") + "",
        number(row, "ChapterId"),
        number(row, "PageId"),
        row.getBigDecimal("RelativeOffset"),
        number(row, "ParentId"),
        row.getLong("AuthorId") + "",
        row.getString("DisplayName"),
        deleted || (hidden && !staff) ? null : row.getString("Body"),
        row.getBoolean("Spoiler"),
        deleted,
        hidden,
        available,
        row.getLong("Votes"),
        row.getInt("MyVote"),
        row.getTimestamp("UpdatedAt").toInstant(),
        Etags.of(row.getBytes("RowVersion")));
  }

  private String number(ResultSet row, String column) throws SQLException {
    long number = row.getLong(column);
    return row.wasNull() ? null : number + "";
  }
}
