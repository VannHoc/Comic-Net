package com.comicnet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.support.SqlTestActors;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** HTTP-level regression for the creator authorization failure through the real filter chain. */
@SpringBootTest(properties = "comicnet.mail.worker-enabled=false")
@AutoConfigureMockMvc
@Transactional
class CreatorAuthorizationHttpIT {
  @Autowired MockMvc mvc;
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  private final JsonMapper json = JsonMapper.builder().build();

  private String token(long userId) {
    String email = auth.user(userId).email();
    return auth.login(new AuthRequests.Login(email, "Test-password-42!", "JUnit HTTP"))
        .accessToken();
  }

  private String comic(String title) {
    var body = new LinkedHashMap<String, Object>();
    body.put("title", title);
    body.put("slug", "http-" + UUID.randomUUID());
    body.put("description", "Mô tả");
    body.put("type", "MANGA");
    body.put("countryCode", "VN");
    body.put("status", "ONGOING");
    body.put("ageRating", "ALL_AGE");
    body.put("alternativeTitles", List.of());
    body.put("genreIds", List.of());
    body.put("tagIds", List.of());
    body.put("warningIds", List.of());
    body.put("contributors", List.of());
    body.put("publisherIds", List.of());
    return json.writeValueAsString(body);
  }

  @Test
  void creatorSavesDraftThroughHttpWhileReaderStaysForbidden() throws Exception {
    var actors = new SqlTestActors(auth, accounts, jdbc, entities);
    Identity admin = actors.administrator(),
        creator = actors.creator(admin),
        reader = actors.reader();
    String creatorToken = token(creator.userId()), readerToken = token(reader.userId());
    mvc.perform(
            post("/api/v1/comics")
                .header("Authorization", "Bearer " + creatorToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(comic("Truyện HTTP")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ownerUserId").value(creator.userId() + ""))
        .andExpect(jsonPath("$.visibility").value("HIDDEN"));
    mvc.perform(get("/api/v1/me/teams").header("Authorization", "Bearer " + creatorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(
            post("/api/v1/comics")
                .header("Authorization", "Bearer " + readerToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json")
                .content(comic("Trái phép")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }
}
