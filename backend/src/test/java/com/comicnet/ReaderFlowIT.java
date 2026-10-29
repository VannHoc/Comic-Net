package com.comicnet;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.catalog.api.CatalogDtos.*;
import com.comicnet.catalog.service.CatalogService;
import com.comicnet.common.web.*;
import com.comicnet.creator.api.CreatorDirectory;
import com.comicnet.publishing.api.PublishingDtos.*;
import com.comicnet.publishing.service.*;
import com.comicnet.monetization.service.ChapterPurchaseService;
import com.comicnet.wallet.service.WalletService;
import com.comicnet.reader.dto.ReadingDtos.*;
import com.comicnet.reader.service.*;
import com.comicnet.storage.api.StagingStore;
import com.comicnet.support.SqlTestActors;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReaderFlowIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired CreatorDirectory teams;
  @Autowired CatalogService catalog;
  @Autowired PublishingService publishing;
  @Autowired UploadCoordinator uploads;
  @Autowired UploadWorker worker;
  @Autowired StagingStore staging;
  @Autowired ReaderService reader;
  @Autowired ChapterPurchaseService purchases;
  @Autowired WalletService wallet;
  @Autowired com.comicnet.entitlement.service.EntitlementService entitlements;
  @Autowired ProgressService progress;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  @Autowired MockMvc mvc;
  @Autowired com.comicnet.library.service.LibraryService library;
  private SqlTestActors actors;
  private final List<Long> jobs = new ArrayList<>();
  private final JsonMapper json = JsonMapper.builder().build();

  private record Context(
      Identity admin,
      Identity creator,
      Identity reader,
      long comic,
      long chapter,
      long version,
      String page) {}

  private String bearer(Identity actor) {
    return auth.login(
            new AuthRequests.Login(
                auth.user(actor.userId()).email(), "Test-password-42!", "JUnit delete contract"))
        .accessToken();
  }

  @Test
  void softDeleteHttpRequiresOwnerAndFreshEtagAndPreservesReadingData() throws Exception {
    var c = context();
    progress.put(c.reader(), c.chapter(), input(c, 0, key(), 0, "0.4"));
    var bookmark =
        library.createBookmark(
            c.reader(),
            new com.comicnet.library.dto.LibraryDtos.BookmarkCreate(
                c.chapter() + "",
                c.version() + "",
                c.page(),
                new BigDecimal("0.4"),
                "Preserve after soft delete"),
            key());
    int pages =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.ChapterVersionPage WHERE ChapterId=?",
            Integer.class,
            c.chapter());
    String path = "/api/v1/comics/" + c.comic();
    String owner = bearer(c.creator()), readerToken = bearer(c.reader());
    String etag = Etags.of(catalog.version(c.comic()));
    mvc.perform(delete(path)).andExpect(status().isUnauthorized());
    mvc.perform(
            delete(path).header("Authorization", "Bearer " + readerToken).header("If-Match", etag))
        .andExpect(status().isNotFound());
    var other = actors.creator(c.admin());
    mvc.perform(
            delete(path)
                .header("Authorization", "Bearer " + bearer(other))
                .header("If-Match", etag))
        .andExpect(status().isNotFound());
    mvc.perform(delete(path).header("Authorization", "Bearer " + owner))
        .andExpect(status().is(428));
    mvc.perform(
            delete(path)
                .header("Authorization", "Bearer " + owner)
                .header("If-Match", Etags.of(new byte[8])))
        .andExpect(status().isPreconditionFailed());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.Comic WHERE Id=? AND DeletedAt IS NOT NULL",
                Integer.class,
                c.comic()))
        .isZero();
    mvc.perform(delete(path).header("Authorization", "Bearer " + owner).header("If-Match", etag))
        .andExpect(status().isNoContent());
    mvc.perform(get(path)).andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/publishing/comics/" + c.comic())
                .header("Authorization", "Bearer " + owner))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/chapters/" + c.chapter() + "/manifest"))
        .andExpect(status().isNotFound());
    assertThat(
            catalog
                .list(
                    new com.comicnet.catalog.api.CatalogQueries.Filter(
                        null, null, null, null, null, null),
                    0,
                    100)
                .items())
        .extracting(ComicSummary::id)
        .doesNotContain(c.comic() + "");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.Comic WHERE Id=? AND DeletedAt IS NOT NULL AND"
                    + " Visibility='ARCHIVED'",
                Integer.class,
                c.comic()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT PublishedVersionId FROM dbo.Chapter WHERE Id=?", Long.class, c.chapter()))
        .isEqualTo(c.version());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.ChapterVersionPage WHERE ChapterId=?",
                Integer.class,
                c.chapter()))
        .isEqualTo(pages);
    assertThat(
            jdbc.queryForObject(
                "SELECT PageId FROM dbo.ReadingProgress WHERE UserId=? AND ChapterId=?",
                Long.class,
                c.reader().userId(),
                c.chapter()))
        .isEqualTo(Long.parseLong(c.page()));
    assertThat(library.bookmark(c.reader(), Long.parseLong(bookmark.id())).anchorStatus())
        .isEqualTo("ANCHOR_UNAVAILABLE");
    assertThat(progress.history(c.reader(), 0, 20).items()).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.AuditLog WHERE Action='COMIC_SOFT_DELETED' AND"
                    + " TargetId=?",
                Integer.class,
                c.comic() + ""))
        .isEqualTo(1);
  }

  @Test
  void administratorCanSoftDeleteAnotherCreatorsComic() throws Exception {
    var c = context();
    mvc.perform(
            delete("/api/v1/comics/" + c.comic())
                .header("Authorization", "Bearer " + bearer(c.admin()))
                .header("If-Match", Etags.of(catalog.version(c.comic()))))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.ChapterVersion WHERE Id=?", Integer.class, c.version()))
        .isEqualTo(1);
  }

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

  private Context context() throws Exception {
    Identity admin = actors.administrator(),
        creator = actors.creator(admin),
        user = actors.reader();
    long team =
        Ids.parse(
            teams
                .create(
                    admin,
                    new CreatorDirectory.TeamCreate(
                        "Read team", "team-" + key(), creator.getName()),
                    key())
                .id());
    var input =
        new ComicInput(
            "Truyện đọc thử",
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
    long
        chapter =
            Ids.parse(
                publishing
                    .createChapter(
                        creator,
                        translation,
                        new ChapterInput(null, "1", "Chương một", "1", "FREE"),
                        key())
                    .id()),
        version =
            Ids.parse(
                publishing.createVersion(creator, chapter, new VersionCreate(null), key()).id());
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(12, 16, BufferedImage.TYPE_INT_RGB), "PNG", bytes);
    var job =
        uploads.accept(
            creator,
            version,
            new MockMultipartFile("file", "page.png", "image/png", bytes.toByteArray()),
            null,
            Etags.of(publishing.versionToken(version)),
            key());
    jobs.add(Ids.parse(job.id()));
    worker.processJob(Ids.parse(job.id()));
    publishing.submit(creator, version, Etags.of(publishing.versionToken(version)));
    publishing.review(
        admin, version, new Review("APPROVE", null), Etags.of(publishing.versionToken(version)));
    publishing.publish(
        creator, chapter, new Publish(version + ""), Etags.of(publishing.chapterToken(chapter)));
    return new Context(
        admin,
        creator,
        user,
        comic,
        chapter,
        version,
        publishing.version(creator, version).pages().getFirst().pageId());
  }

  private ProgressPut input(
      Context c, long revision, String mutation, int generation, String offset) {
    return new ProgressPut(
        c.version() + "", c.page(), new BigDecimal(offset), false, revision, mutation, generation);
  }

  @Test
  void paidChapterUnlockUsesRealLedgerAndGrantsOnlyItsBuyer() throws Exception {
    var c = context();
    var price = purchases.setPrice(c.creator(), c.chapter(), "PAID", 40);
    publishing.patchChapter(c.creator(), c.chapter(),
        json.readTree("{\"accessPolicy\":\"RESTRICTED\"}"),
        Etags.of(publishing.chapterToken(c.chapter())));
    assertThat(reader.access(c.reader(), c.chapter()).allowed()).isFalse();
    assertThat(reader.access(null, c.chapter()).requiresLogin()).isTrue();
    assertThat(purchases.prices(c.chapter())).containsExactly(price);
    Identity noCoins = actors.reader();
    assertThatThrownBy(() -> purchases.setPrice(noCoins, c.chapter(), "PAID", 1))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> purchases.unlock(noCoins, c.chapter(), price.id(), key()))
        .isInstanceOfSatisfying(DomainException.class,
            error -> assertThat(error.status()).isEqualTo(409));
    assertThat(wallet.balance(noCoins.userId()).paidCoin()).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Purchase WHERE UserId=?",
        Integer.class, noCoins.userId())).isZero();
    wallet.apply(c.reader().userId(), "PAID", 100, "SANDBOX_PAYMENT", key());
    String requestKey = key();
    var purchased = purchases.unlock(c.reader(), c.chapter(), price.id(), requestKey);
    assertThat(purchased.status()).isEqualTo("COMPLETED");
    assertThat(wallet.balance(c.reader().userId()).paidCoin()).isEqualTo(60);
    var changed = purchases.setPrice(c.creator(), c.chapter(), "PAID", 45);
    assertThat(purchases.prices(c.chapter())).containsExactly(changed);
    assertThatThrownBy(() -> purchases.unlock(noCoins, c.chapter(), price.id(), key()))
        .isInstanceOfSatisfying(DomainException.class,
            error -> assertThat(error.status()).isEqualTo(409));
    assertThat(reader.access(c.reader(), c.chapter()).allowed()).isTrue();
    assertThat(entitlements.history(c.reader().userId(), 0, 20).items()).singleElement()
        .satisfies(item -> {
          assertThat(item.chapterId()).isEqualTo(c.chapter());
          assertThat(item.comicTitle()).isEqualTo("Truyện đọc thử");
          assertThat(item.chapterTitle()).isEqualTo("Chương một");
          assertThat(item.kind()).isEqualTo("PURCHASE");
          assertThat(item.state()).isEqualTo("ACTIVE");
        });
    assertThat(entitlements.history(noCoins.userId(), 0, 20).items()).isEmpty();
    assertThat(reader.manifest(c.reader(), c.chapter(), "JPEG").pages()).hasSize(1);
    assertThat(reader.access(c.admin(), c.chapter()).allowed()).isFalse();
    assertThat(purchases.unlock(c.reader(), c.chapter(), price.id(), requestKey).status())
        .isEqualTo("ALREADY_PURCHASED");
    assertThat(purchases.unlock(c.reader(), c.chapter(), price.id(), key()).status())
        .isEqualTo("ALREADY_ENTITLED");
    assertThat(wallet.balance(c.reader().userId()).paidCoin()).isEqualTo(60);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Purchase WHERE UserId=? AND ChapterId=?",
        Integer.class, c.reader().userId(), c.chapter())).isEqualTo(1);
    mvc.perform(post("/api/v1/chapters/" + c.chapter() + "/unlock")
        .contentType("application/json").content("{\"priceId\":" + price.id() + "}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rewardCoinUnlockDebitsRewardOnlyAndGrantsBuyerAccess() throws Exception {
    var c = context();
    var price = purchases.setPrice(c.creator(), c.chapter(), "REWARD", 25);
    publishing.patchChapter(c.creator(), c.chapter(),
        json.readTree("{\"accessPolicy\":\"RESTRICTED\"}"),
        Etags.of(publishing.chapterToken(c.chapter())));
    wallet.apply(c.reader().userId(), "REWARD", 40, "CHECK_IN", key());
    assertThat(reader.access(c.reader(), c.chapter()).allowed()).isFalse();
    var result = purchases.unlock(c.reader(), c.chapter(), price.id(), key());
    assertThat(result.status()).isEqualTo("COMPLETED");
    assertThat(wallet.balance(c.reader().userId()).paidCoin()).isZero();
    assertThat(wallet.balance(c.reader().userId()).rewardCoin()).isEqualTo(15);
    assertThat(reader.access(c.reader(), c.chapter()).allowed()).isTrue();
    assertThat(reader.access(c.admin(), c.chapter()).allowed()).isFalse();
    assertThat(entitlements.history(c.reader().userId(), 0, 20).items()).singleElement()
        .satisfies(item -> assertThat(item.kind()).isEqualTo("PURCHASE"));
    assertThat(jdbc.queryForObject("SELECT CurrencyCode FROM dbo.Purchase WHERE Id=?",
        String.class, result.purchaseId())).isEqualTo("REWARD");
  }

  @Test
  @org.springframework.test.annotation.Commit
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
      named = "COMICNET_CREATE_GUI_FIXTURE", matches = "true")
  void prepareRewardGuiFixture() throws Exception {
    var c = context();
    purchases.setPrice(c.creator(), c.chapter(), "REWARD", 25);
    publishing.patchChapter(c.creator(), c.chapter(),
        json.readTree("{\"accessPolicy\":\"RESTRICTED\"}"),
        Etags.of(publishing.chapterToken(c.chapter())));
    wallet.apply(c.reader().userId(), "REWARD", 40, "CHECK_IN", key());
    wallet.apply(c.reader().userId(), "PAID", 7, "SANDBOX_PAYMENT", key());
    // These two accounts exist solely to publish this fixture; keep them unusable afterward.
    jdbc.update("UPDATE dbo.[User] SET Status='SUSPENDED' WHERE Id IN (?,?)",
        c.admin().userId(), c.creator().userId());
    var file = java.nio.file.Path.of("../.local/qa/reward-gui-fixture.txt");
    java.nio.file.Files.createDirectories(file.getParent());
    java.nio.file.Files.writeString(file,
        "readerId=" + c.reader().userId() + "\n"
            + "readerEmail=" + auth.user(c.reader().userId()).email() + "\n"
            + "comicId=" + c.comic() + "\nchapterId=" + c.chapter() + "\n");
  }

  @Test
  void publicReaderSignsRealImagesOnlyForFreeVisibleAllAgeContent() throws Exception {
    var c = context();
    assertThat(reader.access(null, c.chapter()).allowed()).isTrue();
    var manifest = reader.manifest(null, c.chapter(), "PNG");
    assertThat(manifest.pages().size()).isEqualTo(1);
    try (var http = HttpClient.newHttpClient()) {
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create(manifest.pages().getFirst().url())).GET().build(),
              HttpResponse.BodyHandlers.ofByteArray());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(ImageIO.read(new ByteArrayInputStream(response.body())).getWidth()).isEqualTo(12);
    }
    publishing.patchChapter(
        c.creator(),
        c.chapter(),
        json.readTree("{\"accessPolicy\":\"RESTRICTED\"}"),
        Etags.of(publishing.chapterToken(c.chapter())));
    assertThat(reader.access(c.admin(), c.chapter()).allowed()).isFalse();
    assertThatThrownBy(() -> reader.manifest(c.admin(), c.chapter(), "JPEG"))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(403));
    publishing.patchChapter(
        c.creator(),
        c.chapter(),
        json.readTree("{\"accessPolicy\":\"FREE\"}"),
        Etags.of(publishing.chapterToken(c.chapter())));
    catalog.patch(
        c.creator(),
        c.comic(),
        json.readTree("{\"ageRating\":\"18_PLUS\"}"),
        Etags.of(catalog.version(c.comic())));
    assertThat(reader.access(null, c.chapter()).allowed()).isFalse();
    assertThatThrownBy(() -> reader.manifest(null, c.chapter(), "JPEG"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void progressRevisionIdempotencyRewindAndHistoryGenerationUseSql() throws Exception {
    var c = context();
    assertThat(progress.history(c.reader(), 0, 20).historyGeneration()).isZero();
    String mutation = key();
    var first = input(c, 0, mutation, 0, "0.8");
    assertThat(progress.put(c.reader(), c.chapter(), first).revision()).isEqualTo(1);
    assertThat(progress.put(c.reader(), c.chapter(), first).revision()).isEqualTo(1);
    assertThatThrownBy(() -> progress.put(c.reader(), c.chapter(), input(c, 0, mutation, 0, "0.9")))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo("MUTATION_CONFLICT"));
    assertThatThrownBy(() -> progress.put(c.reader(), c.chapter(), input(c, 0, key(), 0, "0.1")))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> {
              assertThat(e.code()).isEqualTo("PROGRESS_CONFLICT");
              assertThat(((Progress) e.current()).revision()).isEqualTo(1);
            });
    assertThat(progress.put(c.reader(), c.chapter(), input(c, 1, key(), 0, "0.2")).relativeOffset())
        .isEqualByComparingTo("0.2");
    var other = actors.reader();
    assertThat(progress.history(other, 0, 20).items()).isEmpty();
    assertThat(progress.clear(c.reader()).historyGeneration()).isEqualTo(1);
    assertThat(progress.history(c.reader(), 0, 20).items()).isEmpty();
    assertThatThrownBy(() -> progress.put(c.reader(), c.chapter(), first))
        .isInstanceOfSatisfying(
            DomainException.class,
            e -> assertThat(e.code()).isEqualTo("HISTORY_GENERATION_CONFLICT"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.ProgressMutation WHERE UserId=? AND (ResultSnapshot IS"
                    + " NOT NULL OR ChapterId IS NOT NULL)",
                Integer.class,
                c.reader().userId()))
        .isZero();
    assertThat(progress.put(c.reader(), c.chapter(), input(c, 0, key(), 1, "0.3")).revision())
        .isEqualTo(1);
  }

  @Test
  void preferenceEtagsAndHttpConflictIncludeCurrentProgress() throws Exception {
    var c = context();
    assertThat(progress.preference(c.reader()).mode()).isEqualTo("SCROLL");
    String etag = Etags.of(progress.preferenceToken(c.reader()));
    progress.preference(c.reader(), new Preference("PAGED", "RTL", "HEIGHT"), etag);
    assertThat(progress.preference(c.reader()).direction()).isEqualTo("RTL");
    assertThatThrownBy(
            () -> progress.preference(c.reader(), new Preference("SCROLL", "LTR", "WIDTH"), etag))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(412));
    progress.put(c.reader(), c.chapter(), input(c, 0, key(), 0, "0.4"));
    String bearer =
        auth.login(
                new AuthRequests.Login(
                    auth.user(c.reader().userId()).email(), "Test-password-42!", "JUnit HTTP"))
            .accessToken();
    mvc.perform(
            put("/api/v1/me/progress/" + c.chapter())
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content(json.writeValueAsString(input(c, 0, key(), 0, "0.2"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.current.revision").value(1))
        .andExpect(jsonPath("$.code").value("PROGRESS_CONFLICT"));
    mvc.perform(get("/api/v1/chapters/" + c.chapter() + "/manifest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pages[0].mimeType").value("image/jpeg"));
  }

  @Test
  void collectionsAreOwnedAndDefaultCollectionIsSingleStatusSource() throws Exception {
    var c = context();
    var initial = library.collections(c.reader(), 0, 20);
    long defaults =
        Ids.parse(
            initial.items().stream()
                .filter(com.comicnet.library.dto.LibraryDtos.CollectionView::isSystem)
                .findFirst()
                .orElseThrow()
                .id());
    assertThat(
            library.collections(c.reader(), 0, 20).items().stream()
                .filter(com.comicnet.library.dto.LibraryDtos.CollectionView::isSystem)
                .count())
        .isEqualTo(1);
    var item =
        library.putItem(
            c.reader(),
            defaults,
            c.comic(),
            json.readTree("{\"readingStatus\":\"READING\",\"isFavorite\":true}"),
            null,
            "*");
    assertThat(item.isFavorite()).isTrue();
    String etag = Etags.of(library.itemToken(c.reader(), defaults, c.comic()));
    assertThatThrownBy(
            () -> library.putItem(c.reader(), defaults, c.comic(), json.readTree("{}"), null, "*"))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(412));
    library.putItem(
        c.reader(),
        defaults,
        c.comic(),
        json.readTree("{\"readingStatus\":\"COMPLETED\"}"),
        etag,
        null);
    assertThat(library.item(c.reader(), defaults, c.comic()).readingStatus())
        .isEqualTo("COMPLETED");
    var other = actors.reader();
    assertThatThrownBy(() -> library.collection(other, defaults))
        .isInstanceOf(DomainException.class);
    String key = key();
    var input = new com.comicnet.library.dto.LibraryDtos.CollectionInput("Bộ riêng");
    var custom = library.createCollection(c.reader(), input, key);
    assertThat(library.createCollection(c.reader(), input, key).id()).isEqualTo(custom.id());
    long collection = Ids.parse(custom.id());
    var membership =
        library.putItem(c.reader(), collection, c.comic(), json.readTree("{}"), null, "*");
    assertThat(json.valueToTree(membership).has("readingStatus")).isFalse();
    assertThat(json.valueToTree(membership).has("isFavorite")).isFalse();
    assertThatThrownBy(
            () ->
                library.putItem(
                    c.reader(),
                    collection,
                    c.comic(),
                    json.readTree("{\"isFavorite\":true}"),
                    Etags.of(library.itemToken(c.reader(), collection, c.comic())),
                    null))
        .isInstanceOf(DomainException.class);
    library.follow(c.reader(), c.comic());
    library.follow(c.reader(), c.comic());
    assertThat(library.follows(c.reader(), 0, 20).items().size()).isEqualTo(1);
    library.unfollow(c.reader(), c.comic());
    library.unfollow(c.reader(), c.comic());
    assertThatThrownBy(
            () ->
                library.deleteCollection(
                    c.reader(), defaults, Etags.of(library.collectionToken(c.reader(), defaults))))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo("SYSTEM_COLLECTION"));
    library.deleteCollection(
        c.reader(), collection, Etags.of(library.collectionToken(c.reader(), collection)));
    assertThat(catalog.publicDetail(c.comic()).id()).isEqualTo(c.comic() + "");
  }

  @Test
  void bookmarkSurvivesHistoryClearAndRemovedPageIsNotRemapped() throws Exception {
    var c = context();
    String key = key();
    var input =
        new com.comicnet.library.dto.LibraryDtos.BookmarkCreate(
            c.chapter() + "", c.version() + "", c.page(), new BigDecimal("0.25"), "Vị trí đã lưu");
    var bookmark = library.createBookmark(c.reader(), input, key);
    assertThat(library.createBookmark(c.reader(), input, key).id()).isEqualTo(bookmark.id());
    long bookmarkId = Ids.parse(bookmark.id());
    var other = actors.reader();
    assertThatThrownBy(() -> library.bookmark(other, bookmarkId))
        .isInstanceOf(DomainException.class);
    progress.put(c.reader(), c.chapter(), input(c, 0, key(), 0, "0.25"));
    progress.clear(c.reader());
    assertThat(library.bookmark(c.reader(), bookmarkId).pageId()).isEqualTo(c.page());
    long draft =
        Ids.parse(
            publishing
                .createVersion(c.creator(), c.chapter(), new VersionCreate(null), key())
                .id());
    publishing.removePage(
        c.creator(), draft, Ids.parse(c.page()), Etags.of(publishing.versionToken(draft)));
    var bytes = new ByteArrayOutputStream();
    var image = new BufferedImage(12, 16, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, 0xff99aa);
    ImageIO.write(image, "PNG", bytes);
    var job =
        uploads.accept(
            c.creator(),
            draft,
            new MockMultipartFile("file", "new.png", "image/png", bytes.toByteArray()),
            null,
            Etags.of(publishing.versionToken(draft)),
            key());
    jobs.add(Ids.parse(job.id()));
    worker.processJob(Ids.parse(job.id()));
    publishing.submit(c.creator(), draft, Etags.of(publishing.versionToken(draft)));
    publishing.review(
        c.admin(), draft, new Review("APPROVE", null), Etags.of(publishing.versionToken(draft)));
    publishing.publish(
        c.creator(),
        c.chapter(),
        new Publish(draft + ""),
        Etags.of(publishing.chapterToken(c.chapter())));
    var stored = library.bookmark(c.reader(), bookmarkId);
    assertThat(stored.anchorStatus()).isEqualTo("ANCHOR_UNAVAILABLE");
    assertThat(stored.versionId()).isEqualTo(c.version() + "");
    assertThat(stored.pageId()).isEqualTo(c.page());
  }
}
