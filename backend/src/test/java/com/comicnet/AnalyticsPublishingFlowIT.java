package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.analytics.dto.AnalyticsDtos.*;
import com.comicnet.analytics.service.AnalyticsService;
import com.comicnet.identity.api.*;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.catalog.api.CatalogDtos.*;
import com.comicnet.catalog.api.CatalogQueries;
import com.comicnet.catalog.service.CatalogService;
import com.comicnet.common.web.*;
import com.comicnet.creator.api.CreatorDirectory;
import com.comicnet.publishing.api.PublishingDtos.*;
import com.comicnet.publishing.service.*;
import com.comicnet.storage.api.StagingStore;
import com.comicnet.support.SqlTestActors;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {
      "comicnet.mail.worker-enabled=false",
      "comicnet.notification.worker-enabled=false",
      "comicnet.publish.schedule-enabled=false"
    })
@Transactional
class AnalyticsPublishingFlowIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired CreatorDirectory teams;
  @Autowired CatalogService catalog;
  @Autowired CatalogQueries queries;
  @Autowired PublishingService publishing;
  @Autowired UploadCoordinator uploads;
  @Autowired UploadWorker worker;
  @Autowired AnalyticsService analytics;
  @Autowired StagingStore staging;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  private SqlTestActors actors;
  private final List<Long> jobs = new ArrayList<>();
  private final JsonMapper json = JsonMapper.builder().build();

  private record Context(
      Identity admin, Identity creator, long comic, long translation, long chapter, long version) {}

  private String key() {
    return UUID.randomUUID().toString();
  }

  @BeforeEach
  void setup() {
    actors = new SqlTestActors(auth, accounts, jdbc, entities);
  }

  @AfterEach
  void removeOnlyTestStaging() {
    for (Long id : jobs)
      for (String source :
          jdbc.queryForList("SELECT StagingKey FROM dbo.UploadJob WHERE Id=?", String.class, id))
        staging.remove(source);
  }

  private Context context() {
    Identity admin = actors.administrator(), creator = actors.creator(admin);
    long team =
        Ids.parse(
            teams
                .create(
                    admin,
                    new CreatorDirectory.TeamCreate("Analytics team", "team-" + key(), creator.getName()),
                    key())
                .id());
    long comic =
        Ids.parse(
            catalog
                .create(
                    creator,
                    new ComicInput(
                        "Xếp hạng",
                        "analytics-" + key(),
                        "Analytics fixture",
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
        admin, comic, new VisibilityPut("PUBLIC", "JUnit analytics"), Etags.of(catalog.version(comic)));
    long translation =
        Ids.parse(
            catalog
                .createTranslation(
                    creator, comic, new TranslationCreate(team + "", "vi", "main", null), key())
                .id());
    catalog.patchTranslation(
        creator,
        translation,
        json.readTree("{\"visibility\":\"PUBLIC\"}"),
        Etags.of(catalog.translationVersion(translation)));
    long chapter =
        Ids.parse(
            publishing
                .createChapter(
                    creator,
                    translation,
                    new ChapterInput(null, "1", "Chương một", "1", "FREE"),
                    key())
                .id());
    long version =
        Ids.parse(publishing.createVersion(creator, chapter, new VersionCreate(null), key()).id());
    return new Context(admin, creator, comic, translation, chapter, version);
  }

  private byte[] image(int color) throws IOException {
    var image = new BufferedImage(12, 16, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, color);
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "PNG", output);
    return output.toByteArray();
  }

  private void uploadAndApprove(Context c, long version) throws IOException {
    var job =
        uploads.accept(
            c.creator(),
            version,
            new MockMultipartFile("file", "page.png", "image/png", image((int) version)),
            null,
            Etags.of(publishing.versionToken(version)),
            key());
    jobs.add(Ids.parse(job.id()));
    assertThat(worker.processJob(Ids.parse(job.id()))).isTrue();
    publishing.submit(c.creator(), version, Etags.of(publishing.versionToken(version)));
    publishing.review(
        c.admin(),
        version,
        new Review("APPROVE", null),
        Etags.of(publishing.versionToken(version)));
  }

  @Test
  void validReadsDedupePerDayFeedRankingsAndCompletionRate() throws IOException {
    var c = context();
    uploadAndApprove(c, c.version());
    publishing.publish(
        c.creator(),
        c.chapter(),
        new Publish(c.version() + ""),
        Etags.of(publishing.chapterToken(c.chapter())));
    Identity reader = actors.reader(), other = actors.reader();

    var first = analytics.record(reader, c.comic(), new ViewInput(c.chapter() + ""));
    assertThat(first.recorded()).isTrue();
    assertThat(first.totalViews()).isEqualTo(1);
    var repeated = analytics.record(reader, c.comic(), new ViewInput(c.chapter() + ""));
    assertThat(repeated.recorded()).isFalse();
    assertThat(repeated.totalViews()).isEqualTo(1);
    assertThat(analytics.record(other, c.comic(), new ViewInput(c.chapter() + "")).totalViews())
        .isEqualTo(2);

    assertThatThrownBy(
            () -> analytics.record(reader, c.comic(), new ViewInput((c.chapter() + 999) + "")))
        .isInstanceOf(DomainException.class);

    assertThat(analytics.complete(reader, c.chapter()).recorded()).isTrue();
    assertThat(analytics.complete(reader, c.chapter()).recorded()).isFalse();
    var chapter = analytics.chapterStats(c.admin(), c.chapter());
    assertThat(chapter.readers()).isEqualTo(2);
    assertThat(chapter.completions()).isEqualTo(1);
    assertThat(chapter.completionRate()).isEqualByComparingTo("50.00");

    var stats = analytics.stats(c.admin(), c.comic());
    assertThat(stats.totalViews()).isEqualTo(2);
    assertThat(stats.totalCompletions()).isEqualTo(1);
    assertThat(stats.viewers()).isEqualTo(2);
    assertThat(stats.recent()).isNotEmpty();

    var trending = analytics.ranking("TRENDING", 20).items();
    assertThat(trending).isNotEmpty();
    assertThat(trending.getFirst().comicId()).isEqualTo(c.comic() + "");
    assertThat(trending.getFirst().views()).isEqualTo(2);
    assertThat(analytics.ranking("DAILY", 20).items().getFirst().comicId())
        .isEqualTo(c.comic() + "");

    var summary = queries.summary(c.comic());
    assertThat(summary.views()).isEqualTo(2);
    assertThat(summary.rating()).isNotNull();
    var byViews =
        queries.list(new CatalogQueries.Filter(null, null, null, null, null, "VIEWS_DESC"), 0, 20);
    assertThat(byViews.items().getFirst().id()).isEqualTo(c.comic() + "");
    assertThat(byViews.items().getFirst().views()).isEqualTo(2);
  }

  @Test
  void scheduledPublishingReleasesDueSnapshotAndRollbackRestoresThePreviousVersion()
      throws IOException {
    var c = context();
    uploadAndApprove(c, c.version());

    assertThatThrownBy(
            () ->
                publishing.schedule(
                    c.creator(),
                    c.chapter(),
                    new ScheduleInput(c.version() + "", Instant.now().plusSeconds(10)),
                    Etags.of(publishing.chapterToken(c.chapter()))))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.code()).isEqualTo("INVALID_SCHEDULE_TIME"));

    var scheduled =
        publishing.schedule(
            c.creator(),
            c.chapter(),
            new ScheduleInput(c.version() + "", Instant.now().plusSeconds(3600)),
            Etags.of(publishing.chapterToken(c.chapter())));
    assertThat(scheduled.state()).isEqualTo("SCHEDULED");
    assertThat(publishing.version(c.creator(), c.version()).state()).isEqualTo("SCHEDULED");
    assertThat(publishing.chapter(c.creator(), c.chapter()).publishedVersionId()).isNull();

    assertThat(
            publishing.schedule(
                c.creator(),
                c.chapter(),
                new ScheduleInput(c.version() + "", Instant.now().plusSeconds(7200)),
                Etags.of(publishing.chapterToken(c.chapter()))))
        .extracting(ScheduleView::state)
        .isEqualTo("SCHEDULED");
    publishing.cancelSchedule(
        c.creator(), c.chapter(), Etags.of(publishing.chapterToken(c.chapter())));
    assertThat(publishing.version(c.creator(), c.version()).state()).isEqualTo("APPROVED");
    publishing.schedule(
        c.creator(),
        c.chapter(),
        new ScheduleInput(c.version() + "", Instant.now().plusSeconds(3600)),
        Etags.of(publishing.chapterToken(c.chapter())));
    jdbc.update(
        "UPDATE dbo.ChapterVersion SET ScheduledAt=DATEADD(minute,-1,SYSUTCDATETIME()) WHERE Id=?",
        c.version());
    assertThat(publishing.releaseNextScheduled()).isTrue();
    assertThat(publishing.chapter(c.creator(), c.chapter()).publishedVersionId())
        .isEqualTo(c.version() + "");
    assertThat(publishing.version(c.creator(), c.version()).state()).isEqualTo("PUBLISHED");
    assertThat(publishing.releaseNextScheduled()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.OutboxEvent WHERE Type='ChapterPublished' AND"
                    + " AggregateId=?",
                Integer.class,
                c.chapter() + ""))
        .isEqualTo(1);

    var second =
        Ids.parse(
            publishing
                .createVersion(c.creator(), c.chapter(), new VersionCreate(null), key())
                .id());
    uploadAndApprove(c, second);
    publishing.publish(
        c.creator(),
        c.chapter(),
        new Publish(second + ""),
        Etags.of(publishing.chapterToken(c.chapter())));
    assertThat(publishing.version(c.creator(), c.version()).state()).isEqualTo("UNPUBLISHED");

    publishing.rollback(
        c.creator(),
        c.chapter(),
        new RollbackInput(c.version() + ""),
        Etags.of(publishing.chapterToken(c.chapter())));
    assertThat(publishing.chapter(c.creator(), c.chapter()).publishedVersionId())
        .isEqualTo(c.version() + "");
    assertThat(publishing.version(c.creator(), c.version()).state()).isEqualTo("PUBLISHED");
    assertThat(publishing.version(c.creator(), second).state()).isEqualTo("UNPUBLISHED");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.AuditLog WHERE Action='CHAPTER_ROLLED_BACK' AND"
                    + " TargetId=?",
                Integer.class,
                c.chapter() + ""))
        .isEqualTo(1);
  }
}
