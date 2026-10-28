package com.comicnet.reader.service;

import com.comicnet.identity.api.*;
import com.comicnet.common.web.*;
import com.comicnet.reader.api.ReadingAnchors;
import com.comicnet.reader.dto.ReadingDtos.*;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ProgressService {
  private final JdbcTemplate jdbc;
  private final IdentityService identities;
  private final ReadingAnchors anchors;
  private final JsonMapper json =
      JsonMapper.builder()
          .enable(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

  public ProgressService(JdbcTemplate jdbc, IdentityService identities, ReadingAnchors anchors) {
    this.jdbc = jdbc;
    this.identities = identities;
    this.anchors = anchors;
  }

  private void self(Identity actor, String permission) {
    if (actor == null) throw DomainException.unauthorized();
    if (!identities.permitted(actor, permission)) throw DomainException.forbidden();
  }

  private int generation(long user) {
    var rows =
        jdbc.queryForList(
            "SELECT HistoryGeneration FROM dbo.ReadingHistoryState WITH (UPDLOCK,HOLDLOCK) WHERE"
                + " UserId=?",
            Long.class,
            user);
    if (rows.isEmpty()) {
      jdbc.update("INSERT dbo.ReadingHistoryState(UserId) VALUES(?)", user);
      return 0;
    }
    return Math.toIntExact(rows.getFirst());
  }

  private Progress find(Identity actor, long chapter, int generation) {
    var rows =
        jdbc.query(
            "SELECT * FROM dbo.ReadingProgress WHERE UserId=? AND ChapterId=?",
            (rs, n) -> {
              long version = rs.getLong("VersionId");
              String versionId = rs.wasNull() ? null : version + "";
              long page = rs.getLong("PageId");
              String pageId = rs.wasNull() ? null : page + "";
              return new Progress(
                  chapter + "",
                  versionId,
                  pageId,
                  rs.getBigDecimal("RelativeOffset"),
                  rs.getBoolean("Completed"),
                  rs.getLong("Revision"),
                  generation,
                  rs.getObject("LastReadAt", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                  "ANCHOR_UNAVAILABLE");
            },
            actor.userId(),
            chapter);
    return rows.isEmpty() ? null : availability(actor, rows.getFirst());
  }

  private Progress availability(Identity actor, Progress value) {
    boolean available =
        value.versionId() != null
            && value.pageId() != null
            && anchors.available(
                actor,
                Ids.parse(value.chapterId()),
                Ids.parse(value.versionId()),
                Ids.parse(value.pageId()));
    return new Progress(
        value.chapterId(),
        value.versionId(),
        value.pageId(),
        value.relativeOffset(),
        value.completed(),
        value.revision(),
        value.historyGeneration(),
        value.lastReadAt(),
        available ? "AVAILABLE" : "ANCHOR_UNAVAILABLE");
  }

  @Transactional
  public Progress get(Identity actor, long chapter) {
    self(actor, "library.self");
    var current = find(actor, chapter, generation(actor.userId()));
    if (current == null)
      throw new DomainException(404, "PROGRESS_NOT_FOUND", "Chưa có tiến độ đọc chapter này.");
    return current;
  }

  @Transactional
  public History history(Identity actor, int page, int size) {
    self(actor, "library.self");
    PageResult.validate(page, size);
    int generation = generation(actor.userId());
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.ReadingProgress WHERE UserId=?",
            Long.class,
            actor.userId());
    var ids =
        jdbc.queryForList(
            "SELECT ChapterId FROM dbo.ReadingProgress WHERE UserId=? ORDER BY LastReadAt DESC,Id"
                + " DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            Long.class,
            actor.userId(),
            PageResult.offset(page, size),
            size);
    var items = ids.stream().map(id -> find(actor, id, generation)).toList();
    return new History(items, PageResult.of(items, page, size, count).page(), generation);
  }

  @Transactional
  public Progress put(Identity actor, long chapter, ProgressPut input) {
    self(actor, "library.self");
    int generation = generation(actor.userId());
    if (input.historyGeneration() != generation)
      throw new DomainException(
              409,
              "HISTORY_GENERATION_CONFLICT",
              "Lịch sử đã được xóa trên thiết bị khác. Tải lại trước khi lưu.")
          .withCurrent(find(actor, chapter, generation));
    String mutation = uuid(input.mutationId());
    long version = Ids.parse(input.versionId()), page = Ids.parse(input.pageId());
    String hash = hash(chapter, input);
    var prior =
        jdbc.queryForList(
            "SELECT PayloadHash,ResultSnapshot,State FROM dbo.ProgressMutation WHERE UserId=? AND"
                + " MutationId=?",
            actor.userId(),
            mutation);
    if (!prior.isEmpty()) {
      var row = prior.getFirst();
      if (!hash.equals(row.get("PayloadHash")) || !row.get("State").equals("APPLIED"))
        throw new DomainException(
            409, "MUTATION_CONFLICT", "Mã thao tác đã được dùng cho dữ liệu khác.");
      return availability(
          actor, json.readValue((String) row.get("ResultSnapshot"), Progress.class));
    }
    var current = find(actor, chapter, generation);
    long revision = current == null ? 0 : current.revision();
    if (input.expectedRevision() != revision)
      throw new DomainException(
              409,
              "PROGRESS_CONFLICT",
              "Tiến độ đã thay đổi trên thiết bị khác. Tải lại hoặc xác nhận ghi lại vị trí đang"
                  + " đọc.")
          .withCurrent(current);
    if (revision == Long.MAX_VALUE)
      throw new DomainException(409, "REVISION_LIMIT", "Tiến độ đã đạt giới hạn revision.");
    anchors.requireValid(actor, chapter, version, page);
    var offset = input.relativeOffset().setScale(8, RoundingMode.HALF_UP);
    if (current == null)
      jdbc.update(
          "INSERT"
              + " dbo.ReadingProgress(UserId,ChapterId,VersionId,PageId,RelativeOffset,Completed,Revision,LastMutationId)"
              + " VALUES(?,?,?,?,?,?,1,?)",
          actor.userId(),
          chapter,
          version,
          page,
          offset,
          input.completed(),
          mutation);
    else
      jdbc.update(
          "UPDATE dbo.ReadingProgress SET"
              + " VersionId=?,PageId=?,RelativeOffset=?,Completed=?,Revision=Revision+1,LastMutationId=?,LastReadAt=SYSUTCDATETIME()"
              + " WHERE UserId=? AND ChapterId=?",
          version,
          page,
          offset,
          input.completed(),
          mutation,
          actor.userId(),
          chapter);
    Progress result = find(actor, chapter, generation);
    jdbc.update(
        "INSERT"
            + " dbo.ProgressMutation(UserId,MutationId,ChapterId,HistoryGeneration,PayloadHash,ResultRevision,ResultSnapshot)"
            + " VALUES(?,?,?,?,?,?,?)",
        actor.userId(),
        mutation,
        chapter,
        generation,
        hash,
        result.revision(),
        json.writeValueAsString(result));
    return result;
  }

  @Transactional
  public HistoryCleared clear(Identity actor) {
    self(actor, "library.self");
    int generation = generation(actor.userId());
    if (generation == Integer.MAX_VALUE)
      throw new DomainException(409, "GENERATION_LIMIT", "Lịch sử đã đạt giới hạn generation.");
    jdbc.update("DELETE dbo.ReadingProgress WHERE UserId=?", actor.userId());
    // Keep idempotency tombstones for their retention period, erase old chapter/position snapshots.
    jdbc.update(
        "UPDATE dbo.ProgressMutation SET ChapterId=NULL,ResultSnapshot=NULL,State='INVALIDATED'"
            + " WHERE UserId=?",
        actor.userId());
    jdbc.update(
        "UPDATE dbo.ReadingHistoryState SET HistoryGeneration=HistoryGeneration+1 WHERE UserId=?",
        actor.userId());
    return new HistoryCleared(generation + 1);
  }

  private String uuid(String value) {
    try {
      var id = UUID.fromString(value);
      if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
      return id.toString();
    } catch (Exception e) {
      throw new DomainException(400, "INVALID_MUTATION_ID", "Mã thao tác cần theo định dạng UUID.");
    }
  }

  private String hash(long chapter, ProgressPut input) {
    String payload =
        json.writeValueAsString(
            Map.of(
                "chapterId",
                chapter + "",
                "versionId",
                input.versionId(),
                "pageId",
                input.pageId(),
                "relativeOffset",
                input.relativeOffset().stripTrailingZeros().toString(),
                "completed",
                input.completed(),
                "expectedRevision",
                input.expectedRevision(),
                "historyGeneration",
                input.historyGeneration()));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private void ensurePreference(long user) {
    if (jdbc.queryForList(
            "SELECT UserId FROM dbo.ReadingPreference WITH (UPDLOCK,HOLDLOCK) WHERE UserId=?",
            Long.class,
            user)
        .isEmpty()) jdbc.update("INSERT dbo.ReadingPreference(UserId) VALUES(?)", user);
  }

  @Transactional
  public Preference preference(Identity actor) {
    self(actor, "profile.self");
    ensurePreference(actor.userId());
    return preferenceRow(actor.userId());
  }

  private Preference preferenceRow(long user) {
    return jdbc.queryForObject(
        "SELECT Mode,Direction,Fit FROM dbo.ReadingPreference WHERE UserId=?",
        (rs, n) -> new Preference(rs.getString(1), rs.getString(2), rs.getString(3)),
        user);
  }

  public byte[] preferenceToken(Identity actor) {
    self(actor, "profile.self");
    return jdbc.queryForObject(
        "SELECT RowVersion FROM dbo.ReadingPreference WHERE UserId=?",
        byte[].class,
        actor.userId());
  }

  @Transactional
  public Preference preference(Identity actor, Preference input, String etag) {
    self(actor, "profile.self");
    ensurePreference(actor.userId());
    Etags.require(etag, preferenceToken(actor));
    jdbc.update(
        "UPDATE dbo.ReadingPreference SET Mode=?,Direction=?,Fit=? WHERE UserId=?",
        input.mode(),
        input.direction(),
        input.fit(),
        actor.userId());
    return preferenceRow(actor.userId());
  }
}
