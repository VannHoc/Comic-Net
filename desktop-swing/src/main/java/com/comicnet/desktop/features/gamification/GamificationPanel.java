package com.comicnet.desktop.features.gamification;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.MainFrame;
import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiIcons;
import com.comicnet.desktop.ui.UiTasks;
import com.comicnet.desktop.ui.Workspace;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

public final class GamificationPanel extends JPanel implements Workspace {
  private static final String[] PRIZE_LABELS = {
    "+10 Xu", "+50 EXP", "+20 Xu", "+100 EXP",
    "+50 Xu", "+200 EXP", "Jackpot!", "May mắn"
  };

  private static final Color[] SLICE_COLORS = {
    new Color(0x6366F1), // Indigo
    new Color(0x10B981), // Emerald
    new Color(0xF59E0B), // Amber
    new Color(0x3B82F6), // Blue
    new Color(0x8B5CF6), // Purple
    new Color(0xEC4899), // Pink
    new Color(0xEF4444), // Red Jackpot
    new Color(0x6B7280)  // Gray Consolation
  };

  private static final int[] CHECK_IN_COIN_VALUES = {5, 10, 20, 30, 50, 70, 100};

  private final ApiClient api;
  private final Runnable onCoinsChanged;
  // Use separate UiTasks instances so profile, history, and actions never cancel each other
  private final UiTasks profileTasks = new UiTasks();
  private final UiTasks historyTasks = new UiTasks();
  private final UiTasks actionTasks = new UiTasks();

  // Header components
  private final JLabel levelBadge = Theme.plainLabel("Cấp 1");
  private final JLabel titleLabel = Theme.plainLabel("Tân thủ");
  private final JLabel expText = Theme.plainLabel("0 / 100 EXP");
  private final JProgressBar expBar = new JProgressBar(0, 100);
  private final JLabel coinBalance = Theme.plainLabel("Xu mua: 0  |  Xu thưởng: 0");

  // Check-in components
  private final JLabel streakLabel = Theme.plainLabel("Chuỗi điểm danh: 0 ngày");
  private final JButton checkInButton = new JButton("ĐIỂM DANH HÔM NAY");
  private final JLabel checkInStatus = Theme.plainLabel(" ");
  private final JPanel dayCardsPanel = new JPanel(new GridLayout(1, 7, 8, 0));
  private final DayCard[] dayCards = new DayCard[7];

  // Lucky wheel components
  private final LuckyWheelCanvas wheelCanvas = new LuckyWheelCanvas();
  private final JButton spinButton = new JButton("QUAY NGAY (Miễn phí)");
  private final JLabel spinStatus = Theme.plainLabel(" ");
  private boolean spinning = false;
  private long secondsUntilFreeSpin = 0;
  private long cachedRewardCoin = 0;
  private final Timer countdownTimer;

  // History tables
  private final DefaultTableModel checkInHistoryModel = new DefaultTableModel(
      new Object[] {"Ngày", "Ngày trong chuỗi", "EXP thưởng", "Xu thưởng"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return false; }
  };
  private final DefaultTableModel spinHistoryModel = new DefaultTableModel(
      new Object[] {"Phần thưởng", "Loại", "Số lượng", "Phí xu", "Thời gian"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return false; }
  };
  private final DefaultTableModel readingExpHistoryModel = new DefaultTableModel(
      new Object[] {"Chương đã đọc", "EXP nhận được", "Thời gian"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return false; }
  };

  public GamificationPanel(ApiClient api) {
    this(api, null);
  }

  public GamificationPanel(ApiClient api, Runnable onCoinsChanged) {
    super(new BorderLayout(0, 16));
    this.api = api;
    this.onCoinsChanged = onCoinsChanged;
    setOpaque(false);
    setBorder(new EmptyBorder(20, 24, 20, 24));

    // Top Header: Level & Profile Card
    add(buildHeaderCard(), BorderLayout.NORTH);

    // Center: Split Check-In (Left) & Lucky Wheel (Right)
    JPanel centerSplit = new JPanel(new GridLayout(1, 2, 20, 0));
    centerSplit.setOpaque(false);
    centerSplit.add(buildCheckInCard());
    centerSplit.add(buildLuckyWheelCard());
    add(centerSplit, BorderLayout.CENTER);

    // Bottom: History TabbedPane
    add(buildHistorySection(), BorderLayout.SOUTH);

    // Setup 1-second countdown timer for free spin 12h
    countdownTimer = new Timer(1000, e -> onCountdownTick());
    countdownTimer.start();
  }

  public static int expToNextLevel(int level) {
    if (level < 1) return 100;
    if (level < 5) return 100; // Lv. 1..4: 100 EXP/cấp
    if (level < 10) return 200; // Lv. 5..9: 200 EXP/cấp
    if (level < 20) return 500; // Lv. 10..19: 500 EXP/cấp
    if (level < 40) return 1000; // Lv. 20..39: 1000 EXP/cấp
    if (level < 50) return 1200; // Lv. 40..49: 1200 EXP/cấp
    if (level < 60) return 1500; // Lv. 50..59: 1500 EXP/cấp
    if (level < 80) return 3000; // Lv. 60..79: 3000 EXP/cấp
    return 5000; // Lv. 80..100+: 5000 EXP/cấp
  }

  public static long cumulativeExpForLevel(int targetLevel) {
    long total = 0;
    for (int lvl = 1; lvl < targetLevel; lvl++) {
      total += expToNextLevel(lvl);
    }
    return total;
  }

  public void updateHeaderCard(int level, long exp, String title, long paid, long reward) {
    long levelBaseExp = cumulativeExpForLevel(level);
    long nxtLvlExp = expToNextLevel(level);
    long curLvlExp = Math.max(0, exp - levelBaseExp);

    levelBadge.setText("Cấp " + level);
    titleLabel.setText(title);
    if (paid >= 0 && reward >= 0) {
      cachedRewardCoin = reward;
      coinBalance.setText("Xu mua: " + paid + "  |  Xu thưởng: " + reward);
    }
    expText.setText(curLvlExp + " / " + nxtLvlExp + " EXP (Tổng: " + exp + ")");
    expBar.setMaximum((int) Math.max(1, nxtLvlExp));
    expBar.setValue((int) Math.min(nxtLvlExp, curLvlExp));
    int percent = (int) (curLvlExp * 100 / Math.max(1, nxtLvlExp));
    expBar.setString(curLvlExp + " / " + nxtLvlExp + " EXP (" + percent + "%)");
  }

  private JPanel buildHeaderCard() {
    JPanel card = new JPanel(new BorderLayout(16, 12));
    card.setBackground(Theme.SURFACE);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1),
        new EmptyBorder(16, 20, 16, 20)));

    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
    left.setOpaque(false);

    levelBadge.setFont(Theme.heading(24));
    levelBadge.setForeground(Theme.ACTION);
    levelBadge.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.ACTION, 2),
        new EmptyBorder(4, 12, 4, 12)));

    JPanel info = new JPanel(new GridLayout(2, 1, 0, 4));
    info.setOpaque(false);
    titleLabel.setFont(Theme.heading(18));
    coinBalance.setFont(Theme.label(13));
    coinBalance.setForeground(Theme.MUTED);
    info.add(titleLabel);
    info.add(coinBalance);

    left.add(levelBadge);
    left.add(info);

    JPanel right = new JPanel(new BorderLayout(0, 4));
    right.setOpaque(false);
    right.setPreferredSize(new Dimension(320, 44));
    expText.setHorizontalAlignment(SwingConstants.RIGHT);
    expText.setFont(Theme.label(12));
    expText.setForeground(Theme.MUTED);
    expBar.setStringPainted(true);
    expBar.setForeground(new Color(0x10B981));
    right.add(expText, BorderLayout.NORTH);
    right.add(expBar, BorderLayout.CENTER);

    card.add(left, BorderLayout.WEST);
    card.add(right, BorderLayout.EAST);
    return card;
  }

  private JPanel buildCheckInCard() {
    JPanel card = new JPanel(new BorderLayout(0, 14));
    card.setBackground(Theme.SURFACE);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1),
        new EmptyBorder(16, 18, 16, 18)));

    JPanel top = new JPanel(new BorderLayout());
    top.setOpaque(false);
    JLabel heading = Theme.plainLabel("Điểm danh nhận quà");
    heading.setFont(Theme.heading(16));
    streakLabel.setFont(Theme.heading(13));
    streakLabel.setForeground(Theme.PRIMARY);
    streakLabel.setIcon(UiIcons.fire(16, Theme.PRIMARY));
    top.add(heading, BorderLayout.WEST);
    top.add(streakLabel, BorderLayout.EAST);
    card.add(top, BorderLayout.NORTH);

    // 7 Day cards
    dayCardsPanel.setOpaque(false);
    for (int i = 0; i < 7; i++) {
      dayCards[i] = new DayCard(i + 1);
      dayCardsPanel.add(dayCards[i]);
    }
    card.add(dayCardsPanel, BorderLayout.CENTER);

    JPanel bottom = new JPanel(new BorderLayout(0, 6));
    bottom.setOpaque(false);
    checkInButton.setFont(Theme.brand(15));
    checkInButton.setBackground(Theme.ACTION);
    checkInButton.setForeground(Color.WHITE);
    checkInButton.setFocusPainted(false);
    checkInButton.putClientProperty("JButton.buttonType", "roundRect");
    checkInButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    checkInButton.addActionListener(e -> executeCheckIn());

    checkInStatus.setHorizontalAlignment(SwingConstants.CENTER);
    checkInStatus.setFont(Theme.label(13));
    bottom.add(checkInButton, BorderLayout.CENTER);
    bottom.add(checkInStatus, BorderLayout.SOUTH);
    card.add(bottom, BorderLayout.SOUTH);

    return card;
  }

  private JPanel buildLuckyWheelCard() {
    JPanel card = new JPanel(new BorderLayout(0, 12));
    card.setBackground(Theme.SURFACE);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1),
        new EmptyBorder(16, 18, 16, 18)));

    JLabel heading = Theme.plainLabel("Vòng quay may mắn");
    heading.setFont(Theme.heading(16));
    card.add(heading, BorderLayout.NORTH);

    // Center Canvas
    JPanel canvasContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    canvasContainer.setOpaque(false);
    wheelCanvas.setPreferredSize(new Dimension(220, 220));
    canvasContainer.add(wheelCanvas);
    card.add(canvasContainer, BorderLayout.CENTER);

    JPanel bottom = new JPanel(new BorderLayout(0, 6));
    bottom.setOpaque(false);
    spinButton.setFont(Theme.brand(15));
    spinButton.setBackground(Theme.ACTION);
    spinButton.setForeground(Color.WHITE);
    spinButton.setFocusPainted(false);
    spinButton.putClientProperty("JButton.buttonType", "roundRect");
    spinButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    spinButton.addActionListener(e -> executeSpin());

    spinStatus.setHorizontalAlignment(SwingConstants.CENTER);
    spinStatus.setFont(Theme.label(13));
    bottom.add(spinButton, BorderLayout.CENTER);
    bottom.add(spinStatus, BorderLayout.SOUTH);
    card.add(bottom, BorderLayout.SOUTH);

    return card;
  }

  private JComponent buildHistorySection() {
    JTabbedPane tabs = new JTabbedPane();
    tabs.putClientProperty("JTabbedPane.tabType", "card");
    tabs.setFont(Theme.brand(13));
    tabs.setPreferredSize(new Dimension(100, 160));

    JTable checkInTable = new JTable(checkInHistoryModel);
    styleHistoryTable(checkInTable);
    tabs.addTab("Lịch sử điểm danh", UiIcons.check(14, Theme.MUTED), new JScrollPane(checkInTable));

    JTable spinTable = new JTable(spinHistoryModel);
    styleHistoryTable(spinTable);
    tabs.addTab("Lịch sử quay thưởng", UiIcons.trophy(14, Theme.MUTED), new JScrollPane(spinTable));

    JTable readingExpTable = new JTable(readingExpHistoryModel);
    styleHistoryTable(readingExpTable);
    tabs.addTab("Thưởng đọc truyện", UiIcons.bookOpen(14, Theme.MUTED), new JScrollPane(readingExpTable));

    return tabs;
  }

  private static void styleHistoryTable(JTable table) {
    Theme.plainTable(table);
    table.setRowHeight(36);
    table.setFont(Theme.label(13));
    table.setSelectionBackground(Theme.ACTION_TINT);
    table.setSelectionForeground(Theme.TEXT);
    if (table.getTableHeader() != null) {
      table.getTableHeader().setFont(Theme.brand(12));
      table.getTableHeader().setBackground(Theme.ELEVATED);
      table.getTableHeader().setForeground(Theme.MUTED);
      table.getTableHeader().setPreferredSize(new Dimension(100, 32));
    }
  }

  private void onCountdownTick() {
    if (secondsUntilFreeSpin > 0) {
      secondsUntilFreeSpin--;
      if (secondsUntilFreeSpin == 0) {
        if (!spinning) {
          spinButton.setText("QUAY NGAY (Miễn phí)");
          spinButton.setEnabled(true);
          spinStatus.setText("Lượt quay miễn phí đã sẵn sàng!");
        }
      } else if (!spinning) {
        long h = secondsUntilFreeSpin / 3600;
        long m = (secondsUntilFreeSpin % 3600) / 60;
        long s = secondsUntilFreeSpin % 60;
        spinStatus.setText(String.format("Lượt quay miễn phí tiếp theo sau: %02d:%02d:%02d", h, m, s));
      }
    }
  }

  private void executeCheckIn() {
    if (!api.signedIn()) {
      checkInStatus.setText("Vui lòng đăng nhập để điểm danh.");
      return;
    }
    checkInButton.setEnabled(false);
    actionTasks.run(
        this,
        checkInStatus,
        api.request("POST", "/gamification/check-in", null, null),
        reply -> {
          JsonNode b = reply.body();
          String msg = b.path("message").asText("Điểm danh thành công!");
          checkInStatus.setText(msg);

          long totalExp = b.path("totalExp").asLong(0);
          int level = b.path("level").asInt(1);
          String title = b.path("title").asText("Tân thủ");
          int streak = b.path("streak").asInt(1);
          streakLabel.setText("● Chuỗi: " + streak + " ngày");

          // Instantly update top header card
          updateHeaderCard(level, totalExp, title, -1, -1);

          if (onCoinsChanged != null) onCoinsChanged.run();
          Container top = getTopLevelAncestor();
          if (top instanceof MainFrame mf) mf.updateTopBarUser();

          // Refresh profile and history concurrently without cancelling
          reloadProfile();
          reloadHistory();
        },
        err -> {
          checkInButton.setEnabled(true);
          Throwable cause = ApiClient.unwrap(err);
          checkInStatus.setText(cause instanceof ApiClient.ApiFailure fail
              ? fail.getMessage()
              : "Lỗi kết nối khi điểm danh.");
        });
  }

  private void executeSpin() {
    if (!api.signedIn()) {
      spinStatus.setText("Vui lòng đăng nhập để quay thưởng.");
      return;
    }
    if (spinning) return;
    spinning = true;
    spinButton.setEnabled(false);
    spinStatus.setText("Đang quay...");

    actionTasks.run(
        this,
        spinStatus,
        api.request("POST", "/gamification/lucky-wheel/spin", null, null),
        reply -> {
          JsonNode b = reply.body();
          int prizeIndex = b.path("prizeIndex").asInt(0);
          String msg = b.path("message").asText();
          long totalExp = b.path("totalExp").asLong(0);
          int level = b.path("level").asInt(1);
          String title = b.path("title").asText("Tân thủ");
          long paid = b.path("paidCoin").asLong(0);
          long reward = b.path("rewardCoin").asLong(0);

          // Smooth animation to target slice
          wheelCanvas.spinTo(prizeIndex, () -> {
            spinning = false;
            spinButton.setEnabled(true);
            spinStatus.setText(msg);

            // Instantly update header UI from spin result (EXP, Level, Title, Coins)
            updateHeaderCard(level, totalExp, title, paid, reward);

            if (onCoinsChanged != null) onCoinsChanged.run();
            Container top = getTopLevelAncestor();
            if (top instanceof MainFrame mf) mf.refreshCoins(paid + reward);

            // Refresh both Profile and History from backend
            reloadProfile();
            reloadHistory();
          });
        },
        err -> {
          spinning = false;
          spinButton.setEnabled(true);
          Throwable cause = ApiClient.unwrap(err);
          spinStatus.setText(cause instanceof ApiClient.ApiFailure fail
              ? fail.getMessage()
              : "Lỗi kết nối khi quay thưởng.");
        });
  }

  private void reloadProfile() {
    if (!api.signedIn()) {
      levelBadge.setText("Khách");
      titleLabel.setText("Đăng nhập để xem cấp độ & điểm danh");
      expText.setText("—");
      expBar.setValue(0);
      coinBalance.setText("—");
      checkInButton.setEnabled(false);
      spinButton.setEnabled(false);
      return;
    }

    profileTasks.run(
        this,
        checkInStatus,
        api.get("/gamification/profile"),
        reply -> {
          JsonNode b = reply.body();
          int level = b.path("level").asInt(1);
          long exp = b.path("exp").asLong(0);
          String title = b.path("title").asText("Tân thủ");
          int streak = b.path("checkInStreak").asInt(0);
          boolean canCheckIn = b.path("canCheckInToday").asBoolean(true);
          int freeSpins = b.path("freeSpinsAvailable").asInt(0);
          secondsUntilFreeSpin = b.path("secondsUntilNextFreeSpin").asLong(0);
          long paid = b.path("paidCoin").asLong(0);
          long reward = b.path("rewardCoin").asLong(0);

          updateHeaderCard(level, exp, title, paid, reward);
          Container top = getTopLevelAncestor();
          if (top instanceof MainFrame mf) mf.refreshCoins(paid + reward);

          streakLabel.setText("Chuỗi điểm danh: " + streak + " ngày");
          checkInButton.setEnabled(canCheckIn);
          checkInButton.setText(canCheckIn ? "ĐIỂM DANH HÔM NAY" : "ĐÃ ĐIỂM DANH HÔM NAY");
          checkInButton.setBackground(canCheckIn ? Theme.ACTION : Theme.ELEVATED);
          checkInButton.setForeground(canCheckIn ? Color.WHITE : Theme.MUTED);

          int activeDay = ((streak - 1) % 7) + 1;
          for (int i = 0; i < 7; i++) {
            dayCards[i].updateState(i + 1, activeDay, !canCheckIn);
          }

          if (freeSpins > 0 || secondsUntilFreeSpin <= 0) {
            spinButton.setText("QUAY NGAY (Miễn phí)");
            spinButton.setEnabled(!spinning);
            if (!spinning) {
              spinStatus.setText("Lượt quay miễn phí đã sẵn sàng!");
            }
          } else {
            spinButton.setText("QUAY (5 Xu thưởng)");
            spinButton.setEnabled(!spinning && reward >= 5);
            if (!spinning) {
              long h = secondsUntilFreeSpin / 3600;
              long m = (secondsUntilFreeSpin % 3600) / 60;
              long s = secondsUntilFreeSpin % 60;
              spinStatus.setText(String.format("Lượt quay miễn phí tiếp theo sau: %02d:%02d:%02d", h, m, s));
            }
          }
        });
  }

  private void reloadHistory() {
    if (!api.signedIn()) return;

    historyTasks.run(
        this,
        checkInStatus,
        api.get("/gamification/history?limit=15"),
        reply -> {
          JsonNode b = reply.body();
          checkInHistoryModel.setRowCount(0);
          for (JsonNode c : b.path("checkIns")) {
            checkInHistoryModel.addRow(new Object[] {
                c.path("checkInDate").asText(),
                "Ngày " + c.path("dayInStreak").asInt(),
                "+" + c.path("expAwarded").asInt() + " EXP",
                "+" + c.path("rewardCoinAwarded").asInt() + " Xu"
            });
          }

          spinHistoryModel.setRowCount(0);
          for (JsonNode s : b.path("spins")) {
            spinHistoryModel.addRow(new Object[] {
                s.path("label").asText(),
                s.path("rewardType").asText(),
                s.path("amount").asInt(),
                s.path("costCoin").asInt() == 0 ? "Miễn phí" : s.path("costCoin").asInt() + " xu",
                s.path("createdAt").asText()
            });
          }

          readingExpHistoryModel.setRowCount(0);
          for (JsonNode r : b.path("readingExp")) {
            readingExpHistoryModel.addRow(new Object[] {
                "Chương #" + r.path("chapterId").asLong(),
                "+" + r.path("expAwarded").asInt() + " EXP",
                r.path("createdAt").asText()
            });
          }
        });
  }

  private void reload() {
    reloadProfile();
    reloadHistory();
  }

  @Override
  public void onShown() {
    reload();
  }

  @Override
  public void onHidden() {
    profileTasks.close();
    historyTasks.close();
    actionTasks.close();
    wheelCanvas.stopTimer();
  }

  @Override
  public void sessionChanged() {
    profileTasks.close();
    historyTasks.close();
    actionTasks.close();
    reload();
  }

  @Override
  public void close() {
    profileTasks.close();
    historyTasks.close();
    actionTasks.close();
    if (countdownTimer != null && countdownTimer.isRunning()) {
      countdownTimer.stop();
    }
    wheelCanvas.stopTimer();
  }

  // Visual card for each of 7 days in check-in with custom vector Gold Coin
  private static final class DayCard extends JPanel {
    private final int day;
    private final JLabel dayLabel = Theme.plainLabel("");
    private final CoinIconCanvas iconCanvas = new CoinIconCanvas();
    private final JLabel rewardLabel = Theme.plainLabel("");
    private final JLabel badgeLabel = Theme.plainLabel("");

    DayCard(int day) {
      super(new BorderLayout(0, 4));
      this.day = day;
      setBackground(Theme.ELEVATED);
      setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(Theme.BORDER, 1),
          new EmptyBorder(8, 4, 8, 4)));

      dayLabel.setHorizontalAlignment(SwingConstants.CENTER);
      dayLabel.setFont(Theme.heading(12));
      dayLabel.setText("Ngày " + day);

      iconCanvas.setPreferredSize(new Dimension(38, 38));

      rewardLabel.setHorizontalAlignment(SwingConstants.CENTER);
      rewardLabel.setFont(Theme.heading(11));
      rewardLabel.setForeground(Theme.ACTION);
      rewardLabel.setText("+" + CHECK_IN_COIN_VALUES[day - 1] + " xu");

      badgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
      badgeLabel.setFont(Theme.label(10));
      badgeLabel.setForeground(Theme.MUTED);
      badgeLabel.setText("Chưa mở");

      JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
      center.setOpaque(false);
      center.add(iconCanvas);

      JPanel bottom = new JPanel(new GridLayout(2, 1, 0, 2));
      bottom.setOpaque(false);
      bottom.add(rewardLabel);
      bottom.add(badgeLabel);

      add(dayLabel, BorderLayout.NORTH);
      add(center, BorderLayout.CENTER);
      add(bottom, BorderLayout.SOUTH);
    }

    void updateState(int cardDay, int activeDay, boolean checkedInToday) {
      if (checkedInToday && cardDay <= activeDay) {
        // Claimed
        setBackground(new Color(0xDCFCE7)); // Light emerald
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0x22C55E), 1),
            new EmptyBorder(8, 4, 8, 4)));
        iconCanvas.setState(CoinIconCanvas.State.CLAIMED);
        rewardLabel.setForeground(new Color(0x16A34A));
        badgeLabel.setText("Đã nhận");
        badgeLabel.setForeground(new Color(0x16A34A));
      } else if (!checkedInToday && cardDay == activeDay + 1) {
        // Today target
        setBackground(new Color(0xFEF3C7)); // Light amber
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xF59E0B), 2),
            new EmptyBorder(7, 3, 7, 3)));
        iconCanvas.setState(CoinIconCanvas.State.TODAY);
        rewardLabel.setForeground(new Color(0xD97706));
        badgeLabel.setText("Hôm nay");
        badgeLabel.setForeground(new Color(0xD97706));
      } else {
        // Future day
        setBackground(Theme.ELEVATED);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(8, 4, 8, 4)));
        iconCanvas.setState(CoinIconCanvas.State.UPCOMING);
        rewardLabel.setForeground(Theme.TEXT);
        badgeLabel.setText("Chưa mở");
        badgeLabel.setForeground(Theme.MUTED);
      }
    }
  }

  // Vector drawn Gold Coin or Claimed Checkmark
  private static final class CoinIconCanvas extends JComponent {
    enum State { CLAIMED, TODAY, UPCOMING }
    private State state = State.UPCOMING;

    CoinIconCanvas() {
      setOpaque(false);
    }

    void setState(State s) {
      this.state = s;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

      int w = getWidth(), h = getHeight();
      int size = Math.min(w, h) - 4;
      int x = (w - size) / 2;
      int y = (h - size) / 2;

      if (state == State.CLAIMED) {
        // Green circle with white checkmark
        g2.setColor(new Color(0x22C55E));
        g2.fillOval(x, y, size, size);
        g2.setColor(new Color(0x16A34A));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(x, y, size, size);

        // White checkmark
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = x + size / 2;
        int cy = y + size / 2;
        Path2D check = new Path2D.Float();
        check.moveTo(cx - 7, cy);
        check.lineTo(cx - 2, cy + 5);
        check.lineTo(cx + 7, cy - 5);
        g2.draw(check);
      } else {
        // Gold Coin
        // Drop shadow / glow for TODAY
        if (state == State.TODAY) {
          g2.setColor(new Color(0xF5, 0x9E, 0x0B, 80));
          g2.fillOval(x - 2, y - 2, size + 4, size + 4);
        }

        // Outer coin rim
        GradientPaint rimGrad = new GradientPaint(
            x, y, new Color(0xFBBF24),
            x + size, y + size, new Color(0xB45309));
        g2.setPaint(rimGrad);
        g2.fillOval(x, y, size, size);

        // Outer border stroke
        g2.setColor(new Color(0xD97706));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(x, y, size, size);

        // Inner coin face
        int innerMargin = 3;
        int inSize = size - innerMargin * 2;
        int inX = x + innerMargin;
        int inY = y + innerMargin;
        GradientPaint faceGrad = new GradientPaint(
            inX, inY, new Color(0xFEF08A),
            inX + inSize, inY + inSize, new Color(0xF59E0B));
        g2.setPaint(faceGrad);
        g2.fillOval(inX, inY, inSize, inSize);

        // Inner thin ring
        g2.setColor(new Color(0xFCD34D));
        g2.setStroke(new BasicStroke(1f));
        g2.drawOval(inX + 2, inY + 2, inSize - 4, inSize - 4);

        // Center coin text "XU"
        g2.setColor(new Color(0x92400E));
        g2.setFont(Theme.heading(size >= 34 ? 11 : 10));
        FontMetrics fm = g2.getFontMetrics();
        String txt = "XU";
        int tw = fm.stringWidth(txt);
        int th = fm.getAscent() - 2;
        g2.drawString(txt, x + (size - tw) / 2, y + (size + th) / 2);
      }

      g2.dispose();
    }
  }

  // Custom Animated Lucky Wheel Component
  private static final class LuckyWheelCanvas extends JPanel {
    private double currentAngle = 0; // Current rotation in radians
    private Timer animTimer;

    LuckyWheelCanvas() {
      setOpaque(false);
    }

    void spinTo(int targetSliceIndex, Runnable onFinished) {
      stopTimer();

      // In 8 slices: slice 0 starts at angle 0. Slice angle is PI / 4.
      // Arrow is at the top (angle -PI/2 or 3*PI/2).
      // Calculate target rotation angle so that targetSlice lands at the top arrow.
      double sliceAngle = 2 * Math.PI / 8.0;
      double sliceCenterAngle = targetSliceIndex * sliceAngle + sliceAngle / 2.0;
      double targetAngleAtTop = 1.5 * Math.PI - sliceCenterAngle;

      // Add 5 full rotations (10*PI)
      double totalRotations = 5 * 2 * Math.PI;
      double finalAngle = totalRotations + targetAngleAtTop;
      double startAngle = currentAngle % (2 * Math.PI);
      double distance = finalAngle - startAngle;

      long startTime = System.currentTimeMillis();
      long duration = 2400; // 2.4 seconds

      animTimer = new Timer(16, e -> {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= duration) {
          currentAngle = finalAngle;
          stopTimer();
          repaint();
          if (onFinished != null) onFinished.run();
          return;
        }

        // Cubic ease-out: progress = 1 - (1 - t)^3
        double t = (double) elapsed / duration;
        double progress = 1.0 - Math.pow(1.0 - t, 3);
        currentAngle = startAngle + distance * progress;
        repaint();
      });
      animTimer.start();
    }

    void stopTimer() {
      if (animTimer != null && animTimer.isRunning()) {
        animTimer.stop();
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      int width = getWidth(), height = getHeight();
      int size = Math.min(width, height) - 24;
      int cx = width / 2, cy = height / 2;
      int radius = size / 2;

      // Draw Wheel Slices
      double sliceAngle = 2 * Math.PI / 8.0;
      for (int i = 0; i < 8; i++) {
        double a1 = currentAngle + i * sliceAngle;

        g2.setColor(SLICE_COLORS[i % SLICE_COLORS.length]);
        g2.fill(new Arc2D.Double(cx - radius, cy - radius, size, size,
            -Math.toDegrees(a1), -Math.toDegrees(sliceAngle), Arc2D.PIE));

        // Draw Slice Text
        double textAngle = a1 + sliceAngle / 2.0;
        int tx = (int) (cx + (radius * 0.65) * Math.cos(textAngle));
        int ty = (int) (cy + (radius * 0.65) * Math.sin(textAngle));

        g2.setColor(Color.WHITE);
        g2.setFont(Theme.brand(11));
        FontMetrics fm = g2.getFontMetrics();
        String label = PRIZE_LABELS[i];
        int textW = fm.stringWidth(label);
        g2.drawString(label, tx - textW / 2, ty + fm.getAscent() / 2 - 2);
      }

      // Wheel Rim
      g2.setColor(new Color(0xD1D5DB));
      g2.setStroke(new BasicStroke(3));
      g2.drawOval(cx - radius, cy - radius, size, size);

      // Center Hub
      int hubSize = 36;
      g2.setColor(Color.WHITE);
      g2.fillOval(cx - hubSize / 2, cy - hubSize / 2, hubSize, hubSize);
      g2.setColor(Theme.ACTION);
      g2.setStroke(new BasicStroke(2));
      g2.drawOval(cx - hubSize / 2, cy - hubSize / 2, hubSize, hubSize);

      g2.setColor(Theme.TEXT);
      g2.setFont(Theme.brand(10));
      FontMetrics hubFm = g2.getFontMetrics();
      String spinText = "SPIN";
      g2.drawString(spinText, cx - hubFm.stringWidth(spinText) / 2, cy + hubFm.getAscent() / 2 - 2);

      // Top Indicator Arrow / Needle
      int arrowY = cy - radius - 2;
      Polygon needle = new Polygon();
      needle.addPoint(cx, arrowY + 14);
      needle.addPoint(cx - 8, arrowY - 2);
      needle.addPoint(cx + 8, arrowY - 2);

      g2.setColor(Theme.ACTION);
      g2.fill(needle);
      g2.setColor(Color.WHITE);
      g2.draw(needle);

      g2.dispose();
    }
  }
}
