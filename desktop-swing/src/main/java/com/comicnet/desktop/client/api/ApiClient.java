package com.comicnet.desktop.client.api;

import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.session.ClientSession;
import com.comicnet.desktop.session.RememberedSession;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON REST client. Bearer and refresh cookies stay in this desktop session. */
public final class ApiClient {
  private final DesktopConfig config;
  private final ApiTransport transport;
  private final ClientSession session;
  private final RememberedSession remembered;
  private final JsonMapper json = JsonMapper.builder().build();
  private CompletableFuture<Reply> refreshInFlight;
  private CompletableFuture<Void> cookieTail=CompletableFuture.completedFuture(null);

  public record Reply(JsonNode body, String etag) {}

  public static final class ApiFailure extends RuntimeException {
    private final int status;
    private final String code, requestId;
    private final JsonNode current;

    public ApiFailure(int status, String code, String detail, String requestId) {
      this(status, code, detail, requestId, null);
    }

    public ApiFailure(int status, String code, String detail, String requestId, JsonNode current) {
      super(detail);
      this.status = status;
      this.code = code;
      this.requestId = requestId;
      this.current = current;
    }

    public int status() {
      return status;
    }

    public String code() {
      return code;
    }

    public String requestId() {
      return requestId;
    }

    public JsonNode current() {
      return current;
    }
  }

  public ApiClient(DesktopConfig config, ApiTransport transport, ClientSession session) {
    this.config = config;
    this.transport = transport;
    this.session = session;
    this.remembered = new RememberedSession(config.apiBase());
  }

  public boolean rememberEnabled() { return remembered.enabled(); }

  public boolean remember(boolean selected) {
    if (!selected) { remembered.forget(); return true; }
    return remembered.save(session);
  }

  public CompletableFuture<Boolean> restoreRemembered() {
    return CompletableFuture.supplyAsync(() -> remembered.restore(session))
        .thenCompose(found -> found ? refresh().thenApply(reply -> true) :
            CompletableFuture.completedFuture(false))
        .exceptionally(error -> { remembered.forget(); return false; });
  }

  public boolean signedIn() {
    return session.accessToken() != null;
  }

  public String apiBase() {
    return config.apiBase().toString();
  }

  public AutoCloseable onSessionChanged(Runnable listener) {
    return session.onChanged(listener);
  }

  public CompletableFuture<Reply> get(String path) {
    return request("GET", path, null, null);
  }

  public CompletableFuture<Reply> request(String method, String path, Object body, String etag) {
    return request(method, path, body, etag, Map.of());
  }

  public CompletableFuture<Reply> create(String path, Object body, String key) {
    return request("POST", path, body, null, Map.of("Idempotency-Key", key));
  }

  public CompletableFuture<Reply> upload(
      String path, Path file, Map<String, String> fields, String key, String etag) {
    long generation = session.generation();
    return CompletableFuture.supplyAsync(
            () -> {
              try {
                if (!Files.isRegularFile(file))
                  throw new java.io.IOException("Upload file missing.");
                if (Files.size(file) > 100L * 1024 * 1024)
                  throw new ApiFailure(413, "CLIENT_FILE_SIZE", "Tệp vượt giới hạn 100 MiB.", "—");
                String boundary = "comicnet-" + java.util.UUID.randomUUID();
                var publishers = new java.util.ArrayList<HttpRequest.BodyPublisher>();
                for (var entry : fields.entrySet()) {
                  if (!entry.getKey().matches("[a-zA-Z]+"))
                    throw new IllegalArgumentException("Invalid multipart field.");
                  publishers.add(
                      HttpRequest.BodyPublishers.ofString(
                          "--"
                              + boundary
                              + "\r\nContent-Disposition: form-data; name=\""
                              + entry.getKey()
                              + "\"\r\n\r\n"
                              + entry.getValue()
                              + "\r\n"));
                }
                String mime = Files.probeContentType(file);
                if (mime == null || mime.contains("\r") || mime.contains("\n"))
                  mime = "application/octet-stream";
                publishers.add(
                    HttpRequest.BodyPublishers.ofString(
                        "--"
                            + boundary
                            + "\r\n"
                            + "Content-Disposition: form-data; name=\"file\";"
                            + " filename=\"upload.bin\"\r\n"
                            + "Content-Type: "
                            + mime
                            + "\r\n\r\n"));
                publishers.add(HttpRequest.BodyPublishers.ofFile(file));
                publishers.add(HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
                return new Multipart(
                    boundary,
                    HttpRequest.BodyPublishers.concat(
                        publishers.toArray(HttpRequest.BodyPublisher[]::new)));
              } catch (java.io.IOException e) {
                throw new CompletionException(e);
              }
            })
        .thenCompose(
            body ->
                (session.expiresSoon() ? refresh() : CompletableFuture.<Reply>completedFuture(null))
                    .thenCompose(
                        ignored -> {
                          if (!path.startsWith("/")
                              || path.startsWith("//")
                              || path.contains(":")
                              || path.contains("\\"))
                            throw new IllegalArgumentException("Relative API path required.");
                          var request =
                              HttpRequest.newBuilder(java.net.URI.create(config.apiBase() + path))
                                  .timeout(Duration.ofMinutes(2))
                                  .header("Accept", "application/json")
                                  .header(
                                      "Content-Type",
                                      "multipart/form-data; boundary=" + body.boundary())
                                  .header("Idempotency-Key", key);
                          if (session.accessToken() != null)
                            request.header("Authorization", "Bearer " + session.accessToken());
                          if (etag != null) request.header("If-Match", etag);
                          return transport
                              .send(request.POST(body.publisher()).build())
                              .thenApply(this::parse);
                        }))
        .thenApply(
            reply -> {
              if (generation != session.generation()) throw new CancellationException();
              return reply;
            });
  }

  private record Multipart(String boundary, HttpRequest.BodyPublisher publisher) {}

  public CompletableFuture<Reply> request(
      String method, String path, Object body, String etag, Map<String, String> headers) {
    long generation = session.generation();
    return (session.expiresSoon() ? refresh() : CompletableFuture.<Reply>completedFuture(null))
        .thenCompose(ignored -> send(method, path, body, etag, headers, true))
        .exceptionallyCompose(
            error -> {
              Throwable cause = unwrap(error);
              if (generation != session.generation())
                return CompletableFuture.failedFuture(new CancellationException());
              if (cause instanceof ApiFailure failure
                  && failure.status() == 401
                  && session.accessToken() != null)
                return refresh()
                    .thenCompose(ignored -> send(method, path, body, etag, headers, true));
              return CompletableFuture.failedFuture(cause);
            })
        .thenApply(
            reply -> {
              if (generation != session.generation()) throw new CancellationException();
              return reply;
            });
  }

  private CompletableFuture<Reply> send(
      String method,
      String path,
      Object body,
      String etag,
      Map<String, String> headers,
      boolean bearer) {
    if (!path.startsWith("/") || path.startsWith("//") || path.contains(":") || path.contains("\\"))
      throw new IllegalArgumentException("Relative API path required.");
    var request =
        HttpRequest.newBuilder(java.net.URI.create(config.apiBase() + path))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json");
    if (bearer && session.accessToken() != null)
      request.header("Authorization", "Bearer " + session.accessToken());
    if (etag != null) request.header("If-Match", etag);
    headers.forEach(request::header);
    var publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
    if (body != null) request.header("Content-Type", "application/json");
    return transport.send(request.method(method, publisher).build()).thenApply(this::parse);
  }

  private Reply parse(HttpResponse<String> response) {
    JsonNode parsed = response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
    if (response.statusCode() < 200 || response.statusCode() >= 300)
      throw new ApiFailure(
          response.statusCode(),
          parsed.path("code").asText("HTTP_ERROR"),
          parsed.path("detail").asText("Không thể hoàn tất thao tác."),
          response.headers().firstValue("X-Request-ID").orElse("—"),
          parsed.path("current"));
    return new Reply(parsed, response.headers().firstValue("ETag").orElse(null));
  }

  private synchronized CompletableFuture<Reply> cookieOperation(String path, Object body) {
    // Even a cancelled view must let its cookie response settle before another login/refresh.
    // Otherwise an older Set-Cookie response can overwrite a newer account's refresh cookie.
    var operation=cookieTail.handle((ignored,error)->null).thenCompose(ignored->send("GET", "/auth/csrf", null, null, Map.of(), false))
        .thenCompose(
            csrf ->
                send(
                    "POST",
                    path,
                    body,
                    null,
                    Map.of("X-CSRF-Token", csrf.body().path("csrfToken").asText()),
                    false));
    cookieTail=operation.handle((reply,error)->null);
    return operation;
  }

  public CompletableFuture<Reply> login(String email, String password) {
    long expected = session.generation();
    return cookieOperation(
            "/auth/login",
            Map.of("email", email, "password", password, "deviceLabel", "Comic Net Swing"))
        .thenApply(reply -> acceptSession(expected, reply));
  }

  private Reply acceptSession(long expected, Reply reply) {
    if (!session.accept(
        expected,
        reply.body().path("accessToken").asText(),
        reply.body().path("expiresIn").asInt()))
      throw new CancellationException("Session changed.");
    return reply;
  }

  public synchronized CompletableFuture<Reply> refresh() {
    if (refreshInFlight != null) return refreshInFlight;
    long expected = session.generation();
    var pending =
        cookieOperation("/auth/refresh", null).thenApply(reply -> {
          Reply accepted = acceptSession(expected, reply);
          if (remembered.enabled()) remembered.save(session);
          return accepted;
        });
    refreshInFlight = pending;
    pending.whenComplete(
        (reply, error) -> {
          synchronized (this) {
            if (refreshInFlight == pending) refreshInFlight = null;
          }
          if (error != null) session.clearIfGeneration(expected);
        });
    return pending;
  }

  public synchronized CompletableFuture<Void> logout() {
    remembered.forget();
    CompletableFuture<?> before =
        refreshInFlight == null ? CompletableFuture.completedFuture(null) : refreshInFlight;
    return before
        .handle((value, error) -> null)
        .thenCompose(ignored -> cookieOperation("/auth/logout", null))
        .handle(
            (reply, error) -> {
              session.clear();
              if (error != null) throw new CompletionException(unwrap(error));
              return null;
            });
  }

  public static Throwable unwrap(Throwable error) {
    while ((error instanceof CompletionException || error instanceof ExecutionException)
        && error.getCause() != null) error = error.getCause();
    return error;
  }
}
