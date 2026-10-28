package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.catalog.api.CatalogDtos.*;
import com.comicnet.catalog.service.CatalogService;
import com.comicnet.common.web.*;
import com.comicnet.creator.api.CreatorDirectory;
import com.comicnet.publishing.api.PublishingDtos.*;
import com.comicnet.publishing.service.*;
import com.comicnet.reader.dto.ReadingDtos.*;
import com.comicnet.reader.service.ProgressService;
import com.comicnet.storage.service.S3Gateway;
import com.comicnet.support.SqlTestActors;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Actual published SQL/S3 fixture, committed so independent client transactions can contend. */
@SpringBootTest
class ReadingConcurrencyIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired CreatorDirectory teams;
  @Autowired CatalogService catalog;
  @Autowired PublishingService publishing;
  @Autowired UploadCoordinator uploads;
  @Autowired UploadWorker worker;
  @Autowired ProgressService progress;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transactions;
  @Autowired jakarta.persistence.EntityManager entities;
  @Autowired S3Gateway storage;
  @Autowired org.springframework.core.env.Environment environment;
  private Identity admin, creator, reader;
  private long team, comic, translation, chapter, version, job;
  private String page;
  private final List<Long> actorIds = new ArrayList<>();
  private final JsonMapper json = JsonMapper.builder().build();

  private String key() {
    return UUID.randomUUID().toString();
  }

  @BeforeEach
  void committedFixture() {
    recoverInterruptedFixtures();
    transactions.executeWithoutResult(
        status -> {
          try {
            var actors = new SqlTestActors(auth, accounts, jdbc, entities);
            admin = actors.administrator();
            actorIds.add(admin.userId());
            creator = actors.creator(admin);
            actorIds.add(creator.userId());
            reader = actors.reader();
            actorIds.add(reader.userId());
            team =
                Ids.parse(
                    teams
                        .create(
                            admin,
                            new CreatorDirectory.TeamCreate(
                                "Concurrency reader fixture", "race-" + key(), creator.getName()),
                            key())
                        .id());
            comic =
                Ids.parse(
                    catalog
                        .create(
                            creator,
                            new ComicInput(
                                "Concurrent reader SQL fixture",
                                "race-" + key(),
                                "Temporary integration fixture",
                                "MANGA",
                                "JP",
                                "ONGOING",
                                "ALL_AGE",
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                null,
                                null),
                            key())
                        .id());
            catalog.visibility(
                admin,
                comic,
                new VisibilityPut("PUBLIC", "Concurrency fixture"),
                Etags.of(catalog.version(comic)));
            translation =
                Ids.parse(
                    catalog
                        .createTranslation(
                            creator,
                            comic,
                            new TranslationCreate(team + "", "vi", "main", null),
                            key())
                        .id());
            catalog.patchTranslation(
                creator,
                translation,
                json.readTree("{\"visibility\":\"PUBLIC\"}"),
                Etags.of(catalog.translationVersion(translation)));
            chapter =
                Ids.parse(
                    publishing
                        .createChapter(
                            creator,
                            translation,
                            new ChapterInput(null, "1", "Concurrent progress", "1", "FREE"),
                            key())
                        .id());
            version =
                Ids.parse(
                    publishing
                        .createVersion(creator, chapter, new VersionCreate(null), key())
                        .id());
            var image = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(8, 12, BufferedImage.TYPE_INT_RGB), "PNG", image);
            job =
                Ids.parse(
                    uploads
                        .accept(
                            creator,
                            version,
                            new MockMultipartFile(
                                "file", "page.png", "image/png", image.toByteArray()),
                            null,
                            Etags.of(publishing.versionToken(version)),
                            key())
                        .id());
            worker.processJob(job);
            publishing.submit(creator, version, Etags.of(publishing.versionToken(version)));
            publishing.review(
                admin,
                version,
                new Review("APPROVE", null),
                Etags.of(publishing.versionToken(version)));
            publishing.publish(
                creator,
                chapter,
                new Publish(version + ""),
                Etags.of(publishing.chapterToken(chapter)));
            page = publishing.version(creator, version).pages().getFirst().pageId();
          } catch (Exception error) {
            throw new RuntimeException(error);
          }
        });
  }

  private void recoverInterruptedFixtures() {
    var leftovers =
        jdbc.queryForList(
            "SELECT c.Id AS ComicId,c.OwnerUserId,t.Id AS TranslationId,t.TeamId,ch.Id AS"
                + " ChapterId,v.Id AS VersionId,v.ReviewedBy,j.Id AS JobId FROM dbo.Comic c JOIN"
                + " dbo.ComicTranslation t ON t.ComicId=c.Id JOIN dbo.CreatorTeam ct ON"
                + " ct.Id=t.TeamId JOIN dbo.Chapter ch ON ch.TranslationId=t.Id JOIN"
                + " dbo.ChapterVersion v ON v.ChapterId=ch.Id JOIN dbo.UploadJob j ON"
                + " j.VersionId=v.Id JOIN dbo.[User] u ON u.Id=c.OwnerUserId WHERE"
                + " c.Title=N'Concurrent reader SQL fixture' AND c.Description=N'Temporary"
                + " integration fixture' AND c.Slug LIKE 'race-%' AND ct.Name=N'Concurrency reader"
                + " fixture' AND u.NormalizedEmail LIKE 'reader-%@comicnet.test'"
                + " AND v.VersionNumber=1 AND v.ReviewedBy IS NOT NULL");
    Set<Long> recovered = new HashSet<>();
    for (var row : leftovers) {
      comic = ((Number) row.get("ComicId")).longValue();
      if (!recovered.add(comic)) continue;
      translation = ((Number) row.get("TranslationId")).longValue();
      team = ((Number) row.get("TeamId")).longValue();
      chapter = ((Number) row.get("ChapterId")).longValue();
      version = ((Number) row.get("VersionId")).longValue();
      job = ((Number) row.get("JobId")).longValue();
      long owner = ((Number) row.get("OwnerUserId")).longValue(),
          reviewer = ((Number) row.get("ReviewedBy")).longValue();
      var owned =
          jdbc.queryForList(
              "SELECT Id FROM dbo.[User] WHERE NormalizedEmail LIKE 'reader-%@comicnet.test' AND"
                  + " CreatedAt>=(SELECT CreatedAt FROM dbo.[User] WHERE Id=?) AND"
                  + " CreatedAt<=(SELECT CreatedAt FROM dbo.Comic WHERE Id=?) ORDER BY Id",
              Long.class, reviewer, comic);
      if (owned.size() != 3 || !owned.contains(owner) || !owned.contains(reviewer))
        throw new IllegalStateException(
            "Interrupted fixture identity boundary requires inspection.");
      actorIds.addAll(owned);
      creator = new Identity(owner, 0, 0, Set.of("READER", "CREATOR"));
      removeOnlyThisCommittedFixture();
      actorIds.clear();
    }
    comic = translation = team = chapter = version = job = 0;
    creator = admin = reader = null;
  }

  private ProgressPut position(long expected, String offset) {
    return new ProgressPut(version + "", page, new BigDecimal(offset), false, expected, key(), 0);
  }

  @Test
  void interruptedReviewWithAdditionalVersionAndUploadIsRecoveredOnce() throws Exception {
    long originalComic = comic;
    long next =
        Ids.parse(publishing.createVersion(creator, chapter, new VersionCreate(null), key()).id());
    var image = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(9, 13, BufferedImage.TYPE_INT_RGB), "PNG", image);
    long extraJob =
        Ids.parse(
            uploads
                .accept(
                    creator,
                    next,
                    new MockMultipartFile("file", "extra.png", "image/png", image.toByteArray()),
                    null,
                    Etags.of(publishing.versionToken(next)),
                    key())
                .id());
    worker.processJob(extraJob);
    // The original review is the ownership boundary; the new working draft has no reviewer yet.
    recoverInterruptedFixtures();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.Comic WHERE Id=?", Integer.class, originalComic))
        .isZero();
    assertThat(actorIds).isEmpty();
  }

  private List<String> race(Supplier<String> a, Supplier<String> b) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                return a.get();
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return b.get();
              });
      start.countDown();
      return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
    }
  }

  private String put(ProgressPut value) {
    try {
      progress.put(reader, chapter, value);
      return "APPLIED";
    } catch (DomainException error) {
      return error.code();
    }
  }

  @Test
  void simultaneousDeviceWritesCannotOverwriteTheSameRevision() throws Exception {
    var a = position(0, "0.8");
    var b = position(0, "0.2");
    assertThat(race(() -> put(a), () -> put(b)))
        .containsExactlyInAnyOrder("APPLIED", "PROGRESS_CONFLICT");
    assertThat(progress.get(reader, chapter).revision()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.ProgressMutation WHERE UserId=? AND State='APPLIED'",
                Integer.class,
                reader.userId()))
        .isEqualTo(1);
  }

  @Test
  void concurrentHistoryClearCannotBeResurrectedByAnOldGeneration() throws Exception {
    progress.put(reader, chapter, position(0, "0.4"));
    ProgressPut stale = position(1, "0.7");
    var results =
        race(
            () -> put(stale),
            () -> {
              progress.clear(reader);
              return "CLEARED";
            });
    assertThat(results).contains("CLEARED");
    assertThat(results)
        .anySatisfy(result -> assertThat(result).isIn("APPLIED", "HISTORY_GENERATION_CONFLICT"));
    var history = progress.history(reader, 0, 20);
    assertThat(history.historyGeneration()).isEqualTo(1);
    assertThat(history.items()).isEmpty();
    assertThat(put(stale)).isEqualTo("HISTORY_GENERATION_CONFLICT");
  }

  @AfterEach
  void removeOnlyThisCommittedFixture() {
    if (actorIds.isEmpty()) return;
    List<String> objects = new ArrayList<>();
    if (creator != null) {
      objects.addAll(
          jdbc.queryForList(
              "SELECT ObjectKey FROM dbo.DigitalAsset WHERE UploadedBy=?",
              String.class,
              creator.userId()));
      objects.addAll(
          jdbc.queryForList(
              "SELECT v.ObjectKey FROM dbo.AssetVariant v JOIN dbo.DigitalAsset a ON a.Id=v.AssetId"
                  + " WHERE a.UploadedBy=?",
              String.class,
              creator.userId()));
    }
    if (chapter > 0)
      objects.addAll(
          jdbc.queryForList(
              "SELECT StagingKey FROM dbo.UploadJob WHERE ChapterId=?", String.class, chapter));
    String user = environment.getRequiredProperty("DB_MIGRATION_USER"),
        url = environment.getRequiredProperty("spring.datasource.url");
    if (!user.equals("comicnet_migrator") || !url.contains("databaseName=ComicNetDev;"))
      throw new IllegalStateException("Cleanup needs the scoped development migration account.");
    var source =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            url, user, environment.getRequiredProperty("DB_MIGRATION_PASSWORD"));
    var clean = new JdbcTemplate(source);
    new TransactionTemplate(new org.springframework.jdbc.support.JdbcTransactionManager(source))
        .executeWithoutResult(
            status -> {
              clean.update("DELETE dbo.ProgressMutation WHERE ChapterId=?", chapter);
              clean.update("DELETE dbo.ReadingProgress WHERE ChapterId=?", chapter);
              clean.update("DELETE dbo.Bookmark WHERE ChapterId=?", chapter);
              clean.update(
                  "DELETE dbo.UploadJobItem WHERE JobId IN (SELECT Id FROM dbo.UploadJob WHERE"
                      + " ChapterId=?)",
                  chapter);
              clean.update("DELETE dbo.UploadJob WHERE ChapterId=?", chapter);
              clean.update("UPDATE dbo.Chapter SET PublishedVersionId=NULL WHERE Id=?", chapter);
              clean.update("DELETE dbo.ChapterVersionPage WHERE ChapterId=?", chapter);
              clean.update("DELETE dbo.ChapterPage WHERE ChapterId=?", chapter);
              clean.update("DELETE dbo.ChapterVersion WHERE ChapterId=?", chapter);
              clean.update("DELETE dbo.Chapter WHERE Id=?", chapter);
              clean.update("DELETE dbo.Volume WHERE TranslationId=?", translation);
              clean.update("DELETE dbo.ComicTranslation WHERE Id=?", translation);
              for (String table :
                  List.of(
                      "ComicAlternativeTitle",
                      "ComicGenre",
                      "ComicTag",
                      "ComicContentWarning",
                      "ComicAuthor",
                      "ComicPublisher"))
                clean.update("DELETE dbo." + table + " WHERE ComicId=?", comic);
              clean.update("DELETE dbo.CollectionItem WHERE ComicId=?", comic);
              clean.update("DELETE dbo.ComicFollow WHERE ComicId=?", comic);
              clean.update("DELETE dbo.Comic WHERE Id=?", comic);
              clean.update(
                  "DELETE dbo.OutboxEvent WHERE AggregateId=? AND Type IN"
                      + " ('ChapterPublished','ChapterUnpublished')",
                  chapter + "");
              clean.update(
                  "DELETE dbo.TeamMemberRole WHERE MemberId IN (SELECT Id FROM dbo.TeamMember WHERE"
                      + " TeamId=?)",
                  team);
              clean.update("DELETE dbo.TeamMember WHERE TeamId=?", team);
              clean.update("DELETE dbo.CreatorTeam WHERE Id=?", team);
              for (long id : actorIds) {
                clean.update(
                    "DELETE dbo.CollectionItem WHERE CollectionId IN (SELECT Id FROM"
                        + " dbo.LibraryCollection WHERE UserId=?)",
                    id);
                clean.update("DELETE dbo.LibraryCollection WHERE UserId=?", id);
                clean.update("DELETE dbo.ComicFollow WHERE UserId=?", id);
                clean.update(
                    "DELETE dbo.AssetAccessPolicy WHERE AssetId IN (SELECT Id FROM dbo.DigitalAsset"
                        + " WHERE UploadedBy=?)",
                    id);
                clean.update(
                    "DELETE dbo.AssetVariant WHERE AssetId IN (SELECT Id FROM dbo.DigitalAsset"
                        + " WHERE UploadedBy=?)",
                    id);
                clean.update("DELETE dbo.DigitalAsset WHERE UploadedBy=?", id);
                clean.update("DELETE dbo.IdempotencyRecord WHERE ActorId=?", id);
                clean.update("DELETE dbo.ProgressMutation WHERE UserId=?", id);
                clean.update("DELETE dbo.ReadingHistoryState WHERE UserId=?", id);
                clean.update("DELETE dbo.ReadingPreference WHERE UserId=?", id);
                clean.update(
                    "DELETE dbo.RefreshToken WHERE SessionId IN (SELECT Id FROM dbo.[Session] WHERE"
                        + " UserId=?)",
                    id);
                clean.update("DELETE dbo.[Session] WHERE UserId=?", id);
                clean.update("DELETE dbo.AccountActionToken WHERE UserId=?", id);
                clean.update(
                    "DELETE dbo.AccountMailJob WHERE Recipient=(SELECT Email FROM dbo.[User] WHERE"
                        + " Id=?)",
                    id);
                clean.update(
                    "DELETE dbo.AuditLog WHERE ActorUserId=? OR (TargetType='User' AND TargetId=?)",
                    id,
                    id + "");
                clean.update("DELETE dbo.UserRole WHERE UserId=?", id);
                clean.update("DELETE dbo.[User] WHERE Id=?", id);
              }
            });
    objects.forEach(storage::delete);
  }

  Map<String, Object> reviewSnapshot() {
    return Map.of(
        "apiBase",
        "http://localhost:8081/api/v1",
        "adminEmail",
        auth.user(admin.userId()).email(),
        "creatorEmail",
        auth.user(creator.userId()).email(),
        "readerEmail",
        auth.user(reader.userId()).email(),
        "comicId",
        comic + "",
        "chapterId",
        chapter + "",
        "versionId",
        version + "",
        "pageId",
        page);
  }
}
