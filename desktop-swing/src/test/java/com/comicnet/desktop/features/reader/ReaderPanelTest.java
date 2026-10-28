package com.comicnet.desktop.features.reader;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.http.*;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.session.ClientSession;
import com.sun.net.httpserver.HttpServer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Headless component tests cover REST binding and timer state; no production data is mocked. */
class ReaderPanelTest {
  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void firstReadUsesHistoryGenerationWhenNoProgressExists() throws Exception {
    CompletableFuture<JsonNode> saved = new CompletableFuture<>();
    try (var fixture = new Fixture(false, saved)) {
      fixture.open();
      JsonNode body = saved.get(8, TimeUnit.SECONDS);
      assertEquals(7, body.path("historyGeneration").asInt());
      assertEquals(0, body.path("expectedRevision").asLong());
      assertEquals("10", body.path("pageId").asText());
      assertFalse(body.path("mutationId").asText().isBlank());
    }
  }

  @Test
  void unavailableSavedAnchorIsNotSilentlyReplacedByTheFirstPage() throws Exception {
    CompletableFuture<JsonNode> saved = new CompletableFuture<>();
    try (var fixture = new Fixture(true, saved)) {
      fixture.open();
      assertTrue(fixture.loaded.await(5, TimeUnit.SECONDS));
      Thread.sleep(1800);
      assertFalse(
          saved.isDone(),
          "An unavailable anchor requires an explicit user choice before writing progress.");
    }
  }

  private final class Fixture implements AutoCloseable {
    final HttpServer server;
    final ClientSession session = new ClientSession();
    final ApiTransport transport = new ApiTransport(session);
    final MediaTransport media = new MediaTransport(Set.of("127.0.0.1"));
    final ReaderPanel panel;
    final CountDownLatch loaded = new CountDownLatch(1);

    Fixture(boolean unavailable, CompletableFuture<JsonNode> saved) throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      var bytes = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(8, 12, BufferedImage.TYPE_INT_RGB), "PNG", bytes);
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      server.createContext(
          "/",
          exchange -> {
            String path = exchange.getRequestURI().getPath();
            String response;
            int status = 200;
            if (path.equals("/image")) {
              exchange.getResponseHeaders().set("Content-Type", "image/png");
              exchange.sendResponseHeaders(200, bytes.size());
              exchange.getResponseBody().write(bytes.toByteArray());
              exchange.close();
              return;
            }
            if (path.endsWith("/manifest"))
              response =
                  "{\"chapterId\":\"1\",\"versionId\":\"2\",\"pages\":[{\"pageId\":\"10\",\"position\":1,\"width\":8,\"height\":12,\"mimeType\":\"image/png\",\"url\":\""
                      + base
                      + "/image\",\"expiresAt\":\""
                      + Instant.now().plusSeconds(120)
                      + "\"}]}";
            else if (path.endsWith("/reading-preferences")) {
              response = "{\"mode\":\"SCROLL\",\"direction\":\"LTR\",\"fit\":\"WIDTH\"}";
              exchange.getResponseHeaders().set("ETag", "\"preference\"");
            } else if (path.endsWith("/history"))
              response = "{\"items\":[],\"historyGeneration\":7}";
            else if (path.endsWith("/progress/1")) {
              if (exchange.getRequestMethod().equals("PUT")) {
                JsonNode value = json.readTree(exchange.getRequestBody().readAllBytes());
                saved.complete(value);
                response = "{\"revision\":1,\"historyGeneration\":7}";
              } else {
                if (unavailable)
                  response =
                      "{\"chapterId\":\"1\",\"versionId\":\"2\",\"pageId\":\"99\",\"relativeOffset\":0.4,\"revision\":3,\"historyGeneration\":7,\"anchorStatus\":\"ANCHOR_UNAVAILABLE\"}";
                else {
                  response = "{\"code\":\"PROGRESS_NOT_FOUND\"}";
                  status = 404;
                }
                loaded.countDown();
              }
            } else
              response =
                  "{\"comicTitle\":\"Reader test\",\"title\":\"Chapter\",\"displayNumber\":\"1\"}";
            byte[] body = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      session.accept(session.generation(), "unit-test-bearer", 600);
      ApiClient api =
          new ApiClient(new DesktopConfig(URI.create(base + "/api/v1")), transport, session);
      var holder = new AtomicReference<ReaderPanel>();
      SwingUtilities.invokeAndWait(() -> holder.set(new ReaderPanel(api, media, ignored -> {})));
      panel = holder.get();
    }

    void open() throws Exception {
      SwingUtilities.invokeAndWait(
          () -> {
            panel.setSize(900, 650);
            layout(panel);
            panel.open(ReaderLocation.chapter("1"));
          });
      SwingUtilities.invokeLater(() -> layout(panel));
    }

    private void layout(Container parent) {
      parent.doLayout();
      for (Component child : parent.getComponents())
        if (child instanceof Container container) layout(container);
    }

    @Override
    public void close() throws Exception {
      SwingUtilities.invokeAndWait(panel::close);
      session.close();
      transport.close();
      media.close();
      server.stop(0);
    }
  }
}
