package com.comicnet.publishing.service;

import com.comicnet.identity.api.*;
import com.comicnet.common.web.*;
import com.comicnet.observability.api.AuditWriter;
import com.comicnet.publishing.repository.PublishingRepository;
import com.comicnet.storage.api.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** SQL-backed single-process worker; lease fencing also supports recovery after restart. */
@Component
public class UploadWorker {
  private final JdbcTemplate jdbc;
  private final PublishingRepository data;
  private final PublishingScope scopes;
  private final IdentityService identities;
  private final StagingStore staging;
  private final AssetStore assets;
  private final ArchiveInput archives;
  private final TransactionTemplate transactions;
  private final AuditWriter audit;
  private final Environment env;

  private record Work(
      long id,
      long version,
      long user,
      String lease,
      String staging,
      String checksum,
      Long replacePage,
      int attempts,
      int maxAttempts,
      boolean staged) {}

  public UploadWorker(
      JdbcTemplate jdbc,
      PublishingRepository data,
      PublishingScope scopes,
      IdentityService identities,
      StagingStore staging,
      AssetStore assets,
      ArchiveInput archives,
      TransactionTemplate transactions,
      AuditWriter audit,
      Environment env) {
    this.jdbc = jdbc;
    this.data = data;
    this.scopes = scopes;
    this.identities = identities;
    this.staging = staging;
    this.assets = assets;
    this.archives = archives;
    this.transactions = transactions;
    this.audit = audit;
    this.env = env;
  }

  @Scheduled(fixedDelayString = "${comicnet.upload.poll-delay:2000}")
  public void scheduled() {
    if (env.getProperty("comicnet.upload.worker-enabled", Boolean.class, true)) {
      processNext();
      cleanupSucceeded();
    }
  }

  public boolean processNext() {
    return process(null);
  }

  public boolean processJob(long id) {
    return process(id);
  }

  private boolean process(Long requestedJob) {
    Work work =
        transactions.execute(
            status -> {
              var rows =
                  jdbc.queryForList(
                      "SELECT TOP(1) * FROM dbo.UploadJob WITH (UPDLOCK,READPAST,READCOMMITTEDLOCK)"
                          + " WHERE ((Status IN ('QUEUED','RETRY_WAIT') AND"
                          + " NextAttemptAt<=SYSUTCDATETIME() AND (StagingReady=1 OR"
                          + " UpdatedAt<DATEADD(minute,-2,SYSUTCDATETIME()))) OR (Status='RUNNING'"
                          + " AND LeaseUntil<SYSUTCDATETIME()))"
                          + (requestedJob == null ? "" : " AND Id=?")
                          + " ORDER BY Id",
                      requestedJob == null ? new Object[0] : new Object[] {requestedJob});
              if (rows.isEmpty()) return null;
              var row = rows.getFirst();
              String lease = UUID.randomUUID().toString();
              int attempts = ((Number) row.get("Attempts")).intValue(),
                  max = ((Number) row.get("MaxAttempts")).intValue();
              int next = attempts + 1;
              long id = ((Number) row.get("Id")).longValue();
              jdbc.update(
                  "UPDATE dbo.UploadJob SET"
                      + " Status='RUNNING',IsActive=1,Attempts=?,LeaseOwner=?,LeaseUntil=DATEADD(second,60,SYSUTCDATETIME()),UpdatedAt=SYSUTCDATETIME()"
                      + " WHERE Id=?",
                  next,
                  lease,
                  id);
              return new Work(
                  id,
                  ((Number) row.get("VersionId")).longValue(),
                  ((Number) row.get("RequestedBy")).longValue(),
                  lease,
                  (String) row.get("StagingKey"),
                  (String) row.get("SourceHash"),
                  row.get("ReplacePageId") == null
                      ? null
                      : ((Number) row.get("ReplacePageId")).longValue(),
                  next,
                  max,
                  Boolean.TRUE.equals(row.get("StagingReady")));
            });
    if (work == null) return false;
    Path temporary = null;
    try {
      actor(work);
      if (!work.staged())
        throw new DomainException(422, "STAGING_MISSING", "Upload staging chưa hoàn tất.");
      if (work.attempts() > work.maxAttempts())
        throw new DomainException(422, "RETRY_EXHAUSTED", "Đã hết lượt tự động xử lý.");
      temporary = Files.createTempFile("comicnet-upload-", ".input");
      staging.download(work.staging(), temporary);
      try (var input = Files.newInputStream(temporary)) {
        if (!UploadCoordinator.checksum(input, 100L * 1024 * 1024).equals(work.checksum()))
          throw new DomainException(422, "STAGING_HASH_MISMATCH", "Tệp staging không toàn vẹn.");
      }
      int remaining = work.replacePage() != null ? 1 : 500 - data.pages(work.version()).size();
      try (var batch = archives.open(temporary, remaining)) {
        if (work.replacePage() != null && batch.size() != 1)
          throw new DomainException(422, "REPLACE_ONE_IMAGE", "Thay trang chỉ nhận một ảnh.");
        for (int index = 0; index < batch.size(); index++) {
          int ordinal = index + 1;
          String name = batch.name(index);
          if (jdbc.queryForObject(
                  "SELECT COUNT(*) FROM dbo.UploadJobItem WHERE JobId=? AND Ordinal=?",
                  Integer.class,
                  work.id(),
                  ordinal)
              == 1) {
            heartbeat(work);
            continue;
          }
          byte[] image = batch.read(index);
          transactions.executeWithoutResult(
              status -> {
                Identity actor = actor(work);
                scopes.version(actor, work.version(), "chapter.upload", true);
                fence(work);
                if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM dbo.UploadJobItem WHERE JobId=? AND Ordinal=?",
                        Integer.class,
                        work.id(),
                        ordinal)
                    == 0) {
                  var asset = assets.storePage(actor.userId(), image);
                  jdbc.update(
                      "INSERT dbo.UploadJobItem(JobId,Ordinal,EntryName,AssetId) VALUES(?,?,?,?)",
                      work.id(),
                      ordinal,
                      name,
                      Ids.parse(asset.id()));
                }
                renew(work);
              });
        }
        complete(work, batch.size());
      }
    } catch (Exception error) {
      String code =
          error instanceof DomainException domain ? domain.code() : "UPLOAD_PROCESSING_FAILED";
      boolean transientFailure = error instanceof DomainException domain && domain.status() == 503;
      fail(work, code, transientFailure);
    } finally {
      if (temporary != null)
        try {
          Files.deleteIfExists(temporary);
        } catch (java.io.IOException ignored) {
          org.slf4j.LoggerFactory.getLogger(UploadWorker.class)
              .warn("Upload temporary-file cleanup pending; job={}", work.id());
        }
    }
    return true;
  }

  private Identity actor(Work work) {
    if (!identities.active(work.user()))
      throw new DomainException(403, "ACTOR_UNAVAILABLE", "Tài khoản gửi upload không hoạt động.");
    // Background principal carries current roles, without minting a login/session token.
    return new Identity(work.user(), 0, 0, identities.roles(work.user()));
  }

  private void fence(Work work) {
    var rows =
        jdbc.queryForList(
            "SELECT Status,LeaseOwner FROM dbo.UploadJob WITH (UPDLOCK,HOLDLOCK) WHERE Id=?",
            work.id());
    if (rows.isEmpty()
        || !rows.getFirst().get("Status").equals("RUNNING")
        || !work.lease().equalsIgnoreCase(String.valueOf(rows.getFirst().get("LeaseOwner"))))
      throw new DomainException(409, "JOB_LEASE_LOST", "Worker không còn sở hữu job này.");
    if (!data.version(work.version()).state().equals("PROCESSING"))
      throw new DomainException(409, "VERSION_NOT_PROCESSING", "Version đã chuyển trạng thái.");
  }

  private void renew(Work work) {
    jdbc.update(
        "UPDATE dbo.UploadJob SET"
            + " LeaseUntil=DATEADD(second,60,SYSUTCDATETIME()),UpdatedAt=SYSUTCDATETIME() WHERE"
            + " Id=? AND LeaseOwner=?",
        work.id(),
        work.lease());
  }

  private void heartbeat(Work work) {
    transactions.executeWithoutResult(
        status -> {
          scopes.version(actor(work), work.version(), "chapter.upload", true);
          fence(work);
          renew(work);
        });
  }

  private void complete(Work work, int expectedItems) {
    transactions.executeWithoutResult(
        status -> {
          Identity actor = actor(work);
          scopes.version(actor, work.version(), "chapter.upload", true);
          fence(work);
          var receipts =
              jdbc.queryForList(
                  "SELECT Ordinal,AssetId FROM dbo.UploadJobItem WHERE JobId=? ORDER BY Ordinal",
                  work.id());
          if (receipts.size() != expectedItems)
            throw new DomainException(409, "UPLOAD_INCOMPLETE", "Lô ảnh chưa được xử lý đầy đủ.");
          for (var receipt : receipts)
            if (!assets.get(((Number) receipt.get("AssetId")).longValue()).status().equals("READY"))
              throw new DomainException(409, "ASSETS_NOT_READY", "Có ảnh chưa sẵn sàng.");
          var version = data.version(work.version());
          long chapter = Ids.parse(version.chapterId());
          int position = data.pages(work.version()).size();
          if (work.replacePage() == null && position + receipts.size() > 500)
            throw new DomainException(413, "PAGE_LIMIT", "Version vượt giới hạn 500 trang.");
          if (work.replacePage() != null) {
            if (receipts.size() != 1
                || jdbc.update(
                        "UPDATE dbo.ChapterVersionPage SET AssetId=? WHERE VersionId=? AND"
                            + " PageId=?",
                        receipts.getFirst().get("AssetId"),
                        work.version(),
                        work.replacePage())
                    != 1)
              throw new DomainException(
                  422, "PAGE_PARENT_MISMATCH", "Trang thay thế không còn trong version.");
            jdbc.update(
                "UPDATE dbo.UploadJobItem SET PageId=? WHERE JobId=? AND Ordinal=1",
                work.replacePage(),
                work.id());
          } else
            for (var receipt : receipts) {
              long page =
                  jdbc.queryForObject(
                      "INSERT dbo.ChapterPage(ChapterId) OUTPUT INSERTED.Id VALUES(?)",
                      Long.class,
                      chapter);
              jdbc.update(
                  "INSERT dbo.ChapterVersionPage(ChapterId,VersionId,PageId,Position,AssetId)"
                      + " VALUES(?,?,?,?,?)",
                  chapter,
                  work.version(),
                  page,
                  ++position,
                  receipt.get("AssetId"));
              jdbc.update(
                  "UPDATE dbo.UploadJobItem SET PageId=? WHERE JobId=? AND Ordinal=?",
                  page,
                  work.id(),
                  receipt.get("Ordinal"));
            }
          jdbc.update(
              "UPDATE dbo.UploadJob SET"
                  + " Status='SUCCEEDED',IsActive=0,LeaseOwner=NULL,LeaseUntil=NULL,ErrorCode=NULL,UpdatedAt=SYSUTCDATETIME()"
                  + " WHERE Id=?",
              work.id());
          data.state(work.version(), "DRAFT", true);
          audit.record(work.user(), "UPLOAD_SUCCEEDED", "UploadJob", work.id() + "", "SUCCESS");
        });
  }

  private void fail(Work work, String code, boolean retry) {
    transactions.executeWithoutResult(
        status -> {
          scopes.lockForJob(work.version());
          var rows =
              jdbc.queryForList(
                  "SELECT LeaseOwner,Status FROM dbo.UploadJob WITH (UPDLOCK,HOLDLOCK) WHERE Id=?",
                  work.id());
          if (rows.isEmpty()
              || !rows.getFirst().get("Status").equals("RUNNING")
              || !work.lease().equalsIgnoreCase(String.valueOf(rows.getFirst().get("LeaseOwner"))))
            return;
          boolean again = retry && work.attempts() < work.maxAttempts();
          jdbc.update(
              "UPDATE dbo.UploadJob SET"
                  + " Status=?,IsActive=?,LeaseOwner=NULL,LeaseUntil=NULL,NextAttemptAt=?,ErrorCode=?,UpdatedAt=SYSUTCDATETIME()"
                  + " WHERE Id=?",
              again ? "RETRY_WAIT" : "FAILED",
              again,
              LocalDateTime.ofInstant(
                  Instant.now().plusSeconds(Math.min(60, 10L * work.attempts())), ZoneOffset.UTC),
              code,
              work.id());
          if (Set.of("UPLOADING", "PROCESSING").contains(data.version(work.version()).state()))
            data.state(work.version(), again ? "PROCESSING" : "FAILED", true);
          audit.record(
              work.user(),
              again ? "UPLOAD_RETRY_WAIT" : "UPLOAD_FAILED",
              "UploadJob",
              work.id() + "",
              code);
        });
  }

  public void cleanupSucceeded() {
    var rows =
        jdbc.queryForList(
            "SELECT TOP(1) Id,StagingKey FROM dbo.UploadJob WHERE Status='SUCCEEDED' AND"
                + " StagingPurged=0 ORDER BY Id");
    if (rows.isEmpty()) return;
    var row = rows.getFirst();
    try {
      staging.remove((String) row.get("StagingKey"));
      jdbc.update(
          "UPDATE dbo.UploadJob SET StagingPurged=1 WHERE Id=? AND Status='SUCCEEDED'",
          row.get("Id"));
    } catch (Exception ignored) {
      /* keep the durable cleanup marker for the next tick */
    }
  }
}
