package com.comicnet.library.service;

import com.comicnet.identity.api.*;
import com.comicnet.catalog.api.CatalogDtos.ComicSummary;
import com.comicnet.catalog.api.CatalogQueries;
import com.comicnet.common.web.*;
import com.comicnet.library.api.ComicAudience;
import com.comicnet.library.dto.LibraryDtos.*;
import com.comicnet.observability.api.Idempotency;
import com.comicnet.reader.api.ReadingAnchors;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class LibraryService implements ComicAudience {
  private final JdbcTemplate jdbc;
  private final IdentityService identities;
  private final CatalogQueries catalog;
  private final ReadingAnchors anchors;
  private final Idempotency idempotency;
  private static final String PUBLIC =
      " c.Visibility='PUBLIC' AND c.DeletedAt IS NULL";

  public LibraryService(
      JdbcTemplate jdbc,
      IdentityService identities,
      CatalogQueries catalog,
      ReadingAnchors anchors,
      Idempotency idempotency) {
    this.jdbc = jdbc;
    this.identities = identities;
    this.catalog = catalog;
    this.anchors = anchors;
    this.idempotency = idempotency;
  }

  @Override
  public List<Long> followers(long comicId) {
    return jdbc.queryForList(
        "SELECT UserId FROM dbo.ComicFollow WHERE ComicId=? ORDER BY UserId",
        Long.class,
        comicId);
  }

  private void self(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    if (!identities.permitted(actor, "library.self")) throw DomainException.forbidden();
  }

  private void ensureDefault(long user) {
    if (jdbc.queryForList(
            "SELECT Id FROM dbo.LibraryCollection WITH (UPDLOCK,HOLDLOCK) WHERE UserId=? AND"
                + " SystemKey='LIBRARY'",
            Long.class,
            user)
        .isEmpty())
      jdbc.update(
          "INSERT dbo.LibraryCollection(UserId,Name,IsSystem,SystemKey) VALUES(?,N'Thư viện của"
              + " tôi',1,'LIBRARY')",
          user);
  }

  private CollectionView collectionRow(Identity actor, long id, boolean lock) {
    self(actor);
    var rows =
        jdbc.query(
            "SELECT Id,Name,IsSystem,SystemKey FROM dbo.LibraryCollection"
                + (lock ? " WITH (UPDLOCK,HOLDLOCK)" : "")
                + " WHERE Id=? AND UserId=?",
            (rs, n) ->
                new CollectionView(
                    rs.getLong("Id") + "",
                    rs.getString("Name"),
                    rs.getBoolean("IsSystem"),
                    rs.getString("SystemKey")),
            id,
            actor.userId());
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  @Transactional
  public PageResult<CollectionView> collections(Identity actor, int page, int size) {
    self(actor);
    ensureDefault(actor.userId());
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.LibraryCollection WHERE UserId=?",
            Long.class,
            actor.userId());
    var ids =
        jdbc.queryForList(
            "SELECT Id FROM dbo.LibraryCollection WHERE UserId=? ORDER BY IsSystem DESC,Id OFFSET ?"
                + " ROWS FETCH NEXT ? ROWS ONLY",
            Long.class,
            actor.userId(),
            PageResult.offset(page, size),
            size);
    return PageResult.of(
        ids.stream().map(id -> collectionRow(actor, id, false)).toList(), page, size, count);
  }

  public CollectionView collection(Identity actor, long id) {
    return collectionRow(actor, id, false);
  }

  public byte[] collectionToken(Identity actor, long id) {
    collectionRow(actor, id, false);
    return jdbc.queryForObject(
        "SELECT RowVersion FROM dbo.LibraryCollection WHERE Id=?", byte[].class, id);
  }

  @Transactional
  public CollectionView createCollection(Identity actor, CollectionInput input, String key) {
    self(actor);
    ensureDefault(actor.userId());
    long id =
        idempotency.create(
            actor.userId(),
            "createCollection",
            "collections",
            key,
            input,
            () ->
                jdbc.queryForObject(
                    "INSERT dbo.LibraryCollection(UserId,Name,IsSystem) OUTPUT INSERTED.Id"
                        + " VALUES(?,?,0)",
                    Long.class,
                    actor.userId(),
                    input.name().strip()));
    return collectionRow(actor, id, false);
  }

  @Transactional
  public CollectionView rename(Identity actor, long id, CollectionInput input, String etag) {
    collectionRow(actor, id, true);
    Etags.require(etag, collectionToken(actor, id));
    jdbc.update("UPDATE dbo.LibraryCollection SET Name=? WHERE Id=?", input.name().strip(), id);
    return collectionRow(actor, id, false);
  }

  @Transactional
  public void deleteCollection(Identity actor, long id, String etag) {
    var collection = collectionRow(actor, id, true);
    if (collection.isSystem())
      throw new DomainException(409, "SYSTEM_COLLECTION", "Không thể xóa thư viện mặc định.");
    Etags.require(etag, collectionToken(actor, id));
    jdbc.update("DELETE dbo.CollectionItem WHERE CollectionId=?", id);
    jdbc.update("DELETE dbo.LibraryCollection WHERE Id=? AND UserId=?", id, actor.userId());
  }

  @Transactional(readOnly = true)
  public PageResult<CollectionItem> items(Identity actor, long collection, int page, int size) {
    collectionRow(actor, collection, false);
    String from =
        " FROM dbo.CollectionItem i JOIN dbo.Comic c WITH (HOLDLOCK) ON c.Id=i.ComicId WHERE"
            + " i.CollectionId=? AND"
            + PUBLIC;
    long count = jdbc.queryForObject("SELECT COUNT_BIG(*)" + from, Long.class, collection);
    var ids =
        jdbc.queryForList(
            "SELECT i.ComicId"
                + from
                + " ORDER BY i.AddedAt DESC,i.ComicId DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            Long.class,
            collection,
            PageResult.offset(page, size),
            size);
    return PageResult.of(
        ids.stream().map(comic -> item(actor, collection, comic)).toList(), page, size, count);
  }

  public CollectionItem item(Identity actor, long collection, long comic) {
    collectionRow(actor, collection, false);
    var summary = catalog.summary(comic);
    var rows =
        jdbc.query(
            "SELECT ReadingStatus,IsFavorite,AddedAt FROM dbo.CollectionItem WHERE CollectionId=?"
                + " AND ComicId=?",
            (rs, n) ->
                new CollectionItem(
                    summary,
                    rs.getString("ReadingStatus"),
                    (Boolean) rs.getObject("IsFavorite"),
                    rs.getObject("AddedAt", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
            collection,
            comic);
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  public byte[] itemToken(Identity actor, long collection, long comic) {
    collectionRow(actor, collection, false);
    var rows =
        jdbc.queryForList(
            "SELECT RowVersion FROM dbo.CollectionItem WHERE CollectionId=? AND ComicId=?",
            collection,
            comic);
    if (rows.isEmpty()) throw DomainException.missing();
    return (byte[]) rows.getFirst().get("RowVersion");
  }

  @Transactional
  public CollectionItem putItem(
      Identity actor, long collection, long comic, JsonNode body, String etag, String noneMatch) {
    var parent = collectionRow(actor, collection, true);
    catalog.summary(comic);
    if (!body.isObject()) throw invalidItem();
    var current =
        jdbc.queryForList(
            "SELECT RowVersion,ReadingStatus,IsFavorite FROM dbo.CollectionItem WITH"
                + " (UPDLOCK,HOLDLOCK) WHERE CollectionId=? AND ComicId=?",
            collection,
            comic);
    if (etag != null && noneMatch != null)
      throw new DomainException(400, "AMBIGUOUS_PRECONDITION", "Chỉ gửi một điều kiện cập nhật.");
    if (current.isEmpty()) {
      if (!"*".equals(noneMatch))
        throw new DomainException(
            etag == null ? 428 : 412,
            "PRECONDITION_REQUIRED",
            "Tạo mục mới cần điều kiện If-None-Match: *.");
    } else {
      if (noneMatch != null)
        throw new DomainException(
            412, "PRECONDITION_FAILED", "Truyện đã có trong bộ sưu tập. Tải lại trước khi sửa.");
      Etags.require(etag, (byte[]) current.getFirst().get("RowVersion"));
    }
    String status = null;
    Boolean favorite = null;
    if (parent.isSystem()) {
      status =
          current.isEmpty() ? "WANT_TO_READ" : (String) current.getFirst().get("ReadingStatus");
      favorite = current.isEmpty() ? false : (Boolean) current.getFirst().get("IsFavorite");
      for (var entry : body.properties())
        switch (entry.getKey()) {
          case "readingStatus" -> {
            if (!entry.getValue().isString()
                || !Set.of("WANT_TO_READ", "READING", "COMPLETED", "REREAD")
                    .contains(entry.getValue().asText())) throw invalidItem();
            status = entry.getValue().asText();
          }
          case "isFavorite" -> {
            if (!entry.getValue().isBoolean()) throw invalidItem();
            favorite = entry.getValue().asBoolean();
          }
          default -> throw invalidItem();
        }
    } else if (!body.isEmpty())
      throw new DomainException(
          400,
          "CUSTOM_COLLECTION_MEMBERSHIP_ONLY",
          "Bộ sưu tập riêng chỉ nhóm truyện; trạng thái đọc và yêu thích nằm trong thư viện mặc"
              + " định.");
    if (current.isEmpty())
      jdbc.update(
          "INSERT dbo.CollectionItem(CollectionId,ComicId,ReadingStatus,IsFavorite)"
              + " VALUES(?,?,?,?)",
          collection,
          comic,
          status,
          favorite);
    else
      jdbc.update(
          "UPDATE dbo.CollectionItem SET ReadingStatus=?,IsFavorite=? WHERE CollectionId=? AND"
              + " ComicId=?",
          status,
          favorite,
          collection,
          comic);
    return item(actor, collection, comic);
  }

  private DomainException invalidItem() {
    return new DomainException(
        400, "INVALID_COLLECTION_ITEM", "Thông tin mục thư viện không hợp lệ.");
  }

  @Transactional
  public void removeItem(Identity actor, long collection, long comic) {
    collectionRow(actor, collection, true);
    jdbc.update("DELETE dbo.CollectionItem WHERE CollectionId=? AND ComicId=?", collection, comic);
  }

  @Transactional
  public void follow(Identity actor, long comic) {
    self(actor);
    catalog.summary(comic);
    if (jdbc.queryForList(
            "SELECT ComicId FROM dbo.ComicFollow WITH (UPDLOCK,HOLDLOCK) WHERE UserId=? AND"
                + " ComicId=?",
            Long.class,
            actor.userId(),
            comic)
        .isEmpty())
      jdbc.update("INSERT dbo.ComicFollow(UserId,ComicId) VALUES(?,?)", actor.userId(), comic);
  }

  @Transactional
  public void unfollow(Identity actor, long comic) {
    self(actor);
    jdbc.update("DELETE dbo.ComicFollow WHERE UserId=? AND ComicId=?", actor.userId(), comic);
  }

  @Transactional(readOnly = true)
  public PageResult<ComicSummary> follows(Identity actor, int page, int size) {
    self(actor);
    String from =
        " FROM dbo.ComicFollow f JOIN dbo.Comic c WITH (HOLDLOCK) ON c.Id=f.ComicId WHERE"
            + " f.UserId=? AND"
            + PUBLIC;
    long count = jdbc.queryForObject("SELECT COUNT_BIG(*)" + from, Long.class, actor.userId());
    var ids =
        jdbc.queryForList(
            "SELECT f.ComicId"
                + from
                + " ORDER BY f.CreatedAt DESC,f.ComicId DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            Long.class,
            actor.userId(),
            PageResult.offset(page, size),
            size);
    return PageResult.of(ids.stream().map(catalog::summary).toList(), page, size, count);
  }

  @Transactional
  public boolean toggleFavorite(Identity actor, long comic) {
    self(actor);
    ensureDefault(actor.userId());
    long defaultColId = jdbc.queryForObject(
        "SELECT Id FROM dbo.LibraryCollection WHERE UserId=? AND SystemKey='LIBRARY'",
        Long.class, actor.userId());
    catalog.summary(comic);
    var current = jdbc.queryForList(
        "SELECT IsFavorite FROM dbo.CollectionItem WITH (UPDLOCK,HOLDLOCK) WHERE CollectionId=? AND ComicId=?",
        defaultColId, comic);
    boolean newFav;
    if (current.isEmpty()) {
      newFav = true;
      jdbc.update(
          "INSERT dbo.CollectionItem(CollectionId,ComicId,ReadingStatus,IsFavorite) VALUES(?,?,'WANT_TO_READ',1)",
          defaultColId, comic);
    } else {
      Boolean cur = (Boolean) current.getFirst().get("IsFavorite");
      newFav = cur == null || !cur;
      jdbc.update(
          "UPDATE dbo.CollectionItem SET IsFavorite=? WHERE CollectionId=? AND ComicId=?",
          newFav, defaultColId, comic);
    }
    return newFav;
  }

  @Transactional(readOnly = true)
  public boolean isFavorite(Identity actor, long comic) {
    if (actor == null) return false;
    var rows = jdbc.queryForList(
        "SELECT i.IsFavorite FROM dbo.CollectionItem i JOIN dbo.LibraryCollection c ON c.Id=i.CollectionId "
            + "WHERE c.UserId=? AND c.SystemKey='LIBRARY' AND i.ComicId=?",
        actor.userId(), comic);
    if (rows.isEmpty()) return false;
    Boolean b = (Boolean) rows.getFirst().get("IsFavorite");
    return b != null && b;
  }

  @Transactional(readOnly = true)
  public PageResult<ComicSummary> favorites(Identity actor, int page, int size) {
    self(actor);
    ensureDefault(actor.userId());
    String from =
        " FROM dbo.CollectionItem i JOIN dbo.LibraryCollection col ON col.Id=i.CollectionId "
            + " JOIN dbo.Comic c WITH (HOLDLOCK) ON c.Id=i.ComicId WHERE col.UserId=? AND col.SystemKey='LIBRARY' AND i.IsFavorite=1 AND"
            + PUBLIC;
    long count = jdbc.queryForObject("SELECT COUNT_BIG(*)" + from, Long.class, actor.userId());
    var ids =
        jdbc.queryForList(
            "SELECT i.ComicId"
                + from
                + " ORDER BY i.AddedAt DESC, i.ComicId DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            Long.class,
            actor.userId(),
            PageResult.offset(page, size),
            size);
    return PageResult.of(ids.stream().map(catalog::summary).toList(), page, size, count);
  }

  private Bookmark bookmarkRow(Identity actor, long id) {
    self(actor);
    var rows =
        jdbc.query(
            "SELECT * FROM dbo.Bookmark WHERE Id=? AND UserId=?",
            (rs, n) -> {
              String chapter = rs.getLong("ChapterId") + "";
              long version = rs.getLong("VersionId");
              String versionId = rs.wasNull() ? null : version + "";
              long page = rs.getLong("PageId");
              String pageId = rs.wasNull() ? null : page + "";
              boolean available =
                  versionId != null
                      && pageId != null
                      && anchors.available(actor, Ids.parse(chapter), version, page);
              return new Bookmark(
                  rs.getLong("Id") + "",
                  chapter,
                  versionId,
                  pageId,
                  rs.getBigDecimal("RelativeOffset"),
                  rs.getString("Label"),
                  available ? "AVAILABLE" : "ANCHOR_UNAVAILABLE");
            },
            id,
            actor.userId());
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  @Transactional
  public Bookmark createBookmark(Identity actor, BookmarkCreate input, String key) {
    self(actor);
    long chapter = Ids.parse(input.chapterId()),
        version = Ids.parse(input.versionId()),
        page = Ids.parse(input.pageId());
    anchors.requireValid(actor, chapter, version, page);
    long id =
        idempotency.create(
            actor.userId(),
            "createBookmark",
            "bookmarks",
            key,
            input,
            () ->
                jdbc.queryForObject(
                    "INSERT dbo.Bookmark(UserId,ChapterId,VersionId,PageId,RelativeOffset,Label)"
                        + " OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?)",
                    Long.class,
                    actor.userId(),
                    chapter,
                    version,
                    page,
                    input.relativeOffset().setScale(8, RoundingMode.HALF_UP),
                    input.label()));
    return bookmarkRow(actor, id);
  }

  @Transactional(readOnly = true)
  public Bookmark bookmark(Identity actor, long id) {
    return bookmarkRow(actor, id);
  }

  public byte[] bookmarkToken(Identity actor, long id) {
    self(actor);
    var rows =
        jdbc.queryForList(
            "SELECT RowVersion FROM dbo.Bookmark WHERE Id=? AND UserId=?", id, actor.userId());
    if (rows.isEmpty()) throw DomainException.missing();
    return (byte[]) rows.getFirst().get("RowVersion");
  }

  @Transactional(readOnly = true)
  public PageResult<Bookmark> bookmarks(Identity actor, int page, int size) {
    self(actor);
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.Bookmark WHERE UserId=?", Long.class, actor.userId());
    var ids =
        jdbc.queryForList(
            "SELECT Id FROM dbo.Bookmark WHERE UserId=? ORDER BY Id DESC OFFSET ? ROWS FETCH NEXT ?"
                + " ROWS ONLY",
            Long.class,
            actor.userId(),
            PageResult.offset(page, size),
            size);
    return PageResult.of(
        ids.stream().map(id -> bookmarkRow(actor, id)).toList(), page, size, count);
  }

  @Transactional
  public Bookmark patchBookmark(Identity actor, long id, BookmarkPatch input, String etag) {
    self(actor);
    lockBookmark(actor, id);
    Etags.require(etag, bookmarkToken(actor, id));
    jdbc.update(
        "UPDATE dbo.Bookmark SET Label=? WHERE Id=? AND UserId=?",
        input.label().strip(),
        id,
        actor.userId());
    return bookmarkRow(actor, id);
  }

  private void lockBookmark(Identity actor, long id) {
    if (jdbc.queryForList(
            "SELECT Id FROM dbo.Bookmark WITH (UPDLOCK,HOLDLOCK) WHERE Id=? AND UserId=?",
            Long.class,
            id,
            actor.userId())
        .isEmpty()) throw DomainException.missing();
  }

  @Transactional
  public void deleteBookmark(Identity actor, long id, String etag) {
    self(actor);
    lockBookmark(actor, id);
    Etags.require(etag, bookmarkToken(actor, id));
    jdbc.update("DELETE dbo.Bookmark WHERE Id=? AND UserId=?", id, actor.userId());
  }
}
