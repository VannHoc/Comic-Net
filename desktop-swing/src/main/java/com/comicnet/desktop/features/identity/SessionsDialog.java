package com.comicnet.desktop.features.identity;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

final class SessionsDialog extends JDialog {
  SessionsDialog(Component owner, ApiClient api) {
    super(
        SwingUtilities.getWindowAncestor(owner),
        "Thiết bị và phiên đăng nhập",
        Dialog.ModalityType.APPLICATION_MODAL);
    var tasks = new UiTasks();
    var message = Theme.plainLabel("Chỉ hiển thị phiên còn hiệu lực của bạn.");
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
                  row.path("current").asBoolean() ? "Có" : ""
                });
    JPanel body = new JPanel(new BorderLayout(8, 8));
    body.add(table);
    body.add(message, BorderLayout.NORTH);
    JButton revoke = new JButton("Thu hồi phiên"),
        others = new JButton("Đăng xuất các thiết bị khác");
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
    JPanel buttons = new JPanel(new FlowLayout());
    buttons.add(revoke);
    buttons.add(others);
    body.add(buttons, BorderLayout.SOUTH);
    add(body);
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
    setSize(950, 500);
    setLocationRelativeTo(owner);
    table.first();
  }
}
