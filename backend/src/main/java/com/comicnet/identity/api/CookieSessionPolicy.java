package com.comicnet.identity.api;

import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class CookieSessionPolicy {
  private CookieSessionPolicy() {}

  public static RequestMatcher mutations() {
    return request ->
        "POST".equals(request.getMethod())
            && switch (request.getRequestURI()) {
              case "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout" -> true;
              default -> false;
            };
  }

  public static CookieCsrfTokenRepository csrf(boolean secure) {
    var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repository.setCookieName("comic_csrf");
    repository.setHeaderName("X-CSRF-Token");
    repository.setCookiePath("/");
    repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secure));
    return repository;
  }
}
