package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.features.catalog.CatalogPanel.CoverArtCanvas;
import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiIcons;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import tools.jackson.databind.JsonNode;

public final class CreatorFeedbackPanel extends JPanel {
  private final ApiClient api;
  private final Runnable onBack;
  private final Consumer<String> onEditChapter;

  private final JLabel feedbackText = new JLabel("Vui lòng kiểm tra lại thứ tự các trang ảnh và ảnh bìa chất lượng quá thấp...");
  private final JLabel comicTitleLbl = new JLabel("Tiêu đề truyện");
  private final CoverArtCanvas coverCanvas = new CoverArtCanvas(8);
  private final JTextArea creatorNoteArea = new JTextArea(3, 20);

  private String versionId;
  private String versionEtag;
  private String chapterId;

  public CreatorFeedbackPanel(ApiClient api, Runnable onBack, Consumer<String> onEditChapter) {
    super(new BorderLayout(0, 16));
    this.api = api;
    this.onBack = onBack;
    this.onEditChapter = onEditChapter;

    setOpaque(false);
    setBorder(new EmptyBorder(16, 24, 24, 24));

    add(buildHeader(), BorderLayout.NORTH);
    add(buildBody(), BorderLayout.CENTER);
  }

  private JPanel buildHeader() {
    JPanel header = new JPanel(new BorderLayout(0, 6));
    header.setOpaque(false);

    JLabel eyebrow = Theme.plainLabel("XƯỞNG XUẤT BẢN / PHẢN HỒI YÊU CẦU");
    eyebrow.setFont(Theme.heading(11));
    eyebrow.setForeground(Theme.PRIMARY);

    JLabel heading = new JLabel("Phản hồi xét duyệt");
    heading.setFont(Theme.heading(24));
    heading.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel("Admin đã xem bản gửi trước và yêu cầu chỉnh sửa một số nội dung.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(heading);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.WEST);

    JButton backBtn = new JButton("← Quay lại danh sách");
    backBtn.setFont(Theme.label(13));
    backBtn.setFocusPainted(false);
    backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    backBtn.addActionListener(e -> onBack.run());
    header.add(backBtn, BorderLayout.EAST);

    return header;
  }

  private JPanel buildBody() {
    JPanel body = new JPanel(new BorderLayout(0, 16));
    body.setOpaque(false);

    // Amber Alert Banner (Mockup 09)
    JPanel alertBanner = new JPanel(new BorderLayout(12, 0));
    alertBanner.setBackground(new Color(0xFE, 0xF3, 0xC7));
    alertBanner.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(0xFC, 0xD3, 0x4D), 1, true),
        new EmptyBorder(14, 18, 14, 18)));

    JLabel alertIcon = new JLabel(UiIcons.alertCircle(22, new Color(0xD9, 0x77, 0x06)));
    alertBanner.add(alertIcon, BorderLayout.WEST);

    JPanel alertTextPanel = new JPanel(new GridLayout(0, 1, 0, 2));
    alertTextPanel.setOpaque(false);

    JLabel alertTitle = new JLabel("Admin yêu cầu chỉnh sửa:");
    alertTitle.setFont(Theme.heading(13));
    alertTitle.setForeground(new Color(0x92, 0x40, 0x0E));

    feedbackText.setFont(Theme.label(13));
    feedbackText.setForeground(new Color(0xB4, 0x53, 0x09));

    alertTextPanel.add(alertTitle);
    alertTextPanel.add(feedbackText);
    alertBanner.add(alertTextPanel, BorderLayout.CENTER);

    body.add(alertBanner, BorderLayout.NORTH);

    // 2-Column Content
    JPanel content = new JPanel(new BorderLayout(20, 0));
    content.setOpaque(false);

    // Left: Revision Draft Card
    JPanel leftCard = new JPanel();
    leftCard.setLayout(new BoxLayout(leftCard, BoxLayout.Y_AXIS));
    leftCard.setBackground(Theme.SURFACE);
    leftCard.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(18, 20, 18, 20)));

    JLabel cardTitle = new JLabel("Bản chỉnh sửa của bạn");
    cardTitle.setFont(Theme.heading(16));
    cardTitle.setForeground(Theme.TEXT);
    cardTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

    JPanel comicRow = new JPanel(new BorderLayout(14, 0));
    comicRow.setOpaque(false);
    comicRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
    comicRow.setAlignmentX(Component.LEFT_ALIGNMENT);

    coverCanvas.setPreferredSize(new Dimension(70, 100));
    comicRow.add(coverCanvas, BorderLayout.WEST);

    JPanel meta = new JPanel(new GridLayout(0, 1, 0, 4));
    meta.setOpaque(false);
    comicTitleLbl.setFont(Theme.heading(16));
    comicTitleLbl.setForeground(Theme.TEXT);
    JLabel statusLbl = Theme.plainLabel("● Trạng thái: Cần sửa lại");
    statusLbl.setFont(Theme.heading(12));
    statusLbl.setForeground(new Color(0xD9, 0x77, 0x06));
    meta.add(comicTitleLbl);
    meta.add(statusLbl);
    comicRow.add(meta, BorderLayout.CENTER);

    // Tasks list
    JPanel taskList = new JPanel(new GridLayout(0, 1, 0, 10));
    taskList.setOpaque(false);
    taskList.setAlignmentX(Component.LEFT_ALIGNMENT);

    taskList.add(buildTaskItem("Chương truyện & Thứ tự trang ảnh", "✎ Chỉnh sửa chương", e -> {
      if (onEditChapter != null && chapterId != null) onEditChapter.accept(chapterId);
    }));
    taskList.add(buildTaskItem("Ảnh bìa tác phẩm", "✎ Thay đổi ảnh", e -> {
      JOptionPane.showMessageDialog(this, "Bạn có thể đổi ảnh bìa trong bước Thông tin truyện.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }));

    leftCard.add(cardTitle);
    leftCard.add(Box.createVerticalStrut(14));
    leftCard.add(comicRow);
    leftCard.add(Box.createVerticalStrut(20));
    leftCard.add(taskList);
    leftCard.add(Box.createVerticalGlue());
    content.add(leftCard, BorderLayout.CENTER);

    // Right: Resubmit Panel
    JPanel rightCard = new JPanel();
    rightCard.setLayout(new BoxLayout(rightCard, BoxLayout.Y_AXIS));
    rightCard.setPreferredSize(new Dimension(320, 0));
    rightCard.setBackground(Theme.SURFACE);
    rightCard.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(18, 20, 18, 20)));

    JLabel resubmitTitle = new JLabel("Gửi lại cho Admin");
    resubmitTitle.setFont(Theme.heading(16));
    resubmitTitle.setForeground(Theme.TEXT);
    resubmitTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

    JPanel checkList = new JPanel(new GridLayout(0, 1, 0, 8));
    checkList.setOpaque(false);
    checkList.setAlignmentX(Component.LEFT_ALIGNMENT);
    checkList.add(buildCheckItem("Đã hoàn thiện nội dung theo phản hồi"));
    checkList.add(buildCheckItem("Đã kiểm tra chất lượng và thứ tự trang"));
    checkList.add(buildCheckItem("Sẵn sàng gửi xét duyệt lại"));

    JLabel noteHead = Theme.plainLabel("Ghi chú cho Admin (tùy chọn):");
    noteHead.setFont(Theme.label(12));
    noteHead.setForeground(Theme.TEXT);
    noteHead.setAlignmentX(Component.LEFT_ALIGNMENT);

    creatorNoteArea.setFont(Theme.label(12));
    creatorNoteArea.setLineWrap(true);
    creatorNoteArea.setWrapStyleWord(true);
    creatorNoteArea.setBorder(new EmptyBorder(6, 6, 6, 6));
    JScrollPane noteScroll = new JScrollPane(creatorNoteArea);
    noteScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    noteScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
    noteScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

    JButton previewBtn = new JButton("👁 Xem trước");
    previewBtn.setFont(Theme.label(13));
    previewBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
    previewBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
    previewBtn.setBackground(Theme.SURFACE);
    previewBtn.setFocusPainted(false);
    previewBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    previewBtn.addActionListener(e -> {
      if (versionId != null) {
        ChapterPreviewDialog dlg = new ChapterPreviewDialog(
            SwingUtilities.getWindowAncestor(this),
            api,
            versionId,
            comicTitleLbl.getText());
        dlg.setVisible(true);
      }
    });

    JButton resubmitBtn = new JButton("✈ Gửi duyệt lại");
    resubmitBtn.setFont(Theme.heading(13));
    resubmitBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
    resubmitBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
    resubmitBtn.setBackground(Theme.ACTION);
    resubmitBtn.setForeground(Color.WHITE);
    resubmitBtn.setFocusPainted(false);
    resubmitBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    resubmitBtn.addActionListener(e -> doResubmit());

    rightCard.add(resubmitTitle);
    rightCard.add(Box.createVerticalStrut(14));
    rightCard.add(checkList);
    rightCard.add(Box.createVerticalStrut(14));
    rightCard.add(noteHead);
    rightCard.add(Box.createVerticalStrut(6));
    rightCard.add(noteScroll);
    rightCard.add(Box.createVerticalStrut(18));
    rightCard.add(previewBtn);
    rightCard.add(Box.createVerticalStrut(10));
    rightCard.add(resubmitBtn);
    rightCard.add(Box.createVerticalGlue());
    content.add(rightCard, BorderLayout.EAST);

    body.add(content, BorderLayout.CENTER);
    return body;
  }

  private JPanel buildTaskItem(String title, String btnText, java.awt.event.ActionListener action) {
    JPanel row = new JPanel(new BorderLayout(10, 0));
    row.setBackground(Theme.ELEVATED);
    row.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(10, 14, 10, 14)));

    JLabel lbl = Theme.plainLabel(title);
    lbl.setFont(Theme.label(13));
    lbl.setForeground(Theme.TEXT);
    row.add(lbl, BorderLayout.CENTER);

    JButton btn = new JButton(btnText);
    btn.setFont(Theme.heading(12));
    btn.setBackground(Theme.SURFACE);
    btn.setFocusPainted(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.addActionListener(action);
    row.add(btn, BorderLayout.EAST);

    return row;
  }

  private JPanel buildCheckItem(String text) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    row.setOpaque(false);
    row.add(new JLabel(UiIcons.check(14, new Color(0x16, 0xA3, 0x4A))));
    JLabel l = Theme.plainLabel(text);
    l.setFont(Theme.label(12));
    l.setForeground(Theme.TEXT);
    row.add(l);
    return row;
  }

  public void setFeedback(String versionId, String chapterId, String title, String reason) {
    this.versionId = versionId;
    this.chapterId = chapterId;
    this.comicTitleLbl.setText(title);
    this.feedbackText.setText(reason != null && !reason.isBlank() ? reason : "Vui lòng xem lại thứ tự các trang ảnh và cập nhật lại bản nháp.");

    // Fetch version etag
    api.get("/publishing/versions/" + versionId).whenComplete((reply, err) -> {
      if (reply != null && err == null) {
        this.versionEtag = reply.etag();
      }
    });
  }

  private void doResubmit() {
    if (versionId == null) return;

    int conf = JOptionPane.showConfirmDialog(
        this,
        "Xác nhận gửi bản đã chỉnh sửa lên Admin để kiểm duyệt lại?",
        "Gửi duyệt lại",
        JOptionPane.YES_NO_OPTION);
    if (conf != JOptionPane.YES_OPTION) return;

    api.request("POST", "/publishing/versions/" + versionId + "/submit", java.util.Map.of(), versionEtag)
        .whenComplete((reply, err) -> {
          SwingUtilities.invokeLater(() -> {
            if (err != null || reply == null) {
              JOptionPane.showMessageDialog(this, "Không thể gửi duyệt: " + (reply != null ? reply.body() : err), "Lỗi", JOptionPane.ERROR_MESSAGE);
            } else {
              JOptionPane.showMessageDialog(this, "Đã gửi bản xét duyệt lại thành công tới Admin!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
              onBack.run();
            }
          });
        });
  }
}
