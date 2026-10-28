package com.comicnet.desktop.client;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.SystemApi;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.client.http.MediaTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.session.ClientSession;
import com.sun.net.httpserver.HttpServer;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemApiTest {
  private HttpServer server;
  private URI base;
  private final ClientSession session = new ClientSession();
  private ApiTransport transport;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    transport = new ApiTransport(session);
  }

  @AfterEach
  void stop() {
    transport.close();
    session.close();
    server.stop(0);
  }

  private void respond(String path, int status, String json) {
    server.createContext(
        path,
        exchange -> {
          byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.getResponseHeaders().set("X-Request-ID", "test-server");
          exchange.sendResponseHeaders(status, bytes.length);
          try (var out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
  }

  private SystemApi api() {
    return new SystemApi(new DesktopConfig(base.resolve("/api/v1")), transport);
  }

  @Test
  void parsesPingAndRealHttpReadinessUp() throws Exception {
    respond("/api/v1/system/ping", 200, "{\"status\":\"UP\",\"service\":\"comic-net-backend\"}");
    respond("/actuator/health/readiness", 200, "{\"status\":\"UP\"}");
    var result = api().probe().completion().get(3, TimeUnit.SECONDS);
    assertTrue(result.apiUp());
    assertTrue(result.databaseReady());
    assertEquals("test-server", result.requestId());
  }

  @Test
  void databaseDownDoesNotBecomeSuccess() throws Exception {
    respond("/api/v1/system/ping", 200, "{\"status\":\"UP\",\"service\":\"comic-net-backend\"}");
    respond("/actuator/health/readiness", 503, "{\"status\":\"DOWN\"}");
    var result = api().probe().completion().get(3, TimeUnit.SECONDS);
    assertTrue(result.apiUp());
    assertFalse(result.databaseReady());
  }

  @Test
  void invalidPayloadAndRedirectAreRejected() {
    respond("/api/v1/system/ping", 200, "{\"status\":\"fake\",\"service\":\"x\"}");
    respond("/actuator/health/readiness", 200, "{\"status\":\"UP\"}");
    assertThrows(Exception.class, () -> api().probe().completion().get(3, TimeUnit.SECONDS));
  }

  @Test
  void unavailableBackendIsAnError() {
    server.stop(0);
    assertThrows(Exception.class, () -> api().probe().completion().get(3, TimeUnit.SECONDS));
  }

  @Test
  void cookiesAreIsolatedAndMediaCarriesNoCredentialsOrRedirects() throws Exception {
    var cookie = new HttpCookie("comic_refresh", "sensitive");
    cookie.setPath("/");
    cookie.setVersion(0);
    session.cookieManager().getCookieStore().add(base, cookie);
    var observedCookie = new AtomicReference<String>();
    var observedAuthorization = new AtomicReference<String>();
    server.createContext(
        "/media",
        exchange -> {
          observedCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
          observedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          exchange.getResponseHeaders().set("Location", "https://not-allowed.example/redirect");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    try (var media = new MediaTransport(Set.of("127.0.0.1"));
        var secondSession = new ClientSession()) {
      assertEquals(302, media.check(base.resolve("/media")).get(3, TimeUnit.SECONDS).statusCode());
      assertNull(observedCookie.get());
      assertNull(observedAuthorization.get());
      assertTrue(secondSession.cookieManager().getCookieStore().getCookies().isEmpty());
      assertThrows(
          IllegalArgumentException.class,
          () -> media.check(URI.create("https://not-allowed.example/a")));
    }
    session.close();
    assertTrue(session.cookieManager().getCookieStore().getCookies().isEmpty());
  }
}
