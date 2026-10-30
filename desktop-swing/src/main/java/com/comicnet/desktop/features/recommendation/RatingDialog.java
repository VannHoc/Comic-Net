package com.comicnet.desktop.features.recommendation;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import tools.jackson.databind.JsonNode;

/**
 * Modern Manga-style Rating & Review Dialog conforming to Design System V1.0.
 * Features interactive star rating picker, review cards list, and real-time synchronization.
 */
public final class RatingDialog extends JDialog {
  private final ApiClient api;
  private final String comic;
  private final Runnable onRatingUpdated;

  private final JPanel body = new JPanel(new BorderLayout(0, 14));
  private final JLabel message = Theme.plainLabel(" ");

  // Overview section
  private final JLabel bigScoreLabel = new JLabel("0.0");
  private final JPanel overviewStars = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
  private final JLabel totalCountLabel = Theme.plainLabel("0 lượt đánh giá");
  private final JPanel distPanel = new JPanel(new GridLayout(5, 1, 0, 4));

  // My Rating section
  private final StarRatingPicker starPicker = new StarRatingPicker();
  private final JTextArea reviewArea = new JTextArea(3, 40);
  private final PrimaryButton saveBtn = new PrimaryButton("Gửi đánh giá");
  private final JButton deleteBtn = new JButton("Xóa đánh giá của tôi");
  private final JLabel myRatingStatus = Theme.plainLabel("Nhấn vào các ngôi sao để đánh giá");

  // Reviews List section
  private final JPanel reviewsContainer = new JPanel();
  private final JScrollPane reviewsScroll;
  private final JLabel pageLabel = Theme.plainLabel("Trang 1");
  private final JButton prevPageBtn = new JButton("← Trước");
  private final JButton nextPageBtn = new JButton("Sau →");
  private int currentPage = 0;
  private int totalPages = 1;

  private final UiTasks totals = new UiTasks(), mine = new UiTasks(), actions = new UiTasks(), reviewsTasks = new UiTasks();
  private ApiClient.Reply currentRatingReply;
  private AutoCloseable sessionListener;
  private boolean mineLoaded;

  public RatingDialog(Component owner, ApiClient api, String comic) {
    this(owner, api, comic, () -> {});
  }

  public RatingDialog(Component owner, ApiClient api, String comic, Runnable onRatingUpdated) {
    super(SwingUtilities.getWindowAncestor(owner), "Đánh giá & Nhận xét truyện", Dialog.ModalityType.APPLICATION_MODAL);
    this.api = api;
    this.comic = comic;
    this.onRatingUpdated = onRatingUpdated;

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    AppIcons.applyTo(this);

    body.setBackground(Theme.BACKGROUND);
    body.setBorder(new EmptyBorder(16, 20, 16, 20));

    // 1. Top: Overview Card
    JPanel overviewCard = buildOverviewCard();
    body.add(overviewCard, BorderLayout.NORTH);

    // 2. Center: Split into [My Rating (Top)] and [Reviews List (Bottom)]
    JPanel centerPanel = new JPanel(new BorderLayout(0, 12));
    centerPanel.setOpaque(false);

    JPanel myRatingCard = buildMyRatingCard();
    centerPanel.add(myRatingCard, BorderLayout.NORTH);

    // Reviews List
    reviewsContainer.setLayout(new BoxLayout(reviewsContainer, BoxLayout.Y_AXIS));
    reviewsContainer.setOpaque(true);
    reviewsContainer.setBackground(Theme.SURFACE);

    reviewsScroll = new JScrollPane(reviewsContainer);
    reviewsScroll.setOpaque(false);
    reviewsScroll.getViewport().setOpaque(true);
    reviewsScroll.getViewport().setBackground(Theme.SURFACE);
    reviewsScroll.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1),
        new EmptyBorder(8, 12, 8, 12)));
    reviewsScroll.getVerticalScrollBar().setUnitIncrement(14);

    JPanel reviewsCard = new JPanel(new BorderLayout(0, 8));
    reviewsCard.setOpaque(false);
    JLabel reviewsHeading = new JLabel("Nhận xét từ cộng đồng");
    reviewsHeading.setFont(Theme.heading(14));
    reviewsHeading.setForeground(Theme.TEXT);
    reviewsCard.add(reviewsHeading, BorderLayout.NORTH);
    reviewsCard.add(reviewsScroll, BorderLayout.CENTER);

    // Pagination toolbar
    JPanel paginationBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    paginationBar.setOpaque(false);
    prevPageBtn.setFont(Theme.label(12));
    nextPageBtn.setFont(Theme.label(12));
    pageLabel.setFont(Theme.label(12));
    pageLabel.setForeground(Theme.MUTED);

    prevPageBtn.addActionListener(e -> {
      if (currentPage > 0) {
        currentPage--;
        loadReviews();
      }
    });
    nextPageBtn.addActionListener(e -> {
      if (currentPage + 1 < totalPages) {
        currentPage++;
        loadReviews();
      }
    });

    paginationBar.add(prevPageBtn);
    paginationBar.add(pageLabel);
    paginationBar.add(nextPageBtn);
    reviewsCard.add(paginationBar, BorderLayout.SOUTH);

    centerPanel.add(reviewsCard, BorderLayout.CENTER);
    body.add(centerPanel, BorderLayout.CENTER);

    // 3. Footer: Dialog Close & Status Message
    JPanel dialogFooter = new JPanel(new BorderLayout());
    dialogFooter.setOpaque(false);
    message.setFont(Theme.label(12));
    message.setForeground(Theme.MUTED);
    dialogFooter.add(message, BorderLayout.WEST);

    JButton closeDialogBtn = new JButton("Đóng");
    closeDialogBtn.setFont(Theme.label(13));
    closeDialogBtn.addActionListener(e -> dispose());
    dialogFooter.add(closeDialogBtn, BorderLayout.EAST);
    body.add(dialogFooter, BorderLayout.SOUTH);

    setContentPane(body);
    getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

    sessionListener = api.onSessionChanged(() -> SwingUtilities.invokeLater(this::dispose));
    addWindowListener(new WindowAdapter() {
      @Override public void windowClosed(WindowEvent event) {
        totals.close();
        mine.close();
        actions.close();
        reviewsTasks.close();
        try { sessionListener.close(); } catch (Exception ignored) {}
      }
    });

    setSize(720, 680);
    setMinimumSize(new Dimension(640, 560));
    setLocationRelativeTo(owner);
    reloadAll();
  }

  private JPanel buildOverviewCard() {
    JPanel card = new JPanel(new BorderLayout(24, 0)) {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.SURFACE);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    card.setOpaque(false);
    card.setBorder(new EmptyBorder(16, 20, 16, 20));

    // Left: Big Score + Stars + Count
    JPanel scoreBox = new JPanel();
    scoreBox.setLayout(new BoxLayout(scoreBox, BoxLayout.Y_AXIS));
    scoreBox.setOpaque(false);
    scoreBox.setPreferredSize(new Dimension(160, 85));

    bigScoreLabel.setFont(Theme.brand(34));
    bigScoreLabel.setForeground(Theme.TEXT);
    bigScoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    scoreBox.add(bigScoreLabel);

    overviewStars.setOpaque(false);
    overviewStars.setAlignmentX(Component.CENTER_ALIGNMENT);
    updateOverviewStars(0.0);
    scoreBox.add(overviewStars);
    scoreBox.add(Box.createVerticalStrut(4));

    totalCountLabel.setFont(Theme.label(12));
    totalCountLabel.setForeground(Theme.MUTED);
    totalCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    scoreBox.add(totalCountLabel);

    card.add(scoreBox, BorderLayout.WEST);

    // Right: Visual Distribution Bars (5★ down to 1★)
    distPanel.setOpaque(false);
    renderDistributionBars(0, 0, 0, 0, 0);
    card.add(distPanel, BorderLayout.CENTER);

    return card;
  }

  private void updateOverviewStars(double avg) {
    overviewStars.removeAll();
    int full = (int) Math.floor(avg);
    for (int i = 1; i <= 5; i++) {
      Color c = (i <= full) ? new Color(0xF5, 0x9E, 0x0B) : new Color(0xE2, 0xE8, 0xF0);
      overviewStars.add(new JLabel(UiIcons.star(16, c)));
    }
    overviewStars.revalidate();
    overviewStars.repaint();
  }

  private void renderDistributionBars(int s5, int s4, int s3, int s2, int s1) {
    distPanel.removeAll();
    int[] counts = {s5, s4, s3, s2, s1};
    int max = Math.max(1, s5 + s4 + s3 + s2 + s1);

    for (int i = 0; i < 5; i++) {
      int starNum = 5 - i;
      int cnt = counts[i];
      int pct = (int) Math.round((double) cnt * 100.0 / max);

      JPanel row = new JPanel(new BorderLayout(8, 0));
      row.setOpaque(false);

      JLabel starTag = new JLabel(String.valueOf(starNum), UiIcons.star(10, new Color(0xF5, 0x9E, 0x0B)), SwingConstants.LEFT);
      starTag.setIconTextGap(3);
      starTag.setFont(Theme.label(11));
      starTag.setForeground(Theme.MUTED);
      starTag.setPreferredSize(new Dimension(30, 14));
      row.add(starTag, BorderLayout.WEST);

      JProgressBar bar = new JProgressBar(0, 100);
      bar.setValue(pct);
      bar.setForeground(new Color(0xF5, 0x9E, 0x0B));
      bar.setBackground(new Color(0xF1, 0xF5, 0xF9));
      bar.setBorderPainted(false);
      bar.setPreferredSize(new Dimension(140, 8));
      row.add(bar, BorderLayout.CENTER);

      distPanel.add(row);
    }
    distPanel.revalidate();
    distPanel.repaint();
  }

  private JPanel buildMyRatingCard() {
    JPanel card = new JPanel(new BorderLayout(0, 10)) {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.SURFACE);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    card.setOpaque(false);
    card.setBorder(new EmptyBorder(14, 16, 14, 16));

    // Top Row: Section Title + Interactive Star Picker + Status text
    JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
    topRow.setOpaque(false);

    JLabel myRatingHeading = new JLabel("Đánh giá của bạn:");
    myRatingHeading.setFont(Theme.heading(14));
    myRatingHeading.setForeground(Theme.TEXT);
    topRow.add(myRatingHeading);

    starPicker.setOnRatingChanged(score -> {
      myRatingStatus.setText(getRatingLabel(score));
      saveBtn.setEnabled(api.signedIn());
    });
    topRow.add(starPicker);

    myRatingStatus.setFont(Theme.label(12));
    myRatingStatus.setForeground(new Color(0xD9, 0x77, 0x06));
    topRow.add(myRatingStatus);
    card.add(topRow, BorderLayout.NORTH);

    // Center: Review Text Area
    reviewArea.setLineWrap(true);
    reviewArea.setWrapStyleWord(true);
    reviewArea.setFont(Theme.label(13));
    reviewArea.setBorder(new EmptyBorder(6, 8, 6, 8));

    JScrollPane textScroll = new JScrollPane(reviewArea);
    textScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
    textScroll.setPreferredSize(new Dimension(0, 65));
    card.add(textScroll, BorderLayout.CENTER);

    // Bottom Action Bar
    JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    btnBar.setOpaque(false);

    saveBtn.setIcon(UiIcons.check(14, Color.WHITE));
    saveBtn.addActionListener(e -> saveRating());

    deleteBtn.setFont(Theme.label(13));
    deleteBtn.setIcon(UiIcons.trash(14, Theme.DANGER));
    deleteBtn.setForeground(Theme.DANGER);
    deleteBtn.addActionListener(e -> deleteRating());

    btnBar.add(deleteBtn);
    btnBar.add(saveBtn);
    card.add(btnBar, BorderLayout.SOUTH);

    return card;
  }

  private String getRatingLabel(int score) {
    return switch (score) {
      case 5 -> "Tuyệt vời (5/5 sao)";
      case 4 -> "Rất hay (4/5 sao)";
      case 3 -> "Bình thường (3/5 sao)";
      case 2 -> "Tạm được (2/5 sao)";
      case 1 -> "Không thích (1/5 sao)";
      default -> "Nhấn vào các ngôi sao để đánh giá";
    };
  }

  private void reloadAll() {
    loadSummary();
    loadMine();
    loadReviews();
  }

  private void loadSummary() {
    totals.run(body, totalCountLabel, api.get("/comics/" + comic + "/rating-summary"), reply -> {
      double avg = reply.body().path("average").asDouble(0.0);
      long count = reply.body().path("count").asLong(0);

      bigScoreLabel.setText(String.format(Locale.ROOT, "%.1f", avg));
      totalCountLabel.setText(String.format(Locale.ROOT, "%,d lượt đánh giá", count));
      updateOverviewStars(avg);
    });
  }

  private void loadMine() {
    mineLoaded = false;
    saveBtn.setEnabled(false);
    deleteBtn.setEnabled(false);

    if (!api.signedIn()) {
      myRatingStatus.setText("Đăng nhập để gửi đánh giá truyện.");
      starPicker.setRating(0);
      reviewArea.setEnabled(false);
      return;
    }

    reviewArea.setEnabled(true);
    mine.run(body, message, api.get("/me/ratings/" + comic), reply -> {
      currentRatingReply = reply;
      mineLoaded = true;
      int score = reply.body().path("score").asInt(5);
      String rev = reply.body().path("review").asText("");

      starPicker.setRating(score);
      reviewArea.setText(rev);
      myRatingStatus.setText(getRatingLabel(score));
      saveBtn.setText("Cập nhật đánh giá");
      saveBtn.setEnabled(true);
      deleteBtn.setEnabled(true);
      message.setText("Đã tải đánh giá của bạn.");
    }, error -> {
      var unwrapped = ApiClient.unwrap(error);
      if (unwrapped instanceof ApiClient.ApiFailure failure && failure.status() == 404) {
        currentRatingReply = null;
        mineLoaded = true;
        starPicker.setRating(0);
        reviewArea.setText("");
        myRatingStatus.setText("Nhấn vào các ngôi sao để đánh giá");
        saveBtn.setText("Gửi đánh giá");
        saveBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        message.setText("Bạn chưa đánh giá truyện này.");
      } else {
        message.setText("Không thể tải đánh giá cá nhân.");
      }
    });
  }

  private void loadReviews() {
    reviewsTasks.run(body, message, api.get("/comics/" + comic + "/reviews?page=" + currentPage + "&size=10"), reply -> {
      reviewsContainer.removeAll();
      JsonNode items = reply.body().path("items");
      totalPages = Math.max(1, reply.body().path("page").path("totalPages").asInt(1));
      pageLabel.setText("Trang " + (currentPage + 1) + " / " + totalPages);
      prevPageBtn.setEnabled(currentPage > 0);
      nextPageBtn.setEnabled(currentPage + 1 < totalPages);

      int s5 = 0, s4 = 0, s3 = 0, s2 = 0, s1 = 0;

      if (items == null || !items.isArray() || items.isEmpty()) {
        JPanel emptyReviews = new JPanel();
        emptyReviews.setLayout(new BoxLayout(emptyReviews, BoxLayout.Y_AXIS));
        emptyReviews.setOpaque(false);
        emptyReviews.setBorder(new EmptyBorder(32, 16, 32, 16));

        JLabel icon = new JLabel(UiIcons.star(36, new Color(0xCB, 0xD5, 0xE1)));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel emptyText = Theme.plainLabel("Chưa có nhận xét nào cho truyện này.");
        emptyText.setFont(Theme.heading(14));
        emptyText.setForeground(Theme.MUTED);
        emptyText.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel emptySub = Theme.plainLabel("Hãy là người đầu tiên để lại đánh giá và cảm nghĩ của bạn!");
        emptySub.setFont(Theme.label(12));
        emptySub.setForeground(new Color(0x94, 0xA3, 0xB8));
        emptySub.setAlignmentX(Component.CENTER_ALIGNMENT);

        emptyReviews.add(icon);
        emptyReviews.add(Box.createVerticalStrut(8));
        emptyReviews.add(emptyText);
        emptyReviews.add(Box.createVerticalStrut(4));
        emptyReviews.add(emptySub);
        reviewsContainer.add(emptyReviews);
      } else {
        for (JsonNode it : items) {
          int sc = it.path("score").asInt(5);
          if (sc == 5) s5++;
          else if (sc == 4) s4++;
          else if (sc == 3) s3++;
          else if (sc == 2) s2++;
          else s1++;

          reviewsContainer.add(buildReviewCard(it));
          reviewsContainer.add(Box.createVerticalStrut(8));
        }
      }

      renderDistributionBars(s5, s4, s3, s2, s1);
      reviewsContainer.revalidate();
      reviewsContainer.repaint();
    });
  }

  private JPanel buildReviewCard(JsonNode it) {
    JPanel card = new JPanel(new BorderLayout(12, 6));
    card.setOpaque(true);
    card.setBackground(Theme.BACKGROUND);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1),
        new EmptyBorder(10, 12, 10, 12)));

    // Top: Avatar + Name + Stars + Date
    JPanel cardHeader = new JPanel(new BorderLayout());
    cardHeader.setOpaque(false);

    JPanel userBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    userBox.setOpaque(false);

    String name = it.path("displayName").asText("Độc giả");
    JLabel avatar = new JLabel(name.isEmpty() ? "U" : name.substring(0, 1).toUpperCase(Locale.ROOT)) {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.ACTION);
        g2.fillOval(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
      }
    };
    avatar.setFont(Theme.brand(11));
    avatar.setForeground(Color.WHITE);
    avatar.setHorizontalAlignment(SwingConstants.CENTER);
    avatar.setPreferredSize(new Dimension(24, 24));
    userBox.add(avatar);

    JLabel nameLabel = Theme.plainLabel(name);
    nameLabel.setFont(Theme.heading(13));
    nameLabel.setForeground(Theme.TEXT);
    userBox.add(nameLabel);
    cardHeader.add(userBox, BorderLayout.WEST);

    // Stars right
    JPanel starBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
    starBox.setOpaque(false);
    int score = it.path("score").asInt(5);
    for (int i = 1; i <= 5; i++) {
      Color c = (i <= score) ? new Color(0xF5, 0x9E, 0x0B) : new Color(0xCB, 0xD5, 0xE1);
      starBox.add(new JLabel(UiIcons.star(12, c)));
    }
    cardHeader.add(starBox, BorderLayout.EAST);
    card.add(cardHeader, BorderLayout.NORTH);

    // Comment text
    String text = it.path("review").asText("").strip();
    if (!text.isBlank()) {
      JLabel commentLabel = Theme.plainLabel(text);
      commentLabel.setFont(Theme.label(13));
      commentLabel.setForeground(Theme.TEXT);
      card.add(commentLabel, BorderLayout.CENTER);
    }

    return card;
  }

  private void saveRating() {
    if (!api.signedIn()) {
      message.setText("Vui lòng đăng nhập để đánh giá.");
      return;
    }
    int score = starPicker.getRating();
    if (score < 1 || score > 5) {
      message.setText("Vui lòng nhấn chọn từ 1 đến 5 sao.");
      return;
    }
    if (actions.busy()) return;

    String text = reviewArea.getText().strip();
    Map<String, Object> payload = text.isBlank() ? Map.of("score", score) : Map.of("score", score, "review", text);
    Map<String, String> headers = (currentRatingReply == null) ? Map.of("If-None-Match", "*") : Map.of();

    actions.run(
        body,
        message,
        api.request("PUT", "/comics/" + comic + "/rating", payload, currentRatingReply == null ? null : currentRatingReply.etag(), headers),
        reply -> {
          currentRatingReply = reply;
          message.setText("Đã lưu đánh giá thành công!");
          reloadAll();
          if (onRatingUpdated != null) {
            onRatingUpdated.run();
          }
        });
  }

  private void deleteRating() {
    if (!api.signedIn() || currentRatingReply == null || actions.busy()) return;
    int choice = JOptionPane.showConfirmDialog(
        this,
        "Bạn có chắc muốn xóa đánh giá của mình?",
        "Xác nhận xóa đánh giá",
        JOptionPane.YES_NO_OPTION);
    if (choice != JOptionPane.YES_OPTION) return;

    actions.run(
        body,
        message,
        api.request("DELETE", "/comics/" + comic + "/rating", null, currentRatingReply.etag(), Map.of()),
        reply -> {
          currentRatingReply = null;
          starPicker.setRating(0);
          reviewArea.setText("");
          myRatingStatus.setText("Nhấn vào các ngôi sao để đánh giá");
          saveBtn.setText("Gửi đánh giá");
          saveBtn.setEnabled(false);
          deleteBtn.setEnabled(false);
          message.setText("Đã xóa đánh giá của bạn.");
          reloadAll();
          if (onRatingUpdated != null) {
            onRatingUpdated.run();
          }
        });
  }

  /**
   * Interactive Star Rating Component.
   * Handles hover, click, and visual preview of 1-5 stars.
   */
  public static final class StarRatingPicker extends JPanel {
    private int selectedScore = 0;
    private int hoveredScore = 0;
    private java.util.function.Consumer<Integer> listener;
    private final JLabel[] stars = new JLabel[5];

    public StarRatingPicker() {
      super(new FlowLayout(FlowLayout.LEFT, 4, 0));
      setOpaque(false);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      for (int i = 0; i < 5; i++) {
        final int starIndex = i + 1;
        JLabel star = new JLabel(UiIcons.star(22, new Color(0xCB, 0xD5, 0xE1)));
        star.addMouseListener(new MouseAdapter() {
          @Override public void mouseEntered(MouseEvent e) {
            hoveredScore = starIndex;
            updateDisplay();
          }
          @Override public void mouseExited(MouseEvent e) {
            hoveredScore = 0;
            updateDisplay();
          }
          @Override public void mouseClicked(MouseEvent e) {
            selectedScore = starIndex;
            updateDisplay();
            if (listener != null) listener.accept(selectedScore);
          }
        });
        stars[i] = star;
        add(star);
      }
    }

    public void setOnRatingChanged(java.util.function.Consumer<Integer> listener) {
      this.listener = listener;
    }

    public int getRating() {
      return selectedScore;
    }

    public void setRating(int score) {
      this.selectedScore = Math.max(0, Math.min(5, score));
      this.hoveredScore = 0;
      updateDisplay();
    }

    private void updateDisplay() {
      int active = (hoveredScore > 0) ? hoveredScore : selectedScore;
      for (int i = 0; i < 5; i++) {
        Color c = (i < active) ? new Color(0xF5, 0x9E, 0x0B) : new Color(0xCB, 0xD5, 0xE1);
        stars[i].setIcon(UiIcons.star(22, c));
      }
      revalidate();
      repaint();
    }
  }
}
