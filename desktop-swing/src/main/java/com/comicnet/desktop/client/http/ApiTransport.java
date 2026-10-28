package com.comicnet.desktop.client.http;

import com.comicnet.desktop.session.ClientSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class ApiTransport implements AutoCloseable {
  private final HttpClient http;

  public ApiTransport(ClientSession session) {
    http =
        HttpClient.newBuilder()
            .cookieHandler(session.cookieManager())
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public CompletableFuture<HttpResponse<String>> get(URI uri) {
    return http.sendAsync(
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  public CompletableFuture<HttpResponse<String>> send(HttpRequest request) {
    return http.sendAsync(
        request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Override
  public void close() {
    http.shutdownNow();
  }
}
