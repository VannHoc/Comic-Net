package com.comicnet.desktop.features.admin;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

/** Administrator provisioning screens share the same REST contracts as future clients. */
public final class AdministrationPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final UiTasks tasks = new UiTasks();
  private final JLabel message = new JLabel("Chọn Tải lại để kiểm tra quyền quản trị.");
  private final DefaultTableModel users =
      new DefaultTableModel(
          new Object[] {"ID", "Tên hiển thị", "Email", "Trạng thái", "Vai trò"}, 0) {
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final DefaultTableModel teams =
      new DefaultTableModel(new Object[] {"ID", "Nhóm", "Slug", "Chủ nhóm", "Trạng thái"}, 0) {
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final JTable userTable = new JTable(users), teamTable = new JTable(teams);
  private final JTextField query = new JTextField(),
      teamName = new JTextField(),
      slug = new JTextField();
  private final JComboBox<String> status = new JComboBox<>(new String[] {"ACTIVE", "SUSPENDED"}),
      memberStatus = new JComboBox<>(new String[] {"ACTIVE", "REMOVED"});
  private final Map<String, JCheckBox> systemRoles = new LinkedHashMap<>(),
      teamRoles = new LinkedHashMap<>();
  private final JComboBox<Option> owner = new JComboBox<>(),
      memberUser = new JComboBox<>(),
      memberTeam = new JComboBox<>();
  private final JLabel selectedUser = new JLabel("Chọn một tài khoản"),
      membershipState = new JLabel("Chọn nhóm và tài khoản, sau đó tải membership.");
  private long userId;
  private String userEtag, memberEtag;
  private boolean newMember;
  private int userPage, teamPage;
  private String createKey = UUID.randomUUID().toString(), createPayload = "";

  private record Option(String id, String label) {
    public String toString() {
      return label + " (#" + id + ")";
    }
  }

  public AdministrationPanel(ApiClient api) {
    super(new BorderLayout(0, 20));
    this.api = api;
    setOpaque(false);
    setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 24, Theme.CONTENT_PADDING));
    JPanel heading = new JPanel(new BorderLayout(0, 4));
    heading.setOpaque(false);
    JLabel eyebrow = Theme.plainLabel("QUẢN TRỊ NỀN TẢNG");
    eyebrow.setForeground(Theme.PRIMARY);
    eyebrow.setFont(Theme.heading(11));
    JLabel title = new JLabel("Quản trị hệ thống");
    title.setFont(Theme.heading(28));
    title.setForeground(Theme.TEXT);
    JLabel sub = Theme.plainLabel("Phân quyền tài khoản, quản lý nhóm dịch và thiết lập membership.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(title);
    titleBox.add(sub);
    heading.add(titleBox, BorderLayout.WEST);

    JButton reloadBtn = button("Tải lại", () -> {
      if (api.signedIn()) loadUsers();
      else message.setText("Đăng nhập bằng tài khoản quản trị viên.");
    });
    reloadBtn.setIcon(UiIcons.refresh(14, Theme.TEXT));
    heading.add(reloadBtn, BorderLayout.EAST);
    add(heading, BorderLayout.NORTH);

    JTabbedPane tabs = new JTabbedPane();
    tabs.putClientProperty("JTabbedPane.tabType", "card");
    tabs.setFont(Theme.brand(13));
    tabs.addTab("Tài khoản", UiIcons.user(14, Theme.MUTED), userView());
    tabs.addTab("Nhóm dịch", UiIcons.shield(14, Theme.MUTED), teamView());
    tabs.addTab("Membership", UiIcons.settings(14, Theme.MUTED), memberView());
    add(message, BorderLayout.SOUTH);
    tabs.addChangeListener(
        e -> {
          if (tabs.getSelectedIndex() == 1) loadTeams();
        });
    userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    userTable.setRowHeight(34);
    teamTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    teamTable.setRowHeight(34);
    Theme.plainTable(userTable);
    Theme.plainTable(teamTable);
    Theme.plainCombo(owner);
    Theme.plainCombo(memberUser);
    Theme.plainCombo(memberTeam);
    selectedUser.putClientProperty("html.disable", Boolean.TRUE);
    userTable
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting() && userTable.getSelectedRow() >= 0)
                loadUser(users.getValueAt(userTable.getSelectedRow(), 0).toString());
            });
    memberUser.addActionListener(e -> invalidateMembership());
    memberTeam.addActionListener(e -> invalidateMembership());
  }

  @Override
  public void onShown() {
    if (api.signedIn()) loadUsers();
  }

  private JButton button(String title, Runnable action) {
    JButton button = new JButton(title);
    button.addActionListener(
        e -> {
          if (tasks.busy()) {
            message.setText("Đợi thao tác hiện tại hoàn tất.");
            return;
          }
          action.run();
        });
    return button;
  }

  private JPanel row(Component... components) {
    JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 8));
    row.setOpaque(false);
    for (Component component : components) row.add(component);
    return row;
  }

  private void fields(JPanel panel, String label, JComponent field) {
    JLabel text = new JLabel(label);
    text.setLabelFor(field);
    field.getAccessibleContext().setAccessibleName(label);
    panel.add(text);
    panel.add(field);
  }

  private JPanel grid() {
    JPanel panel = new FormPanel();
    panel.setBorder(new EmptyBorder(16, 16, 16, 16));
    panel.setBackground(Color.WHITE);
    return panel;
  }

  private static final class FormPanel extends JPanel {
    private int cell;

    private FormPanel() {
      super(new GridBagLayout());
    }

    @Override
    public Component add(Component component) {
      var constraints = new GridBagConstraints();
      constraints.gridx = cell % 2;
      constraints.gridy = cell / 2;
      constraints.weightx = cell % 2 == 0 ? .35 : .65;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.anchor = GridBagConstraints.NORTHWEST;
      constraints.insets = new Insets(6, 6, 6, 6);
      cell++;
      super.add(component, constraints);
      return component;
    }
  }

  private JPanel userView() {
    JPanel view = new JPanel(new BorderLayout(0, 12));
    query.setColumns(24);
    query.addActionListener(
        e -> {
          userPage = 0;
          loadUsers();
        });
    view.add(
        row(
            new JLabel("Tìm email/tên"),
            query,
            button(
                "Tìm",
                () -> {
                  userPage = 0;
                  loadUsers();
                }),
            button(
                "Trước",
                () -> {
                  userPage = Math.max(0, userPage - 1);
                  loadUsers();
                }),
            button(
                "Sau",
                () -> {
                  userPage++;
                  loadUsers();
                })),
        BorderLayout.NORTH);
    JPanel edit = grid();
    edit.add(selectedUser);
    edit.add(new JLabel("Thay đổi quyền và trạng thái tài khoản"));
    fields(edit, "Trạng thái", status);
    JPanel checks = new JPanel(new GridLayout(0, 2, 10, 6));
    for (String role : List.of("READER", "CREATOR", "MODERATOR", "ADMINISTRATOR")) {
      JCheckBox check = new JCheckBox(role);
      systemRoles.put(role, check);
      checks.add(check);
    }
    fields(edit, "Vai trò hệ thống", checks);
    edit.add(button("Lưu trạng thái", () -> saveUser(false)));
    edit.add(button("Lưu vai trò", () -> saveUser(true)));
    JSplitPane split =
        new JSplitPane(
            JSplitPane.VERTICAL_SPLIT, new JScrollPane(userTable), new JScrollPane(edit));
    split.setResizeWeight(.62);
    view.add(split);
    return view;
  }

  private JPanel teamView() {
    JPanel view = new JPanel(new BorderLayout(0, 12));
    view.add(
        row(
            button("Tải nhóm", this::loadTeams),
            button(
                "Trước",
                () -> {
                  teamPage = Math.max(0, teamPage - 1);
                  loadTeams();
                }),
            button(
                "Sau",
                () -> {
                  teamPage++;
                  loadTeams();
                })),
        BorderLayout.NORTH);
    JPanel edit = grid();
    fields(edit, "Tên nhóm", teamName);
    fields(edit, "Slug", slug);
    fields(edit, "Chủ nhóm (đã có CREATOR)", owner);
    edit.add(new JLabel("Tài khoản được nạp từ trang tìm kiếm User."));
    edit.add(button("Tạo nhóm", this::createTeam));
    JSplitPane split =
        new JSplitPane(
            JSplitPane.VERTICAL_SPLIT, new JScrollPane(teamTable), new JScrollPane(edit));
    split.setResizeWeight(.65);
    view.add(split);
    return view;
  }

  private JPanel memberView() {
    JPanel view = new JPanel(new BorderLayout(0, 12));
    JPanel edit = grid();
    fields(edit, "Nhóm", memberTeam);
    fields(edit, "Tài khoản", memberUser);
    fields(edit, "Trạng thái", memberStatus);
    JPanel checks = new JPanel(new GridLayout(0, 2, 10, 6));
    for (String role :
        List.of("OWNER", "CREATOR", "TRANSLATOR", "EDITOR", "PROOFREADER", "UPLOADER")) {
      JCheckBox check = new JCheckBox(role);
      teamRoles.put(role, check);
      checks.add(check);
    }
    fields(edit, "Vai trò trong nhóm", checks);
    edit.add(button("Tải thành viên", this::loadMembership));
    edit.add(button("Lưu thành viên", this::saveMembership));
    edit.add(membershipState);
    view.add(new JScrollPane(edit), BorderLayout.CENTER);
    return view;
  }

  private void loadUsers() {
    if (tasks.busy()) return;
    tasks.run(
        this,
        message,
        api.get(
            "/admin/users?page="
                + userPage
                + "&size=50&q="
                + URLEncoder.encode(query.getText().strip(), StandardCharsets.UTF_8)),
        reply -> {
          users.setRowCount(0);
          owner.removeAllItems();
          memberUser.removeAllItems();
          userId = 0;
          userEtag = null;
          for (JsonNode user : reply.body().path("items")) {
            users.addRow(
                new Object[] {
                  user.path("id").asText(),
                  user.path("displayName").asText(),
                  user.path("email").asText(),
                  user.path("status").asText(),
                  user.path("roles").toString()
                });
            Option option =
                new Option(
                    user.path("id").asText(),
                    user.path("displayName").asText() + " · " + user.path("email").asText());
            memberUser.addItem(option);
            boolean creator = false;
            for (JsonNode role : user.path("roles"))
              if (role.asText().equals("CREATOR")) creator = true;
            if (creator
                && user.path("status").asText().equals("ACTIVE")
                && user.path("emailVerified").asBoolean()) owner.addItem(option);
          }
          message.setText(
              users.getRowCount() == 0
                  ? "Không có tài khoản phù hợp."
                  : "Trang "
                      + (userPage + 1)
                      + " · "
                      + reply.body().path("page").path("totalElements").asLong()
                      + " tài khoản");
        });
  }

  private void loadUser(String id) {
    if (tasks.busy()) return;
    tasks.run(
        this,
        message,
        api.get("/admin/users/" + id),
        reply -> {
          userId = Long.parseLong(id);
          userEtag = reply.etag();
          selectedUser.setText(reply.body().path("displayName").asText() + " (#" + id + ")");
          status.setSelectedItem(reply.body().path("status").asText());
          systemRoles.values().forEach(check -> check.setSelected(false));
          for (JsonNode role : reply.body().path("roles")) {
            var check = systemRoles.get(role.asText());
            if (check != null) check.setSelected(true);
          }
        });
  }

  private void saveUser(boolean roles) {
    if (userId == 0 || userEtag == null) {
      message.setText("Chọn tài khoản và tải dữ liệu trước khi sửa.");
      return;
    }
    Object value =
        roles
            ? Map.of("roles", checked(systemRoles))
            : Map.of("status", status.getSelectedItem());
    tasks.run(
        this,
        message,
        api.request(
            "PUT", "/admin/users/" + userId + (roles ? "/roles" : "/status"), value, userEtag),
        reply -> {
          userEtag = reply.etag();
          message.setText("Đã lưu. Tải lại danh sách để xem dữ liệu mới.");
        });
  }

  private List<String> checked(Map<String, JCheckBox> checks) {
    return checks.entrySet().stream()
        .filter(e -> e.getValue().isSelected())
        .map(Map.Entry::getKey)
        .toList();
  }

  private void loadTeams() {
    if (tasks.busy()) return;
    tasks.run(
        this,
        message,
        api.get("/admin/teams?page=" + teamPage + "&size=50"),
        reply -> {
          teams.setRowCount(0);
          memberTeam.removeAllItems();
          for (JsonNode team : reply.body().path("items")) {
            teams.addRow(
                new Object[] {
                  team.path("id").asText(),
                  team.path("name").asText(),
                  team.path("slug").asText(),
                  team.path("ownerUserId").asText(),
                  team.path("status").asText()
                });
            memberTeam.addItem(new Option(team.path("id").asText(), team.path("name").asText()));
          }
          message.setText(teams.getRowCount() == 0 ? "Chưa có nhóm." : "Trang " + (teamPage + 1));
        });
  }

  private void createTeam() {
    Option selected = (Option) owner.getSelectedItem();
    if (selected == null) {
      message.setText("Cấp CREATOR cho chủ nhóm, sau đó tải lại danh sách User.");
      return;
    }
    var body =
        Map.of(
            "name",
            teamName.getText().strip(),
            "slug",
            slug.getText().strip(),
            "ownerUserId",
            selected.id());
    String fingerprint = body.toString();
    if (!fingerprint.equals(createPayload)) {
      createPayload = fingerprint;
      createKey = UUID.randomUUID().toString();
    }
    tasks.run(
        this,
        message,
        api.create("/admin/teams", body, createKey),
        reply -> {
          teamName.setText("");
          slug.setText("");
          createKey = UUID.randomUUID().toString();
          message.setText(
              "Đã tạo nhóm #"
                  + reply.body().path("id").asText()
                  + " cùng membership của chủ nhóm.");
        });
  }

  private void invalidateMembership() {
    tasks.close();
    memberEtag = null;
    newMember = false;
    membershipState.setText("Tải membership trước khi lưu.");
  }

  private String memberPath() {
    Option team = (Option) memberTeam.getSelectedItem(),
        user = (Option) memberUser.getSelectedItem();
    return team == null || user == null
        ? null
        : "/admin/teams/" + team.id() + "/members/" + user.id();
  }

  private void loadMembership() {
    String path = memberPath();
    if (path == null) {
      message.setText("Tải danh sách User và nhóm trước.");
      return;
    }
    CompletableFuture<ApiClient.Reply> request =
        api.get(path)
            .exceptionallyCompose(
                error -> {
                  Throwable cause = ApiClient.unwrap(error);
                  if (cause instanceof ApiClient.ApiFailure failure && failure.status() == 404)
                    return CompletableFuture.completedFuture(null);
                  return CompletableFuture.failedFuture(cause);
                });
    tasks.run(
        this,
        message,
        request,
        reply -> {
          teamRoles.values().forEach(check -> check.setSelected(false));
          memberEtag = reply == null ? null : reply.etag();
          newMember = reply == null;
          if (reply != null) {
            memberStatus.setSelectedItem(reply.body().path("status").asText());
            for (JsonNode role : reply.body().path("roles")) {
              var check = teamRoles.get(role.asText());
              if (check != null) check.setSelected(true);
            }
          }
          membershipState.setText(
              newMember ? "Thành viên mới · chọn vai trò rồi lưu" : "Đã tải thông tin thành viên");
        });
  }

  private void saveMembership() {
    String path = memberPath();
    if (path == null || (!newMember && memberEtag == null)) {
      message.setText("Tải membership trước khi lưu.");
      return;
    }
    Map<String, String> headers = newMember ? Map.of("If-None-Match", "*") : Map.of();
    tasks.run(
        this,
        message,
        api.request(
            "PUT",
            path,
            Map.of("roles", checked(teamRoles), "status", memberStatus.getSelectedItem()),
            memberEtag,
            headers),
        reply -> {
          memberEtag = reply.etag();
          newMember = false;
          message.setText("Đã lưu membership.");
        });
  }

  @Override
  public void close() {
    tasks.close();
  }

  public void sessionChanged() {
    tasks.close();
    users.setRowCount(0);
    teams.setRowCount(0);
    owner.removeAllItems();
    memberUser.removeAllItems();
    memberTeam.removeAllItems();
    userId = 0;
    userEtag = null;
    teamName.setText("");
    slug.setText("");
    systemRoles.values().forEach(check -> check.setSelected(false));
    teamRoles.values().forEach(check -> check.setSelected(false));
    createKey = UUID.randomUUID().toString();
    createPayload = "";
    invalidateMembership();
    selectedUser.setText("Chọn một tài khoản");
    message.setText(
        api.signedIn()
            ? "Tải lại để kiểm tra quyền quản trị."
            : "Đăng nhập bằng tài khoản quản trị viên.");
  }
}
