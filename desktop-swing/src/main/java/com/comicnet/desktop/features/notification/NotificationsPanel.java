package com.comicnet.desktop.features.notification;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import tools.jackson.databind.JsonNode;

/** In-app notification inbox and per-type preferences (M13). */
public final class NotificationsPanel extends JPanel implements Workspace {
  private static final List<String> TYPES =
      List.of("NEW_CHAPTER", "COMMENT_REPLY", "COMMENT_REACTION", "SYSTEM_ANNOUNCEMENT");

  private final ApiClient api;
  private final JLabel message = Theme.plainLabel("Đăng nhập để xem thông báo."),
      summary = Theme.plainLabel("Chưa đọc: —");
  private final JCheckBox unreadOnly = new JCheckBox("Chỉ chưa đọc");
  private final RemoteTable table;
  private final JComboBox<String> type =
      new JComboBox<>(new String[] {"Chương mới", "Phản hồi", "Cảm xúc", "Thông báo hệ thống"});
  private final JCheckBox inApp = new JCheckBox("Trong ứng dụng", true),
      email = new JCheckBox("Email");
  private final UiTasks actions = new UiTasks(), preferences = new UiTasks();

  public NotificationsPanel(ApiClient api) {
    super(new BorderLayout(0, 14));
    this.api = api;
    setOpaque(false);
    setBorder(new EmptyBorder(26, 30, 20, 30));
    JLabel heading = new JLabel("Thông báo");
    heading.setFont(Theme.heading(28));
    JPanel top = new JPanel(new BorderLayout());
    top.setOpaque(false);
    top.add(heading, BorderLayout.WEST);
    top.add(summary, BorderLayout.EAST);
    add(top, BorderLayout.NORTH);
    table =
        new RemoteTable(
            api,
            new String[] {"Loại", "Tiêu đề", "Nội dung", "Trạng thái"},
            p ->
                "/notifications?page="
                    + p
                    + "&size=20&unreadOnly="
                    + unreadOnly.isSelected(),
            v ->
                new Object[] {
                  label(v.path("type").asText()),
                  v.path("title").asText(),
                  v.path("body").asText(),
                  v.path("read").asBoolean() ? "Đã đọc" : "Chưa đọc"
                });
    table.onLoaded(this::refreshSummary);
    JPanel inbox = new JPanel(new BorderLayout(0, 10));
    inbox.setOpaque(false);
    inbox.add(table);
    inbox.add(
        row(
            unreadOnly,
            button("Làm mới", this::reload),
            button("Đánh dấu đã đọc", () -> mark(true)),
            button("Đánh dấu chưa đọc", () -> mark(false)),
            button("Đọc tất cả", this::readAll)),
        BorderLayout.SOUTH);
    JPanel preferenceView = new JPanel(new GridLayout(0, 1, 0, 10));
    preferenceView.setOpaque(false);
    preferenceView.setBorder(new EmptyBorder(18, 18, 18, 18));
    preferenceView.add(
        new JLabel("Bật/tắt thông báo trong ứng dụng và email theo từng loại sự kiện."));
    preferenceView.add(type);
    preferenceView.add(inApp);
    preferenceView.add(email);
    preferenceView.add(row(button("Lưu thiết lập", this::savePreference)));
    preferenceView.add(new JLabel("Email chỉ gửi khi tài khoản đã xác minh và bật thông báo."));
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Hộp thông báo", inbox);
    tabs.addTab("Thiết lập", preferenceView);
    tabs.addChangeListener(
        e -> {
          if (tabs.getSelectedIndex() == 1) loadPreferences();
        });
    type.addActionListener(e -> showSelectedPreference());
    add(tabs);
    add(message, BorderLayout.SOUTH);
  }

  private JPanel row(Component... components) {
    JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 7, 4));
    row.setOpaque(false);
    for (Component component : components) row.add(component);
    return row;
  }

  private JButton button(String text, Runnable action) {
    JButton button = new JButton(text);
    button.addActionListener(
        e -> {
          if (!api.signedIn()) {
            message.setText("Đăng nhập để sử dụng thông báo.");
            return;
          }
          if (!actions.busy()) action.run();
        });
    return button;
  }

  private String label(String code) {
    int index = TYPES.indexOf(code);
    return index < 0 ? "Khác" : String.valueOf(type.getItemAt(index));
  }

  private void reload() {
    table.first();
    refreshSummary(null);
  }

  private void refreshSummary(JsonNode ignored) {
    if (!api.signedIn()) return;
    api.get("/notifications/summary")
        .whenComplete(
            (reply, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error != null || reply == null) {
                        summary.setText("Chưa đọc: —");
                        return;
                      }
                      summary.setText(
                          "Chưa đọc: "
                              + reply.body().path("unread").asLong()
                              + " · Tổng: "
                              + reply.body().path("total").asLong());
                    }));
  }

  private void mark(boolean read) {
    JsonNode selected = table.selected();
    if (selected == null) {
      message.setText("Chọn một thông báo trước.");
      return;
    }
    String id = selected.path("id").asText();
    actions.run(
        this,
        message,
        api.request(read ? "PUT" : "DELETE", "/notifications/" + id + "/read", null, null),
        reply -> {
          table.reload();
          refreshSummary(null);
          message.setText(read ? "Đã đánh dấu đã đọc." : "Đã đánh dấu chưa đọc.");
        });
  }

  private void readAll() {
    actions.run(
        this,
        message,
        api.request("POST", "/notifications/read-all", Map.of(), null),
        reply -> {
          table.reload();
          refreshSummary(null);
          message.setText("Đã đọc tất cả thông báo.");
        });
  }

  private void loadPreferences() {
    if (!api.signedIn()) return;
    preferences.run(
        this,
        message,
        api.get("/notifications/preferences"),
        reply -> {
          preferencesByType = reply.body();
          showSelectedPreference();
        });
  }

  private JsonNode preferencesByType;

  private void showSelectedPreference() {
    if (preferencesByType == null) return;
    String code = TYPES.get(type.getSelectedIndex());
    for (JsonNode item : preferencesByType) {
      if (item.path("type").asText().equals(code)) {
        inApp.setSelected(item.path("inApp").asBoolean());
        email.setSelected(item.path("email").asBoolean());
        return;
      }
    }
    inApp.setSelected(true);
    email.setSelected(false);
  }

  private void savePreference() {
    String code = TYPES.get(type.getSelectedIndex());
    preferences.run(
        this,
        message,
        api.request(
            "PUT",
            "/notifications/preferences/" + code,
            Map.of("inApp", inApp.isSelected(), "email", email.isSelected()),
            null),
        reply -> {
          message.setText("Đã lưu thiết lập thông báo.");
          loadPreferences();
        });
  }

  @Override
  public void onShown() {
    if (api.signedIn()) {
      table.first();
      refreshSummary(null);
      message.setText(" ");
    } else message.setText("Đăng nhập để xem thông báo.");
  }

  @Override
  public void sessionChanged() {
    actions.close();
    preferences.close();
    table.clear();
    preferencesByType = null;
    summary.setText("Chưa đọc: —");
    message.setText("Phiên tài khoản đã đổi. Tải lại để xem thông báo.");
  }

  @Override
  public void close() {
    sessionChanged();
    table.close();
  }
}
