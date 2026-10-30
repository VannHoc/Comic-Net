package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.catalog.api.CatalogDtos.*;
import com.comicnet.catalog.api.CatalogQueries;
import com.comicnet.catalog.service.CatalogService;
import com.comicnet.common.web.*;
import com.comicnet.creator.api.CreatorDirectory;
import com.comicnet.support.SqlTestActors;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {
      "comicnet.mail.worker-enabled=false",
      "SETUP_ENABLED=true",
      "SETUP_TOKEN=JUnit-local-setup-capability-only-01234567890123456789"
    })
@Transactional
class CatalogFlowIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired CreatorDirectory teams;
  @Autowired CatalogService catalog;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  private final JsonMapper json = JsonMapper.builder().build();
  private SqlTestActors actors;

  @BeforeEach
  void actors() {
    actors = new SqlTestActors(auth, accounts, jdbc, entities);
  }

  private String key() {
    return UUID.randomUUID().toString();
  }

  private ComicInput input(String title, String slug, String age) {
    return new ComicInput(
        title,
        slug,
        "Mô tả tiếng Việt",
        "MANGA",
        "JP",
        "ONGOING",
        age,
        List.of("Tên khác " + slug),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private long team(Identity admin, Identity owner) {
    return Ids.parse(
        teams
            .create(
                admin,
                new CreatorDirectory.TeamCreate("Nhóm", "team-" + key(), owner.getName()),
                key())
            .id());
  }

  @Test
  void publicCatalogFiltersVisibilityAgeAndSearchesUnicodeAliases() {
    Identity admin = actors.administrator(), creator = actors.creator(admin);
    team(admin, creator);
    String slug = "comic-" + key();
    var comic = catalog.create(creator, input("Truyện Việt", slug, "ALL_AGE"), key());
    long id = Ids.parse(comic.id());
    assertThatThrownBy(() -> catalog.publicDetail(id)).isInstanceOf(DomainException.class);
    catalog.visibility(
        admin,
        id,
        new VisibilityPut("PUBLIC", "JUnit publish catalog"),
        Etags.of(catalog.version(id)));
    assertThat(catalog.publicDetail(id).title()).isEqualTo("Truyện Việt");
    assertThat(
            catalog
                .list(
                    new CatalogQueries.Filter(
                        "Tên khác " + slug, null, null, null, null, "TITLE_ASC"),
                    0,
                    20)
                .items())
        .extracting(ComicSummary::id)
        .contains(comic.id());
    assertThat(
            catalog
                .list(new CatalogQueries.Filter("' OR 1=1--", null, null, null, null, null), 0, 20)
                .items())
        .isEmpty();
    catalog.patch(
        creator, id, json.readTree("{\"ageRating\":\"18_PLUS\"}"), Etags.of(catalog.version(id)));
    assertThat(catalog.publicDetail(id).id()).isEqualTo(id + "");
    assertThat(
            catalog
                .list(new CatalogQueries.Filter(slug, null, null, null, null, null), 0, 20)
                .items())
        .hasSize(1);
  }

  @Test
  void administratorCanHideAndRepublishWithoutDeletingComic() {
    Identity admin = actors.administrator();
    var comic = catalog.create(admin, input("Tái xuất bản", "repub-" + key(), "ALL_AGE"), key());
    long id = Ids.parse(comic.id());
    catalog.visibility(admin, id, new VisibilityPut("PUBLIC", "JUnit publish"), Etags.of(catalog.version(id)));
    assertThat(catalog.publicDetail(id).title()).isEqualTo("Tái xuất bản");
    catalog.visibility(admin, id, new VisibilityPut("HIDDEN", "JUnit unpublish"), Etags.of(catalog.version(id)));
    assertThatThrownBy(() -> catalog.publicDetail(id)).isInstanceOf(DomainException.class);
    assertThat(catalog.managedDetail(admin, id).visibility()).isEqualTo("HIDDEN");
    catalog.visibility(admin, id, new VisibilityPut("PUBLIC", "JUnit republish"), Etags.of(catalog.version(id)));
    assertThat(catalog.publicDetail(id).title()).isEqualTo("Tái xuất bản");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Comic WHERE Id=? AND DeletedAt IS NULL", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void ownershipTeamBoundariesAndEtagsAreEnforced() {
    Identity admin = actors.administrator(),
        owner = actors.creator(admin),
        other = actors.creator(admin);
    long owningTeam = team(admin, owner), otherTeam = team(admin, other);
    var comic = catalog.create(owner, input("Comic", "comic-" + key(), "ALL_AGE"), key());
    long id = Ids.parse(comic.id());
    String original = Etags.of(catalog.version(id));
    assertThatThrownBy(() -> catalog.managedDetail(other, id)).isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () -> catalog.patch(other, id, json.readTree("{\"title\":\"Forbidden\"}"), original))
        .isInstanceOf(DomainException.class);
    catalog.patch(owner, id, json.readTree("{\"title\":\"Đã cập nhật\"}"), original);
    assertThatThrownBy(
            () -> catalog.patch(owner, id, json.readTree("{\"title\":\"Stale\"}"), original))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(412));
    assertThatThrownBy(
            () ->
                catalog.createTranslation(
                    owner, id, new TranslationCreate(otherTeam + "", "vi", "main", null), key()))
        .isInstanceOf(DomainException.class);
    var translation =
        catalog.createTranslation(
            owner, id, new TranslationCreate(owningTeam + "", "vi", "main", null), key());
    assertThat(catalog.translation(owner, Ids.parse(translation.id())).teamId())
        .isEqualTo(owningTeam + "");
    assertThatThrownBy(() -> catalog.translation(other, Ids.parse(translation.id())))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void metadataReferencesAndCreateIdempotencyAreReal() {
    Identity admin = actors.administrator();
    String suffix = key();
    var metadata =
        catalog.createMetadata(
            admin, new MetadataCreate("GENRE", "thể-loại-" + suffix, "Phiêu lưu"), key());
    assertThat(metadata.code()).isEqualTo("thể-loại-" + suffix);
    var value =
        new ComicInput(
            "Comic",
            "comic-" + suffix,
            "Description",
            "COMIC",
            "VN",
            "ONGOING",
            "ALL_AGE",
            List.of(),
            List.of(metadata.id()),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            null);
    String request = key();
    var comic = catalog.create(admin, value, request);
    assertThat(catalog.create(admin, value, request).id()).isEqualTo(comic.id());
    assertThat(catalog.managedDetail(admin, Ids.parse(comic.id())).genreIds())
        .containsExactly(metadata.id());
    assertThat(catalog.metadata().genres()).extracting(Lookup::id).contains(metadata.id());
    assertThatThrownBy(
            () ->
                catalog.patch(
                    admin,
                    Ids.parse(comic.id()),
                    json.readTree("{\"ownerUserId\":\"2\"}"),
                    Etags.of(catalog.version(Ids.parse(comic.id())))))
        .isInstanceOf(DomainException.class);
  }
}
