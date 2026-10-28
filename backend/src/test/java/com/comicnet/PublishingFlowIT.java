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
import com.comicnet.storage.api.*;
import com.comicnet.support.SqlTestActors;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "comicnet.mail.worker-enabled=false")
@Transactional
class PublishingFlowIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired CreatorDirectory teams;
  @Autowired CatalogService catalog;
  @Autowired PublishingService publishing;
  @Autowired UploadCoordinator uploads;
  @Autowired UploadWorker worker;
  @Autowired AssetStore assets;
  @Autowired StagingStore staging;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  private SqlTestActors actors;
  private final List<Long> jobs = new ArrayList<>();
  private final JsonMapper json = JsonMapper.builder().build();

  private record Context(
      Identity admin, Identity creator, long translation, long chapter, long version) {}

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
                    new CreatorDirectory.TeamCreate(
                        "JUnit team", "team-" + key(), creator.getName()),
                    key())
                .id());
    var input =
        new ComicInput(
            "Truyện kiểm thử",
            "comic-" + key(),
            "Description",
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
            null);
    long comic = Ids.parse(catalog.create(creator, input, key()).id());
    catalog.visibility(
        admin, comic, new VisibilityPut("PUBLIC", "JUnit"), Etags.of(catalog.version(comic)));
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
    return new Context(admin, creator, translation, chapter, version);
  }

  private byte[] image(int color) throws IOException {
    var image = new BufferedImage(12, 16, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, color);
    var output = new ByteArrayOutputStream();
    ImageIO.write(image, "PNG", output);
    return output.toByteArray();
  }

  private byte[] zip() throws IOException {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      for (String name : List.of("page10.png", "page2.png")) {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(image(name.hashCode()));
        zip.closeEntry();
      }
    }
    return output.toByteArray();
  }

  private UploadJob upload(Context c, long version, byte[] bytes, String page) {
    var job =
        uploads.accept(
            c.creator(),
            version,
            new MockMultipartFile("file", "pages.zip", "application/octet-stream", bytes),
            page,
            Etags.of(publishing.versionToken(version)),
            key());
    jobs.add(Ids.parse(job.id()));
    return job;
  }

  @Test
  void queuedUploadAfterComicSoftDeleteFailsCleanlyAndRetainsItsVersion() throws Exception {
    var c = context();
    var job = upload(c, c.version(), image(0xffaa33), null);
    long comic =
        jdbc.queryForObject(
            "SELECT ComicId FROM dbo.ComicTranslation WHERE Id=?", Long.class, c.translation());
    catalog.softDelete(c.creator(), comic, Etags.of(catalog.version(comic)));
    worker.processJob(Ids.parse(job.id()));
    var failed =
        jdbc.queryForMap(
            "SELECT Status,IsActive,ErrorCode FROM dbo.UploadJob WHERE Id=?", Ids.parse(job.id()));
    assertThat(failed.get("Status")).isEqualTo("FAILED");
    assertThat(failed.get("IsActive")).isEqualTo(false);
    assertThat(failed.get("ErrorCode")).isEqualTo("NOT_FOUND");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.ChapterVersion WHERE Id=?", Integer.class, c.version()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.ChapterVersionPage WHERE VersionId=?",
                Integer.class,
                c.version()))
        .isZero();
  }

  private void approveAndPublish(Context c, long version) {
    publishing.submit(c.creator(), version, Etags.of(publishing.versionToken(version)));
    publishing.review(
        c.admin(),
        version,
        new Review("APPROVE", null),
        Etags.of(publishing.versionToken(version)));
    publishing.publish(
        c.creator(),
        c.chapter(),
        new Publish(version + ""),
        Etags.of(publishing.chapterToken(c.chapter())));
  }

  @Test
  void uploadReviewPublishAndReplacementPreserveStablePages() throws Exception {
    var c = context();
    var job = upload(c, c.version(), zip(), null);
    assertThat(worker.processJob(Ids.parse(job.id()))).isTrue();
    assertThat(uploads.get(c.creator(), Ids.parse(job.id())).status()).isEqualTo("SUCCEEDED");
    var first = publishing.version(c.creator(), c.version());
    assertThat(first.pages()).hasSize(2);
    assertThat(worker.processJob(Ids.parse(job.id()))).isFalse();
    assertThat(
            jdbc.queryForList(
                "SELECT EntryName FROM dbo.UploadJobItem WHERE JobId=? ORDER BY Ordinal",
                String.class,
                Ids.parse(job.id())))
        .containsExactly("page2.png", "page10.png");
    assertThat(publishing.preview(c.creator(), c.version(), "JPEG").pages().size()).isEqualTo(2);
    approveAndPublish(c, c.version());
    assertThat(publishing.publicChapters(c.translation(), 0, 20).items())
        .singleElement()
        .extracting(ChapterSummary::pageCount)
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.OutboxEvent WHERE Type='ChapterPublished' AND"
                    + " AggregateId=?",
                Integer.class,
                c.chapter() + ""))
        .isEqualTo(1);
    var draft = publishing.createVersion(c.creator(), c.chapter(), new VersionCreate(null), key());
    assertThat(draft.pages())
        .extracting(VersionPage::pageId)
        .containsExactlyElementsOf(first.pages().stream().map(VersionPage::pageId).toList());
    long draftId = Ids.parse(draft.id());
    String page = draft.pages().getFirst().pageId(), oldAsset = draft.pages().getFirst().assetId();
    var replace = upload(c, draftId, image(0xff33aa), page);
    worker.processJob(Ids.parse(replace.id()));
    var updated = publishing.version(c.creator(), draftId);
    assertThat(updated.pages().getFirst().pageId()).isEqualTo(page);
    assertThat(updated.pages().getFirst().assetId()).isNotEqualTo(oldAsset);
    assertThat(publishing.version(c.creator(), c.version()).pages().getFirst().assetId())
        .isEqualTo(oldAsset);
    assertThatThrownBy(
            () ->
                publishing.reorder(
                    c.creator(),
                    c.version(),
                    new Reorder(List.of(first.pages().get(1).pageId(), page)),
                    Etags.of(publishing.versionToken(c.version()))))
        .isInstanceOf(DomainException.class);
    publishing.reorder(
        c.creator(),
        draftId,
        new Reorder(List.of(updated.pages().get(1).pageId(), page)),
        Etags.of(publishing.versionToken(draftId)));
    approveAndPublish(c, draftId);
    assertThat(publishing.version(c.creator(), c.version()).state()).isEqualTo("UNPUBLISHED");
    assertThat(publishing.chapter(c.creator(), c.chapter()).publishedVersionId())
        .isEqualTo(draft.id());
  }

  @Test
  void expiredLeaseReusesReceiptsAndDoesNotAppendPagesTwice() throws Exception {
    var c = context();
    var job = upload(c, c.version(), zip(), null);
    long id = Ids.parse(job.id());
    var processed = assets.storePage(c.creator().userId(), image("page2.png".hashCode()));
    jdbc.update(
        "INSERT dbo.UploadJobItem(JobId,Ordinal,EntryName,AssetId) VALUES(?,1,'page2.png',?)",
        id,
        Ids.parse(processed.id()));
    jdbc.update(
        "UPDATE dbo.UploadJob SET"
            + " Status='RUNNING',Attempts=1,LeaseOwner=NEWID(),LeaseUntil=DATEADD(second,-1,SYSUTCDATETIME())"
            + " WHERE Id=?",
        id);
    assertThat(worker.processJob(id)).isTrue();
    assertThat(publishing.version(c.creator(), c.version()).pages()).hasSize(2);
    assertThat(publishing.version(c.creator(), c.version()).pages().getFirst().assetId())
        .isEqualTo(processed.id());
    assertThat(uploads.get(c.creator(), id).attempts()).isEqualTo(2);
    assertThat(worker.processJob(id)).isFalse();
  }

  @Test
  void invalidArchiveLeavesNoBindingsAndEmptyVersionCannotSubmit() throws Exception {
    var c = context();
    assertThatThrownBy(
            () ->
                publishing.submit(
                    c.creator(), c.version(), Etags.of(publishing.versionToken(c.version()))))
        .isInstanceOf(DomainException.class);
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry("../page.png"));
      zip.write(image(0));
      zip.closeEntry();
    }
    var job = upload(c, c.version(), output.toByteArray(), null);
    worker.processJob(Ids.parse(job.id()));
    assertThat(uploads.get(c.creator(), Ids.parse(job.id())).status()).isEqualTo("FAILED");
    assertThat(publishing.version(c.creator(), c.version()).pages()).isEmpty();
    var valid = upload(c, c.version(), image(1234), null);
    worker.processJob(Ids.parse(valid.id()));
    assertThat(publishing.version(c.creator(), c.version()).state()).isEqualTo("DRAFT");
    assertThat(publishing.version(c.creator(), c.version()).pages()).hasSize(1);
  }

  @Test
  void selfReviewAndMixedChapterVersionAreDenied() throws Exception {
    var c = context();
    var job = upload(c, c.version(), image(345), null);
    worker.processJob(Ids.parse(job.id()));
    publishing.submit(c.creator(), c.version(), Etags.of(publishing.versionToken(c.version())));
    accounts.roles(
        c.admin(),
        c.creator().userId(),
        Set.of("READER", "CREATOR", "MODERATOR"),
        "JUnit reviewer fixture",
        Etags.of(accounts.version(c.creator().userId())));
    var reviewer = actors.login(auth.user(c.creator().userId()).email());
    assertThatThrownBy(
            () ->
                publishing.review(
                    reviewer,
                    c.version(),
                    new Review("APPROVE", null),
                    Etags.of(publishing.versionToken(c.version()))))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo("SELF_REVIEW_DENIED"));
    publishing.review(
        c.admin(),
        c.version(),
        new Review("APPROVE", null),
        Etags.of(publishing.versionToken(c.version())));
    long other =
        Ids.parse(
            publishing
                .createChapter(
                    c.creator(),
                    c.translation(),
                    new ChapterInput(null, "2", "Other", "2", "FREE"),
                    key())
                .id());
    assertThatThrownBy(
            () ->
                publishing.publish(
                    c.creator(),
                    other,
                    new Publish(c.version() + ""),
                    Etags.of(publishing.chapterToken(other))))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo("VERSION_PARENT_MISMATCH"));
  }
}
