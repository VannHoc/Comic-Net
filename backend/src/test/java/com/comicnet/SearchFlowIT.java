package com.comicnet;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.catalog.api.CatalogDtos.*;
import com.comicnet.catalog.service.CatalogService;
import com.comicnet.common.web.*;
import com.comicnet.search.controller.SearchController;
import com.comicnet.search.service.SearchHistoryService;
import com.comicnet.support.SqlTestActors;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "comicnet.mail.worker-enabled=false")
@AutoConfigureMockMvc
@Transactional
class SearchFlowIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  @Autowired SearchHistoryService history;
  @Autowired SearchController search;
  @Autowired CatalogService catalog;
  @Autowired MockMvc mvc;

  @Test
  void historyIsDeduplicatedBoundedAndPrivate() throws Exception {
    var actors = new SqlTestActors(auth, accounts, jdbc, entities);
    var first = actors.reader();
    var second = actors.reader();
    history.remember(first.userId(), "  Truyện   hay  ");
    history.remember(first.userId(), "Truyện hay");
    assertThat(history.list(first.userId())).hasSize(1);
    assertThat(
            java.time.Duration.between(
                    history.list(first.userId()).getFirst().lastSearchedAt(),
                    java.time.Instant.now())
                .abs()
                .toSeconds())
        .isLessThan(5);
    assertThat(history.list(second.userId())).isEmpty();
    history.remember(second.userId(), "Riêng tư");
    for (int n = 0; n < 55; n++) history.remember(first.userId(), "query-" + n);
    assertThat(history.list(first.userId())).hasSize(50);
    String chosen = history.list(first.userId()).getFirst().query();
    history.delete(first.userId(), chosen);
    assertThat(history.list(first.userId())).hasSize(49);
    history.delete(first.userId(), null);
    assertThat(history.list(first.userId())).isEmpty();
    assertThat(history.list(second.userId())).hasSize(1);
    mvc.perform(get("/api/v1/me/search-history")).andExpect(status().isUnauthorized());
    String bearer =
        auth.login(
                new com.comicnet.identity.dto.AuthRequests.Login(
                    auth.user(first.userId()).email(), "Test-password-42!", "Search HTTP"))
            .accessToken();
    mvc.perform(
            post("/api/v1/me/search-history")
                .header("Authorization", "Bearer " + bearer)
                .contentType("application/json")
                .content("{\"query\":\"HTTP history\"}"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/me/search-history").header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].query").value("HTTP history"));
    mvc.perform(delete("/api/v1/me/search-history").header("Authorization", "Bearer " + bearer))
        .andExpect(status().isNoContent());
    assertThatThrownBy(() -> history.remember(first.userId(), " "))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void suggestionsNeverExposeHiddenOrDeletedComics() throws Exception {
    var actors = new SqlTestActors(auth, accounts, jdbc, entities);
    var admin = actors.administrator();
    String marker = "Search" + UUID.randomUUID();
    long comic =
        Ids.parse(
            catalog
                .create(
                    admin,
                    new ComicInput(
                        marker,
                        "search-" + UUID.randomUUID(),
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
                        null),
                    UUID.randomUUID().toString())
                .id());
    assertThat(search.suggestions(marker)).isEmpty();
    catalog.visibility(
        admin, comic, new VisibilityPut("PUBLIC", "Search QA"), Etags.of(catalog.version(comic)));
    assertThat(search.suggestions(marker))
        .singleElement()
        .extracting(SearchController.Suggestion::id)
        .isEqualTo(comic + "");
    mvc.perform(get("/api/v1/search/suggestions").param("q", marker)).andExpect(status().isOk());
    catalog.softDelete(admin, comic, Etags.of(catalog.version(comic)));
    assertThat(search.suggestions(marker)).isEmpty();
    assertThat(search.suggestions("' OR 1=1 --")).isEmpty();
  }
}
