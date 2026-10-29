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
  private JsonNode preferencesByType;

  public NotificationsPanel(ApiClient api) {
    super(new BorderLayout(0, 16));
    this.api = api;
    setOpaque(false);
    setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 24, Theme.CONTENT_PADDING));

    // Header Section V1.0
    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setOpaque(false);
    JLabel eyebrow = Theme.plainLabel("TRUNG TÂM TIN TỨC & THÔNG BÁO");
    eyebrow.setForeground(Theme.PRIMARY);
    eyebrow.setFont(Theme.heading(11));
    JLabel heading = new JLabel("Thông báo");
    heading.setFont(Theme.heading(28));
    heading.setForeground(Theme.TEXT);
    JLabel sub =
        Theme.plainLabel("Cập nhật chương mới, phản hồi cộng đồng và thông báo hệ thống.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(heading);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.WEST);

    // Summary badge on top right
    summary.setFont(Theme.brand(12));
    summary.setForeground(Theme.PRIMARY);
    summary.setBackground(Theme.ACTION_TINT);
    summary.setOpaque(true);
    summary.setBorder(new EmptyBorder(6, 12, 6, 12));
    header.add(summary, BorderLayout.EAST);
    add(header, BorderLayout.NORTH);

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
    inbox.add(table, BorderLayout.CENTER);

    // Inbox Bottom Toolbar Card
    JPanel actionCard = new JPanel(new BorderLayout());
    actionCard.setOpaque(true);
    actionCard.setBackground(Theme.SURFACE);
    actionCard.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(10, 14, 10, 14)));

    unreadOnly.setFont(Theme.label(13));
    unreadOnly.addActionListener(e -> reload());

    JButton refresh = button("Làm mới", UiIcons.refresh(14, Theme.TEXT), this::reload);
    JButton markRead = button("Đánh dấu đã đọc", UiIcons.check(14, Theme.SUCCESS), () -> mark(true));
    JButton markUnread = button("Đánh dấu chưa đọc", () -> mark(false));
    PrimaryButton readAll = new PrimaryButton("Đọc tất cả");
    readAll.setIcon(UiIcons.check(14, Color.WHITE));
    readAll.addActionListener(e -> readAll());

    JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    leftActions.setOpaque(false);
    leftActions.add(unreadOnly);
    leftActions.add(refresh);
    leftActions.add(markRead);
    leftActions.add(markUnread);
    leftActions.add(readAll);

    actionCard.add(leftActions, BorderLayout.WEST);
    inbox.add(actionCard, BorderLayout.SOUTH);

    // Preferences View Card
    JPanel preferenceCard = new JPanel(new BorderLayout());
    preferenceCard.setOpaque(true);
    preferenceCard.setBackground(Theme.SURFACE);
    preferenceCard.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(24, 28, 24, 28)));

    JPanel prefForm = new JPanel(new GridLayout(0, 1, 0, 14));
    prefForm.setOpaque(false);
    prefForm.setMaximumSize(new Dimension(500, 300));

    JLabel prefDesc =
        new JLabel("Bật/tắt thông báo trong ứng dụng và email theo từng loại sự kiện.");
    prefDesc.setFont(Theme.heading(14));
    prefDesc.setForeground(Theme.TEXT);

    JLabel typeLabel = Theme.plainLabel("Loại sự kiện:");
    typeLabel.setFont(Theme.heading(12));
    typeLabel.setForeground(Theme.MUTED);

    inApp.setFont(Theme.label(13));
    email.setFont(Theme.label(13));

    PrimaryButton savePref = new PrimaryButton("Lưu thiết lập");
    savePref.setIcon(UiIcons.check(14, Color.WHITE));
    savePref.addActionListener(e -> savePreference());

    JLabel note =
        Theme.plainLabel("Email chỉ gửi khi tài khoản đã xác minh email và bật nhận thông báo.");
    note.setFont(Theme.label(12));
    note.setForeground(Theme.MUTED);

    prefForm.add(prefDesc);
    prefForm.add(typeLabel);
    prefForm.add(type);
    prefForm.add(inApp);
    prefForm.add(email);
    prefForm.add(row(savePref));
    prefForm.add(note);

    JPanel prefWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    prefWrapper.setOpaque(false);
    prefWrapper.add(prefForm);
    preferenceCard.add(prefWrapper, BorderLayout.CENTER);

    // JTabbedPane
    JTabbedPane tabs = new JTabbedPane();
    tabs.putClientProperty("JTabbedPane.tabType", "card");
    tabs.setFont(Theme.brand(13));
    tabs.addTab("Hộp thông báo", UiIcons.bell(15, Theme.MUTED), inbox);
    tabs.addTab("Thiết lập", UiIcons.settings(15, Theme.MUTED), preferenceCard);
    tabs.addChangeListener(
        e -> {
          if (tabs.getSelectedIndex() == 1) loadPreferences();
        });
    type.addActionListener(e -> showSelectedPreference());

    add(tabs, BorderLayout.CENTER);

    // Footer Info
    JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    footer.setOpaque(false);
    message.setFont(Theme.label(12));
    message.setForeground(Theme.MUTED);
    footer.add(message);
    add(footer, BorderLayout.SOUTH);
  }

  private JPanel row(Component... components) {
    JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 7, 4));
    row.setOpaque(false);
    for (Component component : components) row.add(component);
    return row;
  }

  private JButton button(String text, Runnable action) {
    JButton button = new JButton(text);
    button.setFont(Theme.label(13));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

  private JButton button(String text, Icon icon, Runnable action) {
    JButton btn = button(text, action);
    btn.setIcon(icon);
    return btn;
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
                              + "  ·  Tổng: "
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
