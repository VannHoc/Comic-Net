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
import com.comicnet.recommendation.dto.RatingDtos.*;
import com.comicnet.recommendation.service.RatingService;
import com.comicnet.support.SqlTestActors;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {"comicnet.mail.worker-enabled=false", "comicnet.upload.worker-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class RatingFlowIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired CatalogService catalog;
  @Autowired RatingService ratings;
  @Autowired com.comicnet.recommendation.service.RecommendationService recommendations;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  @Autowired MockMvc mvc;
  private SqlTestActors actors;
  private Identity admin, reader;
  private long comic;

  @BeforeEach
  void setup() {
    actors = new SqlTestActors(auth, accounts, jdbc, entities);
    admin = actors.administrator();
    reader = actors.reader();
    comic =
        Ids.parse(
            catalog
                .create(
                    admin,
                    new ComicInput(
                        "Rating SQL",
                        "rating-" + UUID.randomUUID(),
                        "Review integration",
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
                    UUID.randomUUID().toString())
                .id());
    catalog.visibility(
        admin, comic, new VisibilityPut("PUBLIC", "Rating QA"), Etags.of(catalog.version(comic)));
  }

  @Test
  void oneCurrentRatingPerUserAndAggregatesFollowUpdatesAndDeletion() {
    ratings.put(reader, comic, new RatingInput(5, "Truyện hay 😊"), null, "*");
    assertThatThrownBy(() -> ratings.put(reader, comic, new RatingInput(3, "Replay"), null, "*"))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(412));
    String stale = Etags.of(ratings.token(reader, comic));
    ratings.put(reader, comic, new RatingInput(4, "Đã sửa"), stale, null);
    assertThatThrownBy(() -> ratings.put(reader, comic, new RatingInput(1, "Stale"), stale, null))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(412));
    ratings.put(admin, comic, new RatingInput(2, null), null, "*");
    assertThat(ratings.summary(comic).count()).isEqualTo(2);
    assertThat(ratings.summary(comic).average()).isEqualByComparingTo("3.00");
    assertThat(ratings.reviews(comic, 0, 20).items()).hasSize(1);
    ratings.delete(reader, comic, Etags.of(ratings.token(reader, comic)));
    assertThat(ratings.summary(comic).count()).isEqualTo(1);
    assertThat(ratings.summary(comic).average()).isEqualByComparingTo("2.00");
  }

  @Test
  void httpEnforcesAuthValidationConditionalHeadersAndHiddenContent() throws Exception {
    String token =
        auth.login(
                new AuthRequests.Login(
                    auth.user(reader.userId()).email(), "Test-password-42!", "JUnit rating"))
            .accessToken();
    String path = "/api/v1/comics/" + comic + "/rating";
    mvc.perform(get("/api/v1/comics/" + comic + "/rating-summary")).andExpect(status().isOk());
    mvc.perform(put(path).contentType("application/json").content("{\"score\":5}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"score\":5}"))
        .andExpect(status().is(428));
    mvc.perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .header("If-None-Match", "*")
                .contentType("application/json")
                .content("{\"score\":5,\"review\":\"Rất hay\"}"))
        .andExpect(status().isOk())
        .andExpect(header().exists("ETag"));
    mvc.perform(
            put(path)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"score\":6}"))
        .andExpect(status().isBadRequest());
    catalog.visibility(
        admin,
        comic,
        new VisibilityPut("HIDDEN", "Rating hidden QA"),
        Etags.of(catalog.version(comic)));
    mvc.perform(get("/api/v1/comics/" + comic + "/rating-summary"))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/comics/" + comic + "/reviews")).andExpect(status().isNotFound());
  }

  @Test
  void inactiveAccountsDoNotInfluencePublicAverage() {
    ratings.put(reader, comic, new RatingInput(5, "Reader review"), null, "*");
    ratings.put(admin, comic, new RatingInput(1, "Admin review"), null, "*");
    jdbc.update("UPDATE dbo.[User] SET Status='SUSPENDED' WHERE Id=?", reader.userId());
    assertThat(ratings.summary(comic).count()).isEqualTo(1);
    assertThat(ratings.summary(comic).average()).isEqualByComparingTo("1.00");
    assertThat(ratings.reviews(comic, 0, 20).items())
        .extracting(RatingView::userId)
        .containsExactly(admin.userId() + "");
  }

  private long otherComic(String type) {
    long id =
        Ids.parse(
            catalog
                .create(
                    admin,
                    new ComicInput(
                        "Recommendation SQL",
                        "recommend-" + UUID.randomUUID(),
                        "Discovery integration",
                        type,
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
                    UUID.randomUUID().toString())
                .id());
    catalog.visibility(
        admin, id, new VisibilityPut("PUBLIC", "Recommendation QA"), Etags.of(catalog.version(id)));
    return id;
  }

  @Test
  void recommendationsUseRealColdStartAndFilterHiddenRatings() {
    assertThat(recommendations.discover(20).items())
        .extracting(ComicSummary::id)
        .contains(comic + "");
    long hidden = otherComic("MANGA"), visible = otherComic("MANGA");
    ratings.put(reader, hidden, new RatingInput(5, "Will be hidden"), null, "*");
    ratings.put(reader, visible, new RatingInput(4, "Visible"), null, "*");
    catalog.visibility(
        admin,
        hidden,
        new VisibilityPut("HIDDEN", "Hide recommendation QA"),
        Etags.of(catalog.version(hidden)));
    assertThat(recommendations.discover(20).items())
        .extracting(ComicSummary::id)
        .contains(visible + "")
        .doesNotContain(hidden + "");
  }

  @Test
  void similarComicsPreferMatchingContentAndExcludeSource() {
    long same = otherComic("MANGA"), different = otherComic("COMIC");
    var result = recommendations.similar(comic, 20).items();
    assertThat(result)
        .extracting(ComicSummary::id)
        .doesNotContain(comic + "")
        .contains(same + "", different + "");
    var ids = result.stream().map(ComicSummary::id).toList();
    assertThat(ids.indexOf(same + "")).isLessThan(ids.indexOf(different + ""));
    assertThatThrownBy(() -> recommendations.similar(comic, 21))
        .isInstanceOf(DomainException.class);
  }
}
