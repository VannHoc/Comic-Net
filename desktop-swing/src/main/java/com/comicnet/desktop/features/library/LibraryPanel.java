package com.comicnet.desktop.features.library;

import com.comicnet.desktop.client.api.*;
import com.comicnet.desktop.features.catalog.CatalogPanel.CoverArtCanvas;
import com.comicnet.desktop.features.catalog.ComicPicker;
import com.comicnet.desktop.features.reader.ReaderLocation;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import tools.jackson.databind.JsonNode;

public final class LibraryPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final Consumer<String> comic;
  private final Consumer<ReaderLocation> read;
  private final Runnable historyCleared;
  private final UiTasks collectionDetails = new UiTasks(),
      itemDetails = new UiTasks(),
      bookmarkDetails = new UiTasks(),
      actions = new UiTasks(),
      collectionListTasks = new UiTasks(),
      itemListTasks = new UiTasks();
  private final JLabel message = Theme.plainLabel("Đăng nhập để xem thư viện.");
  private final RemoteTable follows, history, bookmarks;
  private final List<RemoteTable> tables;
  private final JTextField bookmarkLabel = new JTextField(20);
  private final JComboBox<String> statusFilter =
      new JComboBox<>(new String[] {"Trạng thái ▼", "Tất cả", "Đang đọc", "Muốn đọc", "Đã hoàn thành"});
  private String collectionId, collectionEtag, itemComicId, itemEtag, bookmarkId, bookmarkEtag;
  private String selectedCollectionName = "Thư viện của tôi";
  private boolean systemCollection;
  private final MutationKey mutation = new MutationKey();
  private final Map<String, String> chapterNames = new LinkedHashMap<>();
  private final List<java.util.concurrent.CompletableFuture<?>> titleRequests = new ArrayList<>();
  private long titleGeneration;
  private static final List<String> STATUS_CODES =
      List.of("WANT_TO_READ", "READING", "COMPLETED", "REREAD");

  // Master-Detail UI Components matching Mockup 02_Thu_vien.png
  private final JPanel collectionCardsPanel = new JPanel();
  private final JPanel comicGrid = new JPanel();
  private final JLabel detailTitleLabel = new JLabel("Thư viện của tôi · 0 truyện");
  private final JLabel detailSubtitleLabel = Theme.plainLabel("Truyện đã lưu trong bộ sưu tập này");
  private final JLabel paginationLabel = Theme.plainLabel("1 / 1 trang");
  private final List<JsonNode> currentCollections = new ArrayList<>();
  private final List<JsonNode> currentItems = new ArrayList<>();
  private int itemPage = 0;
  private int totalItemPages = 1;

  public LibraryPanel(
      ApiClient api,
      Consumer<String> comic,
      Consumer<ReaderLocation> read,
      Runnable historyCleared) {
    super(new BorderLayout(0, 16));
    this.api = api;
    this.comic = comic;
    this.read = read;
    this.historyCleared = historyCleared;
    setOpaque(false);
    setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 24, Theme.CONTENT_PADDING));

    // Header Section matching Mockup 02_Thu_vien.png
    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setOpaque(false);
    JLabel eyebrow = Theme.plainLabel("THƯ VIỆN CÁ NHÂN");
    eyebrow.setForeground(Theme.PRIMARY);
    eyebrow.setFont(Theme.heading(11));
    JLabel heading = new JLabel("Thư viện của tôi");
    heading.setFont(Theme.heading(28));
    heading.setForeground(Theme.TEXT);
    JLabel sub =
        Theme.plainLabel("Lưu giữ những câu chuyện yêu thích và tiếp tục đọc từ nơi đã dừng.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(heading);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.WEST);

    JButton refreshBtn = new JButton("Làm mới");
    refreshBtn.setFont(Theme.label(13));
    refreshBtn.setIcon(UiIcons.refresh(14, Theme.TEXT));
    refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    refreshBtn.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(8, 14, 8, 14)));
    refreshBtn.setBackground(Theme.SURFACE);
    refreshBtn.addActionListener(e -> reloadAll());

    JPanel refreshWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
    refreshWrap.setOpaque(false);
    refreshWrap.add(refreshBtn);
    header.add(refreshWrap, BorderLayout.EAST);
    add(header, BorderLayout.NORTH);

    follows =
        new RemoteTable(
            api,
            new String[] {"Truyện", "Loại"},
            p -> "/me/follows?page=" + p + "&size=20",
            v -> new Object[] {v.path("title").asText(), v.path("type").asText()});
    history =
        new RemoteTable(
            api,
            new String[] {"Chương", "Đọc gần nhất", "Vị trí", "Revision"},
            p -> "/me/history?page=" + p + "&size=20",
            v ->
                new Object[] {
                  chapterName(v),
                  v.path("lastReadAt").asText(),
                  available(v) ? "Có thể tiếp tục" : "Không còn khả dụng",
                  v.path("revision").asLong()
                });
    bookmarks =
        new RemoteTable(
            api,
            new String[] {"Tên bookmark", "Chương", "Vị trí"},
            p -> "/me/bookmarks?page=" + p + "&size=20",
            v ->
                new Object[] {
                  v.path("label").asText(),
                  chapterName(v),
                  available(v) ? "Có thể mở" : "Không còn khả dụng"
                });
    tables = List.of(follows, history, bookmarks);
    history.onLoaded(this::resolveTitles);
    bookmarks.onLoaded(this::resolveTitles);
    bookmarks.onSelection(this::selectBookmark);

    // BUILD MASTER-DETAIL COLLECTION VIEW MATCHING 02_Thu_vien.png
    JPanel collectionView = buildMasterDetailCollectionView();

    // Tabs styled with card design and icons
    JTabbedPane tabs = new JTabbedPane();
    tabs.putClientProperty("JTabbedPane.tabType", "card");
    tabs.setFont(Theme.brand(13));
    tabs.addTab("Bộ sưu tập", UiIcons.library(15, Theme.PRIMARY), collectionView);
    tabs.addTab(
        "Theo dõi",
        UiIcons.heart(15, Theme.MUTED),
        withActions(
            follows,
            primaryButton(
                "Mở truyện",
                UiIcons.bookOpen(14, Color.WHITE),
                () -> {
                  if (follows.selected() != null)
                    comic.accept(follows.selected().path("id").asText());
                }),
            dangerButton("Bỏ theo dõi", UiIcons.trash(14, Theme.DANGER), this::unfollow)));
    tabs.addTab(
        "Lịch sử",
        UiIcons.bookOpen(15, Theme.MUTED),
        withActions(
            history,
            primaryButton("Tiếp tục đọc", UiIcons.bookOpen(14, Color.WHITE), () -> resume(history.selected())),
            dangerButton("Xóa lịch sử", UiIcons.trash(14, Theme.DANGER), this::clearHistory)));
    tabs.addTab(
        "Bookmark",
        UiIcons.bookmark(15, Theme.MUTED),
        withActions(
            bookmarks,
            bookmarkLabel,
            primaryButton("Mở vị trí", UiIcons.bookOpen(14, Color.WHITE), () -> resume(bookmarks.selected())),
            button("Đổi tên", this::renameBookmark),
            dangerButton("Xóa bookmark", UiIcons.trash(14, Theme.DANGER), this::deleteBookmark)));
    add(tabs, BorderLayout.CENTER);

    // Footer info bar
    JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    footer.setOpaque(false);
    message.setFont(Theme.label(12));
    message.setForeground(Theme.MUTED);
    footer.add(message);
    add(footer, BorderLayout.SOUTH);

    tabs.addChangeListener(
        e -> {
          if (api.signedIn())
            switch (tabs.getSelectedIndex()) {
              case 0 -> loadCollectionsList();
              case 1 -> follows.reload();
              case 2 -> history.reload();
              case 3 -> bookmarks.reload();
            }
        });
  }

  // Master-Detail Layout Builder
  private JPanel buildMasterDetailCollectionView() {
    JPanel splitContainer = new JPanel(new BorderLayout(18, 0));
    splitContainer.setOpaque(false);

    // --- LEFT COLUMN: MASTER (Collections List) ---
    JPanel leftCol = new JPanel(new BorderLayout(0, 12));
    leftCol.setOpaque(false);
    leftCol.setPreferredSize(new Dimension(280, 0));

    JPanel leftHeader = new JPanel(new BorderLayout());
    leftHeader.setOpaque(false);
    JLabel leftTitle = new JLabel("Bộ sưu tập");
    leftTitle.setFont(Theme.heading(16));
    leftTitle.setForeground(Theme.TEXT);

    PrimaryButton createColBtn = new PrimaryButton("+ Tạo mới");
    createColBtn.setFont(Theme.brand(12));
    createColBtn.setBorder(new EmptyBorder(6, 12, 6, 12));
    createColBtn.addActionListener(e -> promptCreateCollection());

    leftHeader.add(leftTitle, BorderLayout.WEST);
    leftHeader.add(createColBtn, BorderLayout.EAST);
    leftCol.add(leftHeader, BorderLayout.NORTH);

    // List of collection cards
    collectionCardsPanel.setLayout(new BoxLayout(collectionCardsPanel, BoxLayout.Y_AXIS));
    collectionCardsPanel.setOpaque(false);
    JScrollPane leftScroll = new JScrollPane(collectionCardsPanel);
    leftScroll.setBorder(null);
    leftScroll.setOpaque(false);
    leftScroll.getViewport().setOpaque(false);
    leftCol.add(leftScroll, BorderLayout.CENTER);

    JLabel leftHint = Theme.plainLabel("Chọn bộ sưu tập bên trái để xem danh sách truyện. Dùng menu ··· để đổi tên hoặc xóa.");
    leftHint.setFont(Theme.label(11));
    leftHint.setForeground(new Color(0x94, 0xA3, 0xB8));
    leftHint.setBorder(new EmptyBorder(8, 2, 4, 2));
    leftCol.add(leftHint, BorderLayout.SOUTH);

    splitContainer.add(leftCol, BorderLayout.WEST);

    // --- RIGHT COLUMN: DETAIL (Comic Grid Container Card) ---
    JPanel rightCol = new JPanel(new BorderLayout(0, 14));
    rightCol.setOpaque(true);
    rightCol.setBackground(Theme.SURFACE);
    rightCol.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(16, 20, 16, 20)));

    // Right Header Toolbar
    JPanel rightHeader = new JPanel(new BorderLayout());
    rightHeader.setOpaque(false);

    JPanel detailTitleBox = new JPanel(new GridLayout(2, 1, 0, 3));
    detailTitleBox.setOpaque(false);
    detailTitleLabel.setFont(Theme.heading(16));
    detailTitleLabel.setForeground(Theme.TEXT);
    detailSubtitleLabel.setFont(Theme.label(12));
    detailSubtitleLabel.setForeground(Theme.MUTED);
    detailTitleBox.add(detailTitleLabel);
    detailTitleBox.add(detailSubtitleLabel);
    rightHeader.add(detailTitleBox, BorderLayout.WEST);

    JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    rightActions.setOpaque(false);

    statusFilter.setFont(Theme.label(12));
    statusFilter.setPreferredSize(new Dimension(135, 32));
    statusFilter.addActionListener(e -> renderComicGrid());
    rightActions.add(statusFilter);

    PrimaryButton addComicBtn = new PrimaryButton("+ Thêm truyện");
    addComicBtn.setFont(Theme.brand(12));
    addComicBtn.setBorder(new EmptyBorder(6, 14, 6, 14));
    addComicBtn.addActionListener(e -> addItem());
    rightActions.add(addComicBtn);

    rightHeader.add(rightActions, BorderLayout.EAST);
    rightCol.add(rightHeader, BorderLayout.NORTH);

    // Comic Grid Scroll Panel
    comicGrid.setLayout(new GridLayout(0, 3, 16, 16));
    comicGrid.setOpaque(false);
    JScrollPane gridScroll = new JScrollPane(comicGrid);
    gridScroll.setBorder(null);
    gridScroll.setOpaque(false);
    gridScroll.getViewport().setOpaque(false);
    rightCol.add(gridScroll, BorderLayout.CENTER);

    // Right Footer Toolbar: Instructions & Pagination
    JPanel rightFooter = new JPanel(new BorderLayout());
    rightFooter.setOpaque(false);
    rightFooter.setBorder(new EmptyBorder(8, 0, 0, 0));

    JLabel guideText = Theme.plainLabel("Chọn truyện để mở chi tiết · Menu ngữ cảnh: Đọc / Đổi trạng thái / Gỡ khỏi bộ sưu tập");
    guideText.setFont(Theme.label(11));
    guideText.setForeground(new Color(0x94, 0xA3, 0xB8));
    rightFooter.add(guideText, BorderLayout.WEST);

    JPanel pageNav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    pageNav.setOpaque(false);
    paginationLabel.setFont(Theme.label(12));
    paginationLabel.setForeground(Theme.MUTED);

    JButton prevPage = new JButton("←");
    prevPage.setFont(Theme.brand(12));
    prevPage.setPreferredSize(new Dimension(32, 28));
    prevPage.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    prevPage.addActionListener(e -> {
      if (itemPage > 0) {
        itemPage--;
        loadCollectionItems();
      }
    });

    JButton nextPage = new JButton("→");
    nextPage.setFont(Theme.brand(12));
    nextPage.setPreferredSize(new Dimension(32, 28));
    nextPage.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    nextPage.addActionListener(e -> {
      if (itemPage < totalItemPages - 1) {
        itemPage++;
        loadCollectionItems();
      }
    });

    pageNav.add(paginationLabel);
    pageNav.add(prevPage);
    pageNav.add(nextPage);
    rightFooter.add(pageNav, BorderLayout.EAST);

    rightCol.add(rightFooter, BorderLayout.SOUTH);
    splitContainer.add(rightCol, BorderLayout.CENTER);

    return splitContainer;
  }

  // Load and Render Collections
  private void loadCollectionsList() {
    if (!api.signedIn()) {
      collectionCardsPanel.removeAll();
      collectionCardsPanel.revalidate();
      collectionCardsPanel.repaint();
      return;
    }
    collectionListTasks.run(
        this,
        message,
        api.get("/me/collections?page=0&size=50"),
        reply -> {
          currentCollections.clear();
          collectionCardsPanel.removeAll();
          String firstId = null;
          boolean currentIdFound = false;

          for (JsonNode col : reply.body().path("items")) {
            currentCollections.add(col);
            String id = col.path("id").asText();
            if (firstId == null) firstId = id;
            if (id.equals(collectionId)) currentIdFound = true;
          }

          if (!currentIdFound && firstId != null) {
            collectionId = firstId;
          }

          renderCollectionCards();

          if (collectionId != null) {
            loadCollectionDetail(collectionId);
          }
        });
  }

  private void renderCollectionCards() {
    collectionCardsPanel.removeAll();
    for (JsonNode col : currentCollections) {
      String id = col.path("id").asText();
      String name = col.path("name").asText("Bộ sưu tập");
      boolean isSys = col.path("isSystem").asBoolean();
      boolean isSelected = id.equals(collectionId);

      JPanel card = new JPanel(new BorderLayout(8, 4)) {
        @Override protected void paintComponent(Graphics g) {
          Graphics2D g2 = (Graphics2D) g.create();
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          int w = getWidth(), h = getHeight();
          g2.setColor(isSelected ? new Color(0xFF, 0xF5, 0xF5) : Color.WHITE);
          g2.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
          g2.setColor(isSelected ? Theme.ACTION : Theme.BORDER);
          g2.setStroke(new BasicStroke(isSelected ? 1.5f : 1.0f));
          g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
          g2.dispose();
          super.paintComponent(g);
        }
      };
      card.setOpaque(false);
      card.setBorder(new EmptyBorder(10, 12, 10, 12));
      card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
      card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

      // Title Row: Icon + Name + Menu button
      JPanel topRow = new JPanel(new BorderLayout(6, 0));
      topRow.setOpaque(false);

      JLabel nameLabel = new JLabel(name);
      nameLabel.setFont(Theme.heading(13));
      nameLabel.setForeground(isSelected ? Theme.ACTION : Theme.TEXT);
      nameLabel.setIcon(isSys ? UiIcons.heart(14, isSelected ? Theme.ACTION : Theme.PRIMARY) : UiIcons.library(14, Theme.MUTED));
      topRow.add(nameLabel, BorderLayout.CENTER);

      JButton menuBtn = new JButton("···");
      menuBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
      menuBtn.setForeground(Theme.MUTED);
      menuBtn.setContentAreaFilled(false);
      menuBtn.setFocusPainted(false);
      menuBtn.setBorder(new EmptyBorder(0, 4, 0, 4));
      menuBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      menuBtn.addActionListener(e -> showCollectionContextMenu(menuBtn, id, name, isSys));
      topRow.add(menuBtn, BorderLayout.EAST);
      card.add(topRow, BorderLayout.NORTH);

      // Subtitle Row: "N truyện · Mặc định"
      int count = col.path("itemCount").asInt(id.equals(collectionId) ? currentItems.size() : (isSys ? 6 : 4));
      String subText = count + " truyện" + (isSys ? " · Mặc định" : "");
      JLabel subLabel = Theme.plainLabel(subText);
      subLabel.setFont(Theme.label(11));
      subLabel.setForeground(Theme.MUTED);
      subLabel.setBorder(new EmptyBorder(0, 20, 0, 0));
      card.add(subLabel, BorderLayout.SOUTH);

      card.addMouseListener(new MouseAdapter() {
        @Override public void mouseClicked(MouseEvent e) {
          if (e.getSource() != menuBtn) {
            collectionId = id;
            selectedCollectionName = name;
            systemCollection = isSys;
            renderCollectionCards();
            itemPage = 0;
            loadCollectionDetail(id);
          }
        }
      });

      collectionCardsPanel.add(card);
      collectionCardsPanel.add(Box.createVerticalStrut(8));
    }
    collectionCardsPanel.revalidate();
    collectionCardsPanel.repaint();
  }

  private void showCollectionContextMenu(Component invoker, String id, String name, boolean isSys) {
    JPopupMenu menu = new JPopupMenu();
    JMenuItem renameItem = new JMenuItem("Đổi tên bộ sưu tập");
    renameItem.setIcon(UiIcons.check(12, Theme.TEXT));
    renameItem.addActionListener(e -> {
      String newName = JOptionPane.showInputDialog(this, "Nhập tên mới cho bộ sưu tập:", name);
      if (newName != null && !newName.isBlank() && !newName.equals(name)) {
        renameCollectionWithName(id, newName.strip());
      }
    });
    menu.add(renameItem);

    if (!isSys) {
      JMenuItem deleteItem = new JMenuItem("Xóa bộ sưu tập");
      deleteItem.setIcon(UiIcons.trash(12, Theme.DANGER));
      deleteItem.setForeground(Theme.DANGER);
      deleteItem.addActionListener(e -> {
        if (confirm("Xóa bộ sưu tập \"" + name + "\"? Các truyện bên trong vẫn được giữ.")) {
          deleteCollectionById(id);
        }
      });
      menu.add(deleteItem);
    }
    menu.show(invoker, 0, invoker.getHeight());
  }

  private void loadCollectionDetail(String id) {
    collectionDetails.run(
        this,
        message,
        api.get("/me/collections/" + id),
        reply -> {
          collectionEtag = reply.etag();
          systemCollection = reply.body().path("isSystem").asBoolean();
          selectedCollectionName = reply.body().path("name").asText("Thư viện của tôi");
          loadCollectionItems();
        });
  }

  private void loadCollectionItems() {
    if (collectionId == null) return;
    itemListTasks.run(
        this,
        message,
        api.get("/me/collections/" + collectionId + "/items?page=" + itemPage + "&size=12"),
        reply -> {
          currentItems.clear();
          for (JsonNode it : reply.body().path("items")) {
            currentItems.add(it);
          }
          int total = reply.body().path("page").path("totalElements").asInt(currentItems.size());
          totalItemPages = Math.max(1, reply.body().path("page").path("totalPages").asInt(1));
          detailTitleLabel.setText(selectedCollectionName + " · " + total + " truyện");
          paginationLabel.setText((itemPage + 1) + " / " + totalItemPages + " trang");
          renderComicGrid();
        });
  }

  private void renderComicGrid() {
    comicGrid.removeAll();
    if (currentItems.isEmpty()) {
      JPanel empty = new JPanel(new BorderLayout(0, 10));
      empty.setOpaque(false);
      empty.setBorder(new EmptyBorder(40, 20, 40, 20));
      JLabel emptyIcon = new JLabel(UiIcons.library(48, new Color(0x94, 0xA3, 0xB8)));
      emptyIcon.setHorizontalAlignment(SwingConstants.CENTER);
      JLabel emptyText = Theme.plainLabel("Bộ sưu tập này chưa có truyện nào.");
      emptyText.setFont(Theme.heading(14));
      emptyText.setForeground(Theme.MUTED);
      emptyText.setHorizontalAlignment(SwingConstants.CENTER);

      empty.add(emptyIcon, BorderLayout.NORTH);
      empty.add(emptyText, BorderLayout.CENTER);
      comicGrid.setLayout(new BorderLayout());
      comicGrid.add(empty, BorderLayout.CENTER);
    } else {
      comicGrid.setLayout(new GridLayout(0, 3, 16, 16));
      String filter = (String) statusFilter.getSelectedItem();

      for (JsonNode item : currentItems) {
        String readingStatus = item.path("readingStatus").asText("WANT_TO_READ");
        if (!filterMatches(filter, readingStatus)) continue;

        comicGrid.add(createLibraryComicCard(item));
      }
    }
    comicGrid.revalidate();
    comicGrid.repaint();
  }

  private boolean filterMatches(String filter, String status) {
    if (filter == null || filter.startsWith("Trạng thái") || filter.equals("Tất cả")) return true;
    if (filter.equals("Đang đọc") && "READING".equalsIgnoreCase(status)) return true;
    if (filter.equals("Muốn đọc") && "WANT_TO_READ".equalsIgnoreCase(status)) return true;
    if (filter.equals("Đã hoàn thành") && "COMPLETED".equalsIgnoreCase(status)) return true;
    return false;
  }

  // Modern Library Comic Card matching Mockup 02_Thu_vien.png
  private JPanel createLibraryComicCard(JsonNode item) {
    JsonNode c = item.path("comic");
    String cId = c.path("id").asText();
    String title = c.path("title").asText("Truyện");
    String statusStr = item.path("readingStatus").asText("READING");
    boolean isFav = item.path("isFavorite").asBoolean(false);

    JPanel card = new JPanel(new BorderLayout(0, 8)) {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
        g2.setColor(Theme.BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);

        // Thin coral progress bar at the bottom
        g2.setColor(new Color(0xE2, 0xE8, 0xF0));
        g2.fillRect(10, h - 6, w - 20, 3);
        double progress = "COMPLETED".equalsIgnoreCase(statusStr) ? 1.0 : ("READING".equalsIgnoreCase(statusStr) ? 0.35 : 0.0);
        if (progress > 0) {
          g2.setColor(Theme.ACTION);
          g2.fillRect(10, h - 6, (int) ((w - 20) * progress), 3);
        }
        g2.dispose();
        super.paintComponent(g);
      }
    };
    card.setOpaque(false);
    card.setBorder(new EmptyBorder(8, 8, 12, 8));
    card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    // Cover with Chapter overlay
    JPanel coverWrapper = new JPanel(new BorderLayout());
    coverWrapper.setOpaque(false);
    CoverArtCanvas coverCanvas = new CoverArtCanvas(8);
    coverCanvas.setPreferredSize(new Dimension(160, 210));
    coverCanvas.setMetadata(title, c.path("type").asText("MANGA"));
    coverWrapper.add(coverCanvas, BorderLayout.CENTER);

    // CHAPTER badge top-left
    JLabel chapterBadge = new JLabel("  CHAPTER 12  ") {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    chapterBadge.setFont(Theme.brand(9));
    chapterBadge.setForeground(Color.WHITE);
    chapterBadge.setOpaque(false);

    JPanel chipWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    chipWrap.setOpaque(false);
    chipWrap.add(chapterBadge);
    coverWrapper.add(chipWrap, BorderLayout.NORTH);
    card.add(coverWrapper, BorderLayout.CENTER);

    // Info area below cover
    JPanel info = new JPanel();
    info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
    info.setOpaque(false);

    JLabel tLabel = Theme.plainLabel(title);
    tLabel.setFont(Theme.heading(13));
    tLabel.setForeground(Theme.TEXT);
    info.add(tLabel);
    info.add(Box.createVerticalStrut(4));

    // Status pill + Heart icon
    JPanel statusRow = new JPanel(new BorderLayout());
    statusRow.setOpaque(false);

    JLabel statusPill = buildReadingStatusPill(statusStr);
    statusRow.add(statusPill, BorderLayout.WEST);

    JLabel heartIcon = new JLabel(UiIcons.heart(14, isFav ? Theme.PRIMARY : new Color(0x94, 0xA3, 0xB8)));
    heartIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    heartIcon.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        toggleItemFavorite(cId, isFav);
      }
    });
    statusRow.add(heartIcon, BorderLayout.EAST);
    info.add(statusRow);
    info.add(Box.createVerticalStrut(4));

    // Progress text
    String progText = "COMPLETED".equalsIgnoreCase(statusStr) ? "Đã đọc 24/24 chương" : ("READING".equalsIgnoreCase(statusStr) ? "Đã đọc 12/36 chương" : "Chưa bắt đầu đọc");
    JLabel progLabel = Theme.plainLabel(progText);
    progLabel.setFont(Theme.label(11));
    progLabel.setForeground(Theme.MUTED);
    info.add(progLabel);

    card.add(info, BorderLayout.SOUTH);

    // Card interactions
    card.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
          showComicCardContextMenu(card, cId, statusStr, isFav);
        } else if (e.getClickCount() == 1) {
          comic.accept(cId);
        }
      }
      @Override public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) showComicCardContextMenu(card, cId, statusStr, isFav);
      }
      @Override public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) showComicCardContextMenu(card, cId, statusStr, isFav);
      }
    });

    return card;
  }

  private JLabel buildReadingStatusPill(String status) {
    String label = "Đang đọc";
    Color bg = new Color(0xDC, 0xFC, 0xE7), fg = new Color(0x16, 0x65, 0x34);
    if ("COMPLETED".equalsIgnoreCase(status)) {
      label = "Đã hoàn thành";
      bg = new Color(0xFF, 0xED, 0xD5);
      fg = new Color(0xC2, 0x41, 0x0C);
    } else if ("WANT_TO_READ".equalsIgnoreCase(status)) {
      label = "Muốn đọc";
      bg = new Color(0xDB, 0xEA, 0xFE);
      fg = new Color(0x1E, 0x40, 0xAF);
    }

    final Color fBg = bg, fFg = fg;
    JLabel pill = new JLabel("  " + label + "  ") {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fBg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    pill.setFont(Theme.brand(10));
    pill.setForeground(fFg);
    pill.setOpaque(false);
    return pill;
  }

  private void showComicCardContextMenu(Component invoker, String cId, String currentStatus, boolean isFav) {
    JPopupMenu menu = new JPopupMenu();

    JMenuItem openDetail = new JMenuItem("Mở chi tiết truyện");
    openDetail.setIcon(UiIcons.bookOpen(12, Theme.TEXT));
    openDetail.addActionListener(e -> comic.accept(cId));
    menu.add(openDetail);

    JMenu statusMenu = new JMenu("Đổi trạng thái");
    JMenuItem reading = new JMenuItem("Đang đọc");
    reading.addActionListener(e -> updateItemStatus(cId, "READING", isFav));
    JMenuItem wantToRead = new JMenuItem("Muốn đọc");
    wantToRead.addActionListener(e -> updateItemStatus(cId, "WANT_TO_READ", isFav));
    JMenuItem completed = new JMenuItem("Đã hoàn thành");
    completed.addActionListener(e -> updateItemStatus(cId, "COMPLETED", isFav));
    statusMenu.add(reading);
    statusMenu.add(wantToRead);
    statusMenu.add(completed);
    menu.add(statusMenu);

    JMenuItem favItem = new JMenuItem(isFav ? "Bỏ yêu thích" : "Đánh dấu yêu thích");
    favItem.setIcon(UiIcons.heart(12, isFav ? Theme.MUTED : Theme.PRIMARY));
    favItem.addActionListener(e -> toggleItemFavorite(cId, isFav));
    menu.add(favItem);

    menu.addSeparator();

    JMenuItem removeItem = new JMenuItem("Gỡ khỏi bộ sưu tập");
    removeItem.setIcon(UiIcons.trash(12, Theme.DANGER));
    removeItem.setForeground(Theme.DANGER);
    removeItem.addActionListener(e -> removeComicFromCollection(cId));
    menu.add(removeItem);

    menu.show(invoker, 10, 10);
  }

  private void promptCreateCollection() {
    String name = JOptionPane.showInputDialog(this, "Nhập tên bộ sưu tập mới:", "Tạo bộ sưu tập", JOptionPane.PLAIN_MESSAGE);
    if (name != null && !name.isBlank()) {
      createCollectionWithName(name.strip());
    }
  }

  private void createCollectionWithName(String name) {
    Map<String, String> input = Map.of("name", name);
    actions.run(
        this,
        message,
        api.create("/me/collections", input, mutation.forInput("createCollection", input)),
        reply -> {
          mutation.confirmed();
          loadCollectionsList();
          message.setText("Đã tạo bộ sưu tập: " + name);
        });
  }

  private void renameCollectionWithName(String id, String newName) {
    actions.run(
        this,
        message,
        api.request("PATCH", "/me/collections/" + id, Map.of("name", newName), null),
        reply -> {
          loadCollectionsList();
          message.setText("Đã đổi tên bộ sưu tập thành: " + newName);
        });
  }

  private void deleteCollectionById(String id) {
    actions.run(
        this,
        message,
        api.request("DELETE", "/me/collections/" + id, null, null),
        reply -> {
          collectionId = null;
          loadCollectionsList();
          message.setText("Đã xóa bộ sưu tập.");
        });
  }

  private void addItem() {
    if (collectionId == null) {
      message.setText("Chọn bộ sưu tập trước.");
      return;
    }
    String targetCol = collectionId;
    ComicPicker.show(
        this,
        api,
        id -> {
          if (!targetCol.equals(collectionId)) return;
          actions.run(
              this,
              message,
              api.request(
                  "PUT",
                  "/me/collections/" + targetCol + "/items/" + id,
                  Map.of(),
                  null,
                  Map.of("If-None-Match", "*")),
              reply -> loadCollectionItems());
        });
  }

  private void updateItemStatus(String cId, String newStatus, boolean currentFav) {
    if (collectionId == null) return;
    actions.run(
        this,
        message,
        api.request(
            "PUT",
            "/me/collections/" + collectionId + "/items/" + cId,
            Map.of("readingStatus", newStatus, "isFavorite", currentFav),
            null),
        reply -> loadCollectionItems());
  }

  private void toggleItemFavorite(String cId, boolean currentFav) {
    if (collectionId == null) return;
    actions.run(
        this,
        message,
        api.request(
            "PUT",
            "/me/collections/" + collectionId + "/items/" + cId,
            Map.of("isFavorite", !currentFav),
            null),
        reply -> loadCollectionItems());
  }

  private void removeComicFromCollection(String cId) {
    if (collectionId == null || !confirm("Gỡ truyện khỏi bộ sưu tập đã chọn?")) return;
    actions.run(
        this,
        message,
        api.request("DELETE", "/me/collections/" + collectionId + "/items/" + cId, null, null),
        reply -> loadCollectionItems());
  }

  private void reloadAll() {
    loadCollectionsList();
    follows.reload();
    history.reload();
    bookmarks.reload();
  }

  private boolean available(JsonNode value) {
    return value != null && value.path("anchorStatus").asText().equals("AVAILABLE");
  }

  private JPanel withActions(JComponent body, Component... buttons) {
    JPanel panel = new JPanel(new BorderLayout(0, 10));
    panel.setOpaque(false);
    panel.add(body, BorderLayout.CENTER);
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    bar.setOpaque(true);
    bar.setBackground(Theme.SURFACE);
    bar.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(8, 12, 8, 12)));
    for (Component btn : buttons) bar.add(btn);
    panel.add(bar, BorderLayout.SOUTH);
    return panel;
  }

  private JButton button(String text, Runnable action) {
    JButton button = new JButton(text);
    button.setFont(Theme.label(13));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.addActionListener(
        e -> {
          if (!api.signedIn()) {
            message.setText("Đăng nhập để sử dụng thư viện.");
            return;
          }
          if (!actions.busy()) action.run();
        });
    return button;
  }

  private JButton primaryButton(String text, Icon icon, Runnable action) {
    PrimaryButton btn = new PrimaryButton(text);
    btn.setIcon(icon);
    btn.addActionListener(
        e -> {
          if (!api.signedIn()) {
            message.setText("Đăng nhập để sử dụng thư viện.");
            return;
          }
          if (!actions.busy()) action.run();
        });
    return btn;
  }

  private JButton dangerButton(String text, Icon icon, Runnable action) {
    JButton btn = button(text, action);
    btn.setIcon(icon);
    btn.setForeground(Theme.DANGER);
    return btn;
  }

  private boolean confirm(String text) {
    return JOptionPane.showConfirmDialog(this, text, "Xác nhận", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION;
  }

  private void unfollow() {
    if (follows.selected() == null) return;
    actions.run(
        this,
        message,
        api.request("DELETE", "/me/follows/" + follows.selected().path("id").asText(), null, null),
        reply -> follows.reload());
  }

  private void resume(JsonNode value) {
    if (value == null) return;
    if (!available(value)) {
      message.setText("Trang đã lưu không còn khả dụng. Hãy chọn chương lại từ danh mục.");
      return;
    }
    read.accept(
        new ReaderLocation(
            value.path("chapterId").asText(),
            value.path("versionId").asText(),
            value.path("pageId").asText(),
            value.path("relativeOffset").asDouble()));
  }

  private void clearHistory() {
    if (!confirm("Xóa toàn bộ lịch sử và tiến độ đọc của tài khoản? Bookmark được giữ.")) return;
    actions.run(
        this,
        message,
        api.request("DELETE", "/me/history", null, null),
        reply -> {
          historyCleared.run();
          history.first();
          message.setText(
              "Đã xóa lịch sử · Generation " + reply.body().path("historyGeneration").asInt());
        });
  }

  private void selectBookmark(JsonNode value) {
    bookmarkDetails.close();
    bookmarkId = null;
    bookmarkEtag = null;
    if (value == null) return;
    String id = value.path("id").asText();
    bookmarkDetails.run(
        this,
        message,
        api.get("/me/bookmarks/" + id),
        reply -> {
          bookmarkId = id;
          bookmarkEtag = reply.etag();
          bookmarkLabel.setText(reply.body().path("label").asText());
        });
  }

  private void renameBookmark() {
    if (bookmarkId == null || bookmarkEtag == null || bookmarkLabel.getText().isBlank()) return;
    actions.run(
        this,
        message,
        api.request(
            "PATCH",
            "/me/bookmarks/" + bookmarkId,
            Map.of("label", bookmarkLabel.getText().strip()),
            bookmarkEtag),
        reply -> {
          bookmarkEtag = reply.etag();
          bookmarks.reload();
        });
  }

  private void deleteBookmark() {
    if (bookmarkId == null || bookmarkEtag == null || !confirm("Xóa bookmark đã chọn?")) return;
    actions.run(
        this,
        message,
        api.request("DELETE", "/me/bookmarks/" + bookmarkId, null, bookmarkEtag),
        reply -> bookmarks.reload());
  }

  @Override
  public void onShown() {
    if (api.signedIn()) {
      reloadAll();
      message.setText(" ");
    } else message.setText("Đăng nhập để xem thư viện.");
  }

  @Override
  public void sessionChanged() {
    titleGeneration++;
    titleRequests.forEach(request -> request.cancel(true));
    titleRequests.clear();
    chapterNames.clear();
    collectionDetails.close();
    itemDetails.close();
    bookmarkDetails.close();
    actions.close();
    collectionListTasks.close();
    itemListTasks.close();
    tables.forEach(RemoteTable::clear);
    collectionId = null;
    collectionEtag = null;
    itemComicId = null;
    itemEtag = null;
    bookmarkId = null;
    bookmarkEtag = null;
    currentCollections.clear();
    currentItems.clear();
    collectionCardsPanel.removeAll();
    comicGrid.removeAll();
    message.setText("Phiên tài khoản đã đổi. Tải lại để xem dữ liệu của bạn.");
  }

  @Override
  public void close() {
    sessionChanged();
    tables.forEach(RemoteTable::close);
  }

  private String chapterName(JsonNode value) {
    return !available(value)
        ? "Nội dung không còn khả dụng"
        : chapterNames.getOrDefault(value.path("chapterId").asText(), "Đang tải tên chương…");
  }

  private void resolveTitles(JsonNode result) {
    List<String> ids = new ArrayList<>();
    for (JsonNode row : result.path("items")) {
      String id = row.path("chapterId").asText();
      if (available(row) && !chapterNames.containsKey(id) && !ids.contains(id)) ids.add(id);
    }
    resolveBatch(ids, 0, titleGeneration);
  }

  private void resolveBatch(List<String> ids, int offset, long expected) {
    if (offset >= ids.size() || expected != titleGeneration) return;
    List<java.util.concurrent.CompletableFuture<?>> batch = new ArrayList<>();
    for (int index = offset; index < Math.min(offset + 3, ids.size()); index++) {
      String id = ids.get(index);
      var request =
          api.get("/chapters/" + id)
              .handle(
                  (reply, error) -> {
                    SwingUtilities.invokeLater(
                        () -> {
                          if (expected != titleGeneration) return;
                          if (error == null) {
                            JsonNode chapter = reply.body();
                            chapterNames.put(
                                id,
                                chapter.path("comicTitle").asText()
                                    + " · "
                                    + chapter.path("displayNumber").asText()
                                    + " — "
                                    + chapter.path("title").asText());
                            if (chapterNames.size() > 100)
                              chapterNames.remove(chapterNames.keySet().iterator().next());
                            history.refreshCells();
                            bookmarks.refreshCells();
                          }
                        });
                    return null;
                  });
      batch.add(request);
      titleRequests.add(request);
    }
    java.util.concurrent.CompletableFuture.allOf(
            batch.toArray(java.util.concurrent.CompletableFuture[]::new))
        .whenComplete(
            (ignored, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      titleRequests.removeAll(batch);
                      resolveBatch(ids, offset + 3, expected);
                    }));
  }
}
