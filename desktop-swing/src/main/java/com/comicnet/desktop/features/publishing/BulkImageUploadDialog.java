package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiIcons;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import tools.jackson.databind.JsonNode;

public final class BulkImageUploadDialog extends JDialog {
  private final ApiClient api;
  private final String versionId;
  private final String chapterTitle;
  private final String versionEtag;
  private final Runnable onUploadCompleted;

  private final List<ImageItem> items = new ArrayList<>();
  private final JPanel gridPanel = new JPanel();
  private final JLabel countLabel = Theme.plainLabel("0 / 0");
  private final JLabel sizeLabel = Theme.plainLabel("0 MB");
  private final JLabel errorLabel = Theme.plainLabel("0 lỗi");
  private final JProgressBar progressBar = new JProgressBar();
  private final JButton uploadBtn = new JButton("↑ Tải lên 0 ảnh");
  private boolean uploading = false;

  public static final class ImageItem {
    final File file;
    final String originalName;
    final long sizeBytes;
    BufferedImage thumbnail;

    public ImageItem(File file, String name, long size) {
      this.file = file;
      this.originalName = name;
      this.sizeBytes = size;
    }
  }

  public BulkImageUploadDialog(
      Window owner,
      ApiClient api,
      String versionId,
      String chapterTitle,
      String versionEtag,
      Runnable onUploadCompleted) {
    super(owner, "Comic Net · Tải nhiều ảnh", ModalityType.APPLICATION_MODAL);
    this.api = api;
    this.versionId = versionId;
    this.chapterTitle = chapterTitle;
    this.versionEtag = versionEtag;
    this.onUploadCompleted = onUploadCompleted;

    setSize(920, 680);
    setMinimumSize(new Dimension(800, 560));
    setLocationRelativeTo(owner);
    setLayout(new BorderLayout(0, 0));
    getContentPane().setBackground(Theme.BACKGROUND);

    add(buildHeader(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildFooter(), BorderLayout.SOUTH);
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout(16, 8));
    header.setBackground(Theme.SURFACE);
    header.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
        new EmptyBorder(16, 24, 16, 24)));

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 3));
    titleBox.setOpaque(false);

    JLabel title = new JLabel("Tải nhiều ảnh — " + (chapterTitle != null ? chapterTitle : "Chương"));
    title.setFont(Theme.heading(18));
    title.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel("Đã tự sắp theo: Tên file (natural sort). Kéo đổi vị trí hoặc dùng mũi tên nếu cần.");
    sub.setFont(Theme.label(12));
    sub.setForeground(Theme.MUTED);

    titleBox.add(title);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.WEST);

    // Toolbar on the right
    JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    tools.setOpaque(false);

    // Stats
    JPanel stats = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
    stats.setOpaque(false);

    countLabel.setFont(Theme.heading(13));
    countLabel.setForeground(Theme.TEXT);
    sizeLabel.setFont(Theme.label(13));
    sizeLabel.setForeground(Theme.MUTED);
    errorLabel.setFont(Theme.label(13));
    errorLabel.setForeground(new Color(0x16, 0xA3, 0x4A));

    stats.add(countLabel);
    stats.add(sizeLabel);
    stats.add(errorLabel);
    tools.add(stats);

    JButton addFilesBtn = new JButton("+ Thêm ảnh");
    addFilesBtn.setFont(Theme.label(13));
    addFilesBtn.setBackground(Theme.SURFACE);
    addFilesBtn.setFocusPainted(false);
    addFilesBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    addFilesBtn.addActionListener(e -> chooseFiles());
    tools.add(addFilesBtn);

    JButton addZipBtn = new JButton("↑ Tải từ ZIP");
    addZipBtn.setFont(Theme.label(13));
    addZipBtn.setBackground(Theme.SURFACE);
    addZipBtn.setFocusPainted(false);
    addZipBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    addZipBtn.addActionListener(e -> chooseZip());
    tools.add(addZipBtn);

    header.add(tools, BorderLayout.EAST);
    return header;
  }

  private JPanel buildCenter() {
    JPanel center = new JPanel(new BorderLayout(0, 8));
    center.setOpaque(false);
    center.setBorder(new EmptyBorder(12, 20, 12, 20));

    gridPanel.setOpaque(false);
    gridPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 14, 14));

    JScrollPane scroll = new JScrollPane(gridPanel);
    scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    scroll.setBackground(Theme.SURFACE);
    scroll.getViewport().setBackground(Theme.SURFACE);
    scroll.getVerticalScrollBar().setUnitIncrement(24);

    center.add(scroll, BorderLayout.CENTER);

    progressBar.setStringPainted(true);
    progressBar.setVisible(false);
    center.add(progressBar, BorderLayout.SOUTH);

    return center;
  }

  private JPanel buildFooter() {
    JPanel footer = new JPanel(new BorderLayout(16, 0));
    footer.setBackground(Theme.SURFACE);
    footer.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
        new EmptyBorder(12, 24, 12, 24)));

    JLabel hint = Theme.plainLabel("Hệ thống sẽ kiểm tra định dạng và tối ưu ảnh tự động khi xuất bản.");
    hint.setFont(Theme.label(12));
    hint.setForeground(Theme.MUTED);
    footer.add(hint, BorderLayout.WEST);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    actions.setOpaque(false);

    JButton cancelBtn = new JButton("Hủy");
    cancelBtn.setFont(Theme.label(13));
    cancelBtn.setFocusPainted(false);
    cancelBtn.addActionListener(e -> {
      if (!uploading) dispose();
    });

    uploadBtn.setFont(Theme.heading(13));
    uploadBtn.setBackground(Theme.ACTION);
    uploadBtn.setForeground(Color.WHITE);
    uploadBtn.setFocusPainted(false);
    uploadBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    uploadBtn.addActionListener(e -> startUpload());

    actions.add(cancelBtn);
    actions.add(uploadBtn);
    footer.add(actions, BorderLayout.EAST);

    return footer;
  }

  private void chooseFiles() {
    JFileChooser chooser = new JFileChooser();
    chooser.setMultiSelectionEnabled(true);
    chooser.setFileFilter(new FileNameExtensionFilter("Ảnh truyện (*.jpg, *.png, *.webp)", "jpg", "jpeg", "png", "webp"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File[] files = chooser.getSelectedFiles();
      if (files != null) {
        for (File f : files) {
          items.add(new ImageItem(f, f.getName(), f.length()));
        }
        applyNaturalSort();
        rebuildCards();
      }
    }
  }

  private void chooseZip() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Tệp nén ZIP (*.zip)", "zip"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File zip = chooser.getSelectedFile();
      if (zip != null && zip.exists()) {
        extractAndAddZip(zip);
      }
    }
  }

  private void extractAndAddZip(File zipFile) {
    try {
      Path tempDir = Files.createTempDirectory("comicnet_zip_");
      try (ZipFile zip = new ZipFile(zipFile)) {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          String name = entry.getName();
          if (entry.isDirectory() || name.contains("__MACOSX") || name.startsWith(".")) continue;
          String lower = name.toLowerCase(Locale.ROOT);
          if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
            File outFile = new File(tempDir.toFile(), new File(name).getName());
            try (InputStream in = zip.getInputStream(entry);
                 OutputStream out = new FileOutputStream(outFile)) {
              in.transferTo(out);
            }
            items.add(new ImageItem(outFile, outFile.getName(), outFile.length()));
          }
        }
      }
      applyNaturalSort();
      rebuildCards();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(this, "Không thể đọc tệp ZIP: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void applyNaturalSort() {
    items.sort((a, b) -> NaturalSort.COMPARATOR.compare(a.originalName, b.originalName));
  }

  private void rebuildCards() {
    gridPanel.removeAll();

    long totalBytes = 0;
    for (int i = 0; i < items.size(); i++) {
      ImageItem item = items.get(i);
      totalBytes += item.sizeBytes;
      gridPanel.add(buildCard(item, i));
    }

    countLabel.setText(items.size() + " / " + items.size());
    double mb = totalBytes / (1024.0 * 1024.0);
    sizeLabel.setText(String.format(Locale.ROOT, "%.1f MB", mb));
    uploadBtn.setText("↑ Tải lên " + items.size() + " ảnh");
    uploadBtn.setEnabled(!items.isEmpty());

    gridPanel.revalidate();
    gridPanel.repaint();
  }

  private JPanel buildCard(ImageItem item, int index) {
    JPanel card = new JPanel(new BorderLayout(0, 6));
    card.setPreferredSize(new Dimension(135, 195));
    card.setBackground(Theme.SURFACE);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(6, 6, 8, 6)));

    // Top: Page badge & Remove button
    JPanel cardTop = new JPanel(new BorderLayout());
    cardTop.setOpaque(false);

    JLabel badge = new JLabel(String.format("%02d", index + 1));
    badge.setFont(Theme.heading(12));
    badge.setForeground(Theme.PRIMARY);

    JButton removeBtn = new JButton("×");
    removeBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
    removeBtn.setForeground(Theme.MUTED);
    removeBtn.setContentAreaFilled(false);
    removeBtn.setBorderPainted(false);
    removeBtn.setFocusPainted(false);
    removeBtn.setMargin(new Insets(0, 0, 0, 0));
    removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    removeBtn.addActionListener(e -> {
      items.remove(index);
      rebuildCards();
    });

    cardTop.add(badge, BorderLayout.WEST);
    cardTop.add(removeBtn, BorderLayout.EAST);
    card.add(cardTop, BorderLayout.NORTH);

    // Center: Thumbnail preview
    ThumbnailCanvas thumbCanvas = new ThumbnailCanvas(item);
    card.add(thumbCanvas, BorderLayout.CENTER);

    // Bottom: Move arrows & File name
    JPanel cardBottom = new JPanel(new BorderLayout(0, 2));
    cardBottom.setOpaque(false);

    JLabel nameLbl = new JLabel(item.originalName);
    nameLbl.setFont(Theme.label(10));
    nameLbl.setForeground(Theme.MUTED);
    nameLbl.setToolTipText(item.originalName);
    cardBottom.add(nameLbl, BorderLayout.NORTH);

    JPanel arrows = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
    arrows.setOpaque(false);

    JButton leftBtn = new JButton("◀");
    leftBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
    leftBtn.setMargin(new Insets(1, 4, 1, 4));
    leftBtn.setEnabled(index > 0);
    leftBtn.addActionListener(e -> {
      Collections.swap(items, index, index - 1);
      rebuildCards();
    });

    JButton rightBtn = new JButton("▶");
    rightBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
    rightBtn.setMargin(new Insets(1, 4, 1, 4));
    rightBtn.setEnabled(index < items.size() - 1);
    rightBtn.addActionListener(e -> {
      Collections.swap(items, index, index + 1);
      rebuildCards();
    });

    arrows.add(leftBtn);
    arrows.add(rightBtn);
    cardBottom.add(arrows, BorderLayout.SOUTH);

    card.add(cardBottom, BorderLayout.SOUTH);
    return card;
  }

  private void startUpload() {
    if (items.isEmpty()) return;
    uploading = true;
    uploadBtn.setEnabled(false);
    progressBar.setVisible(true);
    progressBar.setIndeterminate(false);
    progressBar.setValue(10);
    progressBar.setString("Đang chuẩn bị gói nén ảnh...");

    new SwingWorker<Void, Integer>() {
      @Override
      protected Void doInBackground() throws Exception {
        // Create an optimized zip file containing the images named with 4-digit prefixes
        File tempZip = File.createTempFile("chapter_upload_", ".zip");
        tempZip.deleteOnExit();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempZip)))) {
          for (int i = 0; i < items.size(); i++) {
            ImageItem item = items.get(i);
            String ext = "";
            int dot = item.originalName.lastIndexOf('.');
            if (dot >= 0) ext = item.originalName.substring(dot);
            String entryName = String.format("%04d%s", i + 1, ext);
            zos.putNextEntry(new ZipEntry(entryName));
            try (InputStream in = new BufferedInputStream(new FileInputStream(item.file))) {
              in.transferTo(zos);
            }
            zos.closeEntry();
            publish((int) (10 + (double) (i + 1) / items.size() * 30));
          }
        }

        publish(45);
        // Upload zip to backend
        progressBar.setString("Đang tải dữ liệu lên máy chủ...");
        var uploadFuture = api.upload(
            "/publishing/versions/" + versionId + "/uploads",
            tempZip.toPath(),
            Map.of(),
            null,
            versionEtag);

        var reply = uploadFuture.get();
        if (reply == null) {
          throw new IOException("Upload failed: " + reply.body());
        }

        JsonNode job = reply.body();
        String jobId = job.path("id").asText();

        // Poll job status
        publish(70);
        progressBar.setString("Máy chủ đang xử lý và tối ưu hóa ảnh...");
        for (int retry = 0; retry < 60; retry++) {
          Thread.sleep(1000);
          var jobReply = api.get("/publishing/uploads/" + jobId).get();
          if (jobReply != null) {
            String status = jobReply.body().path("status").asText();
            if ("COMPLETED".equals(status)) {
              publish(100);
              break;
            } else if ("FAILED".equals(status)) {
              String err = jobReply.body().path("errorCode").asText("UPLOAD_FAILED");
              throw new IOException("Xử lý ảnh thất bại: " + err);
            }
          }
        }

        tempZip.delete();
        return null;
      }

      @Override
      protected void process(List<Integer> chunks) {
        if (!chunks.isEmpty()) {
          progressBar.setValue(chunks.get(chunks.size() - 1));
        }
      }

      @Override
      protected void done() {
        uploading = false;
        try {
          get();
          JOptionPane.showMessageDialog(BulkImageUploadDialog.this, "Tải lên thành công " + items.size() + " trang ảnh!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
          dispose();
          if (onUploadCompleted != null) onUploadCompleted.run();
        } catch (Exception e) {
          progressBar.setVisible(false);
          uploadBtn.setEnabled(true);
          JOptionPane.showMessageDialog(BulkImageUploadDialog.this, "Lỗi tải ảnh: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
      }
    }.execute();
  }

  private static final class ThumbnailCanvas extends JPanel {
    private final ImageItem item;

    ThumbnailCanvas(ImageItem item) {
      this.item = item;
      setOpaque(false);
      if (item.thumbnail == null) {
        loadThumb();
      }
    }

    private void loadThumb() {
      new SwingWorker<BufferedImage, Void>() {
        @Override
        protected BufferedImage doInBackground() throws Exception {
          BufferedImage original = ImageIO.read(item.file);
          if (original == null) return null;
          int w = 110;
          int h = (int) (((double) w / original.getWidth()) * original.getHeight());
          BufferedImage thumb = new BufferedImage(w, Math.max(h, 1), BufferedImage.TYPE_INT_RGB);
          Graphics2D g2 = thumb.createGraphics();
          g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          g2.drawImage(original, 0, 0, w, h, null);
          g2.dispose();
          return thumb;
        }

        @Override
        protected void done() {
          try {
            item.thumbnail = get();
            repaint();
          } catch (Exception ignored) {}
        }
      }.execute();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int w = getWidth();
      int h = getHeight();

      if (item.thumbnail != null) {
        int tw = item.thumbnail.getWidth();
        int th = item.thumbnail.getHeight();
        int drawW = Math.min(tw, w);
        int drawH = Math.min(th, h);
        int x = (w - drawW) / 2;
        int y = (h - drawH) / 2;
        g2.drawImage(item.thumbnail, x, y, drawW, drawH, null);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(x, y, drawW, drawH, 4, 4);
      } else {
        g2.setColor(new Color(0xEE, 0xF2, 0xF6));
        g2.fillRoundRect(2, 2, w - 4, h - 4, 6, 6);
        g2.setColor(Theme.MUTED);
        g2.setFont(Theme.label(10));
        g2.drawString("Đang nạp...", 10, h / 2);
      }
      g2.dispose();
    }
  }
}
