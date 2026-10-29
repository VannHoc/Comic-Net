package com.comicnet.desktop.features.identity;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class SessionsDialog extends JDialog {
  SessionsDialog(Component owner, ApiClient api) {
    super(
        SwingUtilities.getWindowAncestor(owner),
        "Thiết bị và phiên đăng nhập",
        Dialog.ModalityType.APPLICATION_MODAL);
    var tasks = new UiTasks();
    var message = Theme.plainLabel("Chỉ hiển thị các phiên còn hiệu lực của tài khoản.");
    message.setFont(Theme.label(12));
    message.setForeground(Theme.MUTED);

    var table =
        new RemoteTable(
            api,
            new String[] {"Thiết bị", "Tạo lúc", "Hoạt động gần nhất", "Hết hạn", "Hiện tại"},
            page -> "/me/sessions?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("deviceLabel").asText(),
                  row.path("createdAt").asText(),
                  row.path("lastSeenAt").asText(),
                  row.path("expiresAt").asText(),
                  row.path("current").asBoolean() ? "Đang dùng" : "—"
                });

    JPanel body = new JPanel(new BorderLayout(0, 12));
    body.setBackground(Theme.BACKGROUND);
    body.setBorder(new EmptyBorder(16, 20, 16, 20));

    body.add(message, BorderLayout.NORTH);
    body.add(table, BorderLayout.CENTER);

    JPanel actionCard = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    actionCard.setOpaque(true);
    actionCard.setBackground(Theme.SURFACE);
    actionCard.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(10, 14, 10, 14)));

    JButton revoke = new JButton("Thu hồi phiên đã chọn");
    revoke.setFont(Theme.label(13));
    revoke.setIcon(UiIcons.trash(14, Theme.DANGER));
    revoke.setForeground(Theme.DANGER);

    PrimaryButton others = new PrimaryButton("Đăng xuất các thiết bị khác");
    others.setIcon(UiIcons.logout(14, Color.WHITE));

    revoke.addActionListener(
        e -> {
          var selected = table.selected();
          if (selected == null) return;
          boolean current = selected.path("current").asBoolean();
          if (JOptionPane.showConfirmDialog(
                  this,
                  current
                      ? "Đăng xuất phiên đang dùng?"
                      : "Thu hồi quyền truy cập của thiết bị này?",
                  "Xác nhận",
                  JOptionPane.OK_CANCEL_OPTION)
              != JOptionPane.OK_OPTION) return;
          if (current) tasks.run(body, message, api.logout(), result -> dispose());
          else
            tasks.run(
                body,
                message,
                api.request("DELETE", "/me/sessions/" + selected.path("id").asText(), null, null),
                result -> table.reload());
        });

    others.addActionListener(
        e -> {
          if (JOptionPane.showConfirmDialog(
                  this,
                  "Đăng xuất tất cả phiên khác, giữ phiên hiện tại?",
                  "Xác nhận",
                  JOptionPane.OK_CANCEL_OPTION)
              == JOptionPane.OK_OPTION)
            tasks.run(
                body,
                message,
                api.request("POST", "/me/sessions/revoke-others", null, null),
                result -> table.reload());
        });

    actionCard.add(revoke);
    actionCard.add(others);
    body.add(actionCard, BorderLayout.SOUTH);

    setContentPane(body);
    AutoCloseable subscription =
        api.onSessionChanged(() -> SwingUtilities.invokeLater(this::dispose));
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            tasks.close();
            table.close();
            try {
              subscription.close();
            } catch (Exception ignored) {
            }
          }
        });
    getRootPane()
        .registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(950, 560);
    setMinimumSize(new Dimension(800, 480));
    setLocationRelativeTo(owner);
    table.first();
  }
}
