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
import com.comicnet.desktop.features.wallet.AdminWalletPanel;
import com.comicnet.desktop.features.gamification.GamificationPanel;
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
  private final JTextField globalSearch = new JTextField();
  private final JLabel accountLabel = new JLabel("Khách · Chưa đăng nhập");
  private final JLabel topBarCoinLabel = new JLabel("0");
  private final JLabel topBarUserLabel = new JLabel("Khách");
  private final AccountPanel account;
  private final AdministrationPanel administration;
  private final ApiClient api;
  private final AutoCloseable sessionSubscription;
  private final java.util.Map<String, Workspace> workspaces = new java.util.LinkedHashMap<>();
  private final java.util.Map<String, NavigationButton> navButtons = new java.util.LinkedHashMap<>();
  private String active;
  private final ReaderPanel reader;
  private final CatalogPanel catalog;

  public MainFrame(DesktopConfig config, ApiClient api, MediaTransport media) {
    super("Comic Net");
    this.api = api;
    if (!SwingUtilities.isEventDispatchThread())
      throw new IllegalStateException("Frame requires EDT.");
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(1180, 720));
    setSize(1440, 880);
    setLocationRelativeTo(null);

    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(Theme.BACKGROUND);

    // Modern Sidebar (228px width, clean surface, subtle right border)
    JPanel sidebar = new JPanel(new BorderLayout(0, 10));
    sidebar.setBackground(Theme.SURFACE);
    sidebar.setPreferredSize(new Dimension(Theme.SIDEBAR_WIDTH, 0));
    sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

    // Sidebar Header: Brand Logo
    JPanel brandHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
    brandHeader.setOpaque(false);
    brandHeader.setBorder(new EmptyBorder(22, 18, 16, 18));
    JLabel logo = new JLabel(UiIcons.brandLogo(28, 28));
    JLabel brandName = new JLabel("Comic Net");
    brandName.setFont(Theme.heading(20));
    brandName.setForeground(Theme.TEXT);
    brandHeader.add(logo);
    brandHeader.add(brandName);
    sidebar.add(brandHeader, BorderLayout.NORTH);

    // Sidebar Navigation Scroll
    navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
    navigation.setOpaque(false);
    navigation.setBorder(new EmptyBorder(6, 10, 12, 10));
    JScrollPane navScroll = new JScrollPane(navigation);
    navScroll.setBorder(null);
    navScroll.setOpaque(false);
    navScroll.getViewport().setOpaque(false);
    navScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    sidebar.add(navScroll, BorderLayout.CENTER);

    // Sidebar Footer: User Mini-Profile
    JPanel footer = new JPanel(new BorderLayout(10, 0));
    footer.setOpaque(false);
    footer.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
        new EmptyBorder(14, 16, 14, 16)));

    JPanel userAvatar = new JPanel() {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(api.signedIn() ? Theme.ACTION_TINT : Theme.ELEVATED);
        g2.fillOval(0, 0, getWidth(), getHeight());
        g2.setColor(api.signedIn() ? Theme.ACTION : Theme.MUTED);
        g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
        g2.setFont(Theme.brand(13));
        FontMetrics fm = g2.getFontMetrics();
        String initial = api.signedIn() ? "U" : "K";
        int tw = fm.stringWidth(initial);
        g2.drawString(initial, (getWidth() - tw) / 2, (getHeight() + fm.getAscent() - 2) / 2);
        g2.dispose();
      }
    };
    userAvatar.setPreferredSize(new Dimension(34, 34));
    userAvatar.setOpaque(false);

    accountLabel.setFont(Theme.label(12));
    accountLabel.setForeground(Theme.MUTED);
    footer.add(userAvatar, BorderLayout.WEST);
    footer.add(accountLabel, BorderLayout.CENTER);
    sidebar.add(footer, BorderLayout.SOUTH);

    root.add(sidebar, BorderLayout.WEST);

    // Main Content View
    content.setOpaque(true);
    content.setBackground(Theme.BACKGROUND);
    JPanel main = new JPanel(new BorderLayout(0, 0));
    main.setOpaque(true);
    main.setBackground(Theme.BACKGROUND);
    main.add(buildTopBar(), BorderLayout.NORTH);
    main.add(content, BorderLayout.CENTER);
    root.add(main, BorderLayout.CENTER);

    setContentPane(root);

    // Workspaces
    reader = new ReaderPanel(api, media, this::read, () -> showWorkspace("Khám phá"));
    catalog = new CatalogPanel(api, media, this::read);
    addWorkspace("Khám phá", catalog, UiIcons.home(18, Theme.MUTED));
    addWorkspace("Đang đọc", reader, UiIcons.bookOpen(18, Theme.MUTED));
    addWorkspace(
        "Thư viện",
        new LibraryPanel(
            api,
            id -> {
              showWorkspace("Khám phá");
              catalog.openComic(id);
            },
            this::read,
            reader::sessionChanged),
        UiIcons.library(18, Theme.MUTED));
    addWorkspace("Ví xu", new WalletPanel(api, this::updateTopBarUser), UiIcons.wallet(18, Theme.MUTED));
    addWorkspace("Nhiệm vụ & Thưởng", new GamificationPanel(api, this::updateTopBarUser), UiIcons.gift(18, Theme.MUTED));
    if (navButtons.containsKey("Nhiệm vụ & Thưởng")) {
      navButtons.get("Nhiệm vụ & Thưởng").setHasDot(true);
    }
    addWorkspace("Quản lý ví xu", new AdminWalletPanel(api), UiIcons.wallet(18, Theme.MUTED));
    addWorkspace(
        "Bảng xếp hạng",
        new RankingPanel(
            api,
            id -> {
              showWorkspace("Khám phá");
              catalog.openComic(id);
            }),
        UiIcons.barChart(18, Theme.MUTED));
    addWorkspace("Thông báo", new NotificationsPanel(api), UiIcons.bell(18, Theme.MUTED));
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
            }),
        UiIcons.feather(18, Theme.MUTED));
    account = new AccountPanel(api, () -> {});
    addWorkspace("Tài khoản", account, UiIcons.user(18, Theme.MUTED));
    administration = new AdministrationPanel(api);
    addWorkspace("Quản trị", administration, UiIcons.shield(18, Theme.MUTED));
    addWorkspace("Vận hành", new OperationsPanel(api), UiIcons.settings(18, Theme.MUTED));
    addWorkspace("Hệ thống", systemPanel(config), UiIcons.settings(18, Theme.MUTED));

    refreshRoleNavigation();
    showWorkspace("Khám phá");
    sessionSubscription =
        api.onSessionChanged(() -> SwingUtilities.invokeLater(this::sessionChanged));
    updateTopBarUser();
  }

  private JPanel buildTopBar() {
    JPanel bar = new JPanel(new BorderLayout(20, 0));
    bar.setBackground(Theme.SURFACE);
    bar.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
        new EmptyBorder(14, 28, 14, 28)));
    bar.setPreferredSize(new Dimension(0, Theme.TOPBAR_HEIGHT));

    // Center/Left Search Pill Container
    JPanel searchPill = new JPanel(new BorderLayout(10, 0)) {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.BACKGROUND);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.dispose();
      }
    };
    searchPill.setOpaque(false);
    searchPill.setBorder(new EmptyBorder(6, 14, 6, 14));
    searchPill.setPreferredSize(new Dimension(420, 42));

    JLabel searchIcon = new JLabel(UiIcons.search(16, Theme.HINT));
    globalSearch.setBorder(null);
    globalSearch.setOpaque(false);
    globalSearch.setFont(Theme.label(14));
    globalSearch.setForeground(Theme.TEXT);
    globalSearch.putClientProperty("JTextField.placeholderText", "Tìm kiếm truyện, tác giả, thể loại...");
    globalSearch.setToolTipText("Tìm truyện, tác giả hoặc thể loại...");
    globalSearch.addActionListener(e -> {
      showWorkspace("Khám phá");
      catalog.search(globalSearch.getText());
    });

    searchPill.add(searchIcon, BorderLayout.WEST);
    searchPill.add(globalSearch, BorderLayout.CENTER);
    bar.add(searchPill, BorderLayout.WEST);

    // Right Section: Notifications, Coin Pill, User Profile Pill
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
    right.setOpaque(false);

    // Notification Icon Button
    JButton notifBtn = new JButton(UiIcons.bell(20, Theme.TEXT));
    notifBtn.setContentAreaFilled(false);
    notifBtn.setBorderPainted(false);
    notifBtn.setFocusPainted(false);
    notifBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    notifBtn.setToolTipText("Thông báo");
    notifBtn.addActionListener(e -> showWorkspace("Thông báo"));

    // Coin Balance Pill
    JPanel coinPill = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)) {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.ELEVATED);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
        g2.dispose();
      }
    };
    coinPill.setOpaque(false);
    coinPill.setBorder(new EmptyBorder(4, 10, 4, 6));
    coinPill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    JLabel coinIcon = new JLabel(UiIcons.coin(18));
    topBarCoinLabel.setFont(Theme.brand(13));
    topBarCoinLabel.setForeground(Theme.TEXT);

    JButton plusBtn = new JButton("+");
    plusBtn.setFont(Theme.brand(13));
    plusBtn.setForeground(Color.WHITE);
    plusBtn.setBackground(Theme.ACTION);
    plusBtn.setBorderPainted(false);
    plusBtn.setFocusPainted(false);
    plusBtn.setPreferredSize(new Dimension(22, 22));
    plusBtn.setMargin(new Insets(0, 0, 0, 0));
    plusBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    coinPill.add(coinIcon);
    coinPill.add(topBarCoinLabel);
    coinPill.add(plusBtn);

    java.awt.event.MouseAdapter openWallet = new java.awt.event.MouseAdapter() {
      @Override public void mouseClicked(java.awt.event.MouseEvent e) {
        showWorkspace("Ví xu");
      }
    };
    coinPill.addMouseListener(openWallet);
    plusBtn.addActionListener(e -> showWorkspace("Ví xu"));

    // User Profile Pill
    JPanel userPill = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)) {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.SURFACE);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
        g2.dispose();
      }
    };
    userPill.setOpaque(false);
    userPill.setBorder(new EmptyBorder(4, 8, 4, 10));
    userPill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    JLabel avatarSmall = new JLabel(UiIcons.user(16, Theme.ACTION));
    topBarUserLabel.setFont(Theme.brand(13));
    topBarUserLabel.setForeground(Theme.TEXT);
    JLabel chevron = new JLabel(UiIcons.chevronDown(12, Theme.MUTED));

    userPill.add(avatarSmall);
    userPill.add(topBarUserLabel);
    userPill.add(chevron);
    userPill.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override public void mouseClicked(java.awt.event.MouseEvent e) {
        showWorkspace("Tài khoản");
      }
    });

    right.add(notifBtn);
    right.add(coinPill);
    right.add(userPill);

    bar.add(right, BorderLayout.EAST);
    return bar;
  }

  public void addWorkspace(String title, JComponent component) {
    addWorkspace(title, component, null);
  }

  public void addWorkspace(String title, JComponent component, Icon icon) {
    if (component instanceof Workspace workspace) workspaces.put(title, workspace);
    if (component instanceof ReaderPanel) {
      JPanel host = new JPanel(new BorderLayout());
      host.setOpaque(false);
      host.add(component);
      content.add(host, title);
    } else content.add(component, title);

    NavigationButton button = new NavigationButton(title, icon);
    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
    button.setPreferredSize(new Dimension(200, 42));
    button.setAlignmentX(LEFT_ALIGNMENT);
    button.addActionListener(e -> showWorkspace(title));
    navButtons.put(title, button);
    navigation.add(button);
    navigation.add(Box.createVerticalStrut(3));
    navigation.revalidate();
  }

  private void showWorkspace(String title) {
    if (active != null && workspaces.containsKey(active)) workspaces.get(active).onHidden();
    active = title;
    cards.show(content, title);
    navButtons.forEach((name, button) -> {
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

  public void updateTopBarUser() {
    boolean signedIn = api.signedIn();
    accountLabel.setText(signedIn ? "Đã đăng nhập" : "Khách · Chưa đăng nhập");
    if (!signedIn) {
      topBarUserLabel.setText("Đăng nhập");
      topBarCoinLabel.setText("0");
      return;
    }
    api.get("/me").whenComplete((reply, err) -> {
      if (err == null && reply != null) {
        String name = reply.body().path("displayName").asText("");
        if (name.isBlank()) name = reply.body().path("username").asText("Tài khoản");
        String finalName = name;
        SwingUtilities.invokeLater(() -> topBarUserLabel.setText(finalName));
      }
    });
    api.get("/gamification/profile").whenComplete((reply, err) -> {
      if (err == null && reply != null) {
        long paid = reply.body().path("paidCoin").asLong(0);
        long reward = reply.body().path("rewardCoin").asLong(0);
        long total = paid + reward;
        refreshCoins(total);
      }
    });
  }

  public void refreshCoins(long totalCoins) {
    SwingUtilities.invokeLater(() -> topBarCoinLabel.setText(String.format(java.util.Locale.ROOT, "%,d", totalCoins)));
  }

  private void sessionChanged() {
    updateTopBarUser();
    refreshRoleNavigation();
    for (Window window : getOwnedWindows()) window.dispose();
    workspaces.values().forEach(Workspace::sessionChanged);
    if (active != null && workspaces.containsKey(active)) workspaces.get(active).onShown();
    firePropertyChange("sessionChanged", false, true);
  }

  private void refreshRoleNavigation() {
    for (String title : java.util.List.of("Xuất bản", "Quản trị", "Quản lý ví xu", "Vận hành", "Hệ thống")) {
      NavigationButton button = navButtons.get(title);
      if (button != null) button.setVisible(false);
    }
    if (!api.signedIn()) {
      if ("Xuất bản".equals(active) || "Quản trị".equals(active)
          || "Quản lý ví xu".equals(active)
          || "Vận hành".equals(active) || "Hệ thống".equals(active)) showWorkspace("Khám phá");
      return;
    }
    api.get("/me").whenComplete((reply, error) -> SwingUtilities.invokeLater(() -> {
      if (error != null || !api.signedIn()) return;
      java.util.Set<String> roles = new java.util.HashSet<>();
      for (var role : reply.body().path("roles")) roles.add(role.asText().toUpperCase(java.util.Locale.ROOT));
      boolean admin = roles.contains("ADMINISTRATOR") || roles.contains("ADMIN");
      boolean creator = admin || roles.contains("CREATOR");
      if (navButtons.get("Xuất bản") != null) navButtons.get("Xuất bản").setVisible(creator);
      if (navButtons.get("Quản trị") != null) navButtons.get("Quản trị").setVisible(admin);
      if (navButtons.get("Quản lý ví xu") != null) navButtons.get("Quản lý ví xu").setVisible(admin);
      if (navButtons.get("Vận hành") != null) navButtons.get("Vận hành").setVisible(admin);
      if (navButtons.get("Hệ thống") != null) navButtons.get("Hệ thống").setVisible(admin);
      if (("Xuất bản".equals(active) && !creator)
          || (("Quản trị".equals(active) || "Quản lý ví xu".equals(active)
              || "Vận hành".equals(active) || "Hệ thống".equals(active)) && !admin))
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
