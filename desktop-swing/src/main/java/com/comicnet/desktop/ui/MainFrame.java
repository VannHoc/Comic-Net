package com.comicnet.desktop.ui;

import com.comicnet.desktop.client.api.*;
import com.comicnet.desktop.client.http.MediaTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.features.admin.AdministrationPanel;
import com.comicnet.desktop.features.identity.AccountPanel;
import com.comicnet.desktop.features.catalog.CatalogPanel;
import com.comicnet.desktop.features.analytics.RankingPanel;
import com.comicnet.desktop.features.library.LibraryPanel;
import com.comicnet.desktop.features.wallet.WalletPanel;
import com.comicnet.desktop.features.notification.NotificationsPanel;
import com.comicnet.desktop.features.observability.OperationsPanel;
import com.comicnet.desktop.features.publishing.PublishingPanel;
import com.comicnet.desktop.features.reader.*;
import com.comicnet.desktop.features.observability.SystemPresenter;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class MainFrame extends JFrame implements SystemPresenter.View, AutoCloseable {
  private final JLabel status = new JLabel("Chưa kiểm tra kết nối"),
      database = new JLabel("SQL Server: chưa kiểm tra"),
      requestId = new JLabel("Request ID: —");
  private final JButton retry = new JButton("Kiểm tra kết nối"),
      cancel = new JButton("Hủy kiểm tra");
  private final JProgressBar progress = new JProgressBar();
  private final CardLayout cards = new CardLayout();
  private final JPanel content = new JPanel(cards), navigation = new JPanel();
  private final JTextField globalSearch = new JTextField(28);
  private final JLabel accountLabel = new JLabel("Khách · Chưa đăng nhập");
  private final AccountPanel account;
  private final AdministrationPanel administration;
  private final ApiClient api;
  private final AutoCloseable sessionSubscription;
  private final java.util.Map<String, Workspace> workspaces = new java.util.LinkedHashMap<>();
  private final java.util.Map<String, JButton> navButtons = new java.util.LinkedHashMap<>();
  private String active;
  private final ReaderPanel reader;
  private final CatalogPanel catalog;

  public MainFrame(DesktopConfig config, ApiClient api, MediaTransport media) {
    super("Comic Net");
    this.api = api;
    accountLabel.setText(api.signedIn() ? "Đã đăng nhập" : "Khách · Chưa đăng nhập");
    if (!SwingUtilities.isEventDispatchThread())
      throw new IllegalStateException("Frame requires EDT.");
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(1024, 640));
    setSize(1280, 800);
    setLocationRelativeTo(null);
    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(Theme.BACKGROUND);
    JPanel sidebar = new JPanel(new BorderLayout(0, 30));
    sidebar.setBackground(Theme.SURFACE);
    sidebar.setPreferredSize(new Dimension(210, 0));
    sidebar.setBorder(new EmptyBorder(20, 14, 20, 14));
    navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
    navigation.setBackground(Theme.SURFACE);
    JScrollPane navScroll = new JScrollPane(navigation);
    navScroll.setBorder(null);
    navScroll.getViewport().setBackground(Theme.SURFACE);
    navScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    sidebar.add(navScroll, BorderLayout.CENTER);
    JPanel footer = new JPanel(new GridLayout(0, 1, 0, 8));
    footer.setOpaque(false);
    accountLabel.setForeground(Theme.MUTED);
    footer.add(accountLabel);
    footer.add(new JLabel("Khám phá câu chuyện của bạn"));
    sidebar.add(footer, BorderLayout.SOUTH);
    root.add(sidebar, BorderLayout.WEST);
    content.setOpaque(false);
    JPanel main = new JPanel(new BorderLayout(0, 0));
    main.setOpaque(false);
    main.add(topBar(), BorderLayout.NORTH);
    main.add(content, BorderLayout.CENTER);
    root.add(main, BorderLayout.CENTER);
    setContentPane(root);
    reader = new ReaderPanel(api, media, this::read);
    catalog = new CatalogPanel(api, media, this::read);
    addWorkspace("Khám phá", catalog);
    addWorkspace("Đang đọc", reader);
    addWorkspace(
        "Thư viện",
        new LibraryPanel(
            api,
            id -> {
              showWorkspace("Khám phá");
              catalog.openComic(id);
            },
            this::read,
            reader::sessionChanged));
    addWorkspace("Ví xu", new WalletPanel(api));
    addWorkspace(
        "Bảng xếp hạng",
        new RankingPanel(
            api,
            id -> {
              showWorkspace("Khám phá");
              catalog.openComic(id);
            }));
    addWorkspace("Thông báo", new NotificationsPanel(api));
    addWorkspace(
        "Xuất bản",
        new PublishingPanel(
            api,
            draft -> {
              JDialog dialog = new JDialog(this, "Comic Net · Xem trước", false);
              ReaderPanel preview = new ReaderPanel(api, media, ignored -> {});
              dialog.add(preview);
              dialog.setSize(1000, 760);
              dialog.setMinimumSize(new Dimension(900, 600));
              dialog.setLocationRelativeTo(this);
              dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
              dialog.addWindowListener(
                  new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent event) {
                      preview.close();
                    }
                  });
              preview.openPreview(draft);
              dialog.setVisible(true);
            }));
    account = new AccountPanel(api, () -> {});
    addWorkspace("Tài khoản", account);
    administration = new AdministrationPanel(api);
    addWorkspace("Quản trị", administration);
    addWorkspace("Vận hành", new OperationsPanel(api));
    addWorkspace("Hệ thống", systemPanel(config));
    refreshRoleNavigation();
    showWorkspace("Khám phá");
    sessionSubscription =
        api.onSessionChanged(() -> SwingUtilities.invokeLater(this::sessionChanged));
  }

  private JPanel topBar() {
    JPanel bar = new JPanel(new BorderLayout(16, 0));
    bar.setBackground(Theme.SURFACE);
    bar.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
        new EmptyBorder(12, 24, 12, 24)));
    JLabel brand = new JLabel("Comic Net");
    brand.setFont(Theme.brand(24));
    brand.setForeground(Theme.TEXT);
    brand.setPreferredSize(new Dimension(186, 44));
    globalSearch.setToolTipText("Tìm truyện, tác giả hoặc thể loại");
    globalSearch.getAccessibleContext().setAccessibleName("Tìm truyện");
    globalSearch.addActionListener(e -> catalog.search(globalSearch.getText()));
    JPanel searchBox = new JPanel(new BorderLayout(10, 0));
    searchBox.setOpaque(false);
    searchBox.add(globalSearch, BorderLayout.CENTER);
    JButton search = new JButton("Tìm truyện");
    search.addActionListener(e -> catalog.search(globalSearch.getText()));
    searchBox.add(search, BorderLayout.EAST);
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    right.setOpaque(false);
    JButton notifications = new JButton("Thông báo");
    notifications.addActionListener(e -> showWorkspace("Thông báo"));
    JButton profile = new JButton("Tài khoản");
    profile.addActionListener(e -> showWorkspace("Tài khoản"));
    right.add(notifications);
    right.add(profile);
    bar.add(brand, BorderLayout.WEST);
    bar.add(searchBox, BorderLayout.CENTER);
    bar.add(right, BorderLayout.EAST);
    return bar;
  }

  public void addWorkspace(String title, JComponent component) {
    if (component instanceof Workspace workspace) workspaces.put(title, workspace);
    if (component instanceof ReaderPanel) {
      JPanel host = new JPanel(new BorderLayout());
      host.setOpaque(false);
      host.add(component);
      content.add(host, title);
    } else content.add(component, title);
    JButton button = new NavigationButton(title);
    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
    button.setAlignmentX(LEFT_ALIGNMENT);
    button.setBorder(new EmptyBorder(14, 18, 14, 18));
    button.addActionListener(e -> showWorkspace(title));
    navButtons.put(title, button);
    navigation.add(button);
    navigation.add(Box.createVerticalStrut(10));
    navigation.revalidate();
  }

  private void showWorkspace(String title) {
    if (active != null && workspaces.containsKey(active)) workspaces.get(active).onHidden();
    active = title;
    cards.show(content, title);
    navButtons.forEach(
        (name, button) -> {
          button.setSelected(name.equals(title));
          button.repaint();
        });
    getRootPane().setDefaultButton(null);
    if (workspaces.containsKey(title)) workspaces.get(title).onShown();
  }

  private void read(ReaderLocation location) {
    showWorkspace("Đang đọc");
    reader.open(location);
  }

  private void sessionChanged() {
    accountLabel.setText(api.signedIn() ? "Đã đăng nhập" : "Khách · Chưa đăng nhập");
    refreshRoleNavigation();
    for (Window window : getOwnedWindows()) window.dispose();
    workspaces.values().forEach(Workspace::sessionChanged);
    if (active != null && workspaces.containsKey(active)) workspaces.get(active).onShown();
    firePropertyChange("sessionChanged", false, true);
  }

  private void refreshRoleNavigation() {
    for (String title : java.util.List.of("Xuất bản", "Quản trị", "Vận hành", "Hệ thống")) {
      JButton button = navButtons.get(title);
      if (button != null) button.setVisible(false);
    }
    if (!api.signedIn()) {
      if ("Xuất bản".equals(active) || "Quản trị".equals(active)
          || "Vận hành".equals(active) || "Hệ thống".equals(active)) showWorkspace("Khám phá");
      return;
    }
    api.get("/me").whenComplete((reply, error) -> SwingUtilities.invokeLater(() -> {
      if (error != null || !api.signedIn()) return;
      java.util.Set<String> roles = new java.util.HashSet<>();
      for (var role : reply.body().path("roles")) roles.add(role.asText().toUpperCase(java.util.Locale.ROOT));
      boolean admin = roles.contains("ADMINISTRATOR") || roles.contains("ADMIN");
      boolean creator = admin || roles.contains("CREATOR");
      navButtons.get("Xuất bản").setVisible(creator);
      navButtons.get("Quản trị").setVisible(admin);
      navButtons.get("Vận hành").setVisible(admin);
      navButtons.get("Hệ thống").setVisible(admin);
      if (("Xuất bản".equals(active) && !creator)
          || (("Quản trị".equals(active) || "Vận hành".equals(active) || "Hệ thống".equals(active)) && !admin))
        showWorkspace("Khám phá");
      navigation.revalidate();
      navigation.repaint();
    }));
  }

  private JPanel systemPanel(DesktopConfig config) {
    JPanel view = new JPanel(new BorderLayout(0, 24));
    view.setOpaque(false);
    view.setBorder(new EmptyBorder(36, 40, 36, 40));
    JLabel title = new JLabel("Kết nối hệ thống");
    title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
    view.add(title, BorderLayout.NORTH);
    JPanel card = new JPanel();
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    card.setBackground(Theme.SURFACE);
    card.setBorder(new EmptyBorder(28, 28, 28, 28));
    for (JComponent row :
        new JComponent[] {
          new JLabel("API: " + config.apiBase()), status, database, requestId, progress
        }) {
      row.setAlignmentX(LEFT_ALIGNMENT);
      card.add(row);
      card.add(Box.createVerticalStrut(22));
    }
    status.setFont(status.getFont().deriveFont(Font.BOLD, 18f));
    progress.setVisible(false);
    cancel.setEnabled(false);
    retry.setMnemonic('K');
    cancel.setMnemonic('H');
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    actions.setOpaque(false);
    actions.add(retry);
    actions.add(Box.createHorizontalStrut(12));
    actions.add(cancel);
    card.add(actions);
    JPanel top = new JPanel(new BorderLayout());
    top.setOpaque(false);
    top.add(card, BorderLayout.NORTH);
    view.add(top, BorderLayout.CENTER);
    return view;
  }

  public void bind(SystemPresenter presenter) {
    retry.addActionListener(e -> presenter.refresh());
    cancel.addActionListener(e -> presenter.cancel());
  }

  private void busy(boolean value) {
    retry.setEnabled(!value);
    cancel.setEnabled(value);
    progress.setIndeterminate(value);
    progress.setVisible(value);
  }

  @Override
  public void loading() {
    busy(true);
    status.setText("Đang kiểm tra…");
    database.setText("SQL Server: đang kiểm tra readiness");
    requestId.setText("Request ID: —");
  }

  @Override
  public void completed(ProbeResult result) {
    busy(false);
    status.setText("Spring Boot: đã kết nối");
    database.setText(
        result.databaseReady()
            ? "SQL Server: sẵn sàng"
            : "SQL Server: chưa sẵn sàng — kiểm tra backend");
    requestId.setText("Request ID: " + result.requestId());
  }

  @Override
  public void failed() {
    busy(false);
    status.setText("Không thể xác nhận kết nối backend");
    database.setText("SQL Server: chưa xác nhận. Kiểm tra backend rồi thử lại.");
    requestId.setText("Request ID: —");
  }

  @Override
  public void cancelled() {
    busy(false);
    status.setText("Đã hủy kiểm tra");
    database.setText("SQL Server: chưa xác nhận");
    requestId.setText("Request ID: —");
  }

  @Override
  public void close() {
    for (Window window : getOwnedWindows()) window.dispose();
    workspaces.values().forEach(Workspace::close);
    try {
      sessionSubscription.close();
    } catch (Exception ignored) {
    }
  }
}
