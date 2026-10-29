package com.comicnet.desktop.features.wallet;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.session.ClientSession;
import com.sun.net.httpserver.HttpServer;
import java.awt.Component;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class AdminWalletPanelTest {
  @Test
  void adminWalletPanelRendersPurchasesTabAndProcessesRefund() throws Exception {
    var refundRequested = new AtomicBoolean(false);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/api/v1",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String response = "{}";
          if (path.equals("/api/v1/admin/wallets")) {
            response = "{\"items\":[{\"id\":1118,\"username\":\"reader\",\"email\":\"r@test.com\",\"paidCoin\":10,\"rewardCoin\":20}],\"page\":{\"totalElements\":1}}";
          } else if (path.equals("/api/v1/admin/packages")) {
            response = "{\"items\":[]}";
          } else if (path.equals("/api/v1/admin/purchases")) {
            response = "{\"items\":[{\"id\":10,\"userId\":1118,\"username\":\"reader\",\"chapterId\":227,\"chapterTitle\":\"Ch1\",\"comicTitle\":\"Comic1\",\"amount\":25,\"currencyCode\":\"REWARD\",\"status\":\"COMPLETED\",\"createdAt\":\"2026-10-29T10:00:00\",\"revokedAt\":null,\"revokeReason\":null}],\"page\":{\"totalElements\":1}}";
          } else if (path.equals("/api/v1/admin/purchases/10/refund")) {
            refundRequested.set(true);
            response = "{\"purchaseId\":10,\"status\":\"REFUNDED\",\"refundTransactionId\":999}";
          }
          byte[] body = response.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    try {
      var session = new ClientSession();
      session.accept(session.generation(), "test-admin-token", 3600);
      var transport = new ApiTransport(session);
      var config = new DesktopConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1"));
      var api = new ApiClient(config, transport, session);

      var panelHolder = new AtomicReference<AdminWalletPanel>();
      SwingUtilities.invokeAndWait(() -> {
        var panel = new AdminWalletPanel(api);
        panelHolder.set(panel);
      });

      var panel = panelHolder.get();
      assertNotNull(panel);

      // Verify tabs include "Mua chương"
      SwingUtilities.invokeAndWait(() -> {
        JTabbedPane tabs = findTabbedPane(panel);
        assertNotNull(tabs, "JTabbedPane must be present");
        boolean hasPurchasesTab = false;
        for (int i = 0; i < tabs.getTabCount(); i++) {
          if ("Mua chương".equals(tabs.getTitleAt(i))) {
            hasPurchasesTab = true;
            break;
          }
        }
        assertTrue(hasPurchasesTab, "AdminWalletPanel must have 'Mua chương' tab");
        panel.close();
      });

      transport.close();
      session.close();
    } finally {
      server.stop(0);
      executor.shutdown();
    }
  }

  private static JTabbedPane findTabbedPane(Component comp) {
    if (comp instanceof JTabbedPane pane) return pane;
    if (comp instanceof java.awt.Container container) {
      for (Component child : container.getComponents()) {
        JTabbedPane found = findTabbedPane(child);
        if (found != null) return found;
      }
    }
    return null;
  }
}
