package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiIcons;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

public final class VersionHistoryDialog extends JDialog {
  private final ApiClient api;
  private final String chapterId;
  private final String chapterTitle;
  private final Runnable onHistoryChanged;

  private final DefaultTableModel tableModel = new DefaultTableModel(
      new Object[] {"PHIÊN BẢN", "TRẠNG THÁI", "NGƯỜI TẠO", "SỐ TRANG"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return false; }
  };
  private final JTable table = new JTable(tableModel);
  private final List<JsonNode> versionRows = new ArrayList<>();

  private final JLabel detailTitle = new JLabel("Chọn phiên bản để xem chi tiết");
  private final JLabel detailState = Theme.plainLabel("Trạng thái: —");
  private final JLabel detailPages = Theme.plainLabel("Số trang: —");
  private final JButton previewBtn = new JButton("👁 Xem trước");
  private final JButton rollbackBtn = new JButton("↺ Tạo bản nháp phục hồi");
  private JsonNode selectedVersion;

  public VersionHistoryDialog(Window owner, ApiClient api, String chapterId, String chapterTitle, Runnable onHistoryChanged) {
    super(owner, "Comic Net · Lịch sử phiên bản", ModalityType.APPLICATION_MODAL);
    this.api = api;
    this.chapterId = chapterId;
    this.chapterTitle = chapterTitle;
    this.onHistoryChanged = onHistoryChanged;

    setSize(960, 620);
    setMinimumSize(new Dimension(840, 500));
    setLocationRelativeTo(owner);
    setLayout(new BorderLayout(0, 0));
    getContentPane().setBackground(Theme.BACKGROUND);

    add(buildHeader(), BorderLayout.NORTH);
    add(buildBody(), BorderLayout.CENTER);

    loadVersions();
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout(16, 4));
    header.setBackground(Theme.SURFACE);
    header.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
        new EmptyBorder(16, 28, 16, 28)));

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);

    JLabel eyebrow = Theme.plainLabel("NÂNG CAO / PHIÊN BẢN");
    eyebrow.setFont(Theme.heading(11));
    eyebrow.setForeground(Theme.PRIMARY);

    JLabel title = new JLabel("Lịch sử phiên bản — " + (chapterTitle != null ? chapterTitle : "Chương"));
    title.setFont(Theme.heading(20));
    title.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel("Toàn bộ snapshot của truyện qua từng lần duyệt. Không thể tự tiện xóa lịch sử xuất bản.");
    sub.setFont(Theme.label(12));
    sub.setForeground(Theme.MUTED);

    titleBox.add(eyebrow);
    titleBox.add(title);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.CENTER);

    JButton backBtn = new JButton("← Trở về");
    backBtn.setFont(Theme.label(13));
    backBtn.setFocusPainted(false);
    backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    backBtn.addActionListener(e -> dispose());
    header.add(backBtn, BorderLayout.EAST);

    return header;
  }

  private JPanel buildBody() {
    JPanel body = new JPanel(new BorderLayout(18, 0));
    body.setOpaque(false);
    body.setBorder(new EmptyBorder(18, 28, 18, 28));

    // Left table
    table.setRowHeight(40);
    table.setFont(Theme.label(13));
    table.getTableHeader().setFont(Theme.heading(12));
    table.getTableHeader().setBackground(Theme.SURFACE);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
        String s = String.valueOf(val);
        if ("PUBLISHED".equals(s)) {
          l.setText("● Đang công khai");
          l.setForeground(new Color(0x16, 0xA3, 0x4A));
        } else if ("APPROVED".equals(s)) {
          l.setText("● Đã duyệt");
          l.setForeground(new Color(0x25, 0x63, 0xEB));
        } else if ("DRAFT".equals(s)) {
          l.setText("● Bản nháp");
          l.setForeground(Theme.MUTED);
        } else if ("NEEDS_CHANGES".equals(s)) {
          l.setText("● Cần sửa lại");
          l.setForeground(new Color(0xD9, 0x77, 0x06));
        } else if ("REJECTED".equals(s)) {
          l.setText("● Bị từ chối");
          l.setForeground(new Color(0xDC, 0x26, 0x26));
        } else {
          l.setText("● " + s);
          l.setForeground(Theme.MUTED);
        }
        return l;
      }
    });

    table.getSelectionModel().addListSelectionListener(e -> {
      int idx = table.getSelectedRow();
      if (idx >= 0 && idx < versionRows.size()) {
        selectedVersion = versionRows.get(idx);
        updateDetail();
      }
    });

    JScrollPane tableScroll = new JScrollPane(table);
    tableScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    tableScroll.setBackground(Theme.SURFACE);
    tableScroll.getViewport().setBackground(Theme.SURFACE);
    body.add(tableScroll, BorderLayout.CENTER);

    // Right detail card
    JPanel detailCard = new JPanel();
    detailCard.setLayout(new BoxLayout(detailCard, BoxLayout.Y_AXIS));
    detailCard.setPreferredSize(new Dimension(280, 0));
    detailCard.setBackground(Theme.SURFACE);
    detailCard.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(20, 20, 20, 20)));

    detailTitle.setFont(Theme.heading(16));
    detailTitle.setForeground(Theme.TEXT);
    detailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

    detailState.setFont(Theme.label(13));
    detailState.setForeground(Theme.MUTED);
    detailState.setAlignmentX(Component.LEFT_ALIGNMENT);

    detailPages.setFont(Theme.label(13));
    detailPages.setForeground(Theme.MUTED);
    detailPages.setAlignmentX(Component.LEFT_ALIGNMENT);

    previewBtn.setFont(Theme.label(13));
    previewBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
    previewBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
    previewBtn.setEnabled(false);
    previewBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    previewBtn.addActionListener(e -> {
      if (selectedVersion != null) {
        String vid = selectedVersion.path("id").asText();
        ChapterPreviewDialog dlg = new ChapterPreviewDialog(this, api, vid, chapterTitle);
        dlg.setVisible(true);
      }
    });

    rollbackBtn.setFont(Theme.heading(13));
    rollbackBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
    rollbackBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
    rollbackBtn.setBackground(Theme.ACTION);
    rollbackBtn.setForeground(Color.WHITE);
    rollbackBtn.setEnabled(false);
    rollbackBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    rollbackBtn.addActionListener(e -> doRollback());

    JPanel notice = new JPanel(new BorderLayout(8, 0));
    notice.setBackground(new Color(0xFE, 0xF3, 0xC7));
    notice.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(0xFC, 0xD3, 0x4D), 1, true),
        new EmptyBorder(8, 10, 8, 10)));
    notice.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel noticeIcon = new JLabel(UiIcons.alertCircle(16, new Color(0xD9, 0x77, 0x06)));
    JLabel noticeText = new JLabel("<html><body style='width: 200px; color: #92400E; font-size: 10px;'>"
        + "Không được phép sửa trực tiếp bản đã lưu trữ. Phục hồi sẽ tạo bản nháp mới từ snapshot này."
        + "</body></html>");
    notice.add(noticeIcon, BorderLayout.WEST);
    notice.add(noticeText, BorderLayout.CENTER);

    detailCard.add(detailTitle);
    detailCard.add(Box.createVerticalStrut(10));
    detailCard.add(detailState);
    detailCard.add(Box.createVerticalStrut(4));
    detailCard.add(detailPages);
    detailCard.add(Box.createVerticalStrut(20));
    detailCard.add(previewBtn);
    detailCard.add(Box.createVerticalStrut(8));
    detailCard.add(rollbackBtn);
    detailCard.add(Box.createVerticalStrut(20));
    detailCard.add(notice);
    detailCard.add(Box.createVerticalGlue());

    body.add(detailCard, BorderLayout.EAST);
    return body;
  }

  private void updateDetail() {
    if (selectedVersion == null) return;
    int num = selectedVersion.path("versionNumber").asInt(1);
    String state = selectedVersion.path("state").asText();
    int pages = selectedVersion.path("pages").size();

    detailTitle.setText("Chi tiết bản " + num);
    detailState.setText("Trạng thái: " + state);
    detailPages.setText("Số trang: " + pages + " trang");

    previewBtn.setEnabled(pages > 0);
    rollbackBtn.setEnabled(!"PUBLISHED".equals(state));
  }

  private void loadVersions() {
    api.get("/publishing/chapters/" + chapterId + "/versions").whenComplete((reply, err) -> {
      SwingUtilities.invokeLater(() -> {
        if (err != null || reply == null) return;
        tableModel.setRowCount(0);
        versionRows.clear();

        for (JsonNode v : reply.body()) {
          versionRows.add(v);
          int num = v.path("versionNumber").asInt(1);
          String state = v.path("state").asText();
          String author = v.path("createdBy").asText("—");
          int pages = v.path("pages").size();
          tableModel.addRow(new Object[] {"Bản " + num, state, author, pages + " trang"});
        }

        if (!versionRows.isEmpty()) {
          table.setRowSelectionInterval(0, 0);
        }
      });
    });
  }

  private void doRollback() {
    if (selectedVersion == null) return;
    String vid = selectedVersion.path("id").asText();
    int num = selectedVersion.path("versionNumber").asInt(1);

    int conf = JOptionPane.showConfirmDialog(
        this,
        "Bạn có chắc muốn phục hồi từ Bản " + num + "? Hệ thống sẽ tạo một bản nháp mới từ snapshot này.",
        "Xác nhận phục hồi",
        JOptionPane.YES_NO_OPTION);
    if (conf != JOptionPane.YES_OPTION) return;

    // Fetch chapter etag first
    api.get("/publishing/chapters/" + chapterId).whenComplete((cReply, cErr) -> {
      if (cErr != null || cReply == null) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Không thể lấy thông tin chương.", "Lỗi", JOptionPane.ERROR_MESSAGE));
        return;
      }
      String etag = cReply.etag();

      api.request("POST", "/publishing/chapters/" + chapterId + "/rollback", java.util.Map.of("versionId", vid), etag)
          .whenComplete((rReply, rErr) -> {
            SwingUtilities.invokeLater(() -> {
              if (rErr != null || rReply == null) {
                JOptionPane.showMessageDialog(this, "Phục hồi thất bại: " + (rReply != null ? rReply.body() : rErr), "Lỗi", JOptionPane.ERROR_MESSAGE);
              } else {
                JOptionPane.showMessageDialog(this, "Phục hồi thành công! Bản nháp mới đã được tạo.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                loadVersions();
                if (onHistoryChanged != null) onHistoryChanged.run();
              }
            });
          });
    });
  }
}
