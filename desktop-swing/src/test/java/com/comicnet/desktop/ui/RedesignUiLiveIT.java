package com.comicnet.desktop.ui;

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
import com.comicnet.desktop.features.catalog.CatalogPanel;
import com.comicnet.desktop.features.identity.AccountPanel;
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

public class RedesignUiLiveIT {
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

  private void captureWindow(Window window, Path dest) throws Exception {
    BufferedImage img = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    window.paint(g);
    g.dispose();
    Files.createDirectories(dest.getParent());
    ImageIO.write(img, "jpg", dest.toFile());
  }

  @Test
  void testExploreAndAccountRedesignLive() throws Exception {
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

    // 1. Guest Explore View
    SwingUtilities.invokeAndWait(() -> {
      try {
        showWorkspaceMethod.invoke(frame, "Khám phá");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(3000); // Allow images & catalog to settle

    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/01-explore-guest.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // 2. Guest Account View
    SwingUtilities.invokeAndWait(() -> {
      try {
        showWorkspaceMethod.invoke(frame, "Tài khoản");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(1500);

    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/03-account-guest.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // 3. Login as Reader
    var loginReply = api.login(READER_EMAIL, READER_PASSWORD).get(10, TimeUnit.SECONDS);
    assertNotNull(loginReply);
    assertTrue(api.signedIn(), "Reader should be signed in");

    Thread.sleep(1500); // Allow sessionChanged listener to update topbar & account

    // 4. Authenticated Explore View (1440x880)
    SwingUtilities.invokeAndWait(() -> {
      try {
        showWorkspaceMethod.invoke(frame, "Khám phá");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(3000);

    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/01-explore-redesign.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Scroll down in Explore to see full comic cards and footer
    SwingUtilities.invokeAndWait(() -> {
      try {
        Field catalogField = MainFrame.class.getDeclaredField("catalog");
        catalogField.setAccessible(true);
        CatalogPanel cat = (CatalogPanel) catalogField.get(frame);
        Field scrollField = CatalogPanel.class.getDeclaredField("pageScroll");
        scrollField.setAccessible(true);
        JScrollPane sp = (JScrollPane) scrollField.get(cat);
        sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum());
      } catch (Exception ignored) {}
    });

    Thread.sleep(1000);

    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/01-explore-scrolled.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // 5. Authenticated Account Profile Dashboard (1440x880)
    SwingUtilities.invokeAndWait(() -> {
      try {
        showWorkspaceMethod.invoke(frame, "Tài khoản");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(3000); // Allow profile, stats, level to settle

    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/02-account-redesign.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Scroll down in Account to see full form and footer
    SwingUtilities.invokeAndWait(() -> {
      try {
        Field accountField = MainFrame.class.getDeclaredField("account");
        accountField.setAccessible(true);
        AccountPanel acc = (AccountPanel) accountField.get(frame);
        for (Component c : acc.getComponents()) {
          if (c instanceof JPanel jp) {
            for (Component inner : jp.getComponents()) {
              if (inner instanceof JScrollPane sp) {
                sp.getVerticalScrollBar().setValue(sp.getVerticalScrollBar().getMaximum());
              }
            }
          }
        }
      } catch (Exception ignored) {}
    });

    Thread.sleep(1000);

    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/02-account-scrolled.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // 6. Test Responsive sizes: 1267x795 and 1180x720
    SwingUtilities.invokeAndWait(() -> {
      try {
        frame.setSize(1267, 795);
        showWorkspaceMethod.invoke(frame, "Khám phá");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(2000);

    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/04-explore-1267x795.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    SwingUtilities.invokeAndWait(() -> {
      try {
        frame.setSize(1180, 720);
        showWorkspaceMethod.invoke(frame, "Khám phá");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(1500);

    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/05-explore-1180x720.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // Reset size back to 1440x880 for new screens
    SwingUtilities.invokeAndWait(() -> frame.setSize(1440, 880));

    // 7. Library Panel (Step 4 Priority 1)
    SwingUtilities.invokeAndWait(() -> {
      try {
        showWorkspaceMethod.invoke(frame, "Thư viện");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    Thread.sleep(2500);
    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/06-library-collections.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // 8. Wallet Panel (Step 4 Priority 2)
    SwingUtilities.invokeAndWait(() -> {
      try {
        showWorkspaceMethod.invoke(frame, "Ví xu");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    Thread.sleep(2500);
    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/07-wallet-redesign.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // 9. Gamification Panel (M16/M17 & Lucky Wheel & Check-in)
    SwingUtilities.invokeAndWait(() -> {
      try {
        showWorkspaceMethod.invoke(frame, "Nhiệm vụ & Thưởng");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    Thread.sleep(2500);
    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/08-gamification-redesign.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // 10. Ranking Panel (Step 4 Priority 3)
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
        captureWindow(frame, Path.of(".local/qa/redesign/09-ranking-redesign.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // 11. Notifications Panel
    SwingUtilities.invokeAndWait(() -> {
      try {
        showWorkspaceMethod.invoke(frame, "Thông báo");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    Thread.sleep(2500);
    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/10-notifications-redesign.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    // 12. Comic Detail Dialog (Step 4 Priority 4)
    AtomicReference<JDialog> detailDialogRef = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        Field catalogField = MainFrame.class.getDeclaredField("catalog");
        catalogField.setAccessible(true);
        CatalogPanel cat = (CatalogPanel) catalogField.get(frame);

        // Call loadDetail with sample comic 298
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
          captureWindow(dlg, Path.of(".local/qa/redesign/11-comic-detail-dialog.jpg"));
          dlg.dispose();
        }
      } catch (Exception ignored) {}
    });

    // 13. Test Account responsive on 1180x720
    SwingUtilities.invokeAndWait(() -> {
      try {
        frame.setSize(1180, 720);
        showWorkspaceMethod.invoke(frame, "Tài khoản");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    Thread.sleep(1500);
    SwingUtilities.invokeAndWait(() -> {
      try {
        captureWindow(frame, Path.of(".local/qa/redesign/12-account-1180x720.jpg"));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    SwingUtilities.invokeAndWait(() -> {
      frame.dispose();
    });

    assertTrue(Files.exists(Path.of(".local/qa/redesign/01-explore-redesign.jpg")), "01-explore-redesign.jpg must exist");
    assertTrue(Files.exists(Path.of(".local/qa/redesign/02-account-redesign.jpg")), "02-account-redesign.jpg must exist");
    assertTrue(Files.exists(Path.of(".local/qa/redesign/06-library-collections.jpg")), "06-library-collections.jpg must exist");
    assertTrue(Files.exists(Path.of(".local/qa/redesign/07-wallet-redesign.jpg")), "07-wallet-redesign.jpg must exist");
    assertTrue(Files.exists(Path.of(".local/qa/redesign/08-gamification-redesign.jpg")), "08-gamification-redesign.jpg must exist");
    assertTrue(Files.exists(Path.of(".local/qa/redesign/09-ranking-redesign.jpg")), "09-ranking-redesign.jpg must exist");
  }
}
