package com.comicnet.config;

import com.comicnet.identity.api.IdentityService;
import com.comicnet.identity.controller.AuthRateLimitFilter;
import com.comicnet.identity.controller.BearerAuthenticationFilter;
import com.comicnet.common.web.ProblemWriter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {
  public static RequestMatcher cookieMutationMatcher() {
    return com.comicnet.identity.api.CookieSessionPolicy.mutations();
  }

  public static CookieCsrfTokenRepository csrfRepository() {
    return com.comicnet.identity.api.CookieSessionPolicy.csrf(false);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ProblemWriter problems,
      ObjectProvider<IdentityService> identities,
      org.springframework.core.env.Environment env)
      throws Exception {
    http.sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(
                        com.comicnet.identity.api.CookieSessionPolicy.csrf(
                            !env.acceptsProfiles(
                                org.springframework.core.env.Profiles.of("local"))))
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .requireCsrfProtectionMatcher(cookieMutationMatcher()))
        .addFilterBefore(new OriginPolicyFilter(problems), CsrfFilter.class)
        .addFilterBefore(new AuthRateLimitFilter(problems), CsrfFilter.class)
        .addFilterBefore(
            new com.comicnet.storage.controller.UploadRateLimitFilter(problems),
            CsrfFilter.class)
        .addFilterBefore(
            new BearerAuthenticationFilter(identities, problems),
            AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/system/ping",
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/verify-email-otp",
                        "/api/v1/auth/resend-verification",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/reset-password-otp",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout")
                    .permitAll()
                    .requestMatchers("/api/v1/me", "/api/v1/me/**")
                    .authenticated()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMINISTRATOR")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/comics",
                        "/api/v1/comics/*",
                        "/api/v1/comics/by-slug/*",
                        "/api/v1/comics/*/translations",
                        "/api/v1/comics/*/rating-summary",
                        "/api/v1/comics/*/reviews",
                        "/api/v1/comics/*/similar",
                        "/api/v1/recommendations",
                        "/api/v1/rankings",
                        "/api/v1/community/comments",
                        "/api/v1/community/comments/*",
                        "/api/v1/metadata",
                        "/api/v1/search/comics",
                        "/api/v1/search/suggestions",
                        "/api/v1/chapters/*/prices")
                    .permitAll()
                    .requestMatchers("/api/v1/publishing/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/chapters/*/unlock")
                    .authenticated()
                    .requestMatchers("/api/v1/community/**")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/translations/*/chapters",
                        "/api/v1/translations/*/volumes")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/chapters/*",
                        "/api/v1/chapters/*/access",
                        "/api/v1/chapters/*/manifest")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/translations/*/chapters",
                        "/api/v1/translations/*/volumes",
                        "/api/v1/chapters/*/versions")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/volumes/*", "/api/v1/chapters/*")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST, "/api/v1/comics", "/api/v1/comics/*/translations")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/comics/*", "/api/v1/translations/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/comics/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/comics/*/rating")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/comics/*/rating")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/assets")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, error) ->
                            problems.write(
                                request,
                                response,
                                401,
                                "AUTH_REQUIRED",
                                "Authentication is required."))
                    .accessDeniedHandler(
                        (request, response, error) -> {
                          boolean csrf = error instanceof CsrfException;
                          problems.write(
                              request,
                              response,
                              403,
                              csrf ? "CSRF_INVALID" : "ACCESS_DENIED",
                              csrf
                                  ? "A valid CSRF cookie and header are required."
                                  : "Access is denied.");
                        }));
    return http.build();
  }
}
