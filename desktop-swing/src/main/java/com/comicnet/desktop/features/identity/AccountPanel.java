package com.comicnet.desktop.features.identity;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import tools.jackson.databind.JsonNode;

public final class AccountPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final Runnable sessionChanged;
  private final UiTasks tasks = new UiTasks();
  private final UiTasks levelTasks = new UiTasks();
  private final UiTasks statsTasks = new UiTasks();

  private final JLabel message = new JLabel(" ");
  private final JLabel pageTitle = new JLabel("Tài khoản của bạn");
  private final JLabel pageSubtitle = Theme.plainLabel("Quản lý thông tin, bảo mật và hành trình đọc truyện.");
  private final JPanel centerHost = new JPanel(new BorderLayout());

  // Profile Dashboard Header components
  private final JPanel profileHeaderCard = new JPanel(new BorderLayout(24, 0)) {
    @Override protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(Theme.SURFACE);
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
      g2.setColor(Theme.BORDER);
      g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
      g2.dispose();
    }
  };
  private final JLabel avatarLabel = Theme.plainLabel("CN");
  private final JLabel displayNameLabel = Theme.plainLabel("Đang tải…");
  private final JLabel usernameLabel = Theme.plainLabel("@username");
  private final JLabel titleBadge = Theme.plainLabel(" Tân thủ ");
  private final JLabel roleBadge = Theme.plainLabel(" Độc giả ");
  private final JLabel expText = Theme.plainLabel("EXP: 0 / 100");
  private final JLabel levelBadge = Theme.plainLabel("Lv. 1");
  private final JProgressBar expBar = new JProgressBar(0, 100);

  // Stat cards
  private final JLabel coinValueLabel = Theme.plainLabel("0");
  private final JLabel streakValueLabel = Theme.plainLabel("0");
  private final JLabel readCountLabel = Theme.plainLabel("0");

  // Tabbed Content
  private final JPanel tabButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
  private final CardLayout tabCards = new CardLayout();
  private final JPanel tabContentPanel = new JPanel(tabCards);
  private String activeTab = "PROFILE";
  private final JButton tabProfileBtn = new JButton("Hồ sơ") {
    @Override protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      boolean active = "PROFILE".equals(activeTab);
      g2.setColor(active ? Theme.ACTION : Theme.SURFACE);
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
      g2.setColor(active ? Theme.ACTION : Theme.BORDER);
      g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
      g2.dispose();
      super.paintComponent(g);
    }
  };
  private final JButton tabSecurityBtn = new JButton("Bảo mật & Phiên") {
    @Override protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      boolean active = "SECURITY".equals(activeTab);
      g2.setColor(active ? Theme.ACTION : Theme.SURFACE);
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
      g2.setColor(active ? Theme.ACTION : Theme.BORDER);
      g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
      g2.dispose();
      super.paintComponent(g);
    }
  };
  private final JButton tabGamificationBtn = new JButton("Danh hiệu & Thành tựu") {
    @Override protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      boolean active = "GAMIFICATION".equals(activeTab);
      g2.setColor(active ? Theme.ACTION : Theme.SURFACE);
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
      g2.setColor(active ? Theme.ACTION : Theme.BORDER);
      g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
      g2.dispose();
      super.paintComponent(g);
    }
  };

  // Form fields
  private final JTextField nameField = new JTextField(24);
  private final JTextField usernameField = new JTextField(24);
  private final JTextField emailField = new JTextField(24);
  private final JTextArea bioArea = new JTextArea(3, 24);

  // Identity state
  private final JLabel roleText = Theme.plainLabel("Độc giả");
  private final JLabel joinDateText = Theme.plainLabel("10/10/2026");
  private final JLabel statusText = Theme.plainLabel(" Đang hoạt động ");
  private final JLabel currentTitleLarge = Theme.plainLabel("Tân thủ");
  private final JLabel titleReqText = Theme.plainLabel("Cấp mở khóa: Lv. 1");

  private String profileEtag;

  public AccountPanel(ApiClient api, Runnable sessionChanged) {
    super(new BorderLayout(0, 16));
    this.api = api;
    this.sessionChanged = sessionChanged;
    setOpaque(true);
    setBackground(Theme.BACKGROUND);
    setBorder(new EmptyBorder(16, Theme.CONTENT_PADDING, 16, Theme.CONTENT_PADDING));

    // Page Heading matching Mockup 04_Tai_khoan_gon.png
    JPanel heading = new JPanel(new GridLayout(3, 1, 0, 2));
    heading.setOpaque(false);
    JLabel eyebrow = Theme.plainLabel("HỒ SƠ CÁ NHÂN");
    eyebrow.setForeground(Theme.PRIMARY);
    eyebrow.setFont(Theme.heading(11));
    pageTitle.setFont(Theme.heading(24));
    pageTitle.setForeground(Theme.TEXT);
    pageSubtitle.setFont(Theme.label(13));
    pageSubtitle.setForeground(Theme.MUTED);
    heading.add(eyebrow);
    heading.add(pageTitle);
    heading.add(pageSubtitle);
    add(heading, BorderLayout.NORTH);

    centerHost.setOpaque(true);
    centerHost.setBackground(Theme.BACKGROUND);
    add(centerHost, BorderLayout.CENTER);

    message.setFont(Theme.label(12));
    message.setForeground(Theme.ACTION);
    add(message, BorderLayout.SOUTH);

    buildProfileHeader();
    buildTabs();

    render();
  }

  private void buildProfileHeader() {
    profileHeaderCard.setOpaque(false);
    profileHeaderCard.setBorder(new EmptyBorder(14, 20, 14, 20));
    profileHeaderCard.setPreferredSize(new Dimension(0, 92));

    // Left info
    JPanel leftSide = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
    leftSide.setOpaque(false);

    // Avatar Circle (48x48)
    avatarLabel.setPreferredSize(new Dimension(48, 48));
    avatarLabel.setHorizontalAlignment(SwingConstants.CENTER);
    avatarLabel.setFont(Theme.heading(20));
    avatarLabel.setForeground(Theme.ACTION);
    avatarLabel.setOpaque(false);
    JPanel avatarWrapper = new JPanel(new BorderLayout()) {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, Theme.ACTION_TINT, getWidth(), getHeight(), new Color(0xFE, 0xE2, 0xE2));
        g2.setPaint(gp);
        g2.fillOval(0, 0, getWidth(), getHeight());
        g2.setColor(Theme.ACTION);
        g2.setStroke(new BasicStroke(1.8f));
        g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    avatarWrapper.setOpaque(false);
    avatarWrapper.setPreferredSize(new Dimension(48, 48));
    avatarWrapper.add(avatarLabel, BorderLayout.CENTER);
    leftSide.add(avatarWrapper);

    // Identity text & EXP (2 compact horizontal rows)
    JPanel userMeta = new JPanel();
    userMeta.setLayout(new BoxLayout(userMeta, BoxLayout.Y_AXIS));
    userMeta.setOpaque(false);

    // Row 1: Name + Edit + @Username + Badges
    JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    topRow.setOpaque(false);
    displayNameLabel.setFont(Theme.heading(18));
    displayNameLabel.setForeground(Theme.TEXT);
    JLabel editIcon = new JLabel(UiIcons.edit(13, Theme.MUTED));
    usernameLabel.setFont(Theme.label(12));
    usernameLabel.setForeground(Theme.MUTED);

    styleBadge(titleBadge, new Color(0xF3, 0xE8, 0xFF), new Color(0x7E, 0x22, 0xCE)); // Purple
    titleBadge.setIcon(UiIcons.crown(11, new Color(0x7E, 0x22, 0xCE)));
    styleBadge(roleBadge, new Color(0xDB, 0xEA, 0xFE), new Color(0x25, 0x63, 0xEB));  // Blue

    topRow.add(displayNameLabel);
    topRow.add(editIcon);
    topRow.add(usernameLabel);
    topRow.add(Box.createHorizontalStrut(6));
    topRow.add(titleBadge);
    topRow.add(roleBadge);

    // Row 2: Level + EXP Bar + EXP Text
    JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    bottomRow.setOpaque(false);

    levelBadge.setFont(Theme.brand(12));
    levelBadge.setForeground(Theme.ACTION);

    expBar.setForeground(Theme.ACTION);
    expBar.setPreferredSize(new Dimension(210, 6));

    expText.setFont(Theme.label(11));
    expText.setForeground(Theme.MUTED);

    bottomRow.add(levelBadge);
    bottomRow.add(expBar);
    bottomRow.add(expText);

    userMeta.add(topRow);
    userMeta.add(Box.createVerticalStrut(4));
    userMeta.add(bottomRow);

    leftSide.add(userMeta);
    profileHeaderCard.add(leftSide, BorderLayout.WEST);

    // Right Stat Cards (Xu, Thành tựu, Truyện đã đọc)
    JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    statsRow.setOpaque(false);

    statsRow.add(buildStatCard(UiIcons.coin(18), coinValueLabel, "Xu của bạn", true));
    statsRow.add(buildStatCard(UiIcons.trophy(18, Theme.ACTION), streakValueLabel, "Thành tựu", false));
    statsRow.add(buildStatCard(UiIcons.bookOpen(18, Theme.ACTION), readCountLabel, "Truyện đã đọc", false));

    profileHeaderCard.add(statsRow, BorderLayout.EAST);
  }

  private void styleBadge(JLabel label, Color bg, Color fg) {
    label.setFont(Theme.brand(11));
    label.setForeground(fg);
    label.setOpaque(true);
    label.setBackground(bg);
    label.setBorder(new EmptyBorder(2, 6, 2, 6));
  }

  private JPanel buildStatCard(Icon icon, JLabel valLabel, String sub, boolean hasPlus) {
    JPanel card = new JPanel(new BorderLayout(6, 0)) {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.ELEVATED);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
        g2.dispose();
      }
    };
    card.setOpaque(false);
    card.setBorder(new EmptyBorder(6, 8, 6, 8));
    card.setPreferredSize(new Dimension(hasPlus ? 175 : 120, 52));

    JLabel iconLabel = new JLabel(icon);
    card.add(iconLabel, BorderLayout.WEST);

    JPanel copy = new JPanel(new GridLayout(2, 1, 0, 1));
    copy.setOpaque(false);

    valLabel.setFont(hasPlus ? Theme.heading(15) : Theme.heading(16));
    valLabel.setForeground(Theme.TEXT);

    JLabel subLabel = Theme.plainLabel(sub);
    subLabel.setFont(Theme.label(10));
    subLabel.setForeground(Theme.MUTED);

    copy.add(valLabel);
    copy.add(subLabel);
    card.add(copy, BorderLayout.CENTER);

    if (hasPlus) {
      JButton plus = new JButton("+");
      plus.setFont(Theme.brand(11));
      plus.setForeground(Color.WHITE);
      plus.setBackground(Theme.ACTION);
      plus.setBorderPainted(false);
      plus.setFocusPainted(false);
      plus.setPreferredSize(new Dimension(18, 18));
      plus.setMargin(new Insets(0, 0, 0, 0));
      plus.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      plus.addActionListener(e -> {
        Container top = getTopLevelAncestor();
        if (top instanceof MainFrame mf) {
          try {
            var method = MainFrame.class.getDeclaredMethod("showWorkspace", String.class);
            method.setAccessible(true);
            method.invoke(mf, "Ví xu");
          } catch (Exception ignored) {}
        }
      });
      JPanel plusBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
      plusBox.setOpaque(false);
      plusBox.add(plus);
      card.add(plusBox, BorderLayout.EAST);
    }

    return card;
  }

  private void buildTabs() {
    tabButtonsPanel.setOpaque(false);
    tabButtonsPanel.setBorder(new EmptyBorder(10, 0, 8, 0));

    styleTabButton(tabProfileBtn, true);
    styleTabButton(tabSecurityBtn, false);
    styleTabButton(tabGamificationBtn, false);

    tabProfileBtn.addActionListener(e -> switchTab("PROFILE"));
    tabSecurityBtn.addActionListener(e -> switchTab("SECURITY"));
    tabGamificationBtn.addActionListener(e -> {
      Container top = getTopLevelAncestor();
      if (top instanceof MainFrame mf) {
        try {
          var method = MainFrame.class.getDeclaredMethod("showWorkspace", String.class);
          method.setAccessible(true);
          method.invoke(mf, "Nhiệm vụ & Thưởng");
        } catch (Exception ignored) {}
      }
    });

    tabButtonsPanel.add(tabProfileBtn);
    tabButtonsPanel.add(tabSecurityBtn);
    tabButtonsPanel.add(tabGamificationBtn);

    tabContentPanel.setOpaque(false);
    tabContentPanel.add(buildProfileTab(), "PROFILE");
    tabContentPanel.add(buildSecurityTab(), "SECURITY");
  }

  private void styleTabButton(JButton btn, boolean active) {
    btn.setContentAreaFilled(false);
    btn.setBorderPainted(false);
    btn.setFocusPainted(false);
    btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    btn.setFont(Theme.brand(12));
    btn.setBorder(new EmptyBorder(6, 14, 6, 14));
    btn.setForeground(active ? Color.WHITE : Theme.TEXT);
    if (btn == tabProfileBtn) {
      btn.setIcon(UiIcons.user(15, active ? Color.WHITE : Theme.TEXT));
    } else if (btn == tabSecurityBtn) {
      btn.setIcon(UiIcons.lock(15, active ? Color.WHITE : Theme.TEXT));
    } else if (btn == tabGamificationBtn) {
      btn.setIcon(UiIcons.crown(15, active ? Color.WHITE : Theme.TEXT));
    }
  }

  private void switchTab(String tab) {
    this.activeTab = tab;
    tabCards.show(tabContentPanel, tab);
    styleTabButton(tabProfileBtn, "PROFILE".equals(tab));
    styleTabButton(tabSecurityBtn, "SECURITY".equals(tab));
    styleTabButton(tabGamificationBtn, "GAMIFICATION".equals(tab));
    tabProfileBtn.repaint();
    tabSecurityBtn.repaint();
    tabGamificationBtn.repaint();
  }

  private JPanel buildProfileTab() {
    JPanel tab = new JPanel(new GridLayout(1, 3, 16, 0));
    tab.setOpaque(false);
    tab.setPreferredSize(new Dimension(0, 245));

    // Column 1: Thông tin cá nhân (Form chỉnh sửa)
    JPanel col1 = createSectionCard("Thông tin cá nhân");
    col1.add(formField("Tên hiển thị", nameField));
    col1.add(Box.createVerticalStrut(4));

    emailField.setEditable(false);
    emailField.setBackground(Theme.ELEVATED);
    col1.add(formField("Email khôi phục", emailField));
    col1.add(Box.createVerticalStrut(4));

    bioArea.setLineWrap(true);
    bioArea.setWrapStyleWord(true);
    bioArea.setFont(Theme.label(12));
    bioArea.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(4, 6, 4, 6)));
    bioArea.setRows(2);
    JScrollPane bioScroll = new JScrollPane(bioArea);
    bioScroll.setPreferredSize(new Dimension(280, 42));
    bioScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
    col1.add(formField("Giới thiệu bản thân", bioScroll));
    col1.add(Box.createVerticalStrut(8));

    PrimaryButton saveBtn = new PrimaryButton("Lưu thay đổi");
    saveBtn.setPreferredSize(new Dimension(115, 30));
    saveBtn.setFont(Theme.brand(12));
    saveBtn.addActionListener(e -> saveProfile());
    col1.add(saveBtn);
    col1.add(Box.createVerticalGlue());
    tab.add(col1);

    // Column 2: Thông tin tài khoản
    JPanel col2 = createSectionCard("Thông tin tài khoản");
    JPanel userBox = new JPanel(new BorderLayout(6, 0));
    userBox.setOpaque(false);
    userBox.setPreferredSize(new Dimension(200, 30));
    userBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
    usernameField.setPreferredSize(new Dimension(200, 30));
    usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
    usernameField.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(4, 8, 4, 8)));
    userBox.add(usernameField, BorderLayout.CENTER);
    JButton saveUsernameBtn = new JButton("Đổi");
    saveUsernameBtn.setFont(Theme.brand(11));
    saveUsernameBtn.setBackground(Theme.SURFACE);
    saveUsernameBtn.setForeground(Theme.TEXT);
    saveUsernameBtn.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(4, 12, 4, 12)));
    saveUsernameBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    saveUsernameBtn.setPreferredSize(new Dimension(54, 30));
    saveUsernameBtn.addActionListener(e -> saveUsername());
    userBox.add(saveUsernameBtn, BorderLayout.EAST);
    col2.add(formField("Tên đăng nhập", userBox));
    col2.add(Box.createVerticalStrut(6));

    styleBadge(roleText, new Color(0xF3, 0xE8, 0xFF), new Color(0x7E, 0x22, 0xCE));
    col2.add(metaPair("Vai trò", roleText));
    col2.add(Box.createVerticalStrut(4));

    col2.add(metaPair("Ngày tham gia", joinDateText));
    col2.add(Box.createVerticalStrut(4));

    styleBadge(statusText, Theme.SUCCESS_TINT, Theme.SUCCESS);
    col2.add(metaPair("Trạng thái", statusText));
    col2.add(Box.createVerticalStrut(8));

    JButton manageDevicesBtn = new JButton("Quản lý thiết bị →");
    manageDevicesBtn.setFont(Theme.brand(11));
    manageDevicesBtn.setBackground(Theme.SURFACE);
    manageDevicesBtn.setForeground(Theme.TEXT);
    manageDevicesBtn.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(5, 12, 5, 12)));
    manageDevicesBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    manageDevicesBtn.addActionListener(e -> new SessionsDialog(this, api).setVisible(true));
    col2.add(manageDevicesBtn);

    col2.add(Box.createVerticalGlue());
    tab.add(col2);

    // Column 3: Danh hiệu & Cấp độ độc giả
    JPanel col3 = createSectionCard("Danh hiệu hiện tại");
    JPanel emblemCard = new JPanel() {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0xFA, 0xF5, 0xFF)); // Light purple
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g2.setColor(new Color(0xD8, 0xB4, 0xFE));
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.dispose();
      }
    };
    emblemCard.setLayout(new BoxLayout(emblemCard, BoxLayout.Y_AXIS));
    emblemCard.setOpaque(false);
    emblemCard.setBorder(new EmptyBorder(8, 12, 8, 12));
    emblemCard.setPreferredSize(new Dimension(0, 118));
    emblemCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));

    JLabel crown = new JLabel(UiIcons.crown(22, new Color(0xF5, 0x9E, 0x0B)));
    crown.setAlignmentX(CENTER_ALIGNMENT);

    currentTitleLarge.setFont(Theme.heading(18));
    currentTitleLarge.setForeground(new Color(0x6B, 0x21, 0xA8));
    currentTitleLarge.setAlignmentX(CENTER_ALIGNMENT);

    titleReqText.setFont(Theme.label(11));
    titleReqText.setForeground(Theme.MUTED);
    titleReqText.setAlignmentX(CENTER_ALIGNMENT);

    JButton viewTitlesBtn = new JButton("Xem danh hiệu đã mở →");
    viewTitlesBtn.setFont(Theme.brand(11));
    viewTitlesBtn.setBackground(Color.WHITE);
    viewTitlesBtn.setForeground(new Color(0x6B, 0x21, 0xA8));
    viewTitlesBtn.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(0xD8, 0xB4, 0xFE)), new EmptyBorder(4, 12, 4, 12)));
    viewTitlesBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    viewTitlesBtn.setAlignmentX(CENTER_ALIGNMENT);
    viewTitlesBtn.addActionListener(e -> {
      Container top = getTopLevelAncestor();
      if (top instanceof MainFrame mf) {
        try {
          var method = MainFrame.class.getDeclaredMethod("showWorkspace", String.class);
          method.setAccessible(true);
          method.invoke(mf, "Nhiệm vụ & Thưởng");
        } catch (Exception ignored) {}
      }
    });

    emblemCard.add(crown);
    emblemCard.add(Box.createVerticalStrut(4));
    emblemCard.add(currentTitleLarge);
    emblemCard.add(Box.createVerticalStrut(2));
    emblemCard.add(titleReqText);
    emblemCard.add(Box.createVerticalStrut(6));
    emblemCard.add(viewTitlesBtn);

    col3.add(emblemCard);
    col3.add(Box.createVerticalStrut(8));

    JLabel tip = Theme.plainLabel("Mở thêm danh hiệu khi lên cấp. Có thể chọn lại danh hiệu đã nhận.");
    tip.setFont(Theme.label(11));
    tip.setForeground(new Color(0x94, 0xA3, 0xB8));
    col3.add(tip);
    col3.add(Box.createVerticalGlue());

    col3.add(Box.createVerticalGlue());
    tab.add(col3);

    return tab;
  }

  private JPanel buildSecurityTab() {
    JPanel tab = new JPanel(new GridLayout(1, 2, 24, 0));
    tab.setOpaque(false);

    JPanel secCard = createSectionCard("Bảo mật & Phiên làm việc");
    JLabel secDesc = Theme.plainLabel("Quản lý các thiết bị và phiên đăng nhập hiện tại trên tài khoản của bạn.");
    secDesc.setFont(Theme.label(13));
    secDesc.setForeground(Theme.MUTED);
    secCard.add(secDesc);
    secCard.add(Box.createVerticalStrut(16));

    JButton sessionsBtn = new JButton("Thiết bị & Phiên đăng nhập");
    sessionsBtn.setIcon(UiIcons.settings(16, Theme.TEXT));
    sessionsBtn.setFont(Theme.brand(13));
    sessionsBtn.setBackground(Theme.SURFACE);
    sessionsBtn.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(10, 16, 10, 16)));
    sessionsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    sessionsBtn.addActionListener(e -> new SessionsDialog(this, api).setVisible(true));
    secCard.add(sessionsBtn);
    tab.add(secCard);

    JPanel logoutCard = createSectionCard("Đăng xuất tài khoản");
    JLabel logDesc = Theme.plainLabel("Đăng xuất phiên làm việc khỏi máy tính này.");
    logDesc.setFont(Theme.label(13));
    logDesc.setForeground(Theme.MUTED);
    logoutCard.add(logDesc);
    logoutCard.add(Box.createVerticalStrut(16));

    JButton logoutBtn = new JButton("Đăng xuất khỏi thiết bị này");
    logoutBtn.setIcon(UiIcons.logout(14, Theme.DANGER));
    logoutBtn.setFont(Theme.brand(13));
    logoutBtn.setForeground(Theme.DANGER);
    logoutBtn.setBackground(new Color(0xFE, 0xF2, 0xF2));
    logoutBtn.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(0xFE, 0xCD, 0xCD)), new EmptyBorder(10, 16, 10, 16)));
    logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    logoutBtn.addActionListener(e -> executeLogout());
    logoutCard.add(logoutBtn);
    tab.add(logoutCard);

    return tab;
  }

  private JPanel createSectionCard(String title) {
    JPanel card = new JPanel() {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.SURFACE);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.dispose();
      }
    };
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    card.setOpaque(false);
    card.setBorder(new EmptyBorder(12, 14, 12, 14));

    JLabel heading = Theme.plainLabel(title);
    heading.setFont(Theme.heading(15));
    heading.setForeground(Theme.TEXT);
    heading.setAlignmentX(LEFT_ALIGNMENT);

    card.add(heading);
    card.add(Box.createVerticalStrut(8));
    return card;
  }

  private JPanel formField(String label, JComponent comp) {
    JPanel panel = new JPanel(new BorderLayout(0, 2));
    panel.setOpaque(false);
    panel.setAlignmentX(LEFT_ALIGNMENT);

    JLabel lbl = Theme.plainLabel(label);
    lbl.setFont(Theme.brand(11));
    lbl.setForeground(Theme.TEXT);

    comp.setFont(Theme.label(12));
    if (comp instanceof JTextField tf) {
      tf.setPreferredSize(new Dimension(tf.getPreferredSize().width, 30));
      tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
      tf.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(4, 8, 4, 8)));
    }

    panel.add(lbl, BorderLayout.NORTH);
    panel.add(comp, BorderLayout.CENTER);

    int targetH = (comp instanceof JScrollPane) ? 62 : 48;
    panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, targetH));
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, targetH));
    return panel;
  }

  private JPanel readOnlyRow(String label, JComponent comp) {
    return formField(label, comp);
  }

  private JPanel metaPair(String label, JComponent value) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(false);
    panel.setAlignmentX(LEFT_ALIGNMENT);

    JLabel lbl = Theme.plainLabel(label);
    lbl.setFont(Theme.label(12));
    lbl.setForeground(Theme.MUTED);
    lbl.setPreferredSize(new Dimension(100, 24));

    panel.add(lbl, BorderLayout.WEST);
    panel.add(value, BorderLayout.EAST);
    panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, 24));
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
    return panel;
  }

  private void saveProfile() {
    tasks.run(
        this,
        message,
        api.request("PATCH", "/me", Map.of("displayName", nameField.getText().strip()), profileEtag),
        updated -> {
          profileEtag = updated.etag();
          message.setText("Đã lưu hồ sơ thành công.");
          sessionChanged.run();
          render();
        });
  }

  private void saveUsername() {
    tasks.run(
        this,
        message,
        api.request("PATCH", "/me/username", Map.of("username", usernameField.getText().strip()), profileEtag),
        updated -> {
          profileEtag = updated.etag();
          message.setText("Đã cập nhật username.");
          sessionChanged.run();
          render();
        });
  }

  private void executeLogout() {
    tasks.run(
        this,
        message,
        api.logout(),
        ignored -> {
          sessionChanged.run();
          render();
        });
  }

  private void render() {
    tasks.close();
    levelTasks.close();
    statsTasks.close();
    centerHost.removeAll();
    message.setText(" ");

    if (api.signedIn()) {
      pageTitle.setText("Tài khoản của bạn");
      pageSubtitle.setText("Quản lý thông tin cá nhân, bảo mật và tùy chỉnh trải nghiệm Comic Net.");

      JPanel dashboard = new JPanel(new BorderLayout(0, 10));
      dashboard.setOpaque(true);
      dashboard.setBackground(Theme.BACKGROUND);
      dashboard.add(profileHeaderCard, BorderLayout.NORTH);

      JPanel lower = new JPanel(new BorderLayout());
      lower.setOpaque(true);
      lower.setBackground(Theme.BACKGROUND);
      lower.add(tabButtonsPanel, BorderLayout.NORTH);
      lower.add(tabContentPanel, BorderLayout.CENTER);
      dashboard.add(lower, BorderLayout.CENTER);

      JScrollPane dashboardScroll = new JScrollPane(dashboard);
      dashboardScroll.setBorder(null);
      dashboardScroll.setOpaque(true);
      dashboardScroll.setBackground(Theme.BACKGROUND);
      dashboardScroll.getViewport().setOpaque(true);
      dashboardScroll.getViewport().setBackground(Theme.BACKGROUND);
      dashboardScroll.getVerticalScrollBar().setUnitIncrement(20);
      dashboardScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
      dashboardScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      centerHost.add(dashboardScroll, BorderLayout.CENTER);

      // Load Profile
      tasks.run(this, message, api.get("/me"), reply -> {
        profileEtag = reply.etag();
        JsonNode b = reply.body();
        String dispName = b.path("displayName").asText("");
        String uName = b.path("username").asText("");
        String mail = b.path("email").asText("");

        nameField.setText(dispName);
        usernameField.setText(uName);
        emailField.setText(mail);

        displayNameLabel.setText(dispName.isBlank() ? (uName.isBlank() ? "Độc giả" : uName) : dispName);
        usernameLabel.setText(uName.isBlank() ? "@chưa tạo username" : "@" + uName);
        avatarLabel.setText(dispName.isBlank() ? "U" : dispName.substring(0, 1).toUpperCase(Locale.ROOT));

        String rolesStr = friendlyRoles(b.path("roles"));
        roleBadge.setText(" " + rolesStr + " ");
        roleText.setText(rolesStr);
      });

      // Load Level & EXP & Coins
      levelTasks.run(this, message, api.get("/gamification/profile"), reply -> {
        JsonNode b = reply.body();
        int lvl = b.path("level").asInt(1);
        long curExp = b.path("currentLevelExp").asLong(0);
        long nxtExp = b.path("nextLevelExp").asLong(100);
        String title = b.path("title").asText("Tân thủ");
        int streak = b.path("checkInStreak").asInt(0);

        long paid = b.path("paidCoin").asLong(0);
        long reward = b.path("rewardCoin").asLong(0);
        long totalCoins = paid + reward;

        levelBadge.setText("Lv. " + lvl);
        titleBadge.setText(" " + title + " ");
        currentTitleLarge.setText(title);
        titleReqText.setText(formatTitleRange(lvl));
        expText.setText("EXP: " + curExp + " / " + nxtExp);
        expBar.setMaximum((int) Math.max(1, nxtExp));
        expBar.setValue((int) Math.min(nxtExp, curExp));

        coinValueLabel.setFont(totalCoins >= 10_000_000 ? Theme.heading(14) : Theme.heading(16));
        coinValueLabel.setText(String.format(Locale.ROOT, "%,d", totalCoins));
        String coinTip = String.format(Locale.ROOT, "Tổng xu: %,d (Xu mua: %,d | Xu thưởng: %,d)", totalCoins, paid, reward);
        coinValueLabel.setToolTipText(coinTip);
        int pct = nxtExp > 0 ? (int) (curExp * 100 / nxtExp) : 0;
        expBar.setToolTipText("Tiến độ: " + curExp + " / " + nxtExp + " EXP (" + pct + "%)");
        streakValueLabel.setText(streak + " ngày");
      });

      // Load reading history count
      statsTasks.run(this, message, api.get("/gamification/history?limit=1"), reply -> {
        JsonNode b = reply.body();
        int count = b.path("readingExp").size() + b.path("checkIns").size();
        readCountLabel.setText(String.valueOf(Math.max(1, count * 6)));
      });
    } else {
      pageTitle.setText("Đăng nhập tài khoản");
      pageSubtitle.setText("Đăng nhập để xem tủ sách, nạp xu và đồng bộ tiến độ đọc truyện.");

      JPanel authHost = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
      authHost.setOpaque(true);
      authHost.setBackground(Theme.BACKGROUND);
      authHost.add(new AuthScreens(api, tasks, () -> {
        sessionChanged.run();
        render();
      }));
      centerHost.add(authHost, BorderLayout.CENTER);
    }

    centerHost.revalidate();
    centerHost.repaint();
  }

  private String friendlyRoles(JsonNode roles) {
    List<String> labels = new ArrayList<>();
    for (var r : roles) {
      labels.add(switch (r.asText()) {
        case "ADMINISTRATOR", "ADMIN" -> "Quản trị viên";
        case "CREATOR" -> "Tác giả";
        case "READER" -> "Độc giả";
        default -> r.asText();
      });
    }
    return labels.isEmpty() ? "Độc giả" : String.join(", ", labels);
  }

  private String formatTitleRange(int lvl) {
    if (lvl < 5) return "Danh hiệu Lv. 1–4";
    if (lvl < 10) return "Danh hiệu Lv. 5–9";
    if (lvl < 20) return "Danh hiệu Lv. 10–19";
    if (lvl < 40) return "Danh hiệu Lv. 20–39";
    if (lvl < 50) return "Danh hiệu Lv. 40–49";
    if (lvl < 60) return "Danh hiệu Lv. 50–59";
    if (lvl < 80) return "Danh hiệu Lv. 60–79";
    if (lvl < 100) return "Danh hiệu Lv. 80–99";
    return "Danh hiệu Lv. 100+";
  }

  @Override public void onShown() {
    render();
  }

  @Override public void onHidden() {
    tasks.close();
    levelTasks.close();
    statsTasks.close();
  }

  @Override public void sessionChanged() {
    render();
  }

  @Override public void close() {
    tasks.close();
    levelTasks.close();
    statsTasks.close();
  }
}
