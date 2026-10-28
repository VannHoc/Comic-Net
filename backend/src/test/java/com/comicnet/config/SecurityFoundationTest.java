package com.comicnet.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.comicnet.common.web.ApiExceptionHandler;
import com.comicnet.common.web.ProblemWriter;
import com.comicnet.observability.web.RequestIdFilter;
import com.comicnet.observability.web.SystemController;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebMvcTest(SystemController.class)
@Import({
  SecurityConfiguration.class,
  ProblemWriter.class,
  RequestIdFilter.class,
  ApiExceptionHandler.class
})
class SecurityFoundationTest {
  @Autowired MockMvc mvc;

  @BeforeEach
  void attachSecurityTestSupport(@Autowired WebApplicationContext context) {
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(RequestIdFilter.class))
            .apply(springSecurity())
            .build();
  }

  @Test
  void pingIsPublicAndMatchesContract() throws Exception {
    mvc.perform(get("/api/v1/system/ping").header("X-Request-ID", "test-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.service").value("comic-net-backend"))
        .andExpect(header().string("X-Request-ID", "test-123"))
        .andExpect(header().string("Cache-Control", "no-store"));
  }

  @Test
  void sensitiveRoutesAndFakeTokensAreDeniedWithoutHtmlOrSecretEcho() throws Exception {
    for (String path :
        new String[] {
          "/api/v1/admin/users",
          "/actuator/env",
          "/v3/api-docs",
          "/api/v1/setup/administrator",
          "/login"
        }) {
      mvc.perform(
              get(path)
                  .header("Authorization", "Bearer secret-invalid-token")
                  .header("X-Client-Type", "swing")
                  .param("password", "do-not-echo"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
          .andExpect(jsonPath("$.errors").isArray())
          .andExpect(
              content()
                  .string(
                      org.hamcrest.Matchers.not(
                          org.hamcrest.Matchers.containsString("do-not-echo"))))
          .andExpect(header().doesNotExist("Location"));
    }
  }

  @Test
  @WithMockUser
  void authenticatedFixtureStillCannotUseProtectedRoutes() throws Exception {
    mvc.perform(get("/api/v1/admin/users"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void allBrowserOriginsAreRejectedWhileWebExtensionIsDisabled() throws Exception {
    for (String origin : new String[] {"https://evil.example", "null", "http://localhost:5173"}) {
      mvc.perform(get("/api/v1/system/ping").header("Origin", origin))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("ORIGIN_DENIED"))
          .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    mvc.perform(
            options("/api/v1/auth/login")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }

  @Test
  void nativeClientHeaderCannotBypassCsrf() throws Exception {
    for (String action : new String[] {"login", "refresh", "logout"}) {
      mvc.perform(post("/api/v1/auth/" + action).header("X-Client-Type", "swing"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
  }

  @Test
  void unsafeRequestIdIsReplacedAndQueryIsNotReturned() throws Exception {
    mvc.perform(
            get("/api/v1/private")
                .header("X-Request-ID", "invalid id !")
                .queryParam("token", "secret"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string("X-Request-ID", org.hamcrest.Matchers.matchesPattern("[a-f0-9-]{36}")))
        .andExpect(jsonPath("$.instance").value("/api/v1/private"));
  }

  @Test
  void nativeCookieProtocolRequiresMatchingNonceAndSetsLocalFlags() throws Exception {
    var repository = SecurityConfiguration.csrfRepository();
    var servlet = new MockServletContext();
    servlet.setMajorVersion(6);
    servlet.setEffectiveMajorVersion(6);
    var initialRequest = new MockHttpServletRequest(servlet);
    initialRequest.setSecure(false);
    var initialResponse = new MockHttpServletResponse();
    CsrfToken token = repository.generateToken(initialRequest);
    repository.saveToken(token, initialRequest, initialResponse);
    String setCookie = initialResponse.getHeader("Set-Cookie");
    assertThat(setCookie)
        .contains("comic_csrf=", "Path=/")
        .doesNotContain("HttpOnly", "Secure", "Domain=");
    // MockHttpServletResponse does not serialize arbitrary Servlet cookie attributes.
    assertThat(initialResponse.getCookie("comic_csrf").getAttribute("SameSite")).isEqualTo("Lax");

    var filter = new CsrfFilter(repository);
    filter.setRequireCsrfProtectionMatcher(SecurityConfiguration.cookieMutationMatcher());
    filter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
    for (boolean valid : new boolean[] {true, false}) {
      var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
      request.setCookies(new Cookie("comic_csrf", token.getToken()));
      request.addHeader("X-CSRF-Token", valid ? token.getToken() : "wrong");
      var response = new MockHttpServletResponse();
      var reachedHandler = new AtomicBoolean();
      filter.doFilter(request, response, (req, res) -> reachedHandler.set(true));
      assertThat(reachedHandler.get()).isEqualTo(valid);
      if (!valid) assertThat(response.getStatus()).isEqualTo(403);
    }
  }
}
