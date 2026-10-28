package com.comicnet.desktop.features.catalog;

import com.comicnet.desktop.client.api.*;
import com.comicnet.desktop.client.http.MediaTransport;
import com.comicnet.desktop.features.reader.ReaderLocation;
import com.comicnet.desktop.features.search.SearchAssist;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

public final class CatalogPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final MediaTransport media;
  private final Consumer<ReaderLocation> read;
  private final UiTasks listings = new UiTasks(),
      details = new UiTasks(),
      actions = new UiTasks(),
      metadata = new UiTasks();
  private final UiTasks recommendations = new UiTasks(), rankings = new UiTasks();
  private final JPanel recommendedItems = new JPanel(new GridLayout(0, 1, 0, 6));
  private final JPanel rankingItems = new JPanel(new GridLayout(0, 1, 0, 6));
  private final JLabel recommendedState = Theme.plainLabel("Đang tải đề xuất…");
  private final JLabel rankingState = Theme.plainLabel("Đang tải xếp hạng…");
  private final JLabel message = Theme.plainLabel("Chọn Tìm để tải truyện."),
      detailMessage = Theme.plainLabel("Chọn một truyện để xem chi tiết.");
  private final JTextField query = new JTextField(20);
  private final SearchAssist searchAssist;
  private final JComboBox<String>
      type = new JComboBox<>(new String[] {"Tất cả", "MANGA", "MANHWA", "MANHUA", "COMIC"}),
      status =
          new JComboBox<>(new String[] {"Tất cả", "ONGOING", "COMPLETED", "HIATUS", "DROPPED"}),
      sort = new JComboBox<>(new String[] {"Mới cập nhật", "Tên A–Z"});
  private final JComboBox<Option> genre = new JComboBox<>(),
      tag = new JComboBox<>(),
      translations = new JComboBox<>();
  private final DefaultListModel<JsonNode> comics = new DefaultListModel<>();
  private final JList<JsonNode> comicList = new JList<>(comics);
  private final JPanel comicGrid = new JPanel(new GridLayout(0, 4, 18, 22));
  private final JScrollPane comicScroll;
  private final JPanel detail = new JPanel(new BorderLayout(0, 14));
  private final JPanel hero = new JPanel(new BorderLayout(18, 0));
  private final JLabel heroCover = Theme.plainLabel(""),
      heroTitle = Theme.plainLabel("Khám phá truyện từ thư viện Comic Net"),
      heroMeta = Theme.plainLabel("Các tựa truyện nổi bật sẽ xuất hiện tại đây.");
  private final PrimaryButton openFeatured = new PrimaryButton("Xem chi tiết");
  private String heroComicId;
  private final DefaultTableModel chapters =
      new DefaultTableModel(new Object[] {"Chương", "Tên", "Trang", "Truy cập"}, 0) {
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
    super(new BorderLayout(0, 14));
    this.api = api;
    this.media = media;
    this.read = read;
    searchAssist = new SearchAssist(api, query, this::search);
    setOpaque(false);
    setBorder(new EmptyBorder(18, 30, 22, 30));
    JPanel top = new JPanel(new BorderLayout(0, 14));
    top.setOpaque(false);
    JPanel heading = new JPanel(new GridLayout(0, 1, 0, 3));
    heading.setOpaque(false);
    JLabel eyebrow = Theme.plainLabel("COMIC NET  /  KHÁM PHÁ");
    eyebrow.setForeground(Theme.ACTION);
    eyebrow.setFont(Theme.brand(11));
    JLabel title = new JLabel("Truyện dành cho bạn");
    title.setFont(Theme.heading(28));
    JLabel subtitle = Theme.plainLabel("Tìm câu chuyện tiếp theo từ thư viện Comic Net.");
    subtitle.setForeground(Theme.MUTED);
    heading.add(eyebrow);
    heading.add(title);
    heading.add(subtitle);
    top.add(heading, BorderLayout.NORTH);
    JPanel filters = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 5));
    filters.setOpaque(false);
    query.getAccessibleContext().setAccessibleName("Tên truyện, tác giả hoặc thể loại");
    filters.add(button("Lịch sử tìm", () -> searchAssist.history(this)));
    filters.add(type);
    genre.addItem(new Option("", "Thể loại: Tất cả"));
    tag.addItem(new Option("", "Tag: Tất cả"));
    filters.add(genre);
    filters.add(tag);
    filters.add(status);
    filters.add(sort);
    filters.add(
        button(
            "Gợi ý",
            () ->
                new com.comicnet.desktop.features.recommendation.RecommendationDialog(
                        this, api, null, this::openComic)
                    .setVisible(true)));
    top.add(filters);
    add(top, BorderLayout.NORTH);
    query.addActionListener(e -> search());
    comicList.setCellRenderer(new ComicRenderer());
    comicList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    comicList.setVisibleRowCount(2);
    comicList.setFixedCellWidth(178);
    comicList.setFixedCellHeight(250);
    comicList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    comicList.setBackground(Theme.BACKGROUND);
    comicList.getAccessibleContext().setAccessibleName("Danh sách truyện");
    comicList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting() && comicList.getSelectedValue() != null)
            loadDetail(comicList.getSelectedValue().path("id").asText());
        });
    JPanel left = new JPanel(new BorderLayout(0, 10));
    left.setOpaque(false);
    comicGrid.setOpaque(false);
    comicScroll = new JScrollPane(comicGrid);
    comicScroll.setBorder(null);
    comicScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    comicScroll.getVerticalScrollBar().setUnitIncrement(24);
    left.add(comicScroll, BorderLayout.CENTER);
    JPanel footer = new JPanel(new BorderLayout());
    footer.setOpaque(false);
    footer.add(message);
    footer.add(
        row(
            button(
                "Trước",
                () -> {
                  page = Math.max(0, page - 1);
                  load();
                }),
            button(
                "Sau",
                () -> {
                  page++;
                  load();
                })),
        BorderLayout.SOUTH);
    left.add(footer, BorderLayout.SOUTH);
    detail.setOpaque(false);
    detail.add(detailMessage, BorderLayout.NORTH);
    JPanel feed = new JPanel(new BorderLayout(0, 14));
    feed.setOpaque(false);
    hero.setOpaque(true);
    hero.setBackground(Theme.SURFACE);
    hero.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(18, 22, 18, 22)));
    hero.setPreferredSize(new Dimension(0, 225));
    heroCover.setPreferredSize(new Dimension(145, 190));
    heroCover.setHorizontalAlignment(SwingConstants.CENTER);
    heroCover.setOpaque(true);
    heroCover.setBackground(Theme.ELEVATED);
    heroCover.setForeground(Theme.ACTION);
    heroCover.setFont(Theme.brand(17));
    heroTitle.setFont(Theme.heading(20));
    heroMeta.setForeground(Theme.MUTED);
    JPanel heroCopy = new JPanel(new BorderLayout(0, 8));
    heroCopy.setOpaque(false);
    heroCopy.add(heroTitle, BorderLayout.NORTH);
    heroCopy.add(heroMeta, BorderLayout.CENTER);
    openFeatured.setEnabled(false);
    openFeatured.setVisible(false);
    openFeatured.addActionListener(e -> { if (heroComicId != null) openComic(heroComicId); });
    heroCopy.add(openFeatured, BorderLayout.SOUTH);
    hero.add(heroCover, BorderLayout.WEST);
    hero.add(heroCopy, BorderLayout.CENTER);
    feed.add(hero, BorderLayout.NORTH);
    JLabel section = Theme.plainLabel("MỚI CẬP NHẬT  /  KHÁM PHÁ");
    section.setFont(Theme.brand(16));
    JPanel browse = new JPanel(new BorderLayout(0, 12));
    browse.setOpaque(false);
    browse.add(section, BorderLayout.NORTH);
    browse.add(left, BorderLayout.CENTER);
    feed.add(browse, BorderLayout.CENTER);
    JPanel highlights = new JPanel(new GridLayout(2, 1, 0, 14));
    highlights.setOpaque(false);
    highlights.setPreferredSize(new Dimension(238, 0));
    highlights.add(highlightSection("ĐỀ XUẤT CHO BẠN", recommendedItems, recommendedState));
    highlights.add(highlightSection("PHỔ BIẾN", rankingItems, rankingState));
    feed.add(highlights, BorderLayout.EAST);
    add(feed, BorderLayout.CENTER);
    comicScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override public void componentResized(java.awt.event.ComponentEvent event) {
        int columns = Math.max(1, comicScroll.getViewport().getWidth() / 205);
        if (!(comicGrid.getLayout() instanceof GridLayout layout)) return;
        if (layout.getColumns() != columns) {
          layout.setColumns(columns);
          comicGrid.revalidate();
        }
      }
    });
    Theme.plainCombo(genre);
    Theme.plainCombo(tag);
    Theme.plainCombo(translations);
    Theme.plainTable(chapterTable);
    chapterTable.setRowHeight(34);
    translations.addActionListener(
        e -> {
          if (!filling) {
            chapterPage = 0;
            loadChapters();
          }
        });
  }

  private JButton button(String title, Runnable action) {
    JButton button = new JButton(title);
    button.addActionListener(e -> action.run());
    return button;
  }

  private JPanel row(Component... components) {
    JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 5));
    row.setOpaque(false);
    for (Component component : components) row.add(component);
    return row;
  }

  private String encoded(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private void search() {
    page = 0;
    searchAssist.remember(message);
    load();
  }

  public void search(String phrase) {
    query.setText(phrase == null ? "" : phrase.strip());
    search();
  }

  @Override
  public void onShown() {
    if (!loaded) {
      loaded = true;
      loadMetadata();
      load();
      loadHighlights();
    }
  }

  private void loadMetadata() {
    metadata.run(
        this,
        detailMessage,
        api.get("/metadata"),
        reply -> {
          filling = true;
          for (String kind : List.of("genres", "tags", "contentWarnings", "authors", "publishers"))
            for (JsonNode value : reply.body().path(kind)) {
              names.put(kind + ":" + value.path("id").asText(), value.path("name").asText());
              if (kind.equals("genres"))
                genre.addItem(new Option(value.path("id").asText(), value.path("name").asText()));
              if (kind.equals("tags"))
                tag.addItem(new Option(value.path("id").asText(), value.path("name").asText()));
            }
          filling = false;
          if (selected == null && !details.busy())
            detailMessage.setText("Chọn một truyện để xem chi tiết và các chương.");
        });
  }

  private JPanel highlightSection(String title, JPanel items, JLabel state) {
    JPanel panel = new JPanel(new BorderLayout(0, 10));
    panel.setBackground(Theme.SURFACE);
    panel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(14, 12, 14, 12)));
    JLabel heading = Theme.plainLabel(title);
    heading.setFont(Theme.brand(13));
    panel.add(heading, BorderLayout.NORTH);
    items.setOpaque(false);
    JPanel body = new JPanel(new BorderLayout());
    body.setOpaque(false);
    body.add(items, BorderLayout.NORTH);
    body.add(state, BorderLayout.SOUTH);
    JScrollPane scroll = new JScrollPane(body);
    scroll.setBorder(null);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    panel.add(scroll, BorderLayout.CENTER);
    return panel;
  }

  private void loadHighlights() {
    recommendations.run(this, recommendedState, api.get("/recommendations?size=5"), reply -> {
      recommendedItems.removeAll();
      for (JsonNode item : reply.body().path("items")) {
        JButton link = button(item.path("title").asText("Truyện"),
            () -> openComic(item.path("id").asText()));
        link.setHorizontalAlignment(SwingConstants.LEFT);
        recommendedItems.add(link);
      }
      recommendedState.setText(recommendedItems.getComponentCount() == 0
          ? "Chưa có đề xuất." : "");
      recommendedItems.revalidate();
    });
    rankings.run(this, rankingState, api.get("/rankings?period=WEEKLY&size=5"), reply -> {
      rankingItems.removeAll();
      for (JsonNode item : reply.body().path("items")) {
        JButton link = button(item.path("rank").asText() + "  " + item.path("title").asText("Truyện"),
            () -> openComic(item.path("comicId").asText()));
        link.setHorizontalAlignment(SwingConstants.LEFT);
        rankingItems.add(link);
      }
      rankingState.setText(rankingItems.getComponentCount() == 0
          ? "Chưa có lượt đọc hợp lệ." : "");
      rankingItems.revalidate();
    });
  }

  private void load() {
    long generation = ++imageGeneration;
    images.forEach(future -> future.cancel(true));
    images.clear();
    covers.clear();
    String path =
        (query.getText().isBlank()
                ? "/comics?"
                : "/search/comics?q=" + encoded(query.getText().strip()) + "&")
            + "page="
            + page
            + "&size=20&sort="
            + (sort.getSelectedIndex() == 0 ? "UPDATED_DESC" : "TITLE_ASC");
    if (type.getSelectedIndex() > 0) path += "&type=" + type.getSelectedItem();
    if (status.getSelectedIndex() > 0) path += "&status=" + status.getSelectedItem();
    Option g = (Option) genre.getSelectedItem(), t = (Option) tag.getSelectedItem();
    if (g != null && !g.id().isEmpty()) path += "&genreId=" + g.id();
    if (t != null && !t.id().isEmpty()) path += "&tagId=" + t.id();
    listings.run(
        this,
        message,
        api.get(path),
        reply -> {
          comics.clear();
          comicGrid.removeAll();
          comicGrid.setLayout(new GridLayout(0,
              Math.max(1, comicScroll.getViewport().getWidth() / 205), 18, 22));
          for (JsonNode comic : reply.body().path("items")) {
            comics.addElement(comic);
            JLabel coverLabel = Theme.plainLabel("Ảnh bìa chưa có");
            coverLabel.setHorizontalAlignment(SwingConstants.CENTER);
            coverLabel.setPreferredSize(new Dimension(154, 215));
            JPanel copy = new JPanel(new GridLayout(0, 1, 0, 3));
            copy.setOpaque(false);
            JLabel cardTitle = Theme.plainLabel(comic.path("title").asText(""));
            cardTitle.setFont(Theme.brand(14));
            String rating = comic.path("rating").isNumber()
                ? String.format(Locale.ROOT, "★ %.1f", comic.path("rating").asDouble())
                : "Chưa có đánh giá";
            JLabel cardMeta = Theme.plainLabel(comic.path("type").asText("COMIC") + "  ·  " + rating);
            cardMeta.setFont(Theme.label(11));
            cardMeta.setForeground(Theme.MUTED);
            JLabel reads = Theme.plainLabel(comic.path("views").isNumber()
                ? comic.path("views").asLong() + " lượt đọc" : "Chưa có lượt đọc");
            reads.setFont(Theme.label(11));
            reads.setForeground(Theme.MUTED);
            copy.add(cardTitle);
            copy.add(cardMeta);
            copy.add(reads);
            JButton card = new JButton();
            card.setLayout(new BorderLayout(0, 8));
            card.setBackground(Theme.SURFACE);
            card.setForeground(Theme.TEXT);
            card.setOpaque(true);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ELEVATED), new EmptyBorder(9, 9, 10, 9)));
            card.getAccessibleContext().setAccessibleName(comic.path("title").asText());
            card.add(coverLabel, BorderLayout.NORTH);
            card.add(copy, BorderLayout.CENTER);
            card.addActionListener(e -> loadDetail(comic.path("id").asText()));
            comicGrid.add(card);
            String url = comic.path("coverUrl").asText("");
            if (!url.isEmpty()) {
              try {
                var request = media.thumbnail(URI.create(url), 170, 215);
                images.add(request);
                request.whenComplete(
                    (image, error) ->
                        SwingUtilities.invokeLater(
                            () -> {
                              if (generation != imageGeneration) return;
                              if (error == null)
                                covers.put(comic.path("id").asText(), new ImageIcon(image));
                              if (error == null) {
                                coverLabel.setIcon(new ImageIcon(image));
                                coverLabel.setText("");
                              }
                              comicList.repaint();
                              if (comic.path("id").asText().equals(heroComicId)) setFeatured(comic);
                            }));
              } catch (IllegalArgumentException ignored) {
                /* renderer keeps an explicit unavailable cover state */
              }
            }
          }
          comicGrid.revalidate();
          comicGrid.repaint();
          if (!comics.isEmpty()) setFeatured(comics.get(0));
          else {
            heroComicId = null;
            openFeatured.setEnabled(false);
            openFeatured.setVisible(false);
            heroCover.setIcon(null);
            heroCover.setText("COMIC NET");
            heroTitle.setText("Chưa có truyện để giới thiệu");
            heroMeta.setText("Thử đổi bộ lọc để khám phá truyện khác.");
            JLabel empty = Theme.plainLabel("Chưa có truyện phù hợp với bộ lọc hiện tại.");
            empty.setFont(Theme.label(16));
            empty.setForeground(Theme.MUTED);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setPreferredSize(new Dimension(320, 180));
            comicGrid.setLayout(new BorderLayout());
            comicGrid.add(empty);
            comicGrid.revalidate();
          }
          message.setText(
              comics.isEmpty()
                  ? "Không có truyện phù hợp."
                  : "Trang "
                      + (page + 1)
                      + " · "
                      + reply.body().path("page").path("totalElements").asLong()
                      + " truyện");
        });
  }

  private void setFeatured(JsonNode comic) {
    heroComicId = comic.path("id").asText();
    openFeatured.setEnabled(true);
    openFeatured.setVisible(true);
    heroTitle.setText(comic.path("title").asText("Truyện"));
    heroMeta.setText(comic.path("type").asText("COMIC") + "  ·  "
        + comic.path("status").asText("Đang cập nhật") + "  ·  "
        + comic.path("countryCode").asText("—"));
    ImageIcon cover = covers.get(heroComicId);
    heroCover.setIcon(cover);
    heroCover.setText(cover == null ? "Ảnh bìa" : "");
    hero.revalidate();
    hero.repaint();
  }

  public void openComic(String id) {
    loadDetail(id);
  }

  private void loadDetail(String id) {
    if (detailDialog != null) detailDialog.dispose();
    long generation = ++detailGeneration;
    translationPage = 0;
    chapterPage = 0;
    selected = null;
    chapters.setRowCount(0);
    chapterRows.clear();
    details.run(
        this,
        detailMessage,
        api.get("/comics/" + id),
        reply -> {
          if (generation != detailGeneration) return;
          selected = reply.body();
          renderDetail();
          detailDialog = new JDialog(SwingUtilities.getWindowAncestor(this),
              "Comic Net · " + selected.path("title").asText("Chi tiết truyện"),
              Dialog.ModalityType.MODELESS);
          detailDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
          detailDialog.setContentPane(detail);
          detailDialog.setSize(960, 720);
          detailDialog.setMinimumSize(new Dimension(740, 560));
          detailDialog.setLocationRelativeTo(this);
          detailDialog.setVisible(true);
          loadTranslations();
        });
  }

  private void renderDetail() {
    detail.removeAll();
    JLabel title = Theme.plainLabel(selected.path("title").asText());
    title.setFont(Theme.heading(23));
    JTextArea text = FormGrid.text(8);
    text.setEditable(false);
    String authors = "";
    for (JsonNode credit : selected.path("contributors"))
      authors +=
          names.getOrDefault("authors:" + credit.path("authorId").asText(), "Tác giả chưa có tên")
              + " ("
              + credit.path("contribution").asText()
              + ") ";
    String description = selected.path("description").asText();
    if (description.isBlank()) description = "Chưa có mô tả.";
    StringBuilder body =
        new StringBuilder(description)
            .append("\n\nTác giả / họa sĩ: ")
            .append(authors.isBlank() ? "Chưa cập nhật" : authors)
            .append("\nLoại: ")
            .append(selected.path("type").asText())
            .append(" · ")
            .append(selected.path("countryCode").asText())
            .append("\nĐánh giá / lượt đọc: Chưa có số liệu");
    for (var group :
        Map.of(
                "alternativeTitles",
                "Tên khác",
                "genreIds",
                "Thể loại",
                "tagIds",
                "Tags",
                "warningIds",
                "Cảnh báo",
                "publisherIds",
                "Nhà xuất bản")
            .entrySet()) {
      List<String> values = new ArrayList<>();
      String kind =
          switch (group.getKey()) {
            case "genreIds" -> "genres";
            case "tagIds" -> "tags";
            case "warningIds" -> "contentWarnings";
            case "publisherIds" -> "publishers";
            default -> "";
          };
      for (JsonNode value : selected.path(group.getKey()))
        values.add(
            kind.isEmpty()
                ? value.asText()
                : names.getOrDefault(kind + ":" + value.asText(), value.asText()));
      body.append("\n").append(group.getValue()).append(": ").append(String.join(", ", values));
    }
    text.setText(body.toString());
    text.setCaretPosition(0);
    JPanel header = new JPanel(new BorderLayout(14, 10));
    header.setOpaque(false);
    JPanel coverPanel = new JPanel(new BorderLayout());
    coverPanel.setOpaque(false);
    JLabel cover = Theme.plainLabel("Ảnh bìa chưa có");
    cover.setHorizontalAlignment(SwingConstants.CENTER);
    cover.setPreferredSize(new Dimension(150, 190));
    cover.setOpaque(true);
    cover.setBackground(Theme.ELEVATED);
    ImageIcon coverImage = covers.get(selected.path("id").asText());
    if (coverImage != null) { cover.setIcon(coverImage); cover.setText(""); }
    coverPanel.add(cover, BorderLayout.NORTH);
    JPanel summary = new JPanel(new BorderLayout(0, 8));
    summary.setOpaque(false);
    JLabel metadataLine = Theme.plainLabel(selected.path("type").asText() + "  ·  "
        + selected.path("status").asText("Đang cập nhật") + "  ·  "
        + selected.path("countryCode").asText());
    metadataLine.setForeground(Theme.ACTION);
    summary.add(title, BorderLayout.NORTH);
    summary.add(metadataLine, BorderLayout.CENTER);
    summary.add(new JScrollPane(text), BorderLayout.SOUTH);
    header.add(coverPanel, BorderLayout.WEST);
    header.add(summary, BorderLayout.CENTER);
    JPanel content = new JPanel(new BorderLayout(0, 8));
    content.setOpaque(false);
    content.add(
        row(
            translations,
            button(
                "Bản dịch trước",
                () -> {
                  translationPage = Math.max(0, translationPage - 1);
                  loadTranslations();
                }),
            button(
                "Bản dịch sau",
                () -> {
                  translationPage++;
                  loadTranslations();
                })),
        BorderLayout.NORTH);
    content.add(new JScrollPane(chapterTable));
    PrimaryButton start = new PrimaryButton("Đọc chương đã chọn");
    start.addActionListener(
        e -> {
          int row = chapterTable.getSelectedRow();
          if (row < 0) {
            detailMessage.setText("Chọn một chương để đọc.");
            return;
          }
          read.accept(ReaderLocation.chapter(chapterRows.get(row).path("id").asText()));
        });
    content.add(
        row(
            button(
                "Chương trước",
                () -> {
                  chapterPage = Math.max(0, chapterPage - 1);
                  loadChapters();
                }),
            button(
                "Chương sau",
                () -> {
                  chapterPage++;
                  loadChapters();
                }),
            start),
        BorderLayout.SOUTH);
    JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, header, content);
    vertical.setResizeWeight(.48);
    vertical.setBorder(null);
    detail.add(vertical);
    detail.add(
        row(
            button("Yêu thích", () -> saveFavorite()),
            button("Theo dõi", () -> follow(false)),
            button("Bỏ theo dõi", () -> follow(true)),
            button(
                "Truyện tương tự",
                () -> {
                  if (selected != null)
                    new com.comicnet.desktop.features.recommendation.RecommendationDialog(
                            this, api, selected.path("id").asText(), this::openComic)
                        .setVisible(true);
                }),
            button(
                "Bình luận truyện",
                () -> {
                  if (selected != null)
                    new com.comicnet.desktop.features.community.CommentDialog(
                            this, api, selected.path("id").asText(), null, null, null)
                        .setVisible(true);
                }),
            button(
                "Bình luận chương",
                () -> {
                  int index = chapterTable.getSelectedRow();
                  if (selected != null && index >= 0 && index < chapterRows.size())
                    new com.comicnet.desktop.features.community.CommentDialog(
                            this,
                            api,
                            selected.path("id").asText(),
                            chapterRows.get(index).path("id").asText(),
                            null,
                            null)
                        .setVisible(true);
                }),
            button(
                "Đánh giá / Nhận xét",
                () -> {
                  if (selected != null)
                    new com.comicnet.desktop.features.recommendation.RatingDialog(
                            this, api, selected.path("id").asText())
                        .setVisible(true);
                }),
            detailMessage),
        BorderLayout.SOUTH);
    detail.revalidate();
    detail.repaint();
  }

  private void loadTranslations() {
    if (selected == null) return;
    String id = selected.path("id").asText();
    details.run(
        this,
        detailMessage,
        api.get("/comics/" + id + "/translations?page=" + translationPage + "&size=20"),
        reply -> {
          filling = true;
          translations.removeAllItems();
          for (JsonNode translation : reply.body().path("items"))
            translations.addItem(
                new Option(
                    translation.path("id").asText(),
                    translation.path("languageTag").asText()
                        + " · "
                        + translation.path("editionKey").asText()
                        + " · nhóm "
                        + translation.path("teamId").asText()));
          filling = false;
          loadChapters();
        });
  }

  private void loadChapters() {
    Option option = (Option) translations.getSelectedItem();
    chapters.setRowCount(0);
    chapterRows.clear();
    if (option == null) {
      detailMessage.setText("Chưa có bản dịch công khai.");
      return;
    }
    details.run(
        this,
        detailMessage,
        api.get("/translations/" + option.id() + "/chapters?page=" + chapterPage + "&size=20"),
        reply -> {
          for (JsonNode chapter : reply.body().path("items")) {
            chapterRows.add(chapter);
            chapters.addRow(
                new Object[] {
                  chapter.path("displayNumber").asText(),
                  chapter.path("title").asText(),
                  chapter.path("pageCount").asInt(),
                  chapter.path("accessPolicy").asText().equals("FREE") ? "Miễn phí" : "Giới hạn"
                });
          }
          detailMessage.setText(
              chapterRows.isEmpty() ? "Chưa có chương công khai." : "Chọn chương rồi nhấn Đọc.");
        });
  }

  private boolean account() {
    if (api.signedIn()) return true;
    detailMessage.setText("Đăng nhập ở mục Tài khoản để lưu thư viện và theo dõi.");
    return false;
  }

  private void saveFavorite() {
    if (selected == null || !account() || actions.busy()) return;
    actions.run(
        this,
        detailMessage,
        new LibraryApi(api).saveDefault(selected.path("id").asText(), Map.of("isFavorite", true)),
        reply -> detailMessage.setText("Đã lưu yêu thích vào thư viện."));
  }

  private void follow(boolean remove) {
    if (selected == null || !account() || actions.busy()) return;
    actions.run(
        this,
        detailMessage,
        api.request(
            remove ? "DELETE" : "PUT", "/me/follows/" + selected.path("id").asText(), null, null),
        reply -> detailMessage.setText(remove ? "Đã bỏ theo dõi." : "Đã theo dõi truyện."));
  }

  @Override
  public void sessionChanged() {
    searchAssist.close();
    actions.close();
  }

  @Override
  public void close() {
    if (detailDialog != null) detailDialog.dispose();
    searchAssist.close();
    listings.close();
    details.close();
    actions.close();
    metadata.close();
    recommendations.close();
    rankings.close();
    imageGeneration++;
    images.forEach(future -> future.cancel(true));
    images.clear();
    covers.clear();
  }

  private final class ComicRenderer extends SurfacePanel implements ListCellRenderer<JsonNode> {
    private final JLabel image = Theme.plainLabel("Chưa có ảnh"),
        title = Theme.plainLabel(""),
        meta = Theme.plainLabel("");

    ComicRenderer() {
      super(new BorderLayout(0, 8), 12);
      setBorder(new EmptyBorder(10, 10, 10, 10));
      image.setPreferredSize(new Dimension(154, 184));
      image.setHorizontalAlignment(SwingConstants.CENTER);
      image.setFont(Theme.label(10));
      add(image, BorderLayout.NORTH);
      JPanel labels = new JPanel(new GridLayout(0, 1, 0, 3));
      labels.setOpaque(false);
      title.setFont(Theme.brand(13));
      meta.setForeground(Theme.MUTED);
      meta.setFont(Theme.label(11));
      labels.add(title);
      labels.add(meta);
      add(labels);
    }

    public Component getListCellRendererComponent(
        JList<? extends JsonNode> list,
        JsonNode comic,
        int index,
        boolean selected,
        boolean focus) {
      setBackground(selected
          ? new Color(0xFFE5DF)
          : Theme.SURFACE);
      title.setText(comic.path("title").asText());
      meta.setText(comic.path("type").asText() + " · "
          + comic.path("status").asText("Đang cập nhật"));
      ImageIcon cover = covers.get(comic.path("id").asText());
      image.setIcon(cover);
      image.setText(cover == null ? "Chưa có ảnh" : "");
      return this;
    }
  }
}
