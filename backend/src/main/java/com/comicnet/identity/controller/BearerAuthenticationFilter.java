package com.comicnet.identity.controller;

import com.comicnet.identity.api.IdentityService;
import com.comicnet.common.web.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class BearerAuthenticationFilter extends OncePerRequestFilter {
  private final ObjectProvider<IdentityService> identities;
  private final ProblemWriter problems;

  public BearerAuthenticationFilter(
      ObjectProvider<IdentityService> identities, ProblemWriter problems) {
    this.identities = identities;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null) {
      if (!header.startsWith("Bearer ") || header.length() > 8200) {
        problems.write(request, response, 401, "AUTH_REQUIRED", "Authentication is required.");
        return;
      }
      var service = identities.getIfAvailable();
      if (service == null) {
        problems.write(request, response, 401, "AUTH_REQUIRED", "Authentication is required.");
        return;
      }
      try {
        var identity = service.authenticate(header.substring(7));
        var auth =
            UsernamePasswordAuthenticationToken.authenticated(
                identity,
                null,
                identity.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (DomainException error) {
        SecurityContextHolder.clearContext();
        problems.write(request, response, error.status(), error.code(), error.getMessage());
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
