package com.comicnet;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.*;
import jakarta.servlet.http.Cookie;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {
      "comicnet.mail.worker-enabled=false",
      "SETUP_ENABLED=true",
      "SETUP_TOKEN=JUnit-local-setup-capability-only-01234567890123456789"
    })
@AutoConfigureMockMvc
@Transactional
class IdentityHttpIT {
  @Autowired MockMvc mvc;
  @Autowired AuthenticationService auth;
  @Autowired JdbcTemplate jdbc;
  @Autowired Environment env;
  private final JsonMapper json = JsonMapper.builder().build();

  private String email() {
    return "http-" + UUID.randomUUID() + "@comicnet.test";
  }

  private String body(Object value) {
    return json.writeValueAsString(value);
  }

  private String actionToken(String email) throws Exception {
    String encrypted =
        jdbc.queryForObject(
            "SELECT TOP(1) EncryptedBody FROM dbo.AccountMailJob WHERE Recipient=? ORDER BY Id"
                + " DESC",
            String.class,
            email);
    String key =
        Files.readString(Path.of(env.getRequiredProperty("ACCOUNT_MAIL_KEY_PATH"))).strip();
    String mail = Encryptors.delux(key, "636f6d69636e6574").decrypt(encrypted);
    return mail.split("Mã tương thích cũ: ", 2)[1].split("\n", 2)[0].strip();
  }

  @Test
  void emailVerificationIsOneUseAndResetInvalidatesSessions() throws Exception {
    String email = email(), password = "Test42!";
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content(
                    body(Map.of("email", email, "password", password, "displayName", "Độc giả"))))
        .andExpect(status().isAccepted());
    String verify = actionToken(email);
    mvc.perform(
            post("/api/v1/auth/verify-email")
                .contentType("application/json")
                .content(body(Map.of("token", verify))))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/v1/auth/verify-email")
                .contentType("application/json")
                .content(body(Map.of("token", verify))))
        .andExpect(status().isBadRequest());
    var login = auth.login(new AuthRequests.Login(email, password, "JUnit"));
    auth.requestAction(email, "RESET_PASSWORD");
    String reset = actionToken(email);
    mvc.perform(
            post("/api/v1/auth/reset-password")
                .contentType("application/json")
                .content(body(Map.of("token", reset, "password", "New42!"))))
        .andExpect(status().isNoContent());
    assertThatThrownBy(() -> auth.authenticate(login.accessToken()))
        .isInstanceOf(com.comicnet.common.web.DomainException.class);
    assertThatThrownBy(() -> auth.login(new AuthRequests.Login(email, password, "JUnit")))
        .isInstanceOf(com.comicnet.common.web.DomainException.class);
    assertThat(
            auth.login(new AuthRequests.Login(email, "New42!", "JUnit"))
                .user()
                .emailVerified())
        .isTrue();
  }

  @Test
  void nativeLoginRequiresMatchingCsrfAndRefreshIsHttpOnly() throws Exception {
    String email = email();
    auth.register(new AuthRequests.Register(email, "Test42!", "Reader"));
    auth.verify(actionToken(email));
    String loginBody = body(Map.of("email", email, "password", "Test42!"));
    mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(loginBody))
        .andExpect(status().isForbidden());
    var csrf =
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
    String nonce = json.readTree(csrf.getContentAsString()).path("csrfToken").asText();
    var login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .cookie(new Cookie("comic_csrf", nonce))
                    .header("X-CSRF-Token", nonce)
                    .contentType("application/json")
                    .content(loginBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertThat(login.getHeaders("Set-Cookie"))
        .anyMatch(
            value ->
                value.startsWith("comic_refresh=")
                    && value.contains("HttpOnly")
                    && value.contains("Path=/api/v1/auth")
                    && value.contains("SameSite=Lax"));
    String bearer = json.readTree(login.getContentAsString()).path("accessToken").asText();
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk())
        .andExpect(header().exists("ETag"));
    mvc.perform(
            get("/api/v1/me")
                .header("Authorization", "Bearer " + bearer)
                .header("Origin", "https://foreign.example"))
        .andExpect(status().isForbidden());
  }

  @Test
  void removedSetupEndpointCannotCreateAdministrator() throws Exception {
    mvc.perform(post("/api/v1/setup/administrator")
            .contentType("application/json")
            .content(body(Map.of("email", email(), "password", "Test42!", "displayName", "Admin"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidPasswordsAndRoleInjectionAreRejected() throws Exception {
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content(
                    body(Map.of("email", email(), "password", "ab", "displayName", "Reader"))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType("application/json")
                .content(
                    body(
                        Map.of(
                            "email",
                            email(),
                            "password",
                            "Test42!",
                            "displayName",
                            "Reader",
                            "roles",
                            List.of("ADMINISTRATOR")))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void emailOtpEndpointsArePublicAndRejectReplay() throws Exception {
    String email = email();
    mvc.perform(post("/api/v1/auth/register").contentType("application/json")
        .content(body(Map.of("email", email, "password", "Abc123", "displayName", "Reader"))))
        .andExpect(status().isAccepted());
    Long userId = jdbc.queryForObject("SELECT Id FROM dbo.[User] WHERE NormalizedEmail=?",
        Long.class, email);
    jdbc.update("UPDATE dbo.AccountEmailOtp SET CodeHash=? WHERE UserId=? AND Purpose='VERIFY_EMAIL'",
        TokenSecrets.hash("123456"), userId);
    String verify = body(Map.of("email", email, "code", "123456"));
    mvc.perform(post("/api/v1/auth/verify-email-otp").contentType("application/json")
        .content(verify)).andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/auth/verify-email-otp").contentType("application/json")
        .content(verify)).andExpect(status().isBadRequest());
    auth.requestAction(email, "RESET_PASSWORD");
    assertThat(jdbc.update("UPDATE dbo.AccountEmailOtp SET CodeHash=? WHERE UserId=? AND Purpose='RESET_PASSWORD'",
        TokenSecrets.hash("654321"), userId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT CodeHash FROM dbo.AccountEmailOtp WHERE UserId=? AND Purpose='RESET_PASSWORD'",
        String.class, userId)).isEqualTo(TokenSecrets.hash("654321"));
    assertThat(jdbc.queryForObject("SELECT CASE WHEN ExpiresAt>SYSUTCDATETIME() AND ConsumedAt IS NULL AND Attempts<5 THEN 1 ELSE 0 END FROM dbo.AccountEmailOtp WHERE UserId=? AND Purpose='RESET_PASSWORD'",
        Integer.class, userId)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT Status FROM dbo.[User] WHERE Id=?", String.class, userId))
        .isEqualTo("ACTIVE");
    String reset = body(Map.of("email", email, "code", "654321", "password", "New123"));
    mvc.perform(post("/api/v1/auth/reset-password-otp").contentType("application/json")
        .content(reset)).andExpect(status().isNoContent());
    mvc.perform(post("/api/v1/auth/reset-password-otp").contentType("application/json")
        .content(reset)).andExpect(status().isBadRequest());
  }

  @Test
  void usernameOnlyAccountCanSignInWithoutRecoveryContact() throws Exception {
    String username = "qa" + UUID.randomUUID().toString().substring(0, 8);
    String phone = "+84912345678";
    mvc.perform(post("/api/v1/auth/register").contentType("application/json")
        .content(body(Map.of("username", username, "displayName", "Bạn đọc",
            "password", "Abc123", "phone", phone)))).andExpect(status().isAccepted());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.[User] WHERE NormalizedUsername=?"
        + " AND Email IS NULL AND EmailVerifiedAt IS NULL AND RecoveryPhone=?",
        Integer.class, username, phone)).isEqualTo(1);
    var csrf = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
        .andReturn().getResponse();
    String nonce = json.readTree(csrf.getContentAsString()).path("csrfToken").asText();
    var login = mvc.perform(post("/api/v1/auth/login").cookie(new Cookie("comic_csrf", nonce))
        .header("X-CSRF-Token", nonce).contentType("application/json")
        .content(body(Map.of("email", username, "password", "Abc123"))))
        .andExpect(status().isOk()).andReturn().getResponse();
    String bearer = json.readTree(login.getContentAsString()).path("accessToken").asText();
    String etag = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + bearer))
        .andExpect(status().isOk()).andReturn().getResponse().getHeader("ETag");
    String renamed = "next" + username.substring(2);
    mvc.perform(patch("/api/v1/me/username").header("Authorization", "Bearer " + bearer)
        .header("If-Match", etag).contentType("application/json")
        .content(body(Map.of("username", renamed))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(renamed));
  }
}
