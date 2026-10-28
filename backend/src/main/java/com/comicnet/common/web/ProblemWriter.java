package com.comicnet.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProblemWriter {
  private final ObjectMapper json;

  public ProblemWriter(ObjectMapper json) {
    this.json = json;
  }

  public ApiProblem body(HttpServletRequest request, int status, String code, String detail) {
    String title =
        switch (status) {
          case 400 -> "Bad Request";
          case 401 -> "Unauthorized";
          case 403 -> "Forbidden";
          case 404 -> "Not Found";
          case 405 -> "Method Not Allowed";
          case 409 -> "Conflict";
          case 412 -> "Precondition Failed";
          case 413 -> "Content Too Large";
          case 415 -> "Unsupported Media Type";
          case 422 -> "Unprocessable Content";
          case 428 -> "Precondition Required";
          case 429 -> "Too Many Requests";
          case 503 -> "Service Unavailable";
          default -> "Internal Server Error";
        };
    String path = request.getRequestURI();
    if (path.length() > 1000) path = path.substring(0, 1000);
    return new ApiProblem(
        "about:blank",
        title,
        status,
        code,
        detail,
        path,
        String.valueOf(request.getAttribute(RequestIds.ATTRIBUTE)),
        List.of());
  }

  public void write(
      HttpServletRequest request,
      HttpServletResponse response,
      int status,
      String code,
      String detail)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    json.writeValue(response.getOutputStream(), body(request, status, code, detail));
  }
}
