package com.comicnet.publishing.service;

import com.comicnet.identity.api.*;
import com.comicnet.catalog.api.CatalogAccess;
import com.comicnet.common.web.*;
import com.comicnet.creator.api.CreatorDirectory;
import com.comicnet.observability.api.*;
import com.comicnet.publishing.api.PublishingDtos.*;
import com.comicnet.publishing.repository.PublishingRepository;
import com.comicnet.storage.api.AssetStore;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class PublishingService {
  private final JdbcTemplate jdbc;
  private final PublishingRepository data;
  private final PublishingScope scopes;
  private final CatalogAccess catalog;
  private final CreatorDirectory teams;
  private final Idempotency idempotency;
  private final AuditWriter audit;
  private final EventOutbox events;
  private final AssetStore assets;
  private final PartialUpdates patches;

  public PublishingService(
      JdbcTemplate jdbc,
      PublishingRepository data,
      PublishingScope scopes,
      CatalogAccess catalog,
      CreatorDirectory teams,
      Idempotency idempotency,
      AuditWriter audit,
      EventOutbox events,
      AssetStore assets,
      PartialUpdates patches) {
    this.jdbc = jdbc;
    this.data = data;
    this.scopes = scopes;
    this.catalog = catalog;
    this.teams = teams;
    this.idempotency = idempotency;
    this.audit = audit;
    this.events = events;
    this.assets = assets;
    this.patches = patches;
  }

  public byte[] chapterToken(long id) {
    return data.chapterToken(id);
  }

  public byte[] versionToken(long id) {
    return data.versionToken(id);
  }

  public byte[] volumeToken(long id) {
    return data.volumeToken(id);
  }

  public Chapter chapter(Identity actor, long id) {
    scopes.chapter(actor, id, "chapter.preview", false);
    return data.chapter(id);
  }

  public Volume volume(Identity actor, long id) {
    var volume = data.volume(id);
    catalog.scopedTranslation(actor, Ids.parse(volume.translationId()), "chapter.preview", false);
    return volume;
  }

  public ChapterVersion version(Identity actor, long id) {
    scopes.version(actor, id, "chapter.preview", false);
    return data.version(id);
  }

  public PageResult<ChapterVersion> versions(Identity actor, long chapter, int page, int size) {
    scopes.chapter(actor, chapter, "chapter.preview", false);
    PageResult.validate(page, size);
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.ChapterVersion WHERE ChapterId=?", Long.class, chapter);
    var ids =
        jdbc.queryForList(
            "SELECT Id FROM dbo.ChapterVersion WHERE ChapterId=? ORDER BY VersionNumber DESC OFFSET"
                + " ? ROWS FETCH NEXT ? ROWS ONLY",
            Long.class,
            chapter,
            PageResult.offset(page, size),
            size);
    return PageResult.of(ids.stream().map(data::version).toList(), page, size, count);
  }

  @Transactional(readOnly = true)
  public PageResult<Volume> volumes(
      Identity actor, long translation, int page, int size, boolean managed) {
    if (managed) catalog.scopedTranslation(actor, translation, "chapter.preview", false);
    else catalog.publicTranslation(translation, false);
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.Volume WHERE TranslationId=?", Long.class, translation);
    return PageResult.of(
        jdbc.query(
            "SELECT * FROM dbo.Volume WHERE TranslationId=? ORDER BY SortOrder,Id OFFSET ? ROWS"
                + " FETCH NEXT ? ROWS ONLY",
            data.volumeMapper(),
            translation,
            PageResult.offset(page, size),
            size),
        page,
        size,
        count);
  }

  @Transactional(readOnly = true)
  public PageResult<Chapter> chapters(Identity actor, long translation, int page, int size) {
    catalog.scopedTranslation(actor, translation, "chapter.preview", false);
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.Chapter WHERE TranslationId=? AND DeletedAt IS NULL",
            Long.class,
            translation);
    return PageResult.of(
        jdbc.query(
            "SELECT * FROM dbo.Chapter WHERE TranslationId=? AND DeletedAt IS NULL ORDER BY"
                + " SortOrder,Id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            data.chapterMapper(),
            translation,
            PageResult.offset(page, size),
            size),
        page,
        size,
        count);
  }

  @Transactional(readOnly = true)
  public PageResult<ChapterSummary> publicChapters(long translation, int page, int size) {
    catalog.publicTranslation(translation, false);
    String predicate =
        " WHERE c.TranslationId=? AND c.DeletedAt IS NULL AND c.Visibility='PUBLIC' AND"
            + " v.State='PUBLISHED'";
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.Chapter c JOIN dbo.ChapterVersion v ON"
                + " v.Id=c.PublishedVersionId"
                + predicate,
            Long.class,
            translation);
    var rows =
        jdbc.query(
            "SELECT c.*,(SELECT COUNT(*) FROM dbo.ChapterVersionPage p WHERE p.VersionId=v.Id) AS"
                + " PageCount FROM dbo.Chapter c JOIN dbo.ChapterVersion v ON"
                + " v.Id=c.PublishedVersionId"
                + predicate
                + " ORDER BY c.SortOrder,c.Id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            (rs, n) ->
                new ChapterSummary(
                    data.id(rs, "Id"),
                    rs.getString("DisplayNumber"),
                    rs.getString("Title"),
                    rs.getBigDecimal("SortOrder").stripTrailingZeros().toPlainString(),
                    rs.getInt("PageCount"),
                    rs.getString("AccessPolicy"),
                    data.instant(rs, "PublishedAt")),
            translation,
            PageResult.offset(page, size),
            size);
    return PageResult.of(rows, page, size, count);
  }

  @Transactional
  public Volume createVolume(Identity actor, long translation, VolumeInput input, String key) {
    catalog.scopedTranslation(actor, translation, "chapter.draft", true);
    long id =
        idempotency.create(
            actor.userId(),
            "createVolume",
            translation + "",
            key,
            input,
            () -> {
              long created =
                  jdbc.queryForObject(
                      "INSERT dbo.Volume(TranslationId,DisplayLabel,SortOrder) OUTPUT INSERTED.Id"
                          + " VALUES(?,?,?)",
                      Long.class,
                      translation,
                      input.displayLabel().strip(),
                      new BigDecimal(input.sortOrder()));
              audit.record(actor.userId(), "VOLUME_CREATED", "Volume", created + "", "SUCCESS");
              return created;
            });
    return data.volume(id);
  }

  @Transactional
  public Volume patchVolume(Identity actor, long id, JsonNode patch, String etag) {
    var current = data.volume(id);
    catalog.scopedTranslation(actor, Ids.parse(current.translationId()), "chapter.draft", true);
    jdbc.queryForMap("SELECT Id FROM dbo.Volume WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", id);
    Etags.require(etag, data.volumeToken(id));
    current = data.volume(id);
    var input =
        patches.merge(
            new VolumeInput(current.displayLabel(), current.sortOrder()), patch, VolumeInput.class);
    jdbc.update(
        "UPDATE dbo.Volume SET DisplayLabel=?,SortOrder=? WHERE Id=?",
        input.displayLabel().strip(),
        new BigDecimal(input.sortOrder()),
        id);
    audit.record(actor.userId(), "VOLUME_UPDATED", "Volume", id + "", "SUCCESS");
    return data.volume(id);
  }

  private void volumeParent(String volume, long translation) {
    if (volume != null
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.Volume WHERE Id=? AND TranslationId=?",
                Integer.class,
                Ids.parse(volume),
                translation)
            != 1)
      throw new DomainException(
          422, "VOLUME_PARENT_MISMATCH", "Volume không thuộc bản dịch hiện tại.");
  }

  @Transactional
  public Chapter createChapter(Identity actor, long translation, ChapterInput input, String key) {
    catalog.scopedTranslation(actor, translation, "chapter.draft", true);
    volumeParent(input.volumeId(), translation);
    long id =
        idempotency.create(
            actor.userId(),
            "createChapter",
            translation + "",
            key,
            input,
            () -> {
              long created =
                  jdbc.queryForObject(
                      "INSERT"
                          + " dbo.Chapter(TranslationId,VolumeId,DisplayNumber,Title,SortOrder,AccessPolicy)"
                          + " OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?)",
                      Long.class,
                      translation,
                      input.volumeId() == null ? null : Ids.parse(input.volumeId()),
                      input.displayNumber().strip(),
                      input.title().strip(),
                      new BigDecimal(input.sortOrder()),
                      input.accessPolicy());
              audit.record(actor.userId(), "CHAPTER_CREATED", "Chapter", created + "", "SUCCESS");
              return created;
            });
    return data.chapter(id);
  }

  @Transactional
  public Chapter patchChapter(Identity actor, long id, JsonNode patch, String etag) {
    scopes.chapter(actor, id, "chapter.draft", true);
    Etags.require(etag, data.chapterToken(id));
    var current = data.chapter(id);
    var input =
        patches.merge(
            new ChapterInput(
                current.volumeId(),
                current.displayNumber(),
                current.title(),
                current.sortOrder(),
                current.accessPolicy()),
            patch,
            ChapterInput.class);
    volumeParent(input.volumeId(), Ids.parse(current.translationId()));
    jdbc.update(
        "UPDATE dbo.Chapter SET VolumeId=?,DisplayNumber=?,Title=?,SortOrder=?,AccessPolicy=? WHERE"
            + " Id=?",
        input.volumeId() == null ? null : Ids.parse(input.volumeId()),
        input.displayNumber().strip(),
        input.title().strip(),
        new BigDecimal(input.sortOrder()),
        input.accessPolicy(),
        id);
    audit.record(actor.userId(), "CHAPTER_UPDATED", "Chapter", id + "", "SUCCESS");
    return data.chapter(id);
  }

  @Transactional
  public ChapterVersion createVersion(
      Identity actor, long chapter, VersionCreate input, String key) {
    scopes.chapter(actor, chapter, "chapter.draft", true);
    long id =
        idempotency.create(
            actor.userId(),
            "createChapterVersion",
            chapter + "",
            key,
            input,
            () -> {
              if (jdbc.queryForObject(
                      "SELECT COUNT(*) FROM dbo.ChapterVersion WHERE ChapterId=? AND"
                          + " IsWorkingCopy=1",
                      Integer.class,
                      chapter)
                  > 0)
                throw new DomainException(
                    409, "WORKING_COPY_EXISTS", "Chapter đã có version đang làm việc.");
              String source =
                  input.copyFromVersionId() == null
                      ? data.chapter(chapter).publishedVersionId()
                      : input.copyFromVersionId();
              Long copy = source == null ? null : Long.valueOf(Ids.parse(source));
              if (copy != null
                  && jdbc.queryForObject(
                          "SELECT COUNT(*) FROM dbo.ChapterVersion WHERE Id=? AND ChapterId=? AND"
                              + " IsWorkingCopy=0",
                          Integer.class,
                          copy,
                          chapter)
                      != 1)
                throw new DomainException(
                    422,
                    "VERSION_PARENT_MISMATCH",
                    "Version nguồn không phải snapshot của chapter này.");
              long previous =
                  jdbc.queryForObject(
                      "SELECT COALESCE(MAX(CONVERT(bigint,VersionNumber)),0) FROM"
                          + " dbo.ChapterVersion WHERE ChapterId=?",
                      Long.class,
                      chapter);
              if (previous >= Integer.MAX_VALUE)
                throw new DomainException(409, "VERSION_NUMBER_LIMIT", "Đã đạt giới hạn version.");
              long created =
                  jdbc.queryForObject(
                      "INSERT"
                          + " dbo.ChapterVersion(ChapterId,VersionNumber,State,IsWorkingCopy,CreatedBy)"
                          + " OUTPUT INSERTED.Id VALUES(?,?,'DRAFT',1,?)",
                      Long.class,
                      chapter,
                      previous + 1,
                      actor.userId());
              if (copy != null)
                jdbc.update(
                    "INSERT dbo.ChapterVersionPage(ChapterId,VersionId,PageId,Position,AssetId)"
                        + " SELECT ChapterId,?,PageId,Position,AssetId FROM dbo.ChapterVersionPage"
                        + " WHERE VersionId=?",
                    created,
                    copy);
              audit.record(
                  actor.userId(), "VERSION_CREATED", "ChapterVersion", created + "", "SUCCESS");
              return created;
            });
    return data.version(id);
  }

  @Transactional
  public ChapterVersion reorder(Identity actor, long id, Reorder input, String etag) {
    scopes.version(actor, id, "chapter.upload", true);
    Etags.require(etag, data.versionToken(id));
    data.requireDraft(id);
    List<Long> requested = input.pageIds().stream().map(Ids::parse).toList();
    var existing = data.pages(id).stream().map(p -> Ids.parse(p.pageId())).toList();
    if (requested.isEmpty()
        || requested.size() != existing.size()
        || new HashSet<>(requested).size() != requested.size()
        || !new HashSet<>(existing).equals(new HashSet<>(requested)))
      throw new DomainException(
          422, "INVALID_PAGE_ORDER", "Cần danh sách đầy đủ các trang, không thiếu hoặc trùng.");
    jdbc.update("UPDATE dbo.ChapterVersionPage SET Position=Position+1000 WHERE VersionId=?", id);
    for (int i = 0; i < requested.size(); i++)
      jdbc.update(
          "UPDATE dbo.ChapterVersionPage SET Position=? WHERE VersionId=? AND PageId=?",
          i + 1,
          id,
          requested.get(i));
    data.touchVersion(id);
    audit.record(actor.userId(), "PAGES_REORDERED", "ChapterVersion", id + "", "SUCCESS");
    return data.version(id);
  }

  @Transactional
  public void removePage(Identity actor, long version, long page, String etag) {
    scopes.version(actor, version, "chapter.upload", true);
    Etags.require(etag, data.versionToken(version));
    data.requireDraft(version);
    if (jdbc.update("DELETE dbo.ChapterVersionPage WHERE VersionId=? AND PageId=?", version, page)
        == 0) throw DomainException.missing();
    var remaining = data.pages(version);
    jdbc.update(
        "UPDATE dbo.ChapterVersionPage SET Position=Position+1000 WHERE VersionId=?", version);
    for (int i = 0; i < remaining.size(); i++)
      jdbc.update(
          "UPDATE dbo.ChapterVersionPage SET Position=? WHERE VersionId=? AND PageId=?",
          i + 1,
          version,
          Ids.parse(remaining.get(i).pageId()));
    data.touchVersion(version);
    audit.record(actor.userId(), "PAGE_BINDING_REMOVED", "ChapterVersion", version + "", "SUCCESS");
  }

  private void readyPages(long id) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.ChapterVersionPage WHERE VersionId=?", Integer.class, id)
        < 1) throw new DomainException(409, "VERSION_EMPTY", "Version chưa có trang.");
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.ChapterVersionPage p JOIN dbo.DigitalAsset a ON"
                + " a.Id=p.AssetId WHERE p.VersionId=? AND (a.Status<>'READY' OR (SELECT"
                + " COUNT(DISTINCT d.Format) FROM dbo.AssetVariant d WHERE d.AssetId=a.Id AND"
                + " d.Format IN ('JPEG','PNG'))<>2)",
            Integer.class,
            id)
        > 0)
      throw new DomainException(409, "ASSETS_NOT_READY", "Có ảnh chưa sẵn sàng để xuất bản.");
  }

  @Transactional
  public ChapterVersion submit(Identity actor, long id, String etag) {
    scopes.version(actor, id, "chapter.submit", true);
    Etags.require(etag, data.versionToken(id));
    data.requireDraft(id);
    readyPages(id);
    jdbc.update(
        "UPDATE dbo.ChapterVersion SET State='READY_FOR_REVIEW',SubmittedBy=? WHERE Id=?",
        actor.userId(),
        id);
    audit.record(actor.userId(), "VERSION_SUBMITTED", "ChapterVersion", id + "", "SUCCESS");
    return data.version(id);
  }

  public PageResult<ChapterVersion> reviewQueue(Identity actor, int page, int size) {
    scopes.reviewer(actor);
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.ChapterVersion WHERE State='READY_FOR_REVIEW'",
            Long.class);
    var ids =
        jdbc.queryForList(
            "SELECT Id FROM dbo.ChapterVersion WHERE State='READY_FOR_REVIEW' ORDER BY Id OFFSET ?"
                + " ROWS FETCH NEXT ? ROWS ONLY",
            Long.class,
            PageResult.offset(page, size),
            size);
    return PageResult.of(ids.stream().map(data::version).toList(), page, size, count);
  }

  @Transactional
  public ChapterVersion review(Identity actor, long id, Review input, String etag) {
    scopes.reviewer(actor);
    scopes.version(actor, id, "chapter.preview", true);
    Etags.require(etag, data.versionToken(id));
    var version = data.version(id);
    if (!version.state().equals("READY_FOR_REVIEW"))
      throw new DomainException(
          409, "VERSION_NOT_REVIEWABLE", "Version không ở trạng thái chờ duyệt.");
    if (Ids.parse(version.createdBy()) == actor.userId()
        || version.submittedBy() != null && Ids.parse(version.submittedBy()) == actor.userId())
      throw new DomainException(
          403, "SELF_REVIEW_DENIED", "Người tạo hoặc gửi version không được tự duyệt.");
    boolean approved = input.decision().equals("APPROVE");
    if (!approved && (input.reason() == null || input.reason().isBlank()))
      throw new DomainException(400, "REVIEW_REASON_REQUIRED", "Từ chối duyệt cần lý do.");
    readyPages(id);
    jdbc.update(
        "UPDATE dbo.ChapterVersion SET"
            + " State=?,IsWorkingCopy=?,ReviewedBy=?,ReviewedAt=SYSUTCDATETIME(),ReviewReason=?"
            + " WHERE Id=?",
        approved ? "APPROVED" : "REJECTED",
        approved,
        actor.userId(),
        input.reason(),
        id);
    audit.record(
        actor.userId(),
        approved ? "VERSION_APPROVED" : "VERSION_REJECTED",
        "ChapterVersion",
        id + "",
        "SUCCESS",
        Map.of("reason", input.reason() == null ? "" : input.reason()));
    return data.version(id);
  }

  @Transactional
  public Chapter publish(Identity actor, long chapter, Publish input, String etag) {
    var parent = scopes.chapter(actor, chapter, "chapter.publish", true);
    Etags.require(etag, data.chapterToken(chapter));
    long id = Ids.parse(input.versionId());
    if (Ids.parse(data.version(id).chapterId()) != chapter)
      throw new DomainException(422, "VERSION_PARENT_MISMATCH", "Version không thuộc chapter này.");
    scopes.version(actor, id, "chapter.publish", true);
    var version = data.version(id);
    if (!version.state().equals("APPROVED")
        || parent.comicVisibility().equals("ARCHIVED")
        || parent.translationVisibility().equals("ARCHIVED"))
      throw new DomainException(
          409,
          "VERSION_NOT_PUBLISHABLE",
          "Chỉ xuất bản snapshot đã duyệt trong nội dung chưa lưu trữ.");
    publishSnapshot(chapter, id, actor.userId(), "CHAPTER_PUBLISHED");
    return data.chapter(chapter);
  }

  /** Shared publish transaction body for manual publish, rollback and scheduled release. */
  private void publishSnapshot(long chapter, long version, Long actor, String action) {
    readyPages(version);
    if (data.activeUpload(version))
      throw new DomainException(409, "UPLOAD_ACTIVE", "Đợi upload hoàn tất trước khi xuất bản.");
    var scopes =
        jdbc.queryForList(
            "SELECT c.TranslationId,t.ComicId FROM dbo.Chapter c JOIN dbo.ComicTranslation t ON"
                + " t.Id=c.TranslationId WHERE c.Id=? AND c.DeletedAt IS NULL",
            chapter);
    if (scopes.isEmpty())
      throw new DomainException(409, "CHAPTER_UNAVAILABLE", "Chapter không còn khả dụng.");
    long translation = ((Number) scopes.getFirst().get("TranslationId")).longValue();
    long comic = ((Number) scopes.getFirst().get("ComicId")).longValue();
    var current = data.chapter(chapter);
    if (current.publishedVersionId() != null)
      jdbc.update(
          "UPDATE dbo.ChapterVersion SET State='UNPUBLISHED',IsWorkingCopy=0 WHERE Id=? AND"
              + " State='PUBLISHED'",
          Ids.parse(current.publishedVersionId()));
    data.state(version, "PUBLISHED", false);
    jdbc.update(
        "UPDATE dbo.Chapter SET"
            + " PublishedVersionId=?,PublishedAt=SYSUTCDATETIME(),Visibility='PUBLIC' WHERE Id=?",
        version,
        chapter);
    catalog.publishedContentChanged(comic);
    audit.record(
        actor, action, "Chapter", chapter + "", "SUCCESS", Map.of("versionId", version + ""));
    events.append(
        "ChapterPublished",
        chapter + "",
        Map.of(
            "chapterId", chapter + "",
            "versionId", version + "",
            "translationId", translation + "",
            "comicId", comic + ""));
  }

  @Transactional
  public ScheduleView schedule(Identity actor, long chapter, ScheduleInput input, String etag) {
    var parent = scopes.chapter(actor, chapter, "chapter.publish", true);
    Etags.require(etag, data.chapterToken(chapter));
    long version = Ids.parse(input.versionId());
    if (Ids.parse(data.version(version).chapterId()) != chapter)
      throw new DomainException(422, "VERSION_PARENT_MISMATCH", "Version không thuộc chapter này.");
    scopes.version(actor, version, "chapter.publish", true);
    var snapshot = data.version(version);
    // A scheduled snapshot can be rescheduled until its release time.
    if (!snapshot.state().equals("APPROVED") && !snapshot.state().equals("SCHEDULED"))
      throw new DomainException(
          409, "VERSION_NOT_SCHEDULABLE", "Chỉ lên lịch snapshot đã duyệt.");
    if (parent.comicVisibility().equals("ARCHIVED")
        || parent.translationVisibility().equals("ARCHIVED"))
      throw new DomainException(
          409, "VERSION_NOT_SCHEDULABLE", "Không lên lịch nội dung đã lưu trữ.");
    readyPages(version);
    if (data.activeUpload(version))
      throw new DomainException(409, "UPLOAD_ACTIVE", "Đợi upload hoàn tất trước khi lên lịch.");
    // datetime2 columns hold UTC values; bind UTC LocalDateTime like the upload lease does.
    var at = java.time.LocalDateTime.ofInstant(input.publishAt(), java.time.ZoneOffset.UTC);
    int invalid =
        jdbc.queryForObject(
            "SELECT CASE WHEN ?<DATEADD(minute,1,SYSUTCDATETIME()) OR"
                + " ?>DATEADD(day,90,SYSUTCDATETIME()) THEN 1 ELSE 0 END",
            Integer.class,
            at,
            at);
    if (invalid == 1)
      throw new DomainException(
          422,
          "INVALID_SCHEDULE_TIME",
          "Thời điểm xuất bản phải sau hiện tại ít nhất 1 phút và trong vòng 90 ngày.");
    int changed =
        jdbc.update(
            "UPDATE dbo.ChapterVersion SET State='SCHEDULED',ScheduledAt=? WHERE Id=? AND"
                + " State IN ('APPROVED','SCHEDULED') AND IsWorkingCopy=1",
            at,
            version);
    if (changed != 1)
      throw new DomainException(412, "STALE_VERSION", "Version đã thay đổi. Hãy tải lại.");
    audit.record(
        actor.userId(),
        "CHAPTER_SCHEDULED",
        "Chapter",
        chapter + "",
        "SUCCESS",
        Map.of("versionId", version + "", "publishAt", input.publishAt().toString()));
    events.append(
        "ChapterScheduled",
        chapter + "",
        Map.of("chapterId", chapter + "", "versionId", version + ""));
    return new ScheduleView(version + "", chapter + "", "SCHEDULED", input.publishAt());
  }

  @Transactional
  public void cancelSchedule(Identity actor, long chapter, String etag) {
    scopes.chapter(actor, chapter, "chapter.publish", true);
    Etags.require(etag, data.chapterToken(chapter));
    var rows =
        jdbc.queryForList(
            "SELECT Id,ScheduledAt FROM dbo.ChapterVersion WHERE ChapterId=? AND"
                + " State='SCHEDULED'",
            chapter);
    if (rows.isEmpty())
      throw new DomainException(409, "NO_SCHEDULE", "Chapter không có lịch xuất bản.");
    long version = ((Number) rows.getFirst().get("Id")).longValue();
    jdbc.update(
        "UPDATE dbo.ChapterVersion SET State='APPROVED',ScheduledAt=NULL WHERE Id=? AND"
            + " State='SCHEDULED'",
        version);
    audit.record(
        actor.userId(),
        "CHAPTER_SCHEDULE_CANCELLED",
        "Chapter",
        chapter + "",
        "SUCCESS",
        Map.of("versionId", version + ""));
  }

  @Transactional
  public Chapter rollback(Identity actor, long chapter, RollbackInput input, String etag) {
    var parent = scopes.chapter(actor, chapter, "chapter.publish", true);
    Etags.require(etag, data.chapterToken(chapter));
    long version = Ids.parse(input.versionId());
    scopes.version(actor, version, "chapter.publish", true);
    var snapshot = data.version(version);
    if (Ids.parse(snapshot.chapterId()) != chapter)
      throw new DomainException(422, "VERSION_PARENT_MISMATCH", "Version không thuộc chapter này.");
    if (!snapshot.state().equals("PUBLISHED") && !snapshot.state().equals("UNPUBLISHED"))
      throw new DomainException(
          409, "VERSION_NOT_ROLLBACKABLE", "Chỉ quay lại snapshot đã từng xuất bản.");
    if (parent.comicVisibility().equals("ARCHIVED")
        || parent.translationVisibility().equals("ARCHIVED"))
      throw new DomainException(
          409, "VERSION_NOT_ROLLBACKABLE", "Không quay lại nội dung đã lưu trữ.");
    var current = data.chapter(chapter);
    if (current.publishedVersionId() != null && Ids.parse(current.publishedVersionId()) == version)
      throw new DomainException(
          409, "ALREADY_PUBLISHED", "Snapshot này đang được xuất bản.");
    publishSnapshot(chapter, version, actor.userId(), "CHAPTER_ROLLED_BACK");
    return data.chapter(chapter);
  }

  /**
   * Releases the next due scheduled snapshot inside its own transaction. A snapshot that can no
   * longer be published is failed with a safe reason instead of blocking the schedule queue.
   */
  @Transactional
  public boolean releaseNextScheduled() {
    var rows =
        jdbc.queryForList(
            "SELECT TOP(1) Id,ChapterId FROM dbo.ChapterVersion WITH(UPDLOCK,READPAST) WHERE"
                + " State='SCHEDULED' AND ScheduledAt<=SYSUTCDATETIME() ORDER BY ScheduledAt,Id");
    if (rows.isEmpty()) return false;
    long version = ((Number) rows.getFirst().get("Id")).longValue();
    long chapter = ((Number) rows.getFirst().get("ChapterId")).longValue();
    var archived =
        jdbc.queryForObject(
            "SELECT CASE WHEN t.Visibility='ARCHIVED' OR c2.Visibility='ARCHIVED' OR"
                + " c2.DeletedAt IS NOT NULL THEN 1 ELSE 0 END FROM dbo.Chapter c JOIN"
                + " dbo.ComicTranslation t ON t.Id=c.TranslationId JOIN dbo.Comic c2 ON"
                + " c2.Id=t.ComicId WHERE c.Id=?",
            Integer.class,
            chapter);
    if (archived != null && archived == 1) {
      failSchedule(chapter, version, "CONTENT_ARCHIVED");
      return true;
    }
    try {
      publishSnapshot(chapter, version, null, "CHAPTER_SCHEDULE_RELEASED");
    } catch (DomainException blocked) {
      failSchedule(chapter, version, blocked.code());
    }
    return true;
  }

  private void failSchedule(long chapter, long version, String code) {
    jdbc.update(
        "UPDATE dbo.ChapterVersion SET State='FAILED',ScheduledAt=NULL,ReviewReason=? WHERE Id=?"
            + " AND State='SCHEDULED'",
        "SCHEDULE_BLOCKED:" + code,
        version);
    audit.record(
        null,
        "CHAPTER_SCHEDULE_FAILED",
        "Chapter",
        chapter + "",
        "FAILED",
        Map.of("versionId", version + "", "reason", code));
  }

  @Transactional
  public Chapter unpublish(Identity actor, long chapter, Reason input, String etag) {
    scopes.reviewer(actor);
    var parent = scopes.chapter(actor, chapter, "content.hide", true);
    Etags.require(etag, data.chapterToken(chapter));
    var current = data.chapter(chapter);
    if (current.publishedVersionId() != null)
      jdbc.update(
          "UPDATE dbo.ChapterVersion SET State='UNPUBLISHED',IsWorkingCopy=0 WHERE Id=? AND"
              + " State='PUBLISHED'",
          Ids.parse(current.publishedVersionId()));
    jdbc.update("UPDATE dbo.Chapter SET Visibility='HIDDEN' WHERE Id=?", chapter);
    catalog.publishedContentChanged(parent.comicId());
    audit.record(
        actor.userId(),
        "CHAPTER_UNPUBLISHED",
        "Chapter",
        chapter + "",
        "SUCCESS",
        Map.of("reason", input.reason()));
    events.append("ChapterUnpublished", chapter + "", Map.of("chapterId", chapter + ""));
    return data.chapter(chapter);
  }

  @Transactional
  public Manifest preview(Identity actor, long id, String format) {
    var scope = scopes.version(actor, id, "chapter.preview", true);
    if (!scope.ageRating().equals("ALL_AGE"))
      throw new DomainException(
          403, "AGE_POLICY_PENDING", "Chưa hỗ trợ xem ảnh nội dung giới hạn tuổi.");
    readyPages(id);
    var version = data.version(id);
    List<ManifestPage> pages =
        data.pages(id).stream()
            .map(
                page -> {
                  var image = assets.display(Ids.parse(page.assetId()), format);
                  return new ManifestPage(
                      page.pageId(),
                      page.position(),
                      image.url(),
                      image.expiresAt(),
                      image.width(),
                      image.height(),
                      image.mimeType());
                })
            .toList();
    audit.record(actor.userId(), "VERSION_PREVIEW_OPENED", "ChapterVersion", id + "", "SUCCESS");
    return new Manifest(version.chapterId(), id + "", pages, null, null);
  }
}
