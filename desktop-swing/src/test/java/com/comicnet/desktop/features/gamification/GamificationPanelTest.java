package com.comicnet.desktop.features.gamification;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.session.ClientSession;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GamificationPanelTest {
  @Test
  void gamificationPanelRendersProfileAndHandlesCheckInAndSpin() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var executor = Executors.newCachedThreadPool();
    server.setExecutor(executor);
    server.createContext(
        "/api/v1",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String response = "{}";
          if (path.equals("/api/v1/gamification/profile")) {
            response = "{\"userId\":1118,\"exp\":150,\"level\":2,\"currentLevelExp\":50,\"nextLevelExp\":250,\"title\":\"Học đồ mê truyện\",\"lastCheckInDate\":null,\"checkInStreak\":2,\"totalCheckIns\":5,\"canCheckInToday\":true,\"freeSpinsAvailable\":1,\"paidCoin\":10,\"rewardCoin\":35}";
          } else if (path.equals("/api/v1/gamification/check-in")) {
            response = "{\"checkInDate\":\"2026-10-29\",\"dayInStreak\":3,\"expAwarded\":25,\"rewardCoinAwarded\":12,\"totalExp\":175,\"level\":2,\"title\":\"Học đồ mê truyện\",\"streak\":3,\"message\":\"Điểm danh ngày 3 thành công!\"}";
          } else if (path.equals("/api/v1/gamification/lucky-wheel/spin")) {
            response = "{\"prizeIndex\":0,\"rewardType\":\"COIN_REWARD\",\"amount\":10,\"label\":\"+10 Xu thưởng\",\"costCoin\":0,\"totalExp\":175,\"level\":2,\"title\":\"Học đồ mê truyện\",\"paidCoin\":10,\"rewardCoin\":45,\"message\":\"Quay trúng +10 Xu thưởng!\"}";
          } else if (path.equals("/api/v1/gamification/history")) {
            response = "{\"checkIns\":[],\"spins\":[]}";
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
      session.accept(session.generation(), "test-token", 3600);
      var transport = new ApiTransport(session);
      var config = new DesktopConfig(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1"));
      var api = new ApiClient(config, transport, session);

      var panelHolder = new AtomicReference<GamificationPanel>();
      SwingUtilities.invokeAndWait(() -> {
        var panel = new GamificationPanel(api);
        panelHolder.set(panel);
        panel.onShown();
      });

      var panel = panelHolder.get();
      assertNotNull(panel);

      Thread.sleep(300);

      SwingUtilities.invokeAndWait(() -> {
        panel.onHidden();
        panel.close();
      });

      transport.close();
      session.close();
    } finally {
      server.stop(0);
      executor.shutdown();
    }
  }
}
