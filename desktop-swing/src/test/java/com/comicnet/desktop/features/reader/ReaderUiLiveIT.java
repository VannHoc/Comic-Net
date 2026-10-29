package com.comicnet.desktop.features.reader;

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
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReaderUiLiveIT {
  private static final String API_BASE = "http://127.0.0.1:8080/api/v1";
  private static final String READER_EMAIL = "reader-501f7dd0-a1c6-4c95-b132-8f7facb78be9@comicnet.test";
  private static final String READER_PASSWORD = "Test-password-42!";
  private static final String CHAPTER_ID = "230";

  private final JsonMapper json = JsonMapper.builder().build();

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
  void longChapterAutoScrollPerformanceSmoothnessAndTransitions() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var viewport = new JScrollPane();
      // Simulate very long 30-page webtoon (extent 800, max 30000)
      viewport.getVerticalScrollBar().setModel(new DefaultBoundedRangeModel(0, 800, 0, 30000));
      var canScroll = new AtomicBoolean(true);
      try (var comfort = new ReaderComfort(new JPanel(), viewport, canScroll::get)) {
        var auto = comfort.autoControl();
        var speed = comfort.speedControl();

        // 1. Initial state
        assertFalse(auto.isSelected());
        assertEquals(0, viewport.getVerticalScrollBar().getValue());

        // 2. Start auto scroll at speed 50
        speed.setValue(50);
        auto.doClick();
        assertTrue(auto.isSelected());

        long t0 = System.nanoTime();
        comfort.tick(t0);

        // Tick 50ms later
        comfort.tick(t0 + 50_000_000L);
        int pos1 = viewport.getVerticalScrollBar().getValue();
        assertTrue(pos1 > 0, "Scroll bar must move forward after tick");

        // Tick another 50ms later
        comfort.tick(t0 + 100_000_000L);
        int pos2 = viewport.getVerticalScrollBar().getValue();
        assertTrue(pos2 > pos1, "Smooth scroll must continue moving forward");

        // 3. Acceleration: Increase speed to 200
        speed.setValue(200);
        comfort.tick(t0 + 150_000_000L);
        int pos3 = viewport.getVerticalScrollBar().getValue();
        int deltaSlow = pos2 - pos1;
        int deltaFast = pos3 - pos2;
        assertTrue(deltaFast >= deltaSlow, "Higher speed must yield greater delta distance");

        // 4. Manual Pause: user pauses scrolling
        comfort.pause();
        assertFalse(auto.isSelected(), "Auto scroll toggle must be unselected after pause");
        int pausedPos = viewport.getVerticalScrollBar().getValue();

        // Ensure ticks during pause do not move scroll
        comfort.tick(t0 + 250_000_000L);
        assertEquals(pausedPos, viewport.getVerticalScrollBar().getValue(), "Scroll must not move when paused");

        // 5. Resume scrolling
        auto.doClick();
        assertTrue(auto.isSelected());
        comfort.tick(t0 + 300_000_000L);
        comfort.tick(t0 + 350_000_000L);
        int resumedPos = viewport.getVerticalScrollBar().getValue();
        assertTrue(resumedPos > pausedPos, "Resuming must smoothly advance from paused position");

        // 6. Chapter transition / unallowed condition
        canScroll.set(false);
        comfort.tick(t0 + 400_000_000L);
        assertFalse(auto.isSelected(), "Auto scroll must automatically pause when scroll becomes unavailable");

        // 7. Boundary condition: Auto-pause at the end of the long chapter
        canScroll.set(true);
        // Position right near the bottom: max 30000 - extent 800 = 29200
        viewport.getVerticalScrollBar().setValue(29198);
        auto.doClick();
        assertTrue(auto.isSelected());
        comfort.tick(System.nanoTime());
        comfort.tick(System.nanoTime() + 1_000_000_000L); // 1 second of scrolling reaches the bottom
        assertEquals(29200, viewport.getVerticalScrollBar().getValue(), "Must hit exact end boundary");
        assertFalse(auto.isSelected(), "Must auto-pause when reaching chapter bottom");
      }
    });
  }

  @Test
  void liveReadingPreferencesAndProgressPersistAcrossLogoutAndRelogin() throws Exception {
    Assumptions.assumeTrue(backendHealthy(), "Backend on port 8080 must be healthy for live UI test");

    var session = new ClientSession();
    var transport = new ApiTransport(session);
    var media = new MediaTransport(Set.of("127.0.0.1", "localhost"));
    var config = new DesktopConfig(URI.create(API_BASE));
    var api = new ApiClient(config, transport, session);

    // 1. Live login
    var loginReply = api.login(READER_EMAIL, READER_PASSWORD).get(10, TimeUnit.SECONDS);
    assertNotNull(loginReply);
    assertTrue(api.signedIn(), "Reader must be signed in");

    // 2. Open Reader UI in a real JFrame
    var frameHolder = new AtomicReference<JFrame>();
    var panelHolder = new AtomicReference<ReaderPanel>();

    SwingUtilities.invokeAndWait(() -> {
      var frame = new JFrame("Comic Net - Reader UI Live Test");
      var panel = new ReaderPanel(api, media, ignored -> {});
      frame.setLayout(new BorderLayout());
      frame.add(panel, BorderLayout.CENTER);
      frame.setSize(1024, 768);
      frame.setVisible(true);
      panel.onShown();
      panel.open(ReaderLocation.chapter(CHAPTER_ID));

      frameHolder.set(frame);
      panelHolder.set(panel);
    });

    // Wait for manifest and initial personal state to load
    Thread.sleep(1500);

    // 3. Change reading preferences to ORIGINAL, SCROLL, LTR
    SwingUtilities.invokeAndWait(() -> {
      try {
        var panel = panelHolder.get();
        var fitField = ReaderPanel.class.getDeclaredField("fit");
        fitField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var fitCombo = (JComboBox<String>) fitField.get(panel);

        var modeField = ReaderPanel.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var modeCombo = (JComboBox<String>) modeField.get(panel);

        var dirField = ReaderPanel.class.getDeclaredField("direction");
        dirField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var dirCombo = (JComboBox<String>) dirField.get(panel);

        // Select: "Cuộn dọc" (0), "Trái → phải" (0), "Kích thước gốc" (2)
        modeCombo.setSelectedIndex(0);
        dirCombo.setSelectedIndex(0);
        fitCombo.setSelectedIndex(2); // ORIGINAL

        // Trigger savePreferences() via reflection
        var savePrefMethod = ReaderPanel.class.getDeclaredMethod("savePreferences");
        savePrefMethod.setAccessible(true);
        savePrefMethod.invoke(panel);

        // Jump to Page 2 (index 1) and trigger progress save
        var userJumpMethod = ReaderPanel.class.getDeclaredMethod("userJump", int.class);
        userJumpMethod.setAccessible(true);
        userJumpMethod.invoke(panel, 1);

        // Force progress save
        var saveMethod = ReaderPanel.class.getDeclaredMethod("save");
        saveMethod.setAccessible(true);
        saveMethod.invoke(panel);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Wait for asynchronous REST PUT requests to complete
    Thread.sleep(2000);

    // 4. Verify backend received and persisted the reading preference
    var prefReply = api.get("/me/reading-preferences").get(5, TimeUnit.SECONDS);
    assertEquals("ORIGINAL", prefReply.body().path("fit").asText(), "Backend preference fit must be ORIGINAL");
    assertEquals("SCROLL", prefReply.body().path("mode").asText(), "Backend preference mode must be SCROLL");
    assertEquals("LTR", prefReply.body().path("direction").asText(), "Backend preference direction must be LTR");

    // 5. Verify backend received and persisted the reading progress for chapter 230
    var progReply = api.get("/me/progress/" + CHAPTER_ID).get(5, TimeUnit.SECONDS);
    assertFalse(progReply.body().path("pageId").asText().isBlank(), "Progress pageId must be saved");
    assertEquals("460", progReply.body().path("pageId").asText(), "Progress must be at Page 2 (ID 460)");

    // 6. Close current panel and simulate Logout
    SwingUtilities.invokeAndWait(() -> {
      panelHolder.get().close();
      frameHolder.get().remove(panelHolder.get());
    });
    api.logout().get(5, TimeUnit.SECONDS);
    assertFalse(api.signedIn(), "Must be logged out");

    // 7. Re-login as the same user
    var reloginReply = api.login(READER_EMAIL, READER_PASSWORD).get(10, TimeUnit.SECONDS);
    assertNotNull(reloginReply);
    assertTrue(api.signedIn(), "Must be re-logged in");

    // 8. Open fresh ReaderPanel with Chapter 230 and verify state restoration
    var panel2Holder = new AtomicReference<ReaderPanel>();
    SwingUtilities.invokeAndWait(() -> {
      var panel2 = new ReaderPanel(api, media, ignored -> {});
      frameHolder.get().add(panel2, BorderLayout.CENTER);
      frameHolder.get().revalidate();
      frameHolder.get().repaint();
      panel2.onShown();
      panel2.open(ReaderLocation.chapter(CHAPTER_ID));
      panel2Holder.set(panel2);
    });

    // Wait for async loadPersonalState (/me/reading-preferences and /me/progress/230)
    Thread.sleep(2000);

    // 9. Assert state restored on the UI components
    SwingUtilities.invokeAndWait(() -> {
      try {
        var panel2 = panel2Holder.get();

        var fitField = ReaderPanel.class.getDeclaredField("fit");
        fitField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var fitCombo = (JComboBox<String>) fitField.get(panel2);

        var modeField = ReaderPanel.class.getDeclaredField("mode");
        modeField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var modeCombo = (JComboBox<String>) modeField.get(panel2);

        var pageField = ReaderPanel.class.getDeclaredField("page");
        pageField.setAccessible(true);
        var pageSpinner = (JSpinner) pageField.get(panel2);

        var pageCountField = ReaderPanel.class.getDeclaredField("pageCount");
        pageCountField.setAccessible(true);
        var pageCountLabel = (JLabel) pageCountField.get(panel2);

        // Verification
        assertEquals(2, fitCombo.getSelectedIndex(), "Restored fit must be 'Kích thước gốc' (ORIGINAL)");
        assertEquals("Kích thước gốc", fitCombo.getSelectedItem());
        assertEquals(0, modeCombo.getSelectedIndex(), "Restored mode must be 'Cuộn dọc' (SCROLL)");
        assertEquals(2, pageSpinner.getValue(), "Restored page position must be 2");
        assertEquals("2 / 3", pageCountLabel.getText(), "Page count label must show '2 / 3'");

        // 10. Capture screenshot of the restored Reader UI
        var frame = frameHolder.get();
        BufferedImage img = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        frame.paint(g);
        g.dispose();

        Path outDir = Path.of("../.local/qa/reader-ui");
        Files.createDirectories(outDir);
        File outFile = outDir.resolve("07-relogin-preference-restore.jpg").toFile();
        ImageIO.write(img, "JPG", outFile);
        assertTrue(outFile.exists() && outFile.length() > 0, "Screenshot file must be generated");

        frame.dispose();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    transport.close();
    session.close();
  }
}
