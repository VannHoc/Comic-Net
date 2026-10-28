package com.comicnet.config;

import com.comicnet.common.web.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Web extension is disabled in Phase 1. Native clients send no Origin. */
public final class OriginPolicyFilter extends OncePerRequestFilter {
  private final ProblemWriter problems;

  public OriginPolicyFilter(ProblemWriter problems) {
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getHeader("Origin") != null) {
      problems.write(request, response, 403, "ORIGIN_DENIED", "This origin is not enabled.");
      return;
    }
    chain.doFilter(request, response);
  }
}
