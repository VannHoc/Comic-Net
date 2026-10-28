package com.comicnet.desktop.client;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.session.ClientSession;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ApiClientTest {
  @Test
  void concurrentExpiryUsesOneRefreshAndKeepsCredentialsOnApiTransport() throws Exception {
    var refreshes = new AtomicInteger();
    var protectedReads = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/api/v1/auth/csrf",
        exchange -> {
          exchange
              .getResponseHeaders()
              .add("Set-Cookie", "comic_csrf=test-nonce; Path=/; SameSite=Lax");
          byte[] bytes = "{\"csrfToken\":\"test-nonce\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.createContext(
        "/api/v1/auth/refresh",
        exchange -> {
          refreshes.incrementAndGet();
          assertEquals("test-nonce", exchange.getRequestHeaders().getFirst("X-CSRF-Token"));
          assertTrue(
              exchange.getRequestHeaders().getFirst("Cookie").contains("comic_csrf=test-nonce"));
          assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] bytes =
              "{\"accessToken\":\"rotated-test-token\",\"expiresIn\":600,\"user\":{}}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.createContext(
        "/api/v1/protected",
        exchange -> {
          assertEquals(
              "Bearer rotated-test-token", exchange.getRequestHeaders().getFirst("Authorization"));
          protectedReads.incrementAndGet();
          byte[] bytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try (var session = new ClientSession();
        var transport = new ApiTransport(session)) {
      session.accept(session.generation(), "expired-test-token", -1);
      var api =
          new ApiClient(
              new DesktopConfig(
                  URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1")),
              transport,
              session);
      var requests = new ArrayList<CompletableFuture<ApiClient.Reply>>();
      for (int i = 0; i < 12; i++) requests.add(api.get("/protected"));
      CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(8, TimeUnit.SECONDS);
      assertEquals(1, refreshes.get());
      assertEquals(12, protectedReads.get());
    } finally {
      server.stop(0);
      executor.shutdownNow();
    }
  }

  @Test
  void staleSessionCannotOverwriteOrClearNewLogin() {
    try (var session = new ClientSession()) {
      long old = session.generation();
      session.clear();
      assertFalse(session.accept(old, "stale", 600));
      session.accept(session.generation(), "new", 600);
      session.clearIfGeneration(old);
      assertEquals("new", session.accessToken());
      session.close();
      assertNull(session.accessToken());
    }
  }
  @Test void cancelledLoginCannotRaceTheNextAccountsCookieResponse()throws Exception{
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var executor=Executors.newCachedThreadPool();server.setExecutor(executor);var firstStarted=new CountDownLatch(1);var releaseFirst=new CountDownLatch(1);var csrfCalls=new AtomicInteger();var logins=new AtomicInteger();
    server.createContext("/api/v1/auth/csrf",exchange->{csrfCalls.incrementAndGet();byte[] bytes="{\"csrfToken\":\"nonce\"}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});
    server.createContext("/api/v1/auth/login",exchange->{int number=logins.incrementAndGet();if(number==1){firstStarted.countDown();try{releaseFirst.await(5,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}}exchange.getResponseHeaders().add("Set-Cookie","refresh=account-"+number+"; Path=/api/v1/auth; HttpOnly");byte[] bytes=("{\"accessToken\":\"account-"+number+"\",\"expiresIn\":600}").getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
    try(var session=new ClientSession();var transport=new ApiTransport(session)){
      var api=new ApiClient(new DesktopConfig(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1")),transport,session);var first=api.login("first@comicnet.test","Test-only-password");assertTrue(firstStarted.await(3,TimeUnit.SECONDS));first.cancel(true);var second=api.login("second@comicnet.test","Test-only-password");Thread.sleep(150);assertEquals(1,csrfCalls.get(),"A second CSRF/cookie exchange must wait for the prior response.");releaseFirst.countDown();second.get(5,TimeUnit.SECONDS);assertEquals("account-2",session.accessToken());assertTrue(session.cookieManager().getCookieStore().getCookies().stream().anyMatch(cookie->cookie.getName().equals("refresh")&&cookie.getValue().equals("account-2")));
    }finally{releaseFirst.countDown();server.stop(0);executor.shutdownNow();}
  }
}
