package com.comicnet.desktop.features.publishing;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.session.ClientSession;
import com.sun.net.httpserver.HttpServer;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class PublishingPanelTest {
  @Test
  void optionalApprovalNoteIsOmittedAndRejectionReasonIsPreserved() {
    assertEquals(java.util.Map.of("decision", "APPROVE"), PublishingPanel.reviewInput(true, "   "));
    assertEquals(
        java.util.Map.of("decision", "REJECT", "reason", "Missing pages"),
        PublishingPanel.reviewInput(false, " Missing pages "));
  }

  @Test
  void creatorStudioDashboardLoadsComicsAndOpensWizard() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/api/v1",
        exchange -> {
          String path = exchange.getRequestURI().getPath(), response;
          if (path.equals("/api/v1/me")) response = "{\"roles\":[\"READER\",\"CREATOR\"]}";
          else if (path.equals("/api/v1/publishing/comics"))
            response = list("{\"id\":\"11\",\"title\":\"Truyện V2\",\"type\":\"MANGA\",\"description\":\"Mô tả\"}");
          else if (path.endsWith("/comics/11/translations"))
            response = list("{\"id\":\"22\",\"languageTag\":\"vi\",\"editionKey\":\"main\",\"visibility\":\"PUBLIC\"}");
          else if (path.endsWith("/translations/22/chapters"))
            response = list("{\"id\":\"33\",\"displayNumber\":\"1\",\"title\":\"Chapter 1\",\"accessPolicy\":\"FREE\"}");
          else if (path.equals("/api/v1/publishing/chapters/33/versions"))
            response = list(version("44", 1, "DRAFT"));
          else if (path.equals("/api/v1/publishing/versions/44"))
            response = version("44", 1, "DRAFT");
          else response = "{}";

          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.getResponseHeaders().set("ETag", "\"test-version\"");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();

    var holder = new AtomicReference<PublishingPanel>();
    try (var session = new ClientSession();
        var transport = new ApiTransport(session)) {
      session.accept(session.generation(), "test-bearer", 600);
      var api =
          new ApiClient(
              new DesktopConfig(
                  URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1")),
              transport,
              session);

      SwingUtilities.invokeAndWait(
          () -> {
            var panel = new PublishingPanel(api, ignored -> {});
            holder.set(panel);
            panel.onShown();
          });

      PublishingPanel panel = holder.get();
      // Verify comic card loaded
      await(() -> label(panel, "Truyện V2"));
      assertTrue(label(panel, "Xưởng xuất bản"));

      // Click + Tạo truyện
      SwingUtilities.invokeAndWait(
          () -> find(panel, JButton.class, b -> "+ Tạo truyện".equals(b.getText())).doClick());

      // Verify wizard step 1 opened
      await(() -> label(panel, "Tạo truyện mới"));
    } finally {
      if (holder.get() != null) SwingUtilities.invokeAndWait(holder.get()::close);
      server.stop(0);
      executor.shutdownNow();
    }
  }

  private static String version(String id, int number, String state) {
    return "{\"id\":\""
        + id
        + "\",\"chapterId\":\"33\",\"versionNumber\":"
        + number
        + ",\"state\":\""
        + state
        + "\",\"pages\":[]}";
  }

  private static String list(String values) {
    return "{\"items\":[" + values + "],\"page\":{\"page\":0,\"size\":20,\"totalElements\":1}}";
  }

  private static void await(BooleanSupplier check) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    var result = new AtomicBoolean();
    while (System.nanoTime() < deadline) {
      SwingUtilities.invokeAndWait(() -> result.set(check.getAsBoolean()));
      if (result.get()) return;
      Thread.sleep(20);
    }
    fail("Expected Swing state was not reached.");
  }

  private static boolean label(Container root, String prefix) {
    return findOrNull(
            root,
            JLabel.class,
            value -> value.getText() != null && value.getText().contains(prefix))
        != null;
  }

  private static <T extends Component> T find(Container root, Class<T> kind, Predicate<T> check) {
    T result = findOrNull(root, kind, check);
    if (result == null) throw new AssertionError("Component not found: " + kind.getSimpleName());
    return result;
  }

  private static <T extends Component> T findOrNull(
      Container root, Class<T> kind, Predicate<T> check) {
    for (Component component : root.getComponents()) {
      if (kind.isInstance(component) && check.test(kind.cast(component)))
        return kind.cast(component);
      if (component instanceof Container child) {
        T nested = findOrNull(child, kind, check);
        if (nested != null) return nested;
      }
    }
    return null;
  }
}
