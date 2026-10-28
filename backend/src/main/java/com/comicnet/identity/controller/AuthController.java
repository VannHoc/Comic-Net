package com.comicnet.identity.controller;

import com.comicnet.identity.api.*;
import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.common.web.Etags;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
  private final AuthenticationService auth;
  private final Environment env;

  public AuthController(AuthenticationService auth, Environment env) {
    this.auth = auth;
    this.env = env;
  }

  @PostMapping("/auth/register")
  public ResponseEntity<?> register(@Valid @RequestBody AuthRequests.Register body) {
    auth.register(body);
    return accepted();
  }

  @PostMapping("/auth/resend-verification")
  public ResponseEntity<?> resend(@Valid @RequestBody AuthRequests.EmailRequest body) {
    auth.requestAction(body.email(), "VERIFY_EMAIL");
    return accepted();
  }

  @PostMapping("/auth/forgot-password")
  public ResponseEntity<?> forgot(@Valid @RequestBody AuthRequests.EmailRequest body) {
    auth.requestAction(body.email(), "RESET_PASSWORD");
    return accepted();
  }

  @PostMapping("/auth/verify-email")
  public ResponseEntity<Void> verify(@Valid @RequestBody AuthRequests.TokenRequest body) {
    auth.verify(body.token());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/auth/verify-email-otp")
  public ResponseEntity<Void> verifyOtp(@Valid @RequestBody AuthRequests.OtpRequest body) {
    auth.verifyOtp(body.email(), body.code());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/auth/reset-password-otp")
  public ResponseEntity<Void> resetOtp(@Valid @RequestBody AuthRequests.OtpReset body) {
    auth.resetOtp(body.email(), body.code(), body.password());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/auth/reset-password")
  public ResponseEntity<Void> reset(@Valid @RequestBody AuthRequests.Reset body) {
    auth.reset(body.token(), body.password());
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<?> accepted() {
    return ResponseEntity.accepted()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("message", "Nếu thông tin hợp lệ, hướng dẫn đã được gửi tới email của bạn."));
  }

  @GetMapping("/auth/csrf")
  public ResponseEntity<?> csrf(CsrfToken token) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("csrfToken", token.getToken()));
  }

  public record SessionTokens(String accessToken, String tokenType, int expiresIn, UserView user) {}

  @PostMapping("/auth/login")
  public ResponseEntity<SessionTokens> login(
      @Valid @RequestBody AuthRequests.Login body,
      HttpServletRequest request,
      HttpServletResponse response) {
    return session(auth.login(body), request, response);
  }

  @PostMapping("/auth/refresh")
  public ResponseEntity<SessionTokens> refresh(
      @CookieValue(name = "comic_refresh", required = false) String cookie,
      HttpServletRequest request,
      HttpServletResponse response) {
    return session(auth.refresh(cookie), request, response);
  }

  private ResponseEntity<SessionTokens> session(
      AuthenticationService.SessionResult result,
      HttpServletRequest request,
      HttpServletResponse response) {
    csrfRepository().saveToken(null, request, response);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), Duration.ofDays(30)))
        .body(new SessionTokens(result.accessToken(), "Bearer", 600, result.user()));
  }

  private org.springframework.security.web.csrf.CookieCsrfTokenRepository csrfRepository() {
    return CookieSessionPolicy.csrf(
        !env.acceptsProfiles(org.springframework.core.env.Profiles.of("local")));
  }

  private String cookie(String value, Duration age) {
    return ResponseCookie.from("comic_refresh", value)
        .httpOnly(true)
        .secure(!env.acceptsProfiles(org.springframework.core.env.Profiles.of("local")))
        .sameSite("Lax")
        .path("/api/v1/auth")
        .maxAge(age)
        .build()
        .toString();
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "comic_refresh", required = false) String cookie,
      HttpServletRequest request,
      HttpServletResponse response) {
    auth.logout(cookie);
    csrfRepository().saveToken(null, request, response);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
        .header("Cache-Control", "no-store")
        .build();
  }

  @GetMapping("/me")
  public ResponseEntity<UserView> me(@AuthenticationPrincipal Identity actor) {
    if (!auth.permitted(actor, "profile.self"))
      throw com.comicnet.common.web.DomainException.forbidden();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(auth.userVersion(actor.userId())))
        .body(auth.user(actor.userId()));
  }

  @PatchMapping("/me")
  public ResponseEntity<UserView> profile(
      @AuthenticationPrincipal Identity actor,
      @Valid @RequestBody AuthRequests.Profile body,
      @RequestHeader(name = "If-Match", required = false) String etag) {
    var result = auth.profile(actor, body.displayName(), etag);
    return ResponseEntity.ok().eTag(Etags.of(auth.userVersion(actor.userId()))).body(result);
  }

  @PatchMapping("/me/username")
  public ResponseEntity<UserView> username(
      @AuthenticationPrincipal Identity actor,
      @Valid @RequestBody AuthRequests.Username body,
      @RequestHeader(name = "If-Match", required = false) String etag) {
    var result = auth.username(actor, body.username(), etag);
    return ResponseEntity.ok().eTag(Etags.of(auth.userVersion(actor.userId()))).body(result);
  }

}
