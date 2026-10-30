package com.comicnet.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.client.http.MediaTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.features.publishing.*;
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
import org.junit.jupiter.api.Test;

public class CaptureCreatorStudioV2IT {
  private static final String API_BASE = "http://127.0.0.1:8080/api/v1";

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
    Path p1 = Path.of(".local/qa/creator-studio-v2/" + filename);
    Path p2 = Path.of("../.local/qa/creator-studio-v2/" + filename);
    Files.createDirectories(p1.getParent());
    Files.createDirectories(p2.getParent());
    ImageIO.write(img, "png", p1.toFile());
    ImageIO.write(img, "png", p2.toFile());
  }

  @Test
  void captureAll13CreatorStudioScreens() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try {
        Theme.install();
      } catch (Exception ignored) {}
    });

    var session = new ClientSession();
    var transport = new ApiTransport(session);
    var config = new DesktopConfig(URI.create(API_BASE));
    var api = new ApiClient(config, transport, session);

    // If backend is healthy, sign in as admin or creator
    boolean live = backendHealthy();
    if (live) {
      try {
        // Sign in with seed user
        api.login("admin-7b561c16-d306-4447-920f-02d992157cb7@comicnet.test", "Test-password-42!").get(5, TimeUnit.SECONDS);
      } catch (Exception ignored) {}
    }

    // 1. Capture Mockup 01: Xưởng xuất bản tổng quan (1440x880)
    AtomicReference<JFrame> frameRef = new AtomicReference<>();
    AtomicReference<PublishingPanel> pubRef = new AtomicReference<>();

    SwingUtilities.invokeAndWait(() -> {
      JFrame f = new JFrame("Comic Net · Xưởng xuất bản");
      f.setSize(1440, 880);
      f.setLocationRelativeTo(null);
      f.getContentPane().setBackground(Theme.BACKGROUND);

      PublishingPanel pub = new PublishingPanel(api, d -> {});
      pubRef.set(pub);
      f.add(pub);
      f.setVisible(true);
      frameRef.set(f);
      pub.onShown();
    });

    Thread.sleep(1200);
    captureWindow(frameRef.get(), "01_Xuong_xuat_ban_tong_quan.png");

    // 2. Capture Mockup 02: Wizard 1 - Tạo và sửa truyện
    SwingUtilities.invokeAndWait(() -> {
      try {
        Field wizField = PublishingPanel.class.getDeclaredField("wizard");
        wizField.setAccessible(true);
        ComicEditorWizard wiz = (ComicEditorWizard) wizField.get(pubRef.get());
        wiz.startNewComic();

        Field rootHostField = PublishingPanel.class.getDeclaredField("rootHost");
        rootHostField.setAccessible(true);
        JPanel rootHost = (JPanel) rootHostField.get(pubRef.get());
        CardLayout cl = (CardLayout) rootHost.getLayout();
        cl.show(rootHost, "WIZARD");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(800);
    captureWindow(frameRef.get(), "02_Tao_va_sua_truyen.png");

    // 3. Capture Mockup 03: Wizard 2 - Quản lý chương
    SwingUtilities.invokeAndWait(() -> {
      try {
        Field wizField = PublishingPanel.class.getDeclaredField("wizard");
        wizField.setAccessible(true);
        ComicEditorWizard wiz = (ComicEditorWizard) wizField.get(pubRef.get());
        wiz.openChapterStep("mock-comic-1", "mock-trans-1");

        Field rootHostField = PublishingPanel.class.getDeclaredField("rootHost");
        rootHostField.setAccessible(true);
        JPanel rootHost = (JPanel) rootHostField.get(pubRef.get());
        CardLayout cl = (CardLayout) rootHost.getLayout();
        cl.show(rootHost, "WIZARD");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(800);
    captureWindow(frameRef.get(), "03_Quan_ly_chuong.png");

    // 4. Capture Mockup 04: Upload nhiều ảnh và sắp xếp dialog
    AtomicReference<BulkImageUploadDialog> bulkDlgRef = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      BulkImageUploadDialog dlg = new BulkImageUploadDialog(
          frameRef.get(),
          api,
          "mock-version-1",
          "Chương 3 — Đi tìm bóng ma",
          "\"etag-mock\"",
          () -> {});

      // Add sample image items for visual QA
      try {
        Field itemsField = BulkImageUploadDialog.class.getDeclaredField("items");
        itemsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<BulkImageUploadDialog.ImageItem> items =
            (java.util.List<BulkImageUploadDialog.ImageItem>) itemsField.get(dlg);

        File f1 = new File("01.jpg");
        for (int i = 1; i <= 12; i++) {
          items.add(new BulkImageUploadDialog.ImageItem(f1, String.format("page_%02d.jpg", i), 1024 * 1024 + i * 45000));
        }

        Method rebuild = BulkImageUploadDialog.class.getDeclaredMethod("rebuildCards");
        rebuild.setAccessible(true);
        rebuild.invoke(dlg);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      dlg.setVisible(true);
      bulkDlgRef.set(dlg);
    });

    Thread.sleep(800);
    captureWindow(bulkDlgRef.get(), "04_Upload_nhieu_anh_va_sap_xep.png");
    SwingUtilities.invokeAndWait(() -> bulkDlgRef.get().dispose());

    // 5. Capture Mockup 05: Wizard 3 - Xem trước và gửi duyệt (Creator view)
    SwingUtilities.invokeAndWait(() -> {
      try {
        ComicEditorWizard creatorWiz = new ComicEditorWizard(api, false, () -> {});
        creatorWiz.startNewComic();

        Method prep3 = ComicEditorWizard.class.getDeclaredMethod("prepareStep3");
        prep3.setAccessible(true);
        prep3.invoke(creatorWiz);

        Field stepHostField = ComicEditorWizard.class.getDeclaredField("stepHost");
        stepHostField.setAccessible(true);
        JPanel stepHost = (JPanel) stepHostField.get(creatorWiz);
        CardLayout cl = (CardLayout) stepHost.getLayout();
        cl.show(stepHost, "STEP3");

        frameRef.get().getContentPane().removeAll();
        frameRef.get().add(creatorWiz);
        frameRef.get().revalidate();
        frameRef.get().repaint();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(800);
    captureWindow(frameRef.get(), "05_Xem_truoc_va_gui_duyet_Creator.png");

    // 6. Capture Mockup 06: Admin Hàng chờ duyệt
    AtomicReference<AdminReviewQueuePanel> adminQueueRef = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      AdminReviewQueuePanel adminPanel = new AdminReviewQueuePanel(api);
      adminQueueRef.set(adminPanel);
      adminPanel.loadQueue();

      frameRef.get().getContentPane().removeAll();
      frameRef.get().add(adminPanel);
      frameRef.get().revalidate();
      frameRef.get().repaint();
    });

    Thread.sleep(800);
    captureWindow(frameRef.get(), "06_Admin_hang_cho_duyet.png");

    // 7. Capture Mockup 07: Admin Xem và duyệt chi tiết
    SwingUtilities.invokeAndWait(() -> {
      try {
        Field cardHostField = AdminReviewQueuePanel.class.getDeclaredField("cardHost");
        cardHostField.setAccessible(true);
        JPanel cardHost = (JPanel) cardHostField.get(adminQueueRef.get());
        CardLayout cl = (CardLayout) cardHost.getLayout();
        cl.show(cardHost, "DETAIL");
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(800);
    captureWindow(frameRef.get(), "07_Admin_xem_va_duyet_chi_tiet.png");

    // 8. Capture Mockup 08: Admin Từ chối yêu cầu sửa (Dialog)
    SwingUtilities.invokeAndWait(() -> {
      new Thread(() -> {
        try {
          Thread.sleep(400);
          for (Window w : Window.getWindows()) {
            if (w instanceof JDialog d && "Yêu cầu chỉnh sửa".equals(d.getTitle())) {
              captureWindow(d, "08_Admin_tu_choi_yeu_cau_sua.png");
              d.dispose();
              break;
            }
          }
        } catch (Exception ignored) {}
      }).start();

      ReviewDecisionDialogs.showRequestChanges(frameRef.get(), "Người gỡ ánh sao", reason -> {});
    });

    Thread.sleep(1200);

    // 9. Capture Mockup 09: Creator sửa và gửi lại
    SwingUtilities.invokeAndWait(() -> {
      CreatorFeedbackPanel fb = new CreatorFeedbackPanel(api, () -> {}, ignored -> {});
      fb.setFeedback("44", "33", "Người gỡ ánh sao", "Vui lòng kiểm tra lại thứ tự các trang 7-10 của Chương 2 và ảnh bìa chất lượng quá thấp.");

      frameRef.get().getContentPane().removeAll();
      frameRef.get().add(fb);
      frameRef.get().revalidate();
      frameRef.get().repaint();
    });

    Thread.sleep(800);
    captureWindow(frameRef.get(), "09_Creator_sua_va_gui_lai.png");

    // 10. Capture Mockup 10: Admin xuất bản trực tiếp
    SwingUtilities.invokeAndWait(() -> {
      try {
        ComicEditorWizard adminWiz = new ComicEditorWizard(api, true, () -> {});
        adminWiz.startNewComic();

        Method prep3 = ComicEditorWizard.class.getDeclaredMethod("prepareStep3");
        prep3.setAccessible(true);
        prep3.invoke(adminWiz);

        Field stepHostField = ComicEditorWizard.class.getDeclaredField("stepHost");
        stepHostField.setAccessible(true);
        JPanel stepHost = (JPanel) stepHostField.get(adminWiz);
        CardLayout cl = (CardLayout) stepHost.getLayout();
        cl.show(stepHost, "STEP3");

        frameRef.get().getContentPane().removeAll();
        frameRef.get().add(adminWiz);
        frameRef.get().revalidate();
        frameRef.get().repaint();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    Thread.sleep(800);
    captureWindow(frameRef.get(), "10_Admin_xuat_ban_truc_tiep.png");

    // 11. Capture Mockup 11: Lịch sử phiên bản Nâng cao
    AtomicReference<VersionHistoryDialog> vhDlgRef = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      VersionHistoryDialog dlg = new VersionHistoryDialog(
          frameRef.get(),
          api,
          "33",
          "Chương 2 — Nét mực đầu tiên",
          () -> {});
      dlg.setVisible(true);
      vhDlgRef.set(dlg);
    });

    Thread.sleep(800);
    captureWindow(vhDlgRef.get(), "11_Lich_su_phien_ban_Nang_cao.png");
    SwingUtilities.invokeAndWait(() -> vhDlgRef.get().dispose());

    // 12. Capture Mockup 12: Admin Từ chối xuất bản (Dialog)
    SwingUtilities.invokeAndWait(() -> {
      new Thread(() -> {
        try {
          Thread.sleep(400);
          for (Window w : Window.getWindows()) {
            if (w instanceof JDialog d && "Từ chối xuất bản".equals(d.getTitle())) {
              captureWindow(d, "12_Admin_tu_choi_xuat_ban.png");
              d.dispose();
              break;
            }
          }
        } catch (Exception ignored) {}
      }).start();

      ReviewDecisionDialogs.showReject(frameRef.get(), "Người gỡ ánh sao", reason -> {});
    });

    Thread.sleep(1200);

    // 13. Capture Mockup 13: Xem trước chương trước khi duyệt
    AtomicReference<ChapterPreviewDialog> prevDlgRef = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      ChapterPreviewDialog dlg = new ChapterPreviewDialog(
          frameRef.get(),
          api,
          "mock-version-1",
          "Chương 2 — Nét mực đầu tiên");
      dlg.setVisible(true);
      prevDlgRef.set(dlg);
    });

    Thread.sleep(800);
    captureWindow(prevDlgRef.get(), "13_Xem_truoc_chuong_truoc_khi_duyet.png");
    SwingUtilities.invokeAndWait(() -> prevDlgRef.get().dispose());

    // Cleanup frame
    SwingUtilities.invokeAndWait(() -> frameRef.get().dispose());
  }
}
