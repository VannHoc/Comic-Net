package com.comicnet.desktop.features.gamification;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.client.http.MediaTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.session.ClientSession;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class GamificationUiLiveIT {
  private static final String API_BASE = "http://127.0.0.1:8080/api/v1";
  private static final String READER_EMAIL = "reader-501f7dd0-a1c6-4c95-b132-8f7facb78be9@comicnet.test";
  private static final String READER_PASSWORD = "Test-password-42!";

  private boolean backendHealthy() {
    try (var client = HttpClient.newHttpClient()) {
      var req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080/actuator/health")).GET().build();
      var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      return resp.statusCode() == 200 && resp.body().contains("UP");
    } catch (Exception e) {
      return false;
    }
  }

  @Test
  void liveGamificationPanelRendersAndHandlesCheckInAndLuckyWheel() throws Exception {
    Assumptions.assumeTrue(backendHealthy(), "Backend on port 8080 must be healthy for live UI test");

    var session = new ClientSession();
    var transport = new ApiTransport(session);
    var config = new DesktopConfig(URI.create(API_BASE));
    var api = new ApiClient(config, transport, session);

    // 1. Live login
    var loginReply = api.login(READER_EMAIL, READER_PASSWORD).get(10, TimeUnit.SECONDS);
    assertNotNull(loginReply);
    assertTrue(api.signedIn(), "Reader must be signed in");

    // 2. Open Gamification UI in a real JFrame
    var frameHolder = new AtomicReference<JFrame>();
    var panelHolder = new AtomicReference<GamificationPanel>();

    SwingUtilities.invokeAndWait(() -> {
      var frame = new JFrame("Comic Net - Nhiệm vụ & Thưởng Live Demo");
      var panel = new GamificationPanel(api);
      frame.setLayout(new BorderLayout());
      frame.add(panel, BorderLayout.CENTER);
      frame.setSize(1100, 750);
      frame.setVisible(true);
      panel.onShown();

      frameHolder.set(frame);
      panelHolder.set(panel);
    });

    // Wait for initial profile and history to load
    Thread.sleep(1500);

    // 3. Test Daily Check-in through UI button if available
    SwingUtilities.invokeAndWait(() -> {
      try {
        var panel = panelHolder.get();
        Field btnField = GamificationPanel.class.getDeclaredField("checkInButton");
        btnField.setAccessible(true);
        JButton btn = (JButton) btnField.get(panel);
        if (btn.isEnabled()) {
          btn.doClick();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Wait for check-in response and UI update
    Thread.sleep(1500);

    // 4. Test Lucky Wheel Spin through UI button
    SwingUtilities.invokeAndWait(() -> {
      try {
        var panel = panelHolder.get();
        Field spinBtnField = GamificationPanel.class.getDeclaredField("spinButton");
        spinBtnField.setAccessible(true);
        JButton spinBtn = (JButton) spinBtnField.get(panel);
        if (spinBtn.isEnabled()) {
          spinBtn.doClick();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Wait for wheel animation (2.4s) and result to settle
    Thread.sleep(3500);

    // 5. Verify UI state and capture screenshot
    SwingUtilities.invokeAndWait(() -> {
      try {
        var panel = panelHolder.get();
        var frame = frameHolder.get();

        Field lvlField = GamificationPanel.class.getDeclaredField("levelBadge");
        lvlField.setAccessible(true);
        JLabel lvlLabel = (JLabel) lvlField.get(panel);
        assertTrue(lvlLabel.getText().startsWith("Cấp "), "Level badge must be populated");

        Field titleField = GamificationPanel.class.getDeclaredField("titleLabel");
        titleField.setAccessible(true);
        JLabel title = (JLabel) titleField.get(panel);
        assertFalse(title.getText().isBlank(), "Title must be populated");

        // Capture screenshot
        BufferedImage img = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        frame.paint(g);
        g.dispose();

        Path outDir = Path.of("../.local/qa/gamification");
        Files.createDirectories(outDir);
        File outFile = outDir.resolve("01-gamification-live.jpg").toFile();
        ImageIO.write(img, "JPG", outFile);
        assertTrue(outFile.exists() && outFile.length() > 0, "Screenshot file must exist");

        panel.close();
        frame.dispose();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    transport.close();
    session.close();
  }
}
