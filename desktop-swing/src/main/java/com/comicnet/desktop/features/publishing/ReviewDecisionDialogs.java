package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiIcons;
import java.awt.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class ReviewDecisionDialogs {
  private ReviewDecisionDialogs() {}

  public static void showRequestChanges(Window owner, String comicTitle, Consumer<String> onConfirmed) {
    createRequestChangesDialog(owner, comicTitle, onConfirmed).setVisible(true);
  }

  public static JDialog createRequestChangesDialog(Window owner, String comicTitle, Consumer<String> onConfirmed) {
    JDialog dialog = new JDialog(owner, "Yêu cầu chỉnh sửa", Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setSize(540, 430);
    dialog.setLocationRelativeTo(owner);
    dialog.setLayout(new BorderLayout(0, 0));
    dialog.getContentPane().setBackground(Theme.SURFACE);

    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setBackground(Theme.SURFACE);
    header.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
        new EmptyBorder(16, 24, 16, 24)));

    JLabel title = new JLabel("Yêu cầu chỉnh sửa");
    title.setFont(Theme.heading(18));
    title.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel("Yêu cầu Creator sửa lại trước khi gửi duyệt lại.");
    sub.setFont(Theme.label(12));
    sub.setForeground(Theme.MUTED);

    header.add(title, BorderLayout.NORTH);
    header.add(sub, BorderLayout.CENTER);
    dialog.add(header, BorderLayout.NORTH);

    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    body.setBorder(new EmptyBorder(18, 24, 18, 24));

    if (comicTitle != null && !comicTitle.isBlank()) {
      JLabel comicLbl = new JLabel("Tác phẩm: " + comicTitle);
      comicLbl.setFont(Theme.heading(13));
      comicLbl.setForeground(Theme.TEXT);
      comicLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
      body.add(comicLbl);
      body.add(Box.createVerticalStrut(12));
    }

    JLabel reasonLabel = new JLabel("Lý do yêu cầu sửa *");
    reasonLabel.setFont(Theme.label(13));
    reasonLabel.setForeground(Theme.TEXT);
    reasonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(reasonLabel);
    body.add(Box.createVerticalStrut(6));

    JTextArea reasonArea = new JTextArea(4, 30);
    reasonArea.setFont(Theme.label(13));
    reasonArea.setText("Vui lòng kiểm tra lại thứ tự trang 7-10 của Chương 2 và ảnh bìa chất lượng quá thấp...");
    reasonArea.setLineWrap(true);
    reasonArea.setWrapStyleWord(true);
    reasonArea.setBorder(new EmptyBorder(8, 8, 8, 8));
    JScrollPane reasonScroll = new JScrollPane(reasonArea);
    reasonScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
    reasonScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    body.add(reasonScroll);
    body.add(Box.createVerticalStrut(14));

    // Notice card
    JPanel notice = new JPanel(new BorderLayout(10, 0));
    notice.setBackground(new Color(0xFE, 0xF3, 0xC7));
    notice.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(0xFC, 0xD3, 0x4D), 1, true),
        new EmptyBorder(10, 14, 10, 14)));
    notice.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel noticeIcon = new JLabel(UiIcons.alertCircle(18, new Color(0xD9, 0x77, 0x06)));
    JLabel noticeText = new JLabel("<html><body style='width: 380px; color: #92400E; font-size: 11px;'>"
        + "Nội dung chưa được xuất bản. Phiên bản hiện tại của độc giả (nếu có) không bị ảnh hưởng."
        + "</body></html>");
    notice.add(noticeIcon, BorderLayout.WEST);
    notice.add(noticeText, BorderLayout.CENTER);
    body.add(notice);

    dialog.add(body, BorderLayout.CENTER);

    // Footer
    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
    footer.setBackground(Theme.SURFACE);
    footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));

    JButton cancelBtn = new JButton("Hủy");
    cancelBtn.setFont(Theme.label(13));
    cancelBtn.setFocusPainted(false);
    cancelBtn.addActionListener(e -> dialog.dispose());

    JButton confirmBtn = new JButton("✈ Gửi yêu cầu chỉnh sửa");
    confirmBtn.setFont(Theme.label(13));
    confirmBtn.setBackground(new Color(0xD9, 0x77, 0x06));
    confirmBtn.setForeground(Color.WHITE);
    confirmBtn.setFocusPainted(false);
    confirmBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    confirmBtn.addActionListener(e -> {
      String reason = reasonArea.getText().trim();
      if (reason.length() < 10) {
        JOptionPane.showMessageDialog(dialog, "Vui lòng nhập lý do cụ thể (tối thiểu 10 ký tự) để Creator hiểu cách sửa đổi.", "Thông báo", JOptionPane.WARNING_MESSAGE);
        return;
      }
      dialog.dispose();
      onConfirmed.accept(reason);
    });

    footer.add(cancelBtn);
    footer.add(confirmBtn);
    dialog.add(footer, BorderLayout.SOUTH);

    return dialog;
  }

  public static void showReject(Window owner, String comicTitle, Consumer<String> onConfirmed) {
    createRejectDialog(owner, comicTitle, onConfirmed).setVisible(true);
  }

  public static JDialog createRejectDialog(Window owner, String comicTitle, Consumer<String> onConfirmed) {
    JDialog dialog = new JDialog(owner, "Từ chối xuất bản", Dialog.ModalityType.APPLICATION_MODAL);
    dialog.setSize(540, 430);
    dialog.setLocationRelativeTo(owner);
    dialog.setLayout(new BorderLayout(0, 0));
    dialog.getContentPane().setBackground(Theme.SURFACE);

    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setBackground(Theme.SURFACE);
    header.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
        new EmptyBorder(16, 24, 16, 24)));

    JLabel title = new JLabel("Từ chối xuất bản");
    title.setFont(Theme.heading(18));
    title.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel("Đưa ra nhận xét chính xác để Creator hiểu lý do.");
    sub.setFont(Theme.label(12));
    sub.setForeground(Theme.MUTED);

    header.add(title, BorderLayout.NORTH);
    header.add(sub, BorderLayout.CENTER);
    dialog.add(header, BorderLayout.NORTH);

    JPanel body = new JPanel();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setOpaque(false);
    body.setBorder(new EmptyBorder(18, 24, 18, 24));

    if (comicTitle != null && !comicTitle.isBlank()) {
      JLabel comicLbl = new JLabel("Tác phẩm: " + comicTitle);
      comicLbl.setFont(Theme.heading(13));
      comicLbl.setForeground(Theme.TEXT);
      comicLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
      body.add(comicLbl);
      body.add(Box.createVerticalStrut(12));
    }

    JLabel reasonLabel = new JLabel("Lý do từ chối *");
    reasonLabel.setFont(Theme.label(13));
    reasonLabel.setForeground(Theme.TEXT);
    reasonLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    body.add(reasonLabel);
    body.add(Box.createVerticalStrut(6));

    JTextArea reasonArea = new JTextArea(4, 30);
    reasonArea.setFont(Theme.label(13));
    reasonArea.setText("Nội dung chưa đáp ứng tiêu chuẩn xuất bản: Truyện này không thuộc bản quyền phát hành...");
    reasonArea.setLineWrap(true);
    reasonArea.setWrapStyleWord(true);
    reasonArea.setBorder(new EmptyBorder(8, 8, 8, 8));
    JScrollPane reasonScroll = new JScrollPane(reasonArea);
    reasonScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
    reasonScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    body.add(reasonScroll);
    body.add(Box.createVerticalStrut(14));

    // Caution card
    JPanel notice = new JPanel(new BorderLayout(10, 0));
    notice.setBackground(new Color(0xFE, 0xE2, 0xE2));
    notice.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(0xFC, 0xA5, 0xA5), 1, true),
        new EmptyBorder(10, 14, 10, 14)));
    notice.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel noticeIcon = new JLabel(UiIcons.alertCircle(18, new Color(0xDC, 0x26, 0x26)));
    JLabel noticeText = new JLabel("<html><body style='width: 380px; color: #991B1B; font-size: 11px;'>"
        + "Nội dung chưa được công khai. Yêu cầu sẽ bị đóng, trạng thái sẽ chuyển sang Bị từ chối."
        + "</body></html>");
    notice.add(noticeIcon, BorderLayout.WEST);
    notice.add(noticeText, BorderLayout.CENTER);
    body.add(notice);

    dialog.add(body, BorderLayout.CENTER);

    // Footer
    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
    footer.setBackground(Theme.SURFACE);
    footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));

    JButton cancelBtn = new JButton("Hủy");
    cancelBtn.setFont(Theme.label(13));
    cancelBtn.setFocusPainted(false);
    cancelBtn.addActionListener(e -> dialog.dispose());

    JButton confirmBtn = new JButton("✕ Xác nhận từ chối");
    confirmBtn.setFont(Theme.label(13));
    confirmBtn.setBackground(new Color(0xDC, 0x26, 0x26));
    confirmBtn.setForeground(Color.WHITE);
    confirmBtn.setFocusPainted(false);
    confirmBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    confirmBtn.addActionListener(e -> {
      String reason = reasonArea.getText().trim();
      if (reason.length() < 10) {
        JOptionPane.showMessageDialog(dialog, "Vui lòng nhập lý do từ chối cụ thể (tối thiểu 10 ký tự).", "Thông báo", JOptionPane.WARNING_MESSAGE);
        return;
      }
      dialog.dispose();
      onConfirmed.accept(reason);
    });

    footer.add(cancelBtn);
    footer.add(confirmBtn);
    dialog.add(footer, BorderLayout.SOUTH);

    return dialog;
  }
}
