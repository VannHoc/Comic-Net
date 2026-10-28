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
  void creatingDraftAfterSelectingPublishedVersionKeepsTheNewEditorOpen() throws Exception {
    var created = new AtomicBoolean();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/api/v1",
        exchange -> {
          String path = exchange.getRequestURI().getPath(), response;
          if (path.equals("/api/v1/me")) response = "{\"roles\":[\"READER\",\"CREATOR\"]}";
          else if (path.equals("/api/v1/publishing/comics"))
            response = list("{\"id\":\"11\",\"title\":\"Comic\",\"type\":\"MANGA\"}");
          else if (path.endsWith("/comics/11/translations"))
            response =
                list(
                    "{\"id\":\"22\",\"languageTag\":\"vi\",\"editionKey\":\"main\",\"visibility\":\"PUBLIC\"}");
          else if (path.endsWith("/translations/22/chapters"))
            response =
                list(
                    "{\"id\":\"33\",\"displayNumber\":\"1\",\"title\":\"Chapter\",\"visibility\":\"PUBLIC\"}");
          else if (path.endsWith("/translations/22/volumes")) response = list("");
          else if (path.equals("/api/v1/chapters/33/versions")) {
            created.set(true);
            response = version("55", 2, "DRAFT");
          } else if (path.equals("/api/v1/publishing/chapters/33/versions")) {
            if (created.get()) {
              try {
                Thread.sleep(250);
              } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
              }
              response = list(version("55", 2, "DRAFT") + "," + version("44", 1, "PUBLISHED"));
            } else response = list(version("44", 1, "PUBLISHED"));
          } else if (path.equals("/api/v1/publishing/versions/44"))
            response = version("44", 1, "PUBLISHED");
          else if (path.equals("/api/v1/publishing/versions/55"))
            response = version("55", 2, "DRAFT");
          else if (path.equals("/api/v1/publishing/versions/55/submit"))
            response = version("55", 2, "READY_FOR_REVIEW");
          else if (path.equals("/api/v1/publishing/chapters/33"))
            response = "{\"id\":\"33\",\"title\":\"Chapter\",\"publishedVersionId\":\"44\"}";
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
      JTable comics = table(panel, "Truyện quản lý"),
          translations = table(panel, "Bản dịch"),
          chapters = table(panel, "Chương"),
          versions = table(panel, "Version");
      await(() -> comics.getRowCount() == 1);
      SwingUtilities.invokeAndWait(() -> comics.setRowSelectionInterval(0, 0));
      await(() -> translations.getRowCount() == 1);
      SwingUtilities.invokeAndWait(() -> translations.setRowSelectionInterval(0, 0));
      await(() -> chapters.getRowCount() == 1);
      SwingUtilities.invokeAndWait(() -> chapters.setRowSelectionInterval(0, 0));
      await(() -> versions.getRowCount() == 1);
      SwingUtilities.invokeAndWait(() -> versions.setRowSelectionInterval(0, 0));
      await(() -> label(panel, "Version 1 · PUBLISHED"));
      SwingUtilities.invokeAndWait(
          () ->
              find(panel, JButton.class, button -> button.getText().equals("Tạo bản nháp"))
                  .doClick());
      await(() -> versions.getRowCount() == 2);
      await(() -> label(panel, "Version 2 · DRAFT"));
      SwingUtilities.invokeAndWait(
          () -> {
            assertEquals(0, versions.getSelectedRow());
            assertEquals(2, versions.getValueAt(0, 0));
          });
      SwingUtilities.invokeAndWait(
          () ->
              find(panel, JButton.class, button -> button.getText().equals("Gửi duyệt")).doClick());
      await(() -> label(panel, "Version 2 · READY_FOR_REVIEW"));
      SwingUtilities.invokeAndWait(
          () -> {
            assertEquals("READY_FOR_REVIEW", versions.getValueAt(0, 1));
            assertEquals(0, versions.getSelectedRow());
          });
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
    return "{\"items\":[" + values + "],\"page\":{\"page\":0,\"size\":20,\"totalElements\":2}}";
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

  private static JTable table(Container root, String heading) {
    return find(root, JTable.class, table -> table.getColumnName(0).equals(heading));
  }

  private static boolean label(Container root, String prefix) {
    return findOrNull(
            root,
            JLabel.class,
            value -> value.getText() != null && value.getText().startsWith(prefix))
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
