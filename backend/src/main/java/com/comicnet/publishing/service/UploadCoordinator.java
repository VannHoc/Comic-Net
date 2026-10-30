package com.comicnet.publishing.service;

import com.comicnet.identity.api.*;
import com.comicnet.common.web.*;
import com.comicnet.observability.api.*;
import com.comicnet.publishing.api.PublishingDtos.UploadJob;
import com.comicnet.publishing.repository.PublishingRepository;
import com.comicnet.storage.api.StagingStore;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadCoordinator implements com.comicnet.publishing.api.UploadJobs {
  /** Working copies stay editable after a reviewer requests changes, exactly like a fresh draft. */
  private static final Set<String> EDITABLE = Set.of("DRAFT", "FAILED", "NEEDS_CHANGES");
  private final JdbcTemplate jdbc;
  private final PublishingRepository data;
  private final PublishingScope scopes;
  private final StagingStore staging;
  private final TransactionTemplate transactions;
  private final Idempotency idempotency;
  private final AuditWriter audit;

  public UploadCoordinator(
      JdbcTemplate jdbc,
      PublishingRepository data,
      PublishingScope scopes,
      StagingStore staging,
      TransactionTemplate transactions,
      Idempotency idempotency,
      AuditWriter audit) {
    this.jdbc = jdbc;
    this.data = data;
    this.scopes = scopes;
    this.staging = staging;
    this.transactions = transactions;
    this.idempotency = idempotency;
    this.audit = audit;
  }

  public UploadJob get(Identity actor, long id) {
    var job = data.job(id);
    scopes.version(actor, Ids.parse(job.versionId()), "chapter.preview", false);
    return job;
  }

  public PageResult<UploadJob> list(Identity actor, int page, int size) {
    if (actor == null || !actor.administrator()) throw DomainException.forbidden();
    long count = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.UploadJob", Long.class);
    return PageResult.of(
        jdbc.query(
            "SELECT * FROM dbo.UploadJob ORDER BY CreatedAt DESC,Id DESC OFFSET ? ROWS FETCH NEXT ?"
                + " ROWS ONLY",
            data.jobMapper(),
            PageResult.offset(page, size),
            size),
        page,
        size,
        count);
  }

  public UploadJob accept(
      Identity actor,
      long version,
      MultipartFile file,
      String replacePage,
      String etag,
      String key) {
    scopes.version(actor, version, "chapter.upload", false);
    if (file.getSize() < 1 || file.getSize() > 100L * 1024 * 1024)
      throw new DomainException(413, "UPLOAD_SIZE_LIMIT", "Tệp tải lên cần từ 1 byte đến 100 MiB.");
    String hash;
    try (var input = file.getInputStream()) {
      hash = checksum(input, 100L * 1024 * 1024);
    } catch (IOException e) {
      throw new DomainException(422, "UPLOAD_READ_FAILED", "Không thể đọc tệp tải lên.");
    }
    Long id =
        transactions.execute(
            status -> {
              scopes.version(actor, version, "chapter.upload", true);
              return idempotency.create(
                  actor.userId(),
                  "uploadChapterPages",
                  version + "",
                  key,
                  Map.of("sha256", hash, "pageId", replacePage == null ? "" : replacePage),
                  () -> {
                    var current = data.version(version);
                    if (!EDITABLE.contains(current.state()) || data.activeUpload(version))
                      throw new DomainException(
                          409, "VERSION_NOT_EDITABLE", "Version đang xử lý hoặc đã khóa nội dung.");
                    Etags.require(etag, data.versionToken(version));
                    if (replacePage != null
                        && jdbc.queryForObject(
                                "SELECT COUNT(*) FROM dbo.ChapterVersionPage WHERE VersionId=? AND"
                                    + " PageId=?",
                                Integer.class,
                                version,
                                Ids.parse(replacePage))
                            != 1)
                      throw new DomainException(
                          422, "PAGE_PARENT_MISMATCH", "Trang thay thế không thuộc version này.");
                    long created =
                        jdbc.queryForObject(
                            "INSERT"
                                + " dbo.UploadJob(ChapterId,VersionId,RequestedBy,ClientRequestKey,Status,IsActive,StagingKey,ReplacePageId,SourceHash,SourceSize)"
                                + " OUTPUT INSERTED.Id VALUES(?,?,?,?,'QUEUED',1,?,?,?,?)",
                            Long.class,
                            Ids.parse(current.chapterId()),
                            version,
                            actor.userId(),
                            key,
                            "staging/" + UUID.randomUUID(),
                            replacePage == null ? null : Ids.parse(replacePage),
                            hash,
                            file.getSize());
                    data.state(version, "UPLOADING", true);
                    audit.record(
                        actor.userId(), "UPLOAD_ACCEPTED", "UploadJob", created + "", "SUCCESS");
                    return created;
                  });
            });
    var row =
        jdbc.queryForMap("SELECT Status,StagingReady,StagingKey FROM dbo.UploadJob WHERE Id=?", id);
    if (Boolean.TRUE.equals(row.get("StagingReady"))
        || !Set.of("QUEUED", "FAILED").contains(row.get("Status"))) return data.job(id);
    boolean needsStaging =
        Boolean.TRUE.equals(
            transactions.execute(
                status -> {
                  scopes.version(actor, version, "chapter.upload", true);
                  var latest =
                      jdbc.queryForMap(
                          "SELECT Status,StagingReady FROM dbo.UploadJob WITH (UPDLOCK,HOLDLOCK)"
                              + " WHERE Id=?",
                          id);
                  if (Boolean.TRUE.equals(latest.get("StagingReady"))
                      || !Set.of("QUEUED", "FAILED").contains(latest.get("Status"))) return false;
                  if (latest.get("Status").equals("FAILED")) {
                    if (!EDITABLE.contains(data.version(version).state())) return false;
                    if (data.activeUpload(version))
                      throw new DomainException(
                          409, "UPLOAD_ACTIVE", "Version đang có một upload khác.");
                    jdbc.update(
                        "UPDATE dbo.UploadJob SET"
                            + " Status='QUEUED',IsActive=1,ErrorCode=NULL,MaxAttempts=Attempts+3"
                            + " WHERE Id=?",
                        id);
                    data.state(version, "UPLOADING", true);
                  }
                  jdbc.update("UPDATE dbo.UploadJob SET UpdatedAt=SYSUTCDATETIME() WHERE Id=?", id);
                  return true;
                }));
    if (!needsStaging) return data.job(id);
    try (var input = file.getInputStream()) {
      staging.put((String) row.get("StagingKey"), input, file.getSize());
      transactions.executeWithoutResult(
          status -> {
            scopes.version(actor, version, "chapter.upload", true);
            var latest =
                jdbc.queryForMap(
                    "SELECT Status,StagingReady FROM dbo.UploadJob WITH (UPDLOCK,HOLDLOCK) WHERE"
                        + " Id=?",
                    id);
            if (Boolean.TRUE.equals(latest.get("StagingReady"))) return;
            if (!latest.get("Status").equals("QUEUED"))
              throw new DomainException(
                  409,
                  "UPLOAD_STAGE_EXPIRED",
                  "Upload đã hết thời gian staging. Thử lại với cùng mã thao tác.");
            jdbc.update(
                "UPDATE dbo.UploadJob SET StagingReady=1,UpdatedAt=SYSUTCDATETIME() WHERE Id=?",
                id);
            data.state(version, "PROCESSING", true);
          });
    } catch (Exception error) {
      // The durable reservation survives a transport failure and can be restaged with the same key.
      transactions.executeWithoutResult(
          status -> {
            scopes.lockForJob(version);
            int changed =
                jdbc.update(
                    "UPDATE dbo.UploadJob SET"
                        + " Status='FAILED',IsActive=0,ErrorCode='STAGING_FAILED',UpdatedAt=SYSUTCDATETIME()"
                        + " WHERE Id=? AND StagingReady=0 AND Status='QUEUED'",
                    id);
            if (changed > 0
                && !data.activeUpload(version)
                && Set.of("UPLOADING", "PROCESSING").contains(data.version(version).state()))
              data.state(version, "FAILED", true);
          });
    }
    return data.job(id);
  }

  public UploadJob retry(Identity actor, long id, String key) {
    var job = data.job(id);
    long version = Ids.parse(job.versionId());
    transactions.executeWithoutResult(
        status -> {
          scopes.version(actor, version, "chapter.upload", true);
          idempotency.create(
              actor.userId(),
              "retryUploadJob",
              id + "",
              key,
              Map.of("jobId", id + ""),
              () -> {
                var current = data.job(id);
                if (!current.status().equals("FAILED")
                    || data.activeUpload(version)
                    || !EDITABLE.contains(data.version(version).state()))
                  throw new DomainException(
                      409,
                      "JOB_NOT_RETRYABLE",
                      "Chỉ retry job lỗi khi version còn chỉnh sửa được và không có upload khác.");
                var row =
                    jdbc.queryForMap(
                        "SELECT StagingKey,StagingReady FROM dbo.UploadJob WHERE Id=?", id);
                if (!Boolean.TRUE.equals(row.get("StagingReady"))
                    || !staging.exists((String) row.get("StagingKey")))
                  throw new DomainException(
                      422, "STAGING_MISSING", "Tải lại tệp gốc bằng mã thao tác upload ban đầu.");
                jdbc.update(
                    "UPDATE dbo.UploadJob SET"
                        + " Status='QUEUED',IsActive=1,MaxAttempts=Attempts+3,LeaseOwner=NULL,LeaseUntil=NULL,NextAttemptAt=SYSUTCDATETIME(),ErrorCode=NULL,UpdatedAt=SYSUTCDATETIME()"
                        + " WHERE Id=?",
                    id);
                data.state(version, "PROCESSING", true);
                audit.record(actor.userId(), "UPLOAD_RETRIED", "UploadJob", id + "", "SUCCESS");
                return id;
              });
        });
    return data.job(id);
  }

  static String checksum(InputStream input, long limit) throws IOException {
    try {
      var hash = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[16384];
      long total = 0;
      int size;
      while ((size = input.read(buffer)) >= 0) {
        total += size;
        if (total > limit)
          throw new DomainException(413, "UPLOAD_SIZE_LIMIT", "Tệp tải lên vượt giới hạn.");
        hash.update(buffer, 0, size);
      }
      return HexFormat.of().formatHex(hash.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
