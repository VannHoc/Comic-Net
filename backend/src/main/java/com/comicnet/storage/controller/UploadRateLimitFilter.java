package com.comicnet.storage.controller;

import com.comicnet.common.web.ProblemWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounded single-process admission before multipart parsing; user authorization stays in services.
 */
public final class UploadRateLimitFilter extends OncePerRequestFilter {
  private record Window(long until, int count) {}

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
  private final ProblemWriter problems;

  public UploadRateLimitFilter(ProblemWriter problems) {
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!request.getMethod().equals("POST")
        || !(path.equals("/api/v1/assets")
            || path.matches("/api/v1/publishing/versions/[0-9]+/uploads"))) {
      chain.doFilter(request, response);
      return;
    }
    long now = System.currentTimeMillis();
    if (windows.size() >= 10000)
      windows.entrySet().removeIf(entry -> entry.getValue().until() < now);
    String key = request.getRemoteAddr();
    if (windows.size() >= 10000 && !windows.containsKey(key)) {
      limited(request, response, 60);
      return;
    }
    var current =
        windows.compute(
            key,
            (ignored, old) ->
                old == null || old.until() < now
                    ? new Window(now + 60000, 1)
                    : new Window(old.until(), old.count() + 1));
    if (current.count() > 30) {
      limited(request, response, Math.max(1, (current.until() - now + 999) / 1000));
      return;
    }
    chain.doFilter(request, response);
  }

  private void limited(HttpServletRequest request, HttpServletResponse response, long seconds)
      throws IOException {
    response.setHeader("Retry-After", Long.toString(seconds));
    problems.write(
        request,
        response,
        429,
        "UPLOAD_RATE_LIMITED",
        "Có quá nhiều upload. Vui lòng chờ trước khi thử lại.");
  }
}
