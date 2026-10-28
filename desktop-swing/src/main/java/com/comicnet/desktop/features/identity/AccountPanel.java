package com.comicnet.desktop.features.identity;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class AccountPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final Runnable sessionChanged;
  private final UiTasks tasks = new UiTasks();
  private final JLabel message = new JLabel(" ");
  private final JLabel title = new JLabel("Tài khoản của bạn");
  private final JPanel form = new JPanel();
  private final JPanel centerHost = new JPanel(new BorderLayout());
  private JScrollPane profileScroll;
  private final JComboBox<String> mode =
      new JComboBox<>(
          new String[] {
            "Đăng nhập",
            "Đăng ký",
            "Xác minh email",
            "Gửi lại mã xác minh",
            "Quên mật khẩu",
            "Đặt lại mật khẩu"
          });
  private final JTextField email = new JTextField(),
      name = new JTextField(),
      accountName = new JTextField(),
      token = new JTextField();
  private final JPasswordField password = new JPasswordField();
  private String profileEtag;

  public AccountPanel(ApiClient api, Runnable sessionChanged) {
    super(new BorderLayout(0, 24));
    this.api = api;
    this.sessionChanged = sessionChanged;
    setOpaque(false);
    setBorder(new EmptyBorder(32, 40, 32, 40));
    title.setFont(Theme.heading(28));
    add(title, BorderLayout.NORTH);
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.setBackground(Theme.SURFACE);
    form.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.ELEVATED), new EmptyBorder(28, 32, 28, 32)));
    form.setPreferredSize(new Dimension(920, 640));
    form.setMaximumSize(new Dimension(960, Integer.MAX_VALUE));
    JPanel centered = new JPanel(new GridBagLayout());
    centered.setOpaque(false);
    var constraints = new GridBagConstraints();
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.NORTH;
    constraints.weighty = 1;
    centered.add(form, constraints);
    profileScroll = new JScrollPane(centered);
    centerHost.setOpaque(false);
    centerHost.add(profileScroll, BorderLayout.CENTER);
    add(centerHost, BorderLayout.CENTER);
    message.setForeground(Theme.TEXT);
    add(message, BorderLayout.SOUTH);
    mode.addActionListener(e -> render());
    addHierarchyListener(
        e -> {
          if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
              && isShowing()) render();
        });
    render();
  }

  private void field(String label, JComponent input) {
    JLabel caption = new JLabel(label);
    caption.setAlignmentX(LEFT_ALIGNMENT);
    caption.setLabelFor(input);
    form.add(caption);
    form.add(Box.createVerticalStrut(6));
    input.setAlignmentX(LEFT_ALIGNMENT);
    input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
    input.setPreferredSize(new Dimension(380, 38));
    form.add(input);
    form.add(Box.createVerticalStrut(18));
    input.getAccessibleContext().setAccessibleName(label);
  }

  private void render() {
    tasks.close();
    form.removeAll();
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.ELEVATED), new EmptyBorder(28, 32, 28, 32)));
    title.setVisible(api.signedIn());
    setBorder(api.signedIn() ? new EmptyBorder(32, 40, 32, 40)
        : new EmptyBorder(12, 20, 8, 20));
    password.setText("");
    token.setText("");
    message.setText(" ");
    if (api.signedIn()) {
      centerHost.removeAll();
      centerHost.add(profileScroll, BorderLayout.CENTER);
      JLabel state = new JLabel("Đang tải hồ sơ…");
      form.add(state);
      form.add(logoutButton());
      tasks.run(
          this,
          message,
          api.get("/me"),
          reply -> {
            form.removeAll();
            profileEtag = reply.etag();
            name.setText(reply.body().path("displayName").asText());
            accountName.setText(reply.body().path("username").asText(""));
            String recoveryEmail = reply.body().path("email").asText("");
            form.add(Theme.plainLabel(recoveryEmail.isBlank()
                ? "Chưa có email khôi phục" : recoveryEmail));
            form.add(Box.createVerticalStrut(20));
            field("Tài khoản", accountName);
            JButton saveUsername = new JButton("Lưu tài khoản");
            saveUsername.addActionListener(e -> tasks.run(this, message,
                api.request("PATCH", "/me/username",
                    Map.of("username", accountName.getText().strip()), profileEtag),
                updated -> { profileEtag = updated.etag(); message.setText("Đã lưu tài khoản."); }));
            form.add(saveUsername);
            form.add(Box.createVerticalStrut(16));
            field("Tên hiển thị", name);
            form.add(new JLabel("Vai trò: " + reply.body().path("roles").toString()));
            form.add(Box.createVerticalStrut(20));
            JButton save = new JButton("Lưu hồ sơ");
            save.addActionListener(
                e ->
                    tasks.run(
                        this,
                        message,
                        api.request(
                            "PATCH",
                            "/me",
                            Map.of("displayName", name.getText().strip()),
                            profileEtag),
                        updated -> {
                          profileEtag = updated.etag();
                          message.setText("Đã lưu hồ sơ.");
                          sessionChanged.run();
                        }));
            form.add(save);
            JButton sessions = new JButton("Thiết bị và phiên đăng nhập");
            sessions.addActionListener(e -> new SessionsDialog(this, api).setVisible(true));
            form.add(Box.createVerticalStrut(12));
            form.add(sessions);
            form.add(Box.createVerticalStrut(12));
            form.add(logoutButton());
            refreshLayout();
          });
    } else {
      JPanel authHost = new JPanel(new BorderLayout());
      authHost.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
      authHost.add(new AuthScreens(api, tasks, () -> { sessionChanged.run(); render(); }), BorderLayout.CENTER);
      centerHost.removeAll();
      centerHost.add(authHost, BorderLayout.CENTER);
    }
    refreshLayout();
  }

  private void refreshLayout() {
    centerHost.revalidate();
    centerHost.repaint();
    form.revalidate();
    form.repaint();
  }

  private JButton logoutButton() {
    JButton logout = new JButton("Đăng xuất");
    logout.addActionListener(
        e ->
            tasks.run(
                this,
                message,
                api.logout(),
                ignored -> {
                  sessionChanged.run();
                  render();
                }));
    return logout;
  }

  private void submit() {
    if (tasks.busy()) return;
    int action = mode.getSelectedIndex();
    char[] secret = password.getPassword();
    String rawPassword = new String(secret);
    Arrays.fill(secret, '\0');
    password.setText("");
    if ((action == 0 && (rawPassword.length() < 3 || rawPassword.length() > 128))
        || ((action == 1 || action == 5)
            && (rawPassword.length() < 3 || rawPassword.length() > 12))) {
      message.setText("Mật khẩu cần từ 3 đến 12 ký tự.");
      return;
    }
    var request =
        switch (action) {
          case 0 -> api.login(email.getText().strip(), rawPassword);
          case 1 ->
              api.request(
                  "POST",
                  "/auth/register",
                  Map.of(
                      "email",
                      email.getText().strip(),
                      "password",
                      rawPassword,
                      "displayName",
                      name.getText().strip()),
                  null);
          case 2 ->
              api.request(
                  "POST", "/auth/verify-email", Map.of("token", token.getText().strip()), null);
          case 3 ->
              api.request(
                  "POST",
                  "/auth/resend-verification",
                  Map.of("email", email.getText().strip()),
                  null);
          case 4 ->
              api.request(
                  "POST", "/auth/forgot-password", Map.of("email", email.getText().strip()), null);
          case 5 ->
              api.request(
                  "POST",
                  "/auth/reset-password",
                  Map.of("token", token.getText().strip(), "password", rawPassword),
                  null);
          default -> throw new IllegalStateException();
        };
    tasks.run(
        this,
        message,
        request,
        reply -> {
          token.setText("");
          if (action == 0) {
            sessionChanged.run();
            render();
          } else if (action == 1 || action == 3 || action == 4)
            message.setText("Nếu thông tin hợp lệ, hướng dẫn đã được gửi tới email của bạn.");
          else {
            mode.setSelectedIndex(0);
            message.setText("Thao tác thành công. Hãy đăng nhập.");
          }
        });
  }

  @Override
  public void close() {
    tasks.close();
    password.setText("");
  }

  public void sessionChanged() {
    render();
  }
}
