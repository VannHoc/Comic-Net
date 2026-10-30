package com.comicnet.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.client.http.MediaTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.features.catalog.CatalogPanel;
import com.comicnet.desktop.features.recommendation.RatingDialog;
import com.comicnet.desktop.session.ClientSession;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class CaptureNewFeaturesIT {
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

  private void captureWindow(Window window, String filename) throws Exception {
    BufferedImage img = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    window.paint(g);
    g.dispose();
    Path p1 = Path.of(".local/qa/redesign/" + filename);
    Path p2 = Path.of("../.local/qa/redesign/" + filename);
    Files.createDirectories(p1.getParent());
    Files.createDirectories(p2.getParent());
    ImageIO.write(img, "jpg", p1.toFile());
    ImageIO.write(img, "jpg", p2.toFile());
  }

  @Test
  void testCaptureRatingAndDetail() throws Exception {
    Assumptions.assumeTrue(backendHealthy(), "Backend on port 8080 must be healthy for live UI test");

    SwingUtilities.invokeAndWait(() -> {
      try {
        Theme.install();
      } catch (Exception ignored) {}
    });

    var session = new ClientSession();
    var transport = new ApiTransport(session);
    var config = new DesktopConfig(URI.create(API_BASE));
    var api = new ApiClient(config, transport, session);
    var media = new MediaTransport(config.mediaHosts());

    // 1. Sign in
    var loginReply = api.login(READER_EMAIL, READER_PASSWORD).get(10, TimeUnit.SECONDS);
    assertNotNull(loginReply);

    AtomicReference<MainFrame> frameRef = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      MainFrame frame = new MainFrame(config, api, media);
      frame.setSize(1440, 880);
      frame.setVisible(true);
      frameRef.set(frame);
    });

    MainFrame frame = frameRef.get();
    Method showWorkspaceMethod = MainFrame.class.getDeclaredMethod("showWorkspace", String.class);
    showWorkspaceMethod.setAccessible(true);

    // Switch to Ranking
    SwingUtilities.invokeAndWait(() -> {
      try {
        showWorkspaceMethod.invoke(frame, "Bảng xếp hạng");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    Thread.sleep(2500);

    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, "13-ranking-live.jpg");
      } catch (Exception ignored) {}
    });

    // Open Rating Dialog directly
    AtomicReference<RatingDialog> ratingDialogRef = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      RatingDialog dialog = new RatingDialog(frame, api, "298", () -> {});
      dialog.setModalityType(Dialog.ModalityType.MODELESS);
      dialog.setVisible(true);
      ratingDialogRef.set(dialog);
    });

    Thread.sleep(2500); // Allow reviews and summary to load

    SwingUtilities.invokeAndWait(() -> {
      try {
        RatingDialog dialog = ratingDialogRef.get();
        if (dialog != null) {
          captureWindow(dialog, "14-rating-dialog-redesign.jpg");
          dialog.dispose();
        }
      } catch (Exception ignored) {}
    });

    // Open Comic Detail Dialog
    SwingUtilities.invokeAndWait(() -> {
      try {
        Field catalogField = MainFrame.class.getDeclaredField("catalog");
        catalogField.setAccessible(true);
        CatalogPanel cat = (CatalogPanel) catalogField.get(frame);
        cat.openComic("298");
      } catch (Exception ignored) {}
    });

    Thread.sleep(2500);

    SwingUtilities.invokeAndWait(() -> {
      try {
        Field catalogField = MainFrame.class.getDeclaredField("catalog");
        catalogField.setAccessible(true);
        CatalogPanel cat = (CatalogPanel) catalogField.get(frame);
        Field detailDialogField = CatalogPanel.class.getDeclaredField("detailDialog");
        detailDialogField.setAccessible(true);
        JDialog dlg = (JDialog) detailDialogField.get(cat);
        if (dlg != null && dlg.isVisible()) {
          captureWindow(dlg, "15-comic-detail-polished.jpg");
          dlg.dispose();
        }
      } catch (Exception ignored) {}
      frame.dispose();
    });
  }
}
