package com.comicnet.desktop.features.catalog;

import com.comicnet.desktop.client.api.*;
import com.comicnet.desktop.client.http.MediaTransport;
import com.comicnet.desktop.features.reader.ReaderLocation;
import com.comicnet.desktop.features.recommendation.RatingDialog;
import com.comicnet.desktop.features.search.SearchAssist;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

public final class CatalogPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final MediaTransport media;
  private final Consumer<ReaderLocation> read;
  private final UiTasks listings = new UiTasks(),
      details = new UiTasks(),
      ratingSummaryTasks = new UiTasks(),
      actions = new UiTasks(),
      metadata = new UiTasks();
  private final UiTasks recommendations = new UiTasks(), rankings = new UiTasks();
  private final JPanel recommendedItems = new JPanel(new GridLayout(0, 1, 0, 8));
  private final JPanel rankingItems = new JPanel(new GridLayout(0, 1, 0, 8));
  private final JLabel recommendedState = Theme.plainLabel("Đang tải đề xuất…");
  private final JLabel rankingState = Theme.plainLabel("Đang tải xếp hạng…");
  private final JLabel message = Theme.plainLabel("Chọn Tìm để tải truyện."),
      detailMessage = Theme.plainLabel("Chọn một truyện để xem chi tiết.");
  private final JTextField query = new JTextField(20);
  private final SearchAssist searchAssist;

  // Filter components
  private String selectedType = "Tất cả";
  private final JPanel typePillsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
  private final List<JButton> typeButtons = new ArrayList<>();
  private final JComboBox<String>
      typeHidden = new JComboBox<>(new String[] {"Tất cả", "MANGA", "MANHWA", "MANHUA", "COMIC"}),
      status = new JComboBox<>(new String[] {"Trạng thái: Tất cả", "ONGOING", "COMPLETED", "HIATUS", "DROPPED"}),
      sort = new JComboBox<>(new String[] {"Mới nhất", "Tên A–Z"});
  private final JComboBox<Option> genre = new JComboBox<>(),
      tag = new JComboBox<>();
  private final JComboBox<String> translations = new JComboBox<>();

  private final DefaultListModel<JsonNode> comics = new DefaultListModel<>();
  private final JPanel comicGrid = new JPanel(new GridLayout(0, 4, 18, 22));
  private final JPanel pageContainer;
  private final JScrollPane pageScroll;
  private final JPanel detail = new JPanel(new BorderLayout(0, 14));

  // Hero Featured Banner
  private final JPanel hero = new JPanel(new BorderLayout(24, 0)) {
    @Override protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      // Dark slate modern manga gradient
      GradientPaint gp = new GradientPaint(
          0, 0, new Color(0x0F, 0x17, 0x2A),
          getWidth(), getHeight(), new Color(0x1E, 0x29, 0x3B));
      g2.setPaint(gp);
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
      g2.dispose();
    }
  };
  private final CoverArtCanvas heroCoverCanvas = new CoverArtCanvas(12);
  private final JLabel heroTitle = Theme.plainLabel("Khám phá truyện từ thư viện Comic Net"),
      heroDesc = Theme.plainLabel("Các tựa truyện nổi bật sẽ xuất hiện tại đây.");
  private final JPanel heroTags = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
  private final PrimaryButton readFeatured = new PrimaryButton("Đọc ngay");
  private final JButton openFeatured = new JButton("Xem chi tiết");
  private String heroComicId;

  private boolean isFavorite = false;
  private boolean isFollowing = false;
  private double currentRating = 0.0;
  private int currentRatingCount = 0;
  private final JButton favButton = new JButton("Yêu thích");
  private final JButton followButton = new JButton("Theo dõi");
  private final JLabel ratingStatLabel = Theme.plainLabel("★ 0.0/5 (0 đánh giá)");
  private final JLabel chCountLabel = Theme.plainLabel("· 0 chương");
  private final JButton chMoreBtn = new JButton("Xem thêm chương ▼");

  private final DefaultTableModel chapters =
      new DefaultTableModel(new Object[] {"CHƯƠNG", "TÊN CHƯƠNG", "TRẠNG THÁI", "HÀNH ĐỘNG"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final JTable chapterTable = new JTable(chapters);
  private final List<JsonNode> chapterRows = new ArrayList<>();
  private final Map<String, ImageIcon> covers = new LinkedHashMap<>();
  private final List<CompletableFuture<?>> images = new ArrayList<>();
  private final Map<String, String> names = new HashMap<>();
  private JsonNode selected;
  private JDialog detailDialog;
  private int page, chapterPage, translationPage;
  private long imageGeneration, detailGeneration;
  private boolean filling, loaded;

  private record Option(String id, String label) {
    public String toString() {
      return label;
    }
  }

  public CatalogPanel(ApiClient api, MediaTransport media, Consumer<ReaderLocation> read) {
    super(new BorderLayout());
    this.api = api;
    this.media = media;
    this.read = read;
    query.putClientProperty("JTextField.placeholderText", "Tìm kiếm truyện, tác giả, thể loại...");
    searchAssist = new SearchAssist(api, query, this::search);
    setOpaque(false);

    // Top Header Section
    JPanel topSection = new JPanel(new BorderLayout(0, 16));
    topSection.setOpaque(false);

    JPanel heading = new JPanel(new GridLayout(0, 1, 0, 4));
    heading.setOpaque(false);
    JLabel eyebrow = Theme.plainLabel("TRANG CHỦ");
    eyebrow.setForeground(Theme.ACTION);
    eyebrow.setFont(Theme.brand(11));
    JLabel title = new JLabel("Hôm nay đọc gì?");
    title.setFont(Theme.heading(28));
    title.setForeground(Theme.TEXT);
    JLabel subtitle = Theme.plainLabel("Tìm câu chuyện tiếp theo từ thư viện Comic Net.");
    subtitle.setForeground(Theme.MUTED);
    subtitle.setFont(Theme.label(14));
    heading.add(eyebrow);
    heading.add(title);
    heading.add(subtitle);
    topSection.add(heading, BorderLayout.NORTH);

    // Hero Banner Card
    buildHeroBanner();
    topSection.add(hero, BorderLayout.CENTER);

    // Filter Controls Bar
    JPanel controlsBar = buildControlsBar();
    topSection.add(controlsBar, BorderLayout.SOUTH);

    // Center Browse Feed: Main Grid + Right Rail
    JPanel feed = new JPanel(new BorderLayout(24, 0));
    feed.setOpaque(false);

    // Comic Grid Container
    JPanel gridContainer = new JPanel(new BorderLayout(0, 14));
    gridContainer.setOpaque(false);

    JPanel sectionHeader = new JPanel(new BorderLayout());
    sectionHeader.setOpaque(false);
    JLabel sectionTitle = Theme.plainLabel("Mới cập nhật");
    sectionTitle.setFont(Theme.heading(20));
    sectionTitle.setForeground(Theme.TEXT);
    JButton viewAllBtn = new JButton("Xem tất cả →");
    viewAllBtn.setContentAreaFilled(false);
    viewAllBtn.setBorderPainted(false);
    viewAllBtn.setFocusPainted(false);
    viewAllBtn.setFont(Theme.brand(13));
    viewAllBtn.setForeground(Theme.ACTION);
    viewAllBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    viewAllBtn.addActionListener(e -> {
      selectedType = "Tất cả";
      updateTypePills();
      load();
    });
    sectionHeader.add(sectionTitle, BorderLayout.WEST);
    sectionHeader.add(viewAllBtn, BorderLayout.EAST);
    gridContainer.add(sectionHeader, BorderLayout.NORTH);

    comicGrid.setOpaque(false);
    gridContainer.add(comicGrid, BorderLayout.CENTER);

    // Pagination Footer
    JPanel footer = new JPanel(new BorderLayout(16, 0));
    footer.setOpaque(false);
    footer.setBorder(new EmptyBorder(14, 0, 0, 0));
    message.setFont(Theme.label(13));
    message.setForeground(Theme.MUTED);
    footer.add(message, BorderLayout.WEST);

    JPanel pagination = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    pagination.setOpaque(false);
    JButton prevBtn = button("← Trang trước", () -> {
      page = Math.max(0, page - 1);
      load();
    });
    JButton nextBtn = button("Trang sau →", () -> {
      page++;
      load();
    });
    pagination.add(prevBtn);
    pagination.add(nextBtn);
    footer.add(pagination, BorderLayout.EAST);
    gridContainer.add(footer, BorderLayout.SOUTH);

    feed.add(gridContainer, BorderLayout.CENTER);

    // Right Rail (Sidebar Phải: Đề xuất & Top tuần này)
    JPanel rightRail = buildRightRail();
    feed.add(rightRail, BorderLayout.EAST);

    // Whole Page Scroll
    pageContainer = new JPanel(new BorderLayout(0, 24));
    pageContainer.setOpaque(false);
    pageContainer.setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 32, Theme.CONTENT_PADDING));
    pageContainer.add(topSection, BorderLayout.NORTH);
    pageContainer.add(feed, BorderLayout.CENTER);

    pageScroll = new JScrollPane(pageContainer);
    pageScroll.setBorder(null);
    pageScroll.setOpaque(false);
    pageScroll.getViewport().setOpaque(false);
    pageScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pageScroll.getVerticalScrollBar().setUnitIncrement(24);
    add(pageScroll, BorderLayout.CENTER);

    // Responsive columns
    pageScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override public void componentResized(java.awt.event.ComponentEvent event) {
        int w = pageScroll.getViewport().getWidth() - 320;
        int columns = Math.max(2, Math.min(5, w > 0 ? w / 215 : 4));
        if (comicGrid.getLayout() instanceof GridLayout layout) {
          if (layout.getColumns() != columns) {
            layout.setColumns(columns);
            comicGrid.revalidate();
          }
        }
      }
    });

    loadMetadata();
    load();
  }

  private void buildHeroBanner() {
    hero.setOpaque(false);
    hero.setBorder(new EmptyBorder(16, 24, 16, 24));
    hero.setPreferredSize(new Dimension(0, 185));

    // Left info
    JPanel heroLeft = new JPanel();
    heroLeft.setLayout(new BoxLayout(heroLeft, BoxLayout.Y_AXIS));
    heroLeft.setOpaque(false);

    JLabel badge = new JLabel("  Đề xuất cho bạn  ") {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.ACTION);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    badge.setForeground(Color.WHITE);
    badge.setFont(Theme.brand(11));
    badge.setOpaque(false);
    badge.setAlignmentX(LEFT_ALIGNMENT);

    heroTitle.setFont(Theme.heading(20));
    heroTitle.setForeground(Color.WHITE);
    heroTitle.setAlignmentX(LEFT_ALIGNMENT);

    heroTags.setOpaque(false);
    heroTags.setAlignmentX(LEFT_ALIGNMENT);

    heroDesc.setFont(Theme.label(13));
    heroDesc.setForeground(new Color(0xD1, 0xD5, 0xDB));
    heroDesc.setAlignmentX(LEFT_ALIGNMENT);

    JPanel heroActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
    heroActions.setOpaque(false);
    heroActions.setAlignmentX(LEFT_ALIGNMENT);

    readFeatured.setIcon(UiIcons.bookOpen(14, Color.WHITE));
    readFeatured.setText(" Đọc ngay");
    readFeatured.setPreferredSize(new Dimension(135, 36));
    readFeatured.addActionListener(e -> {
      if (heroComicId != null) openComic(heroComicId);
    });

    openFeatured.setFont(Theme.brand(13));
    openFeatured.setForeground(Color.WHITE);
    openFeatured.setBackground(new Color(255, 255, 255, 30));
    openFeatured.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(255, 255, 255, 120), 1),
        new EmptyBorder(6, 14, 6, 14)));
    openFeatured.setFocusPainted(false);
    openFeatured.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    openFeatured.addActionListener(e -> {
      if (heroComicId != null) loadDetail(heroComicId);
    });

    heroActions.add(readFeatured);
    heroActions.add(openFeatured);

    heroLeft.add(badge);
    heroLeft.add(Box.createVerticalStrut(6));
    heroLeft.add(heroTitle);
    heroLeft.add(Box.createVerticalStrut(4));
    heroLeft.add(heroTags);
    heroLeft.add(Box.createVerticalStrut(4));
    heroLeft.add(heroDesc);
    heroLeft.add(Box.createVerticalStrut(10));
    heroLeft.add(heroActions);

    hero.add(heroLeft, BorderLayout.CENTER);

    // Right Cover Thumbnail (2:3 aspect)
    heroCoverCanvas.setPreferredSize(new Dimension(110, 155));
    hero.add(heroCoverCanvas, BorderLayout.EAST);
  }

  private JPanel buildControlsBar() {
    JPanel bar = new JPanel(new BorderLayout(16, 8));
    bar.setOpaque(false);

    // Left: Type Pills (Tất cả, Manga, Manhwa, Manhua, Comic)
    typePillsPanel.setOpaque(false);
    String[] types = {"Tất cả", "Manga", "Manhwa", "Manhua", "Comic"};
    for (String t : types) {
      JButton pill = new JButton(t) {
        @Override protected void paintComponent(Graphics g) {
          Graphics2D g2 = (Graphics2D) g.create();
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          boolean active = t.equalsIgnoreCase(selectedType);
          g2.setColor(active ? Theme.ACTION : Theme.SURFACE);
          g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
          g2.setColor(active ? Theme.ACTION : Theme.BORDER);
          g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 999, 999);
          g2.dispose();
          super.paintComponent(g);
        }
      };
      pill.setContentAreaFilled(false);
      pill.setBorderPainted(false);
      pill.setFocusPainted(false);
      pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      pill.setBorder(new EmptyBorder(6, 12, 6, 12));
      pill.setFont(Theme.brand(13));
      pill.addActionListener(e -> {
        selectedType = t;
        updateTypePills();
        page = 0;
        load();
      });
      typeButtons.add(pill);
      typePillsPanel.add(pill);
    }
    updateTypePills();
    bar.add(typePillsPanel, BorderLayout.WEST);

    // Right: Dropdowns (Thể loại, Trạng thái, Sắp xếp, Gợi ý)
    JPanel dropdowns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    dropdowns.setOpaque(false);

    genre.addItem(new Option("", "Thể loại: Tất cả"));
    genre.setFont(Theme.label(13));
    genre.setPreferredSize(new Dimension(135, 36));
    genre.addActionListener(e -> { page = 0; load(); });

    status.setFont(Theme.label(13));
    status.setPreferredSize(new Dimension(125, 36));
    status.addActionListener(e -> { page = 0; load(); });

    sort.setFont(Theme.label(13));
    sort.setPreferredSize(new Dimension(115, 36));
    sort.addActionListener(e -> { page = 0; load(); });

    JButton suggestBtn = new JButton(" Gợi ý");
    suggestBtn.setIcon(UiIcons.star(14, Theme.ACTION));
    suggestBtn.setFont(Theme.brand(13));
    suggestBtn.setBackground(Theme.SURFACE);
    suggestBtn.setForeground(Theme.TEXT);
    suggestBtn.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(6, 10, 6, 10)));
    suggestBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    suggestBtn.addActionListener(e ->
        new com.comicnet.desktop.features.recommendation.RecommendationDialog(
            this, api, null, this::openComic).setVisible(true));

    dropdowns.add(genre);
    dropdowns.add(status);
    dropdowns.add(sort);
    dropdowns.add(suggestBtn);

    bar.add(dropdowns, BorderLayout.EAST);
    return bar;
  }

  private void updateTypePills() {
    for (JButton btn : typeButtons) {
      boolean active = btn.getText().equalsIgnoreCase(selectedType);
      btn.setForeground(active ? Color.WHITE : Theme.TEXT);
      btn.repaint();
    }
  }

  private JPanel buildRightRail() {
    JPanel rail = new JPanel(new GridLayout(2, 1, 0, 16));
    rail.setOpaque(false);
    rail.setPreferredSize(new Dimension(270, 520));

    rail.add(buildRailCard("Đề xuất cho bạn", recommendedItems, recommendedState));
    rail.add(buildRailCard("Top tuần này", rankingItems, rankingState));
    return rail;
  }

  private JPanel buildRailCard(String title, JPanel items, JLabel state) {
    JPanel card = new JPanel(new BorderLayout(0, 12)) {
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
    card.setOpaque(false);
    card.setBorder(new EmptyBorder(16, 18, 16, 18));

    JLabel heading = Theme.plainLabel(title);
    heading.setFont(Theme.heading(16));
    heading.setForeground(Theme.TEXT);
    card.add(heading, BorderLayout.NORTH);

    items.setOpaque(false);
    JPanel body = new JPanel(new BorderLayout());
    body.setOpaque(false);
    body.add(items, BorderLayout.NORTH);
    state.setFont(Theme.label(12));
    state.setForeground(Theme.MUTED);
    body.add(state, BorderLayout.SOUTH);

    JScrollPane scroll = new JScrollPane(body);
    scroll.setBorder(null);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    card.add(scroll, BorderLayout.CENTER);
    return card;
  }

  private JPanel createEmptyRailPlaceholder(String message, Icon icon) {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    p.setBorder(new EmptyBorder(32, 12, 32, 12));
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.insets = new Insets(0, 0, 10, 0);
    p.add(new JLabel(icon), c);
    c.gridy = 1;
    c.insets = new Insets(0, 0, 4, 0);
    JLabel title = Theme.plainLabel(message);
    title.setFont(Theme.brand(12));
    title.setForeground(Theme.TEXT);
    p.add(title, c);
    c.gridy = 2;
    JLabel sub = Theme.plainLabel("Đọc truyện để cập nhật thêm.");
    sub.setFont(Theme.label(11));
    sub.setForeground(Theme.MUTED);
    p.add(sub, c);
    return p;
  }

  private void loadHighlights() {
    recommendations.run(this, recommendedState, api.get("/recommendations?size=5"), reply -> {
      recommendedItems.removeAll();
      for (JsonNode item : reply.body().path("items")) {
        recommendedItems.add(buildCompactComicItem(
            item.path("id").asText(),
            item.path("title").asText("Truyện"),
            item.path("type").asText("Manga"),
            item.path("rating").isNumber() ? String.format(Locale.ROOT, " %.1f", item.path("rating").asDouble()) : " 0.0",
            item.path("coverUrl").asText(""),
            -1));
      }
      if (recommendedItems.getComponentCount() == 0) {
        recommendedItems.add(createEmptyRailPlaceholder("Chưa có đề xuất cá nhân", UiIcons.star(24, new Color(0xF5, 0x9E, 0x0B))));
      }
      recommendedState.setText("");
      recommendedItems.revalidate();
      recommendedItems.repaint();
    });

    rankings.run(this, rankingState, api.get("/rankings?period=WEEKLY&size=5"), reply -> {
      rankingItems.removeAll();
      int rank = 1;
      for (JsonNode item : reply.body().path("items")) {
        rankingItems.add(buildCompactComicItem(
            item.path("comicId").asText(),
            item.path("title").asText("Truyện"),
            item.path("views").isNumber() ? formatViews(item.path("views").asLong()) : "1.2M",
            "",
            item.path("coverUrl").asText(""),
            rank++));
      }
      if (rankingItems.getComponentCount() == 0) {
        rankingItems.add(createEmptyRailPlaceholder("Chưa có dữ liệu tuần này", UiIcons.barChart(24, Theme.MUTED)));
      }
      rankingState.setText("");
      rankingItems.revalidate();
      rankingItems.repaint();
    });
  }

  private JPanel buildCompactComicItem(String id, String title, String meta, String rating, String coverUrl, int rank) {
    JPanel item = new JPanel(new BorderLayout(10, 0));
    item.setOpaque(false);
    item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    item.setBorder(new EmptyBorder(4, 0, 4, 0));

    if (rank > 0) {
      JLabel rankLabel = new JLabel(String.valueOf(rank));
      rankLabel.setFont(Theme.brand(15));
      rankLabel.setForeground(rank == 1 ? Theme.ACTION : rank <= 3 ? new Color(0xF59E0B) : Theme.MUTED);
      rankLabel.setPreferredSize(new Dimension(16, 32));
      item.add(rankLabel, BorderLayout.WEST);
    }

    JPanel copy = new JPanel(new GridLayout(2, 1, 0, 2));
    copy.setOpaque(false);
    JLabel titleLabel = Theme.plainLabel(title);
    titleLabel.setFont(Theme.brand(13));
    titleLabel.setForeground(Theme.TEXT);

    JPanel metaBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    metaBox.setOpaque(false);
    JLabel metaLabel = Theme.plainLabel(meta);
    metaLabel.setFont(Theme.label(11));
    metaLabel.setForeground(Theme.MUTED);
    metaBox.add(metaLabel);

    if (!rating.isEmpty()) {
      JLabel starLabel = Theme.plainLabel(rating);
      starLabel.setFont(Theme.label(11));
      starLabel.setForeground(new Color(0xD9, 0x77, 0x06));
      starLabel.setIcon(UiIcons.star(10, new Color(0xF5, 0x9E, 0x0B)));
      metaBox.add(starLabel);
    }

    copy.add(titleLabel);
    copy.add(metaBox);
    item.add(copy, BorderLayout.CENTER);

    item.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override public void mouseClicked(java.awt.event.MouseEvent e) {
        openComic(id);
      }
    });
    return item;
  }

  private static String formatViews(long views) {
    if (views >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", views / 1_000_000.0);
    if (views >= 1_000) return String.format(Locale.ROOT, "%.1fK", views / 1_000.0);
    return String.valueOf(views);
  }

  private void load() {
    long generation = ++imageGeneration;
    images.forEach(future -> future.cancel(true));
    images.clear();
    covers.clear();

    String path = (query.getText().isBlank()
        ? "/comics?"
        : "/search/comics?q=" + encoded(query.getText().strip()) + "&")
        + "page=" + page
        + "&size=20&sort=" + (sort.getSelectedIndex() == 0 ? "UPDATED_DESC" : "TITLE_ASC");

    if (!"Tất cả".equalsIgnoreCase(selectedType)) {
      path += "&type=" + selectedType.toUpperCase(Locale.ROOT);
    }
    if (status.getSelectedIndex() > 0) {
      path += "&status=" + status.getSelectedItem();
    }
    Option g = (Option) genre.getSelectedItem();
    if (g != null && !g.id().isEmpty()) path += "&genreId=" + g.id();

    listings.run(
        this,
        message,
        api.get(path),
        reply -> {
          comics.clear();
          comicGrid.removeAll();
          int w = pageScroll.getViewport().getWidth() - 320;
          int columns = Math.max(2, Math.min(5, w > 0 ? w / 215 : 4));
          comicGrid.setLayout(new GridLayout(0, columns, 18, 22));

          for (JsonNode comic : reply.body().path("items")) {
            comics.addElement(comic);
            ComicCard card = new ComicCard(comic);
            card.addActionListener(e -> loadDetail(comic.path("id").asText()));
            comicGrid.add(card);

            String url = comic.path("coverUrl").asText("");
            if (!url.isEmpty()) {
              try {
                var request = media.thumbnail(URI.create(url), 180, 270);
                images.add(request);
                request.whenComplete((image, error) -> SwingUtilities.invokeLater(() -> {
                  if (generation != imageGeneration) return;
                  if (error == null && image != null) {
                    ImageIcon icon = new ImageIcon(image);
                    covers.put(comic.path("id").asText(), icon);
                    card.setCover(icon);
                    if (comic.path("id").asText().equals(heroComicId)) setFeatured(comic);
                  }
                }));
              } catch (Exception ignored) {}
            }
          }

          int rows = (int) Math.ceil((double) comics.size() / columns);
          if (rows == 0) rows = 1;
          comicGrid.setPreferredSize(new Dimension(0, rows * 340 + (rows - 1) * 22));
          comicGrid.revalidate();
          comicGrid.repaint();
          pageContainer.revalidate();
          pageContainer.repaint();

          hero.setVisible(!comics.isEmpty());
          if (!comics.isEmpty()) {
            setFeatured(comics.get(0));
          } else {
            heroComicId = null;
            heroTitle.setText("Chưa có truyện để giới thiệu");
            heroDesc.setText("Thử đổi bộ lọc để khám phá các truyện khác.");
            readFeatured.setEnabled(false);
            openFeatured.setEnabled(false);
            heroCoverCanvas.setImage(null);
            heroCoverCanvas.setMetadata("", "");

            JLabel empty = Theme.plainLabel("Chưa có truyện phù hợp với bộ lọc hiện tại.");
            empty.setFont(Theme.heading(16));
            empty.setForeground(Theme.MUTED);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setPreferredSize(new Dimension(320, 180));
            comicGrid.setLayout(new BorderLayout());
            comicGrid.add(empty, BorderLayout.CENTER);
            comicGrid.revalidate();
          }

          message.setText(comics.isEmpty() ? "Không có truyện phù hợp." : "Hiển thị trang " + (page + 1));
        });
  }

  private void setFeatured(JsonNode comic) {
    heroComicId = comic.path("id").asText();
    String titleStr = comic.path("title").asText("Truyện nổi bật");
    String typeTag = comic.path("type").asText("Manga");
    heroTitle.setText(titleStr);
    String desc = comic.path("description").asText("Khám phá ngay trên Comic Net.");
    if (desc.length() > 140) desc = desc.substring(0, 137) + "…";
    heroDesc.setText(desc);

    heroTags.removeAll();
    heroTags.add(buildHeroTag(typeTag));
    heroTags.add(buildHeroTag("Đang ra"));
    heroTags.revalidate();
    heroTags.repaint();

    heroCoverCanvas.setMetadata(titleStr, typeTag);
    ImageIcon icon = covers.get(heroComicId);
    heroCoverCanvas.setImage(icon != null ? icon.getImage() : null);
    readFeatured.setEnabled(true);
    openFeatured.setEnabled(true);
  }

  private JLabel buildHeroTag(String text) {
    JLabel tag = new JLabel("  " + text + "  ") {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255, 35));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    tag.setFont(Theme.brand(11));
    tag.setForeground(new Color(0xE2, 0xE8, 0xF0));
    tag.setOpaque(false);
    return tag;
  }

  private void search() {
    search(query.getText());
  }

  public void search(String text) {
    query.setText(text == null ? "" : text.strip());
    page = 0;
    load();
  }

  public void openComic(String comicId) {
    loadDetail(comicId);
  }

  public static final class CoverArtCanvas extends JComponent {
    private Image image;
    private String title = "";
    private String type = "MANGA";
    private final int arc;

    public CoverArtCanvas(int arc) {
      this.arc = arc;
      setOpaque(false);
    }

    public void setImage(Image img) {
      this.image = img;
      repaint();
    }

    public void setMetadata(String title, String type) {
      this.title = title == null ? "" : title;
      this.type = type == null ? "MANGA" : type;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      int w = getWidth(), h = getHeight();

      Shape clipShape = new RoundRectangle2D.Float(0, 0, w, h, arc, arc);

      if (image != null) {
        g2.setClip(clipShape);
        g2.drawImage(image, 0, 0, w, h, null);
        g2.setClip(null);
        g2.setColor(Theme.BORDER);
        g2.draw(clipShape);
      } else {
        // Editorial Manga Minimalist Cover Placeholder
        g2.setClip(clipShape);
        GradientPaint gp = new GradientPaint(
            0, 0, new Color(0x1E, 0x29, 0x3B),
            w, h, new Color(0x0F, 0x17, 0x2A));
        g2.setPaint(gp);
        g2.fill(clipShape);

        // Subtle geometric frame
        g2.setColor(new Color(255, 255, 255, 14));
        g2.drawRect(8, 8, Math.max(1, w - 17), Math.max(1, h - 17));
        g2.drawLine(8, 28, Math.max(1, w - 9), 28);
        g2.drawLine(8, Math.max(1, h - 26), Math.max(1, w - 9), Math.max(1, h - 26));

        // Header Label: Comic type
        g2.setColor(new Color(0x94, 0xA3, 0xB8));
        g2.setFont(Theme.brand(Math.max(9, w / 15)));
        FontMetrics fmTop = g2.getFontMetrics();
        String typeLabel = type.toUpperCase(Locale.ROOT);
        int twTop = fmTop.stringWidth(typeLabel);
        g2.drawString(typeLabel, (w - twTop) / 2, 20);

        // Center Monogram / Logo
        String monogram = makeMonogram(title);
        g2.setColor(Color.WHITE);
        g2.setFont(Theme.heading(Math.max(18, w / 7)));
        FontMetrics fmCenter = g2.getFontMetrics();
        int twCenter = fmCenter.stringWidth(monogram);
        g2.drawString(monogram, (w - twCenter) / 2, (h + fmCenter.getAscent() - 10) / 2);

        // Small Brand Logo below monogram
        Icon logo = UiIcons.brandLogo(Math.max(22, w / 6), Math.max(22, w / 6));
        int lw = logo.getIconWidth(), lh = logo.getIconHeight();
        logo.paintIcon(this, g2, (w - lw) / 2, (h + fmCenter.getAscent() + 6) / 2);

        // Footer Brand Text
        g2.setColor(new Color(0xE2, 0xE8, 0xF0));
        g2.setFont(Theme.brand(Math.max(8, w / 18)));
        FontMetrics fmBot = g2.getFontMetrics();
        String botLabel = "COMIC NET";
        int twBot = fmBot.stringWidth(botLabel);
        g2.drawString(botLabel, (w - twBot) / 2, h - 10);

        g2.setClip(null);
        g2.setColor(new Color(0x33, 0x41, 0x55));
        g2.draw(clipShape);
      }
      g2.dispose();
    }

    private static String makeMonogram(String text) {
      if (text == null || text.isBlank()) return "CN";
      String[] parts = text.strip().split("\\s+");
      if (parts.length >= 2) {
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase(Locale.ROOT);
      }
      return text.substring(0, Math.min(2, text.length())).toUpperCase(Locale.ROOT);
    }
  }

  // Modern Comic Card Component matching Design System V1
  private static final class ComicCard extends JButton {
    private final CoverArtCanvas coverCanvas = new CoverArtCanvas(10);
    private final JLabel titleLabel = Theme.plainLabel("");
    private final JLabel tagLabel = Theme.plainLabel("");
    private final JLabel ratingLabel = Theme.plainLabel("★ 0.0");
    private final JLabel viewsLabel = Theme.plainLabel("2.1M");

    ComicCard(JsonNode comic) {
      super();
      setLayout(new BorderLayout(0, 8));
      setBackground(Theme.SURFACE);
      setOpaque(false);
      setFocusPainted(false);
      setContentAreaFilled(false);
      setBorder(new EmptyBorder(10, 10, 12, 10));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setPreferredSize(new Dimension(180, 335));

      String cTitle = comic.path("title").asText("Truyện");
      String cType = comic.path("type").asText("Manga");
      String cId = comic.path("id").asText("");
      setToolTipText(cTitle + (!cId.isBlank() ? " · ID #" + cId : ""));

      // Cover container with 2:3 ratio (160x228 px)
      coverCanvas.setPreferredSize(new Dimension(160, 228));
      coverCanvas.setMetadata(cTitle, cType);

      // Status badge on cover
      JPanel coverPanel = new JPanel(new BorderLayout());
      coverPanel.setOpaque(false);
      coverPanel.add(coverCanvas, BorderLayout.CENTER);

      // Status chip (Đang ra / Hoàn thành)
      String statusStr = comic.path("status").asText("ONGOING");
      boolean isDone = "COMPLETED".equalsIgnoreCase(statusStr);
      JLabel statusChip = new JLabel(isDone ? " Hoàn thành " : " Đang ra ") {
        @Override protected void paintComponent(Graphics g) {
          Graphics2D g2 = (Graphics2D) g.create();
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setColor(isDone ? new Color(0xDB, 0xEA, 0xFE) : Theme.SUCCESS_TINT);
          g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
          g2.dispose();
          super.paintComponent(g);
        }
      };
      statusChip.setFont(Theme.brand(10));
      statusChip.setForeground(isDone ? new Color(0x25, 0x63, 0xEB) : Theme.SUCCESS);
      statusChip.setOpaque(false);

      JPanel chipWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
      chipWrapper.setOpaque(false);
      chipWrapper.add(statusChip);
      coverPanel.add(chipWrapper, BorderLayout.NORTH);

      // Info
      JPanel info = new JPanel(new GridLayout(3, 1, 0, 3));
      info.setOpaque(false);

      titleLabel.setText(cTitle);
      titleLabel.setFont(Theme.heading(13));
      titleLabel.setForeground(Theme.TEXT);

      tagLabel.setText(cType);
      tagLabel.setFont(Theme.label(11));
      tagLabel.setForeground(Theme.MUTED);

      JPanel stats = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
      stats.setOpaque(false);
      double r = comic.path("rating").asDouble(0.0);
      ratingLabel.setIcon(UiIcons.star(11, new Color(0xF5, 0x9E, 0x0B)));
      ratingLabel.setText(String.format(Locale.ROOT, " %.1f", r));
      ratingLabel.setFont(Theme.brand(11));
      ratingLabel.setForeground(new Color(0xD9, 0x77, 0x06)); // Amber gold

      long v = comic.path("views").asLong(1200000);
      viewsLabel.setIcon(UiIcons.eye(11, Theme.MUTED));
      viewsLabel.setText(" " + formatViews(v));
      viewsLabel.setFont(Theme.label(11));
      viewsLabel.setForeground(Theme.MUTED);

      stats.add(ratingLabel);
      stats.add(viewsLabel);

      info.add(titleLabel);
      info.add(tagLabel);
      info.add(stats);

      add(coverPanel, BorderLayout.CENTER);
      add(info, BorderLayout.SOUTH);
    }

    void setCover(ImageIcon icon) {
      if (icon != null) {
        coverCanvas.setImage(icon.getImage());
      }
    }

    @Override protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth(), h = getHeight();
      g2.setColor(Theme.SURFACE);
      g2.fillRoundRect(0, 0, w, h, 14, 14);

      if (getModel().isRollover()) {
        g2.setColor(Theme.ACTION);
        g2.setStroke(new BasicStroke(1.5f));
      } else {
        g2.setColor(Theme.BORDER);
        g2.setStroke(new BasicStroke(1.0f));
      }
      g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
      g2.dispose();
      super.paintComponent(g);
    }
  }

  // Comic Details and Chapters Dialog matching Mockup 01_Chi_tiet_truyen.png
  private void loadDetail(String comicId) {
    long generation = ++detailGeneration;
    details.run(
        this,
        detailMessage,
        api.get("/comics/" + comicId),
        reply -> {
          if (generation != detailGeneration) return;
          selected = reply.body();

          // Fetch rating summary
          ratingSummaryTasks.run(
              this,
              detailMessage,
              api.get("/comics/" + comicId + "/rating-summary"),
              rReply -> {
                if (rReply.body() != null && rReply.body().has("average")) {
                  currentRating = rReply.body().path("average").asDouble(0.0);
                  currentRatingCount = rReply.body().path("count").asInt(0);
                } else {
                  currentRating = selected.path("rating").asDouble(0.0);
                  currentRatingCount = 0;
                }
                updateRatingDisplay();
              });

          // Fetch favorite & follow states if signed in
          if (api.signedIn()) {
            actions.run(
                this,
                detailMessage,
                api.get("/me/favorites?page=0&size=50"),
                fReply -> {
                  isFavorite = false;
                  if (fReply.body() != null) {
                    for (JsonNode fav : fReply.body().path("items")) {
                      if (comicId.equals(fav.path("id").asText())) {
                        isFavorite = true;
                        break;
                      }
                    }
                  }
                  updateFavoriteButton();
                });
            actions.run(
                this,
                detailMessage,
                api.get("/me/follows?page=0&size=50"),
                flReply -> {
                  isFollowing = false;
                  if (flReply.body() != null) {
                    for (JsonNode f : flReply.body().path("items")) {
                      if (comicId.equals(f.path("id").asText())) {
                        isFollowing = true;
                        break;
                      }
                    }
                  }
                  updateFollowButton();
                });
          }

          renderDetail();
          if (detailDialog == null) {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            detailDialog = new JDialog(owner, "Comic Net · Chi tiết truyện", false);
            AppIcons.applyTo(detailDialog);
            detailDialog.getContentPane().add(detail);
          }
          detailDialog.setTitle("Comic Net · " + selected.path("title").asText());
          detailDialog.setSize(960, 680);
          detailDialog.setMinimumSize(new Dimension(860, 620));
          detailDialog.setLocationRelativeTo(this);
          detailDialog.setVisible(true);
          loadTranslations();
        });
  }

  private void updateRatingDisplay() {
    ratingStatLabel.setIcon(UiIcons.star(12, new Color(0xF5, 0x9E, 0x0B)));
    if (currentRatingCount > 0) {
      ratingStatLabel.setText(String.format(Locale.ROOT, " %.1f/5 (%d đánh giá)", currentRating, currentRatingCount));
    } else {
      ratingStatLabel.setText(String.format(Locale.ROOT, " %.1f/5 (Chưa có đánh giá)", currentRating));
    }
  }

  private void updateFavoriteButton() {
    if (isFavorite) {
      favButton.setText("Đã yêu thích");
      favButton.setForeground(Theme.PRIMARY);
      favButton.setIcon(UiIcons.heart(14, Theme.PRIMARY));
    } else {
      favButton.setText("Yêu thích");
      favButton.setForeground(Theme.TEXT);
      favButton.setIcon(UiIcons.heart(14, Theme.MUTED));
    }
  }

  private void updateFollowButton() {
    if (isFollowing) {
      followButton.setText("Đang theo dõi");
      followButton.setForeground(new Color(0x16, 0x65, 0x34));
      followButton.setIcon(UiIcons.bell(14, new Color(0x16, 0x65, 0x34)));
    } else {
      followButton.setText("Theo dõi");
      followButton.setForeground(Theme.TEXT);
      followButton.setIcon(UiIcons.bell(14, Theme.MUTED));
    }
  }

  private void renderDetail() {
    detail.removeAll();
    detail.setLayout(new BorderLayout(0, 12));
    detail.setBorder(new EmptyBorder(14, 18, 14, 18));
    detail.setBackground(Theme.BACKGROUND);

    // 1. Dialog Header Bar: "Chi tiết truyện  COMIC NET / THÔNG TIN TRUYỆN" and "×"
    JPanel dialogHeader = new JPanel(new BorderLayout());
    dialogHeader.setOpaque(false);

    JPanel titleBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    titleBox.setOpaque(false);
    JLabel headingLabel = new JLabel("Chi tiết truyện");
    headingLabel.setFont(Theme.heading(18));
    headingLabel.setForeground(Theme.TEXT);

    JLabel breadcrumb = Theme.plainLabel("COMIC NET / THÔNG TIN TRUYỆN");
    breadcrumb.setFont(Theme.brand(11));
    breadcrumb.setForeground(new Color(0x94, 0xA3, 0xB8));

    titleBox.add(headingLabel);
    titleBox.add(breadcrumb);
    dialogHeader.add(titleBox, BorderLayout.WEST);

    // Single clean OS window close control, avoiding duplicate × button
    // dialogHeader.add(closeBtn, BorderLayout.EAST);
    detail.add(dialogHeader, BorderLayout.NORTH);

    // Main Center Split Pane / Body
    JPanel body = new JPanel(new BorderLayout(0, 12));
    body.setOpaque(false);

    // 2. Metadata Card (Top Card)
    JPanel headerCard = new JPanel(new BorderLayout(18, 0));
    headerCard.setBackground(Theme.SURFACE);
    headerCard.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(16, 18, 16, 18)));

    CoverArtCanvas cover = new CoverArtCanvas(10);
    cover.setPreferredSize(new Dimension(130, 195));
    cover.setMetadata(selected.path("title").asText("Truyện"), selected.path("type").asText("MANGA"));
    ImageIcon coverImage = covers.get(selected.path("id").asText());
    if (coverImage != null) {
      cover.setImage(coverImage.getImage());
    }
    headerCard.add(cover, BorderLayout.WEST);

    JPanel infoPanel = new JPanel();
    infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
    infoPanel.setOpaque(false);

    // Row 1: Title + Ongoing Badge
    JPanel row1 = new JPanel(new BorderLayout(10, 0));
    row1.setOpaque(false);
    JLabel comicTitle = Theme.plainLabel(selected.path("title").asText());
    comicTitle.setFont(Theme.heading(20));
    comicTitle.setForeground(Theme.TEXT);
    row1.add(comicTitle, BorderLayout.CENTER);

    String statusStr = selected.path("status").asText("ONGOING");
    boolean isDone = "COMPLETED".equalsIgnoreCase(statusStr);
    JLabel statusBadge = new JLabel(isDone ? "● Hoàn thành" : "● Đang cập nhật") {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isDone ? new Color(0xDB, 0xEA, 0xFE) : new Color(0xDC, 0xFC, 0xE7));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    statusBadge.setFont(Theme.brand(11));
    statusBadge.setForeground(isDone ? new Color(0x1E, 0x40, 0xAF) : new Color(0x16, 0x65, 0x34));
    statusBadge.setOpaque(false);
    statusBadge.setBorder(new EmptyBorder(4, 10, 4, 10));

    JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    badgeWrap.setOpaque(false);
    badgeWrap.add(statusBadge);
    row1.add(badgeWrap, BorderLayout.EAST);
    infoPanel.add(row1);
    infoPanel.add(Box.createVerticalStrut(6));

    // Row 2: Author · Updated Date
    String authors = "";
    for (JsonNode credit : selected.path("contributors")) {
      authors += names.getOrDefault("authors:" + credit.path("authorId").asText(), "Tác giả") + " ";
    }
    if (authors.isBlank()) authors = "Đang cập nhật";
    String updated = selected.path("updatedAt").asText("");
    if (updated.length() >= 10) updated = updated.substring(0, 10);
    else updated = "29/10/2026";
    JLabel authorDateLabel = Theme.plainLabel("Tác giả: " + authors + "  ·  Cập nhật: " + updated);
    authorDateLabel.setFont(Theme.label(12));
    authorDateLabel.setForeground(Theme.MUTED);
    infoPanel.add(authorDateLabel);
    infoPanel.add(Box.createVerticalStrut(8));

    // Row 3: Tag pills
    JPanel tagPills = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    tagPills.setOpaque(false);
    tagPills.add(buildMetaPill(selected.path("type").asText("Manga"), new Color(0xF1, 0xF5, 0xF9), Theme.TEXT));
    tagPills.add(buildMetaPill("Phiêu lưu", new Color(0xF1, 0xF5, 0xF9), Theme.TEXT));
    tagPills.add(buildMetaPill("Fantasy", new Color(0xF1, 0xF5, 0xF9), Theme.TEXT));
    tagPills.add(buildMetaPill("Tiếng Việt", new Color(0xFE, 0xF3, 0xC7), new Color(0xB4, 0x53, 0x09)));
    infoPanel.add(tagPills);
    infoPanel.add(Box.createVerticalStrut(10));

    // Row 4: 3 Stats (Rating, Views, Chapters)
    JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
    statsRow.setOpaque(false);

    ratingStatLabel.setFont(Theme.brand(12));
    ratingStatLabel.setForeground(new Color(0xD9, 0x77, 0x06));
    updateRatingDisplay();

    long views = selected.path("views").asLong(12408);
    JLabel viewsStat = Theme.plainLabel(" " + formatViews(views) + " lượt đọc");
    viewsStat.setIcon(UiIcons.eye(12, new Color(0x64, 0x74, 0x8B)));
    viewsStat.setFont(Theme.label(12));
    viewsStat.setForeground(new Color(0x47, 0x55, 0x69));

    JLabel chapterStat = Theme.plainLabel(" " + chapterRows.size() + " chương");
    chapterStat.setIcon(UiIcons.bookmark(12, new Color(0x64, 0x74, 0x8B)));
    chapterStat.setFont(Theme.label(12));
    chapterStat.setForeground(new Color(0x47, 0x55, 0x69));

    statsRow.add(ratingStatLabel);
    statsRow.add(viewsStat);
    statsRow.add(chapterStat);
    infoPanel.add(statsRow);
    infoPanel.add(Box.createVerticalStrut(8));

    // Row 5: Vietnamese Description with interactive expand/collapse
    String rawDesc = selected.path("description").asText("").strip();
    if (rawDesc.isBlank() || "Description".equalsIgnoreCase(rawDesc)) {
      rawDesc = "Chưa có phần giới thiệu chi tiết cho truyện này.";
    }
    final String fullDesc = rawDesc;
    boolean isLong = fullDesc.length() > 140;
    String shortDesc = isLong ? fullDesc.substring(0, 137) + "…" : fullDesc;
    JLabel descLabel = Theme.plainLabel(shortDesc);
    descLabel.setFont(Theme.label(12));
    descLabel.setForeground(Theme.MUTED);
    JPanel descBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    descBox.setOpaque(false);
    descBox.add(descLabel);
    if (isLong) {
      JLabel toggleDesc = new JLabel("Xem thêm");
      toggleDesc.setFont(Theme.brand(12));
      toggleDesc.setForeground(Theme.ACTION);
      toggleDesc.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      toggleDesc.addMouseListener(new MouseAdapter() {
        boolean expanded = false;
        @Override public void mouseClicked(MouseEvent e) {
          expanded = !expanded;
          descLabel.setText(expanded ? fullDesc : shortDesc);
          toggleDesc.setText(expanded ? "Thu gọn" : "Xem thêm");
          infoPanel.revalidate();
        }
      });
      descBox.add(toggleDesc);
    }
    infoPanel.add(descBox);

    headerCard.add(infoPanel, BorderLayout.CENTER);

    // 3. Chapters Card (Bottom Card)
    JPanel chaptersCard = new JPanel(new BorderLayout(0, 8));
    chaptersCard.setOpaque(true);
    chaptersCard.setBackground(Theme.SURFACE);
    chaptersCard.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(12, 16, 12, 16)));

    // Chapter Header Toolbar
    JPanel chHeader = new JPanel(new BorderLayout());
    chHeader.setOpaque(false);

    JPanel chTitleBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    chTitleBox.setOpaque(false);
    JLabel chTitle = new JLabel("Danh sách chương");
    chTitle.setFont(Theme.heading(15));
    chTitle.setForeground(Theme.TEXT);
    chCountLabel.setText("· " + chapterRows.size() + " chương");
    chCountLabel.setFont(Theme.label(12));
    chCountLabel.setForeground(Theme.MUTED);
    chTitleBox.add(chTitle);
    chTitleBox.add(chCountLabel);
    chHeader.add(chTitleBox, BorderLayout.WEST);

    JPanel chTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    chTools.setOpaque(false);
    JLabel transTag = Theme.plainLabel("Bản dịch");
    transTag.setFont(Theme.label(12));
    transTag.setForeground(Theme.MUTED);
    chTools.add(transTag);
    translations.setFont(Theme.label(12));
    translations.setPreferredSize(new Dimension(225, 30));
    translations.setRenderer(new DefaultListCellRenderer() {
      @Override public Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof String s && s.contains(": ")) {
          String[] parts = s.split(": ", 2);
          if (parts.length > 1) {
            lbl.setText(parts[1] + " (#" + parts[0] + ")");
          }
        }
        return lbl;
      }
    });
    chTools.add(translations);

    JComboBox<String> sortOrder = new JComboBox<>(new String[] {"Mới nhất ▼", "Cũ nhất ▼"});
    sortOrder.setFont(Theme.label(12));
    sortOrder.setPreferredSize(new Dimension(100, 30));
    sortOrder.addActionListener(e -> {
      Collections.reverse(chapterRows);
      reloadChapterRows();
    });
    chTools.add(sortOrder);
    chHeader.add(chTools, BorderLayout.EAST);
    chaptersCard.add(chHeader, BorderLayout.NORTH);

    // Chapter Table with custom modern styling
    chapterTable.setRowHeight(42);
    chapterTable.setFont(Theme.label(13));
    chapterTable.setForeground(Theme.TEXT);
    chapterTable.setBackground(Theme.SURFACE);
    chapterTable.setSelectionBackground(new Color(0xFF, 0xF1, 0xEE));
    chapterTable.setSelectionForeground(Theme.TEXT);
    chapterTable.setShowHorizontalLines(true);
    chapterTable.setShowVerticalLines(false);
    chapterTable.setGridColor(Theme.BORDER);
    if (chapterTable.getTableHeader() != null) {
      chapterTable.getTableHeader().setFont(Theme.brand(11));
      chapterTable.getTableHeader().setBackground(new Color(0xF8, 0xFA, 0xFC));
      chapterTable.getTableHeader().setForeground(Theme.MUTED);
      chapterTable.getTableHeader().setPreferredSize(new Dimension(100, 32));
    }

    setupChapterTableRenderers();

    chapterTable.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override public void mouseClicked(java.awt.event.MouseEvent e) {
        int row = chapterTable.getSelectedRow();
        if (row >= 0 && row < chapterRows.size()) {
          if (e.getClickCount() == 2 || chapterTable.columnAtPoint(e.getPoint()) == 3) {
            openSelectedChapter(row);
          }
        }
      }
    });

    JScrollPane chScroll = new JScrollPane(chapterTable);
    chScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
    chaptersCard.add(chScroll, BorderLayout.CENTER);

    // "Xem thêm chương ▼" footer with non-destructive pagination
    JPanel chMoreBar = new JPanel(new FlowLayout(FlowLayout.CENTER));
    chMoreBar.setOpaque(false);
    chMoreBtn.setText("Xem thêm chương ▼");
    chMoreBtn.setFont(Theme.label(12));
    chMoreBtn.setForeground(Theme.MUTED);
    chMoreBtn.setContentAreaFilled(false);
    chMoreBtn.setBorder(new EmptyBorder(4, 12, 4, 12));
    chMoreBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    for (var l : chMoreBtn.getActionListeners()) chMoreBtn.removeActionListener(l);
    chMoreBtn.addActionListener(e -> {
      chapterPage++;
      loadChapters(true);
    });
    chMoreBar.add(chMoreBtn);
    chaptersCard.add(chMoreBar, BorderLayout.SOUTH);

    // Split pane vertical
    JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, headerCard, chaptersCard);
    vertical.setResizeWeight(.38);
    vertical.setDividerLocation(235);
    vertical.setBorder(null);
    body.add(vertical, BorderLayout.CENTER);
    detail.add(body, BorderLayout.CENTER);

    // 4. Dialog Bottom Action Bar: Left (Đọc tiếp, Đọc từ đầu), Right (Yêu thích, Theo dõi, Đánh giá)
    JPanel bottomBar = new JPanel(new BorderLayout());
    bottomBar.setOpaque(true);
    bottomBar.setBackground(Theme.SURFACE);
    bottomBar.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(10, 16, 10, 16)));

    // Left buttons
    JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    leftButtons.setOpaque(false);
    PrimaryButton readContinue = new PrimaryButton("Đọc tiếp");
    readContinue.setIcon(UiIcons.bookOpen(14, Color.WHITE));
    readContinue.addActionListener(e -> {
      if (chapterRows.isEmpty()) {
        detailMessage.setText("Chưa có chương để đọc.");
        return;
      }
      if (!api.signedIn()) {
        if (detailDialog != null) detailDialog.setVisible(false);
        read.accept(ReaderLocation.chapter(chapterRows.get(0).path("id").asText()));
        return;
      }
      details.run(this, detailMessage, api.get("/me/history?size=50"), reply -> {
        JsonNode items = reply.body() != null ? reply.body().path("items") : null;
        JsonNode found = null;
        int foundIdx = -1;
        if (items != null && items.isArray()) {
          for (JsonNode prog : items) {
            String chId = prog.path("chapterId").asText();
            for (int i = 0; i < chapterRows.size(); i++) {
              if (chId.equals(chapterRows.get(i).path("id").asText())) {
                found = prog;
                foundIdx = i;
                break;
              }
            }
            if (found != null) break;
          }
        }
        if (detailDialog != null) detailDialog.setVisible(false);
        if (found == null) {
          read.accept(ReaderLocation.resume(chapterRows.get(0).path("id").asText()));
        } else {
          boolean completed = found.path("completed").asBoolean(false);
          if (completed && foundIdx + 1 < chapterRows.size()) {
            read.accept(ReaderLocation.chapter(chapterRows.get(foundIdx + 1).path("id").asText()));
          } else {
            String cId = found.path("chapterId").asText();
            String vId = found.path("versionId").asText(null);
            String pId = found.path("pageId").asText(null);
            double off = found.path("relativeOffset").asDouble(0.0);
            read.accept(ReaderLocation.resume(cId, vId, pId, off));
          }
        }
      });
    });

    JButton readFirst = button("Đọc từ đầu", () -> {
      if (chapterRows.isEmpty()) {
        detailMessage.setText("Chưa có chương để đọc.");
        return;
      }
      if (detailDialog != null) detailDialog.setVisible(false);
      read.accept(ReaderLocation.startFromBeginning(chapterRows.get(0).path("id").asText()));
    });

    leftButtons.add(readContinue);
    leftButtons.add(readFirst);
    bottomBar.add(leftButtons, BorderLayout.WEST);

    // Right buttons
    JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    rightButtons.setOpaque(false);

    updateFavoriteButton();
    favButton.setFont(Theme.label(13));
    favButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    favButton.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(8, 14, 8, 14)));
    favButton.addActionListener(e -> toggleFavorite());

    updateFollowButton();
    followButton.setFont(Theme.label(13));
    followButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    followButton.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(8, 14, 8, 14)));
    followButton.addActionListener(e -> toggleFollow());

    JButton rateBtn = button("Đánh giá truyện", UiIcons.star(14, new Color(0xD9, 0x77, 0x06)), () -> {
      if (selected != null) {
        String comicId = selected.path("id").asText();
        new RatingDialog(detailDialog, api, comicId, () -> {
          ratingSummaryTasks.run(this, detailMessage, api.get("/comics/" + comicId + "/rating-summary"),
              rReply -> {
                if (rReply.body() != null && rReply.body().has("average")) {
                  currentRating = rReply.body().path("average").asDouble(0.0);
                  currentRatingCount = rReply.body().path("count").asInt(0);
                  updateRatingDisplay();
                }
              });
        }).setVisible(true);
      }
    });

    rightButtons.add(favButton);
    rightButtons.add(followButton);
    rightButtons.add(rateBtn);
    bottomBar.add(rightButtons, BorderLayout.EAST);

    detail.add(bottomBar, BorderLayout.SOUTH);
    detail.revalidate();
    detail.repaint();
  }

  private JLabel buildMetaPill(String text, Color bg, Color fg) {
    JLabel pill = new JLabel("  " + text + "  ") {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    pill.setFont(Theme.brand(11));
    pill.setForeground(fg);
    pill.setOpaque(false);
    return pill;
  }

  private void setupChapterTableRenderers() {
    chapterTable.getColumnModel().getColumn(0).setPreferredWidth(120);
    chapterTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(
          JTable table, Object val, boolean isSel, boolean hasFoc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(table, val, isSel, hasFoc, row, col);
        l.setFont(Theme.heading(13));
        l.setForeground(Theme.TEXT);
        l.setBorder(new EmptyBorder(0, 12, 0, 0));
        return l;
      }
    });

    chapterTable.getColumnModel().getColumn(1).setPreferredWidth(320);
    chapterTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(
          JTable table, Object val, boolean isSel, boolean hasFoc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(table, val, isSel, hasFoc, row, col);
        l.setFont(Theme.label(13));
        l.setForeground(new Color(0x33, 0x41, 0x55));
        return l;
      }
    });

    chapterTable.getColumnModel().getColumn(2).setPreferredWidth(100);
    chapterTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(
          JTable table, Object val, boolean isSel, boolean hasFoc, int row, int col) {
        String statusText = String.valueOf(val);
        boolean isFree = "Miễn phí".equalsIgnoreCase(statusText);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        p.setOpaque(true);
        p.setBackground(isSel ? new Color(0xFF, 0xF1, 0xEE) : Theme.SURFACE);

        JLabel pill = new JLabel("  " + statusText + "  ") {
          @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isFree ? new Color(0xDC, 0xFC, 0xE7) : new Color(0xFF, 0xED, 0xD5));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.dispose();
            super.paintComponent(g);
          }
        };
        pill.setFont(Theme.brand(11));
        pill.setForeground(isFree ? new Color(0x16, 0x65, 0x34) : new Color(0xC2, 0x41, 0x0C));
        p.add(pill);
        return p;
      }
    });

    chapterTable.getColumnModel().getColumn(3).setPreferredWidth(120);
    chapterTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(
          JTable table, Object val, boolean isSel, boolean hasFoc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(table, val, isSel, hasFoc, row, col);
        String actionText = String.valueOf(val);
        boolean isFree = actionText.contains("Đọc");
        l.setFont(Theme.brand(12));
        l.setForeground(isFree ? Theme.ACTION : new Color(0x25, 0x63, 0xEB));
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
      }
    });
  }

  private void openSelectedChapter(int row) {
    if (row < 0 || row >= chapterRows.size()) return;
    JsonNode ch = chapterRows.get(row);
    if (detailDialog != null) detailDialog.setVisible(false);
    read.accept(ReaderLocation.chapter(ch.path("id").asText()));
  }

  private void loadTranslations() {
    if (selected == null) return;
    String comicId = selected.path("id").asText();
    details.run(
        this,
        detailMessage,
        api.get("/comics/" + comicId + "/translations?page=" + translationPage + "&size=10"),
        reply -> {
          translations.removeAllItems();
          for (JsonNode item : reply.body().path("items")) {
            String id = item.path("id").asText();
            String lang = item.path("languageTag").asText();
            if (lang == null || lang.isBlank()) lang = item.path("language").asText();
            String langDisplay = "Tiếng Việt";
            if ("en".equalsIgnoreCase(lang) || "en-US".equalsIgnoreCase(lang)) {
              langDisplay = "English";
            } else if (lang != null && !lang.isBlank() && !"vi".equalsIgnoreCase(lang) && !"vi-VN".equalsIgnoreCase(lang)) {
              langDisplay = lang;
            }
            String team = item.path("teamId").asText("");
            String label = id + ": " + langDisplay;
            if (!team.isBlank() && !"null".equalsIgnoreCase(team)) {
              label = id + ": " + langDisplay + " - Nhóm " + team;
            }
            translations.addItem(label);
          }
          loadChapters();
        });
  }

  private void loadChapters() {
    loadChapters(false);
  }

  private void loadChapters(boolean append) {
    if (selected == null || translations.getSelectedItem() == null) return;
    String translationId = ((String) translations.getSelectedItem()).split(":")[0];
    if (!append) {
      chapterPage = 0;
      chapterRows.clear();
      chapters.setRowCount(0);
    }
    details.run(
        this,
        detailMessage,
        api.get("/translations/" + translationId + "/chapters?page=" + chapterPage + "&size=20"),
        reply -> {
          JsonNode items = reply.body() != null ? reply.body().path("items") : null;
          long total = reply.body() != null ? reply.body().path("page").path("totalElements").asLong(0) : 0;
          int newCount = 0;
          if (items != null && items.isArray()) {
            for (JsonNode ch : items) {
              String id = ch.path("id").asText();
              boolean exists = false;
              for (JsonNode existing : chapterRows) {
                if (id.equals(existing.path("id").asText())) {
                  exists = true;
                  break;
                }
              }
              if (!exists) {
                chapterRows.add(ch);
                newCount++;
              }
            }
          }
          reloadChapterRows();
          chCountLabel.setText("· " + Math.max(chapterRows.size(), total) + " chương");
          if (append && newCount == 0) {
            chapterPage = Math.max(0, chapterPage - 1);
          }
          if (chapterRows.size() >= total || newCount < 20) {
            chMoreBtn.setText(chapterRows.isEmpty() ? "Chưa có chương" : "Đã hiển thị tất cả " + chapterRows.size() + " chương");
            chMoreBtn.setEnabled(false);
          } else {
            chMoreBtn.setText("Xem thêm chương ▼");
            chMoreBtn.setEnabled(true);
          }
        });
  }

  private void reloadChapterRows() {
    chapters.setRowCount(0);
    for (JsonNode ch : chapterRows) {
      String disp = ch.path("displayNumber").asText("");
      if (disp.isBlank()) disp = ch.path("chapterNumber").asText("1");
      String policy = ch.path("accessPolicy").asText(ch.path("accessLevel").asText("FREE"));
      boolean isFree = "FREE".equalsIgnoreCase(policy);
      chapters.addRow(new Object[] {
          "Chương " + disp,
          ch.path("title").asText("—"),
          isFree ? "Miễn phí" : "10 xu",
          isFree ? "Đọc chương →" : "Mở khóa →"
      });
    }
  }

  private void toggleFavorite() {
    if (selected == null) return;
    String comicId = selected.path("id").asText();
    String method = isFavorite ? "DELETE" : "POST";
    actions.run(this, detailMessage,
        api.request(method, "/me/favorites/" + comicId, null, null),
        reply -> {
          isFavorite = !isFavorite;
          updateFavoriteButton();
          detailMessage.setText(isFavorite ? "Đã thêm vào danh sách yêu thích." : "Đã bỏ yêu thích.");
        });
  }

  private void toggleFollow() {
    if (selected == null) return;
    String comicId = selected.path("id").asText();
    String method = isFollowing ? "DELETE" : "PUT";
    actions.run(this, detailMessage,
        api.request(method, "/me/follows/" + comicId, null, null),
        reply -> {
          isFollowing = !isFollowing;
          updateFollowButton();
          detailMessage.setText(isFollowing ? "Đã theo dõi truyện." : "Đã bỏ theo dõi.");
        });
  }

  private void loadMetadata() {
    if (loaded) return;
    loaded = true;
    metadata.run(this, message, api.get("/genres"), reply -> {
      for (JsonNode g : reply.body().path("items")) {
        genre.addItem(new Option(g.path("id").asText(), g.path("name").asText()));
      }
    });
    loadHighlights();
  }

  private static JPanel row(JComponent... components) {
    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    panel.setOpaque(false);
    for (JComponent c : components) panel.add(c);
    return panel;
  }

  private static JButton button(String text, Runnable action) {
    JButton b = new JButton(text);
    b.setFont(Theme.label(13));
    b.setForeground(Theme.TEXT);
    b.setBackground(Theme.SURFACE);
    b.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(8, 14, 8, 14)));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.addActionListener(e -> action.run());
    return b;
  }

  private static JButton button(String text, Icon icon, Runnable action) {
    JButton b = button(text, action);
    if (icon != null) b.setIcon(icon);
    return b;
  }

  private static String encoded(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  @Override public void onShown() {
    loadMetadata();
    load();
  }

  @Override public void onHidden() {
    listings.close();
    details.close();
    ratingSummaryTasks.close();
    actions.close();
  }

  @Override public void sessionChanged() {
    listings.close();
    details.close();
    ratingSummaryTasks.close();
    actions.close();
    load();
  }

  @Override public void close() {
    listings.close();
    details.close();
    ratingSummaryTasks.close();
    actions.close();
    recommendations.close();
    rankings.close();
  }
}
