package com.comicnet.identity.controller;

import com.comicnet.common.web.ProblemWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance limiter; remote peer comes from the server, never forwarded/client headers. */
public final class AuthRateLimitFilter extends OncePerRequestFilter {
  private record Window(long ends, int count) {}

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
  private final ProblemWriter problems;

  public AuthRateLimitFilter(ProblemWriter problems) {
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!request.getMethod().equals("POST")
        || !path.startsWith("/api/v1/auth/")) {
      chain.doFilter(request, response);
      return;
    }
    long now = System.currentTimeMillis();
    long period = 60_000;
    if (windows.size() >= 10000) windows.entrySet().removeIf(e -> e.getValue().ends() < now);
    String key = request.getRemoteAddr() + ":" + path;
    if (windows.size() >= 10000 && !windows.containsKey(key)) {
      limited(request, response, 60);
      return;
    }
    int max =
        path.endsWith("register")
                || path.endsWith("forgot-password")
                || path.endsWith("resend-verification")
            ? 5
            : 30;
    var window =
        windows.compute(
            key,
            (k, current) ->
                current == null || current.ends() < now
                    ? new Window(now + period, 1)
                    : new Window(current.ends(), current.count() + 1));
    if (window.count() > max) {
      limited(request, response, Math.max(1, (window.ends() - now + 999) / 1000));
      return;
    }
    chain.doFilter(request, response);
  }

  private void limited(HttpServletRequest request, HttpServletResponse response, long seconds)
      throws IOException {
    response.setHeader("Retry-After", Long.toString(seconds));
    problems.write(request, response, 429, "RATE_LIMITED", "Vui lòng chờ trước khi thử lại.");
  }
}
