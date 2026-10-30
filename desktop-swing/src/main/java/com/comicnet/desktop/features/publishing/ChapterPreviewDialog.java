package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiIcons;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import tools.jackson.databind.JsonNode;

public final class ChapterPreviewDialog extends JDialog {
  private final ApiClient api;
  private final String chapterTitle;
  private final List<String> pageUrls = new ArrayList<>();
  private int currentPage = 0;

  private final JLabel pageIndicator = Theme.plainLabel("Trang 1 / 1");
  private final JButton prevBtn = new JButton("← Trang trước");
  private final JButton nextBtn = new JButton("Trang sau →");
  private final JPanel thumbnailList = new JPanel();
  private final PageCanvas mainCanvas = new PageCanvas();
  private final List<JButton> thumbButtons = new ArrayList<>();

  public ChapterPreviewDialog(Window owner, ApiClient api, String versionId, String chapterTitle) {
    super(owner, "Comic Net · Xem trước chương", ModalityType.APPLICATION_MODAL);
    this.api = api;
    this.chapterTitle = chapterTitle;

    setSize(1080, 780);
    setMinimumSize(new Dimension(880, 600));
    setLocationRelativeTo(owner);
    setLayout(new BorderLayout(0, 0));
    getContentPane().setBackground(Theme.BACKGROUND);

    add(buildHeader(), BorderLayout.NORTH);
    add(buildBody(), BorderLayout.CENTER);
    add(buildFooter(), BorderLayout.SOUTH);

    loadManifest(versionId);
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout(16, 4));
    header.setBackground(Theme.SURFACE);
    header.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
        new EmptyBorder(14, 24, 14, 24)));

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);

    JLabel title = new JLabel("Xem trước chương — " + (chapterTitle != null ? chapterTitle : "Nội bộ"));
    title.setFont(Theme.heading(18));
    title.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel("Chế độ xem trước nội bộ (Internal Preview). Không tính lượt đọc thật, không cộng EXP hay nhiệm vụ.");
    sub.setFont(Theme.label(12));
    sub.setForeground(Theme.MUTED);

    titleBox.add(title);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.CENTER);

    JButton closeX = new JButton("×");
    closeX.setFont(new Font("SansSerif", Font.PLAIN, 24));
    closeX.setForeground(Theme.MUTED);
    closeX.setContentAreaFilled(false);
    closeX.setBorderPainted(false);
    closeX.setFocusPainted(false);
    closeX.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    closeX.addActionListener(e -> dispose());
    header.add(closeX, BorderLayout.EAST);

    return header;
  }

  private JPanel buildBody() {
    JPanel body = new JPanel(new BorderLayout(12, 0));
    body.setOpaque(false);
    body.setBorder(new EmptyBorder(12, 20, 12, 20));

    // Left thumbnail strip
    thumbnailList.setLayout(new BoxLayout(thumbnailList, BoxLayout.Y_AXIS));
    thumbnailList.setOpaque(false);
    thumbnailList.setBorder(new EmptyBorder(4, 4, 4, 4));

    JScrollPane scrollThumbs = new JScrollPane(thumbnailList);
    scrollThumbs.setPreferredSize(new Dimension(140, 0));
    scrollThumbs.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    scrollThumbs.setBackground(Theme.SURFACE);
    scrollThumbs.getViewport().setBackground(Theme.SURFACE);
    scrollThumbs.getVerticalScrollBar().setUnitIncrement(16);
    body.add(scrollThumbs, BorderLayout.WEST);

    // Center canvas inside scroll pane
    JScrollPane scrollCanvas = new JScrollPane(mainCanvas);
    scrollCanvas.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    scrollCanvas.setBackground(Theme.BACKGROUND);
    scrollCanvas.getViewport().setBackground(Theme.BACKGROUND);
    scrollCanvas.getVerticalScrollBar().setUnitIncrement(24);
    body.add(scrollCanvas, BorderLayout.CENTER);

    return body;
  }

  private JPanel buildFooter() {
    JPanel footer = new JPanel(new BorderLayout(16, 0));
    footer.setBackground(Theme.SURFACE);
    footer.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
        new EmptyBorder(12, 24, 12, 24)));

    JPanel navBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    navBox.setOpaque(false);

    prevBtn.setFont(Theme.label(13));
    prevBtn.setFocusPainted(false);
    prevBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    prevBtn.addActionListener(e -> {
      if (currentPage > 0) selectPage(currentPage - 1);
    });

    nextBtn.setFont(Theme.label(13));
    nextBtn.setFocusPainted(false);
    nextBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    nextBtn.addActionListener(e -> {
      if (currentPage < pageUrls.size() - 1) selectPage(currentPage + 1);
    });

    pageIndicator.setFont(Theme.heading(13));
    pageIndicator.setForeground(Theme.TEXT);

    navBox.add(prevBtn);
    navBox.add(pageIndicator);
    navBox.add(nextBtn);
    footer.add(navBox, BorderLayout.CENTER);

    JButton closeBtn = new JButton("Đóng xem trước");
    closeBtn.setFont(Theme.label(13));
    closeBtn.setFocusPainted(false);
    closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    closeBtn.addActionListener(e -> dispose());
    footer.add(closeBtn, BorderLayout.EAST);

    return footer;
  }

  private void loadManifest(String versionId) {
    mainCanvas.setLoading(true);
    api.get("/publishing/versions/" + versionId + "/preview").whenComplete((reply, err) -> {
      SwingUtilities.invokeLater(() -> {
        mainCanvas.setLoading(false);
        if (err != null || reply == null) {
          mainCanvas.setErrorMessage("Không thể tải bản xem trước hoặc bạn không có quyền.");
          return;
        }

        JsonNode pagesNode = reply.body().path("pages");
        pageUrls.clear();
        thumbButtons.clear();
        thumbnailList.removeAll();

        if (pagesNode.isArray()) {
          for (JsonNode p : pagesNode) {
            String url = p.path("url").asText();
            if (!url.isBlank()) pageUrls.add(url);
          }
        }

        if (pageUrls.isEmpty()) {
          mainCanvas.setErrorMessage("Chương chưa có trang ảnh nào để xem trước.");
          pageIndicator.setText("0 / 0");
          prevBtn.setEnabled(false);
          nextBtn.setEnabled(false);
          thumbnailList.revalidate();
          thumbnailList.repaint();
          return;
        }

        for (int i = 0; i < pageUrls.size(); i++) {
          int pageIdx = i;
          JButton thumb = new JButton("Trang " + (i + 1));
          thumb.setFont(Theme.label(12));
          thumb.setAlignmentX(Component.CENTER_ALIGNMENT);
          thumb.setMaximumSize(new Dimension(120, 34));
          thumb.setFocusPainted(false);
          thumb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          thumb.addActionListener(e -> selectPage(pageIdx));
          thumbButtons.add(thumb);
          thumbnailList.add(thumb);
          thumbnailList.add(Box.createVerticalStrut(4));
        }

        thumbnailList.revalidate();
        thumbnailList.repaint();
        selectPage(0);
      });
    });
  }

  private void selectPage(int index) {
    if (index < 0 || index >= pageUrls.size()) return;
    this.currentPage = index;

    pageIndicator.setText("Trang " + (index + 1) + " / " + pageUrls.size());
    prevBtn.setEnabled(index > 0);
    nextBtn.setEnabled(index < pageUrls.size() - 1);

    for (int i = 0; i < thumbButtons.size(); i++) {
      JButton btn = thumbButtons.get(i);
      if (i == index) {
        btn.setBackground(Theme.ACTION);
        btn.setForeground(Color.WHITE);
      } else {
        btn.setBackground(Theme.SURFACE);
        btn.setForeground(Theme.TEXT);
      }
    }

    String url = pageUrls.get(index);
    mainCanvas.loadPage(url, api);
  }

  private static final class PageCanvas extends JPanel {
    private BufferedImage image;
    private boolean loading;
    private String errorMessage;

    PageCanvas() {
      setOpaque(false);
      setLayout(null);
    }

    void setLoading(boolean loading) {
      this.loading = loading;
      this.errorMessage = null;
      repaint();
    }

    void setErrorMessage(String msg) {
      this.errorMessage = msg;
      this.loading = false;
      this.image = null;
      repaint();
    }

    void loadPage(String rawUrl, ApiClient api) {
      this.loading = true;
      this.errorMessage = null;
      repaint();

      String targetUrl = rawUrl;
      if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
        targetUrl = api.apiBase() + (targetUrl.startsWith("/") ? "" : "/") + targetUrl;
      }
      final String fetchUrl = targetUrl;

      new SwingWorker<BufferedImage, Void>() {
        @Override
        protected BufferedImage doInBackground() throws Exception {
          URL u = new URL(fetchUrl);
          return ImageIO.read(u);
        }

        @Override
        protected void done() {
          try {
            image = get();
            loading = false;
            if (image != null) {
              int w = Math.min(800, image.getWidth());
              int h = (int) (((double) w / image.getWidth()) * image.getHeight());
              setPreferredSize(new Dimension(w, h));
              revalidate();
            }
          } catch (Exception e) {
            loading = false;
            errorMessage = "Lỗi nạp ảnh: " + e.getMessage();
          }
          repaint();
        }
      }.execute();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

      int w = getWidth();
      int h = getHeight();

      if (loading) {
        g2.setColor(Theme.MUTED);
        g2.setFont(Theme.label(14));
        String text = "Đang tải trang ảnh...";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, (w - fm.stringWidth(text)) / 2, h / 2);
      } else if (errorMessage != null) {
        g2.setColor(new Color(0xDC, 0x26, 0x26));
        g2.setFont(Theme.label(14));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(errorMessage, (w - fm.stringWidth(errorMessage)) / 2, h / 2);
      } else if (image != null) {
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        int maxW = Math.max(300, w - 40);
        int drawW = Math.min(imgW, maxW);
        int drawH = (int) (((double) drawW / imgW) * imgH);
        int x = (w - drawW) / 2;
        int y = 16;
        g2.drawImage(image, x, y, drawW, drawH, null);
      }
      g2.dispose();
    }
  }
}
