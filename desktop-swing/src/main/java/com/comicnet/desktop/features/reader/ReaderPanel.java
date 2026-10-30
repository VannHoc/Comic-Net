package com.comicnet.desktop.features.reader;

import com.comicnet.desktop.client.api.*;
import com.comicnet.desktop.client.http.MediaTransport;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import tools.jackson.databind.JsonNode;

/** Native image reader; the viewport bounds both network work and decoded-image retention. */
public final class ReaderPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final MediaTransport media;
  private final Consumer<ReaderLocation> navigate;
  private final Runnable back;
  private final UiTasks loading = new UiTasks(),
      preferences = new UiTasks(),
      bookmarks = new UiTasks(),
      community = new UiTasks();
  private final JLabel title = Theme.plainLabel("Bộ đọc"),
      message = Theme.plainLabel("Chọn chương từ danh mục hoặc thư viện."),
      saveMessage = Theme.plainLabel(" ");
  private final JComboBox<String> mode = new JComboBox<>(new String[] {"Cuộn dọc", "Từng trang"}),
      direction = new JComboBox<>(new String[] {"Trái → phải", "Phải → trái"}),
      fit = new JComboBox<>(new String[] {"Vừa chiều rộng", "Vừa chiều cao", "Kích thước gốc"});
  private final JSpinner page = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
  private final JSlider progress = new JSlider(1, 1, 1), zoom = new JSlider(50, 200, 100);
  private final JLabel pageCount = Theme.plainLabel("0 / 0");
  private final JButton previousChapter = button("‹ Trước", () -> adjacent(false)),
      nextChapter = button("Sau ›", () -> adjacent(true));
  private final JPanel chapterList = new JPanel();
  private final JScrollPane chapterScroll = new JScrollPane(chapterList);
  private final JPanel chapterDrawer = new JPanel(new BorderLayout());
  private final JPopupMenu settings = new JPopupMenu();
  private String translationId;
  private boolean syncingProgress;
  private final JPanel pages = new JPanel();
  private final JScrollPane viewport = new JScrollPane(pages);
  private final ReaderComfort comfort;
  private final List<PageView> views = new ArrayList<>();
  private final LinkedHashMap<Integer, BufferedImage> cache = new LinkedHashMap<>(8, .75f, true);
  private final Map<Integer, CompletableFuture<?>> imageRequests = new HashMap<>();
  private final javax.swing.Timer viewportTimer, saveTimer, accessTimer;
  private JsonNode manifest;
  private String chapterId, preferenceEtag;
  private int historyGeneration;
  private long revision, generation, imageEpoch;
  private boolean adjusting,
      preview,
      progressReady,
      saving,
      blocked,
      anchorUnavailable,
      visibleWorkspace,
      renewing,
      mediaRenewalBlocked,
      checkingAccess;
  private final MutationKey bookmarkKey = new MutationKey();
  private final MutationKey unlockKey = new MutationKey();
  private ReaderLocation anchor, dirty;
  private PendingSave pending;
  private ReaderLocation initialLocation;
  private CompletableFuture<?> progressRequest;

  private record PendingSave(ReaderLocation location, Map<String, Object> body) {}

  public ReaderPanel(ApiClient api, MediaTransport media, Consumer<ReaderLocation> navigate) {
    this(api, media, navigate, () -> {});
  }

  public ReaderPanel(ApiClient api, MediaTransport media, Consumer<ReaderLocation> navigate, Runnable back) {
    super(new BorderLayout(0, 8));
    this.api = api;
    this.media = media;
    this.navigate = navigate;
    this.back = back;
    comfort =
        new ReaderComfort(
            this,
            viewport,
            () ->
                visibleWorkspace
                    && manifest != null
                    && !views.isEmpty()
                    && mode.getSelectedIndex() == 0
                    && !anchorUnavailable);
    viewportTimer = new javax.swing.Timer(130, e -> viewportChanged());
    viewportTimer.setRepeats(false);
    saveTimer = new javax.swing.Timer(1200, e -> save());
    saveTimer.setRepeats(false);
    accessTimer = new javax.swing.Timer(30_000, e -> checkAccess());
    setOpaque(false);
    setBorder(new EmptyBorder(10, 12, 10, 12));
    title.setFont(Theme.heading(18));
    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setOpaque(false);
    JButton backButton = button("←", back);
    backButton.setToolTipText("Quay lại Trang chủ");
    header.add(title, BorderLayout.NORTH);
    JButton chapters = button("Chương", this::toggleChapters);
    JButton options = button("Cài đặt", () -> settings.show(this, Math.max(0, getWidth() - 370), 54));
    JButton bookmarkButton = button("Lưu dấu", this::bookmark);
    JButton full = button("F11", comfort::toggleFullscreen);
    for (JButton control : new JButton[] {previousChapter, nextChapter, chapters, options, bookmarkButton, full})
      control.setToolTipText(control.getText());
    header.add(scrollableControls(backButton, previousChapter, nextChapter, chapters, options, bookmarkButton, full), BorderLayout.CENTER);
    add(header, BorderLayout.NORTH);
    pages.setLayout(new BoxLayout(pages, BoxLayout.Y_AXIS));
    pages.setBackground(Theme.SURFACE);
    viewport.setBorder(null);
    viewport.getVerticalScrollBar().setUnitIncrement(28);
    JPanel readingArea = new JPanel(new BorderLayout(8, 0));
    readingArea.add(viewport, BorderLayout.CENTER);
    chapterList.setLayout(new BoxLayout(chapterList, BoxLayout.Y_AXIS));
    chapterList.setBackground(Theme.SURFACE);
    chapterScroll.setBorder(null);
    chapterScroll.getVerticalScrollBar().setUnitIncrement(28);
    chapterDrawer.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
    chapterDrawer.setPreferredSize(new Dimension(240, 100));
    chapterDrawer.add(row(Theme.plainLabel("Danh sách chương"), button("Đóng", this::toggleChapters)), BorderLayout.NORTH);
    chapterDrawer.add(chapterScroll);
    chapterDrawer.setVisible(false);
    readingArea.add(chapterDrawer, BorderLayout.EAST);
    add(readingArea, BorderLayout.CENTER);
    JPanel footer = new JPanel(new BorderLayout());
    footer.setOpaque(false);
    progress.setPreferredSize(new Dimension(220, 30));
    progress.addChangeListener(e -> { if (!syncingProgress && !progress.getValueIsAdjusting() && manifest != null) userJump(progress.getValue() - 1); });
    zoom.setPreferredSize(new Dimension(110, 30));
    zoom.setToolTipText("Thu phóng ảnh 50–200%");
    zoom.addChangeListener(e -> { if (manifest != null) { resizePages(); pages.revalidate(); pages.repaint(); viewportTimer.restart(); } });
    footer.add(scrollableControls(pageCount, progress, Theme.plainLabel("Zoom"),
        button("-", () -> zoom.setValue(Math.max(50, zoom.getValue()-10))), zoom,
        button("+", () -> zoom.setValue(Math.min(200, zoom.getValue()+10))), comfort.autoControl()), BorderLayout.CENTER);
    footer.add(row(message, saveMessage), BorderLayout.SOUTH);
    add(footer, BorderLayout.SOUTH);
    buildSettings();
    viewport.getViewport().addChangeListener(e -> viewportTimer.restart());
    viewport.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent event) {
            viewportTimer.restart();
          }
        });
    mode.addActionListener(e -> settingsChanged());
    direction.addActionListener(
        e -> {
          if (!adjusting)
            message.setText(
                direction.getSelectedIndex() == 1
                    ? "Phím trái là trang tiếp theo."
                    : "Phím phải là trang tiếp theo.");
        });
    fit.addActionListener(e -> settingsChanged());
    page.addChangeListener(
        e -> {
          if (!adjusting) userJump((Integer) page.getValue() - 1);
        });
    bindKey(KeyEvent.VK_RIGHT, () -> move(1));
    bindKey(KeyEvent.VK_LEFT, () -> move(-1));
    getAccessibleContext().setAccessibleName("Bộ đọc truyện");
  }

  private JPanel row(Component... components) {
    JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 7, 4));
    row.setOpaque(false);
    for (Component component : components) row.add(component);
    return row;
  }

  private JScrollPane scrollableControls(Component... components) {
    JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 4));
    line.setOpaque(false);
    for (Component component : components) line.add(component);
    JScrollPane scroll = new JScrollPane(line, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scroll.setBorder(null);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.getHorizontalScrollBar().setUnitIncrement(24);
    scroll.setPreferredSize(new Dimension(200, 48));
    return scroll;
  }

  private JButton button(String text, Runnable action) {
    JButton button = new JButton(text);
    button.addActionListener(e -> action.run());
    return button;
  }

  private void buildSettings() {
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setBorder(new EmptyBorder(10, 12, 10, 12));
    content.setPreferredSize(new Dimension(320, 470));
    JSlider popupZoom = new JSlider(50, 200, zoom.getValue());
    popupZoom.setPreferredSize(new Dimension(150, 28));
    popupZoom.addChangeListener(e -> zoom.setValue(popupZoom.getValue()));
    zoom.addChangeListener(e -> { if (popupZoom.getValue() != zoom.getValue()) popupZoom.setValue(zoom.getValue()); });
    JComboBox<String> background = new JComboBox<>(new String[] {"Sáng", "Xám", "Đen"});
    background.addActionListener(e -> {
      pages.setBackground(switch (background.getSelectedIndex()) {
        case 1 -> new Color(0xE5E7EB);
        case 2 -> new Color(0x20242B);
        default -> Theme.SURFACE;
      });
      pages.repaint();
    });
    for (JComponent line : new JComponent[] {
        row(Theme.plainLabel("Kiểu đọc"), mode), row(Theme.plainLabel("Hướng đọc"), direction),
        row(Theme.plainLabel("Hiển thị ảnh"), fit),
        row(Theme.plainLabel("Tốc độ tự cuộn"), comfort.speedControl()),
        row(Theme.plainLabel("Độ sáng"), comfort.brightnessControl()),
        row(Theme.plainLabel("Zoom"), popupZoom), row(Theme.plainLabel("Nền đọc"), background),
        row(button("Bình luận trang", this::pageComments), button("Tải lại ảnh", this::renew)),
        row(button("Lưu vị trí", this::saveExplicit), button("Lưu thiết lập", this::savePreferences))
    }) content.add(line);
    settings.add(content);
  }

  private void toggleChapters() {
    chapterDrawer.setVisible(!chapterDrawer.isVisible());
    if (chapterDrawer.isVisible()) loadChapterList(0);
    revalidate();
  }

  private void loadChapterList(int number) {
    if (translationId == null || translationId.isBlank()) {
      chapterList.removeAll();
      chapterList.add(Theme.plainLabel("Không có danh sách chương cho bản xem trước."));
      chapterList.revalidate();
      return;
    }
    api.get("/translations/" + translationId + "/chapters?page=" + number + "&size=50")
        .whenComplete((reply, error) -> SwingUtilities.invokeLater(() -> {
          if (error != null) {
            chapterList.add(button("Không tải được · Thử lại", () -> loadChapterList(number)));
          } else {
            if (number == 0) chapterList.removeAll();
            JsonNode items = reply.body().path("items");
            for (JsonNode item : items) {
              String id = item.path("id").asText();
              JButton chapter = button(item.path("displayNumber").asText() + " · " + item.path("title").asText(),
                  () -> { chapterDrawer.setVisible(false); navigate.accept(ReaderLocation.chapter(id)); });
              chapter.setAlignmentX(LEFT_ALIGNMENT);
              chapter.setToolTipText(chapter.getText());
              if (id.equals(chapterId)) chapter.setBackground(new Color(0xFFE5DF));
              chapterList.add(chapter);
            }
            if (items.size() == 50) chapterList.add(button("Xem thêm chương", () -> loadChapterList(number + 1)));
            if (number == 0 && items.isEmpty()) chapterList.add(Theme.plainLabel("Chưa có chương công khai."));
          }
          chapterList.revalidate(); chapterList.repaint();
        }));
  }

  private void bindKey(int key, Runnable action) {
    String name = "reader-" + key;
    viewport
        .getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(key, 0), name);
    viewport
        .getActionMap()
        .put(
            name,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent e) {
                action.run();
              }
            });
  }

  private void pageComments() {
    if (preview || anchor == null || manifest == null || community.busy()) {
      message.setText("Mở một trang trong chương đã xuất bản để bình luận.");
      return;
    }
    ReaderLocation location = anchor;
    long expected = generation;
    community.run(
        this,
        message,
        api.get("/chapters/" + location.chapterId()),
        reply -> {
          if (generation != expected || !visibleWorkspace) return;
          new com.comicnet.desktop.features.community.CommentDialog(
                  this,
                  api,
                  reply.body().path("comicId").asText(),
                  location.chapterId(),
                  location.pageId(),
                  java.math.BigDecimal.valueOf(location.relativeOffset()))
              .setVisible(true);
        });
  }

  public void open(ReaderLocation location) {
    flush();
    reset();
    preview = false;
    chapterId = location.chapterId();
    initialLocation = location;
    visibleWorkspace = true;
    long expected = ++generation;
    loading.run(
        this,
        message,
        api.get("/chapters/" + chapterId)
            .thenCompose(
                context -> {
                  String comic = context.body().path("comicId").asText();
                  translationId = context.body().path("translationId").asText();
                  return api.get("/chapters/" + chapterId + "/manifest?displayFormat=JPEG")
                      .thenApply(
                          images -> {
                            recordView(comic, chapterId);
                            return new ApiClient.Reply(
                                images.body(),
                                context.body().path("comicTitle").asText()
                                    + " · "
                                    + context.body().path("displayNumber").asText()
                                    + " — "
                                    + context.body().path("title").asText());
                          });
                }),
        reply -> {
          if (expected != generation) return;
          title.setText(reply.etag());
          acceptManifest(reply.body());
          loadPersonalState(expected);
          accessTimer.start();
        },
        error -> {
          if (expected != generation) return;
          Throwable cause = ApiClient.unwrap(error);
          if (cause instanceof ApiClient.ApiFailure failure
              && "READING_RESTRICTED".equals(failure.code())) {
            checkUnlock(chapterId, location);
          } else {
            message.setText(cause instanceof ApiClient.ApiFailure failure
                ? failure.getMessage() + " · " + failure.requestId()
                : "Không thể tải chương. Hãy kiểm tra backend rồi thử lại.");
          }
        });
  }

  private void checkUnlock(String chapter, ReaderLocation location) {
    loading.run(this, message, api.get("/chapters/" + chapter + "/access"), reply -> {
      if (reply.body().path("requiresLogin").asBoolean()) {
        message.setText("Đăng nhập trong mục Tài khoản để mở khóa chương.");
      } else if ("PURCHASE_REQUIRED".equals(reply.body().path("reason").asText())) {
        loadUnlockPrices(chapter, location);
      } else {
        message.setText("Chương hiện chưa thể đọc. Hãy tải lại sau.");
      }
    });
  }

  private void loadUnlockPrices(String chapter, ReaderLocation location) {
    loading.run(this, message, api.get("/chapters/" + chapter + "/prices"), reply -> {
      JsonNode prices = reply.body();
      if (!prices.isArray() || prices.isEmpty()) {
        message.setText("Chương chưa có giá mở khóa. Hãy liên hệ người xuất bản.");
        return;
      }
      String[] labels = new String[prices.size()];
      for (int i = 0; i < prices.size(); i++) {
        JsonNode price = prices.get(i);
        labels[i] = price.path("amount").asText() + " "
            + ("PAID".equals(price.path("currencyCode").asText()) ? "Paid Coin" : "Reward Coin");
      }
      EditDialog dialog = new EditDialog(this, "Mở khóa chương");
      JComboBox<String> offers = new JComboBox<>(labels);
      dialog.form.field("Chọn giá", offers);
      dialog.submit(() -> {
        int choice = offers.getSelectedIndex();
        if (choice < 0) throw new IllegalArgumentException("Chọn một mức giá.");
        String priceId = prices.get(choice).path("id").asText();
        Map<String, Object> body = Map.of("priceId", Long.parseLong(priceId));
        return api.create("/chapters/" + chapter + "/unlock", body,
            unlockKey.forInput("unlock:" + chapter, body));
      }, purchased -> {
        message.setText("Mở khóa thành công.");
        open(location);
      });
      dialog.setSize(460, 260);
      dialog.setVisible(true);
    });
  }

  /** Fire-and-forget valid read; the backend de-duplicates per reader, chapter and day. */
  private void recordView(String comic, String chapter) {
    if (preview || comic == null || comic.isBlank()) return;
    api.request("POST", "/comics/" + comic + "/views", Map.of("chapterId", chapter), null)
        .exceptionally(ignored -> null);
  }

  private void recordCompletion() {
    if (preview || chapterId == null) return;
    api.request("POST", "/chapters/" + chapterId + "/completion", null, null)
        .exceptionally(ignored -> null);
    try {
      long id = Long.parseLong(chapterId);
      api.request("POST", "/gamification/reading-exp", Map.of("chapterId", id), null)
          .exceptionally(ignored -> null);
    } catch (NumberFormatException ignored) {}
  }

  /** A scoped draft manifest has already passed the backend's preview authorization. */
  public void openPreview(JsonNode draft) {
    flush();
    reset();
    preview = true;
    visibleWorkspace = true;
    chapterId = draft.path("chapterId").asText();
    translationId = null;
    initialLocation = null;
    generation++;
    title.setText("Xem trước bản biên tập · Không lưu tiến độ");
    acceptManifest(draft);
  }

  private void reset() {
    comfort.pause();
    loading.close();
    preferences.close();
    bookmarks.close();
    community.close();
    cancelImages();
    saveTimer.stop();
    viewportTimer.stop();
    accessTimer.stop();
    checkingAccess = false;
    manifest = null;
    translationId = null;
    pages.removeAll();
    views.clear();
    anchor = null;
    dirty = null;
    pending = null;
    blocked = false;
    anchorUnavailable = false;
    saving = false;
    progressReady = false;
    revision = 0;
    historyGeneration = 0;
    preferenceEtag = null;
    saveMessage.setText(" ");
  }

  private void acceptManifest(JsonNode value) {
    renewing = false;
    mediaRenewalBlocked = false;
    manifest = value;
    int count = value.path("pages").size();
    if (count == 0) {
      message.setText("Chương chưa có trang để đọc.");
      pageCount.setText("0 / 0");
      pages.removeAll();
      JLabel empty = Theme.plainLabel("Chương này chưa có ảnh. Hãy quay lại hoặc tải lại chương.");
      empty.setAlignmentX(CENTER_ALIGNMENT);
      empty.setBorder(new EmptyBorder(56, 16, 56, 16));
      pages.add(empty);
      pages.revalidate(); pages.repaint();
      return;
    }
    adjusting = true;
    page.setModel(new SpinnerNumberModel(1, 1, count, 1));
    syncingProgress = true;
    progress.setMaximum(count);
    progress.setValue(1);
    pageCount.setText("1 / " + count);
    syncingProgress = false;
    adjusting = false;
    renderPages();
    if (initialLocation != null && initialLocation.pageId() != null) restore(initialLocation);
    else jump(0, 0);
    message.setText(preview ? "Bản xem trước được cấp quyền riêng." : " ");
  }

  private void loadPersonalState(long expected) {
    if (!api.signedIn()) {
      saveMessage.setText("Khách · Vị trí chỉ giữ trong phiên đọc.");
      return;
    }
    preferences.run(
        this,
        saveMessage,
        api.get("/me/reading-preferences"),
        reply -> {
          if (expected != generation) return;
          preferenceEtag = reply.etag();
          adjusting = true;
          mode.setSelectedIndex(reply.body().path("mode").asText().equals("PAGED") ? 1 : 0);
          direction.setSelectedIndex(reply.body().path("direction").asText().equals("RTL") ? 1 : 0);
          String savedFit = reply.body().path("fit").asText();
          fit.setSelectedIndex(savedFit.equals("ORIGINAL") ? 2 : savedFit.equals("HEIGHT") ? 1 : 0);
          adjusting = false;
          renderPages();
          if (anchor != null) restore(anchor);
        });
    progressRequest =
        api.get("/me/history?size=1")
            .thenCompose(
                history ->
                    api.get("/me/progress/" + chapterId)
                        .handle(
                            (current, error) -> {
                              if (error == null) return current;
                              Throwable cause = ApiClient.unwrap(error);
                              if (cause instanceof ApiClient.ApiFailure failure
                                  && failure.status() == 404) {
                                var empty =
                                    tools.jackson.databind.json.JsonMapper.builder()
                                        .build()
                                        .createObjectNode();
                                empty.put("revision", 0);
                                empty.put(
                                    "historyGeneration",
                                    history.body().path("historyGeneration").asInt());
                                return new ApiClient.Reply(empty, null);
                              }
                              throw new java.util.concurrent.CompletionException(cause);
                            }));
    progressRequest.whenComplete(
        (reply, error) ->
            SwingUtilities.invokeLater(
                () -> {
                  if (expected != generation || preview || !api.signedIn()) return;
                  if (error != null) {
                    saveMessage.setText("Chưa tải được tiến độ; tải lại chương trước khi lưu.");
                    return;
                  }
                  JsonNode value = ((ApiClient.Reply) reply).body();
                  revision = value.path("revision").asLong();
                  historyGeneration = value.path("historyGeneration").asInt();
                  progressReady = true;
                  if (initialLocation != null && initialLocation.mode() == ReaderLocation.ReadMode.START_FROM_BEGINNING) {
                    jump(0, 0);
                    if (manifest != null && manifest.path("pages").size() > 0) {
                      JsonNode firstPage = manifest.path("pages").get(0);
                      anchor = new ReaderLocation(
                          chapterId,
                          manifest.path("versionId").asText(),
                          firstPage.path("pageId").asText(),
                          0.0,
                          ReaderLocation.ReadMode.START_FROM_BEGINNING);
                    }
                    saveMessage.setText("Đang đọc từ đầu · Tiến độ cũ vẫn được bảo lưu.");
                    return;
                  }
                  if (initialLocation != null && initialLocation.pageId() != null)
                    restore(initialLocation);
                  else if (!value.path("pageId").isNull()
                      && !value.path("pageId").asText().isBlank()) {
                    if (!value.path("anchorStatus").asText().equals("AVAILABLE")) {
                      blocked = true;
                      anchorUnavailable = true;
                      saveMessage.setText(
                          "Vị trí cũ không còn khả dụng. Chọn trang hoặc xác nhận Lưu lại vị trí.");
                    } else restore(location(value));
                  } else if (anchor != null) {
                    dirty = anchor;
                    saveTimer.restart();
                  }
                }));
  }

  private ReaderLocation location(JsonNode value) {
    return new ReaderLocation(
        value.path("chapterId").asText(),
        value.path("versionId").asText(),
        value.path("pageId").asText(),
        value.path("relativeOffset").asDouble());
  }

  private void renderPages() {
    if (manifest == null) return;
    cancelImages();
    views.clear();
    pages.removeAll();
    int count = manifest.path("pages").size();
    int selected = Math.max(0, Math.min(count - 1, (Integer) page.getValue() - 1));
    for (int i = 0; i < count; i++) {
      PageView view = new PageView(i);
      views.add(view);
      if (mode.getSelectedIndex() == 0 || selected == i) pages.add(view);
    }
    resizePages();
    pages.revalidate();
    pages.repaint();
    viewportTimer.restart();
  }

  private void resizePages() {
    int availableWidth = Math.max(200, viewport.getViewport().getWidth() - 24),
        availableHeight = Math.max(200, viewport.getViewport().getHeight() - 16);
    for (PageView view : views) {
      JsonNode info = manifest.path("pages").get(view.index);
      int w = info.path("width").asInt(1), h = info.path("height").asInt(1);
      double scale = fit.getSelectedIndex() == 2 ? 1
          : fit.getSelectedIndex() == 1
              ? Math.min((double) availableHeight / h, (double) availableWidth / w)
              : (double) availableWidth / w;
      scale *= zoom.getValue() / 100.0;
      view.setPreferredSize(
          new Dimension(Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale))));
      view.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(1, (int) (h * scale))));
    }
  }

  private void viewportChanged() {
    if (manifest == null || views.isEmpty() || !visibleWorkspace) return;
    resizePages();
    pages.revalidate();
    Rectangle area = viewport.getViewport().getViewRect();
    int current = (Integer) page.getValue() - 1;
    if (mode.getSelectedIndex() == 0) {
      for (PageView view : views) {
        if (view.getBounds().intersects(area)) {
          current = view.index;
          break;
        }
      }
    }
    adjusting = true;
    page.setValue(current + 1);
    adjusting = false;
    syncingProgress = true;
    progress.setValue(current + 1);
    pageCount.setText((current + 1) + " / " + views.size());
    syncingProgress = false;
    PageView view = views.get(current);
    double offset =
        mode.getSelectedIndex() == 0
            ? Math.max(
                0, Math.min(1, (double) (area.y - view.getY()) / Math.max(1, view.getHeight())))
            : 0;
    JsonNode info = manifest.path("pages").get(current);
    ReaderLocation next =
        new ReaderLocation(
            chapterId, manifest.path("versionId").asText(), info.path("pageId").asText(), offset);
    if (!same(anchor, next)) {
      anchor = next;
      if (progressReady && !preview && !blocked) {
        dirty = next;
        saveTimer.restart();
      }
    }
    Set<Integer> wanted = new LinkedHashSet<>();
    wanted.add(current);
    if (current + 1 < views.size()) wanted.add(current + 1);
    if (current > 0) wanted.add(current - 1);
    imageRequests
        .entrySet()
        .removeIf(
            entry -> {
              if (!wanted.contains(entry.getKey())) {
                entry.getValue().cancel(true);
                return true;
              }
              return false;
            });
    cache
        .entrySet()
        .removeIf(
            entry -> {
              if (Math.abs(entry.getKey() - ((Integer) page.getValue() - 1)) > 2) {
                entry.getValue().flush();
                return true;
              }
              return false;
            });
    for (int index : wanted) loadImage(index);
    pages.repaint();
  }

  private boolean same(ReaderLocation a, ReaderLocation b) {
    return a != null
        && b != null
        && Objects.equals(a.pageId(), b.pageId())
        && Math.abs(a.relativeOffset() - b.relativeOffset()) < .002;
  }

  private void loadImage(int index) {
    if (cache.containsKey(index) || imageRequests.containsKey(index) || views.get(index).failed)
      return;
    long expected = generation, epoch = imageEpoch;
    if (renewing) return;
    if (!preview && !mediaRenewalBlocked && expiresSoon(manifest.path("pages").get(index))) {
      renewUrls();
      return;
    }
    try {
      var request =
          media.image(
              URI.create(manifest.path("pages").get(index).path("url").asText()),
              Math.max(200, viewport.getWidth()));
      imageRequests.put(index, request);
      request.whenComplete(
          (image, error) ->
              SwingUtilities.invokeLater(
                  () -> {
                    if (expected != generation || epoch != imageEpoch || !visibleWorkspace) {
                      if (image != null) image.flush();
                      return;
                    }
                    imageRequests.remove(index);
                    if (error == null) cache.put(index, image);
                    else views.get(index).failed = true;
                    views.get(index).retry.setVisible(error != null);
                    views.get(index).repaint();
                  }));
    } catch (IllegalArgumentException error) {
      views.get(index).failed = true;
      views.get(index).retry.setVisible(true);
    }
  }

  private boolean expiresSoon(JsonNode info) {
    try {
      return java.time.Instant.parse(info.path("expiresAt").asText())
          .isBefore(java.time.Instant.now().plusSeconds(5));
    } catch (java.time.DateTimeException error) {
      return false;
    }
  }

  private void renewUrls() {
    renewing = true;
    String currentVersion = manifest.path("versionId").asText();
    loading.run(
        this,
        message,
        api.get("/chapters/" + chapterId + "/manifest?displayFormat=JPEG"),
        reply -> {
          renewing = false;
          if (!reply.body().path("versionId").asText().equals(currentVersion)) {
            mediaRenewalBlocked = true;
            message.setText(
                "Đã có bản xuất bản mới. Nhấn Tải lại ảnh để mở lại chương tại trang đã lưu.");
            return;
          }
          manifest = reply.body();
          cancelImages();
          views.forEach(view -> { view.failed = false; view.retry.setVisible(false); });
          viewportTimer.restart();
        },
        error -> {
          renewing = false;
          mediaRenewalBlocked = true;
          message.setText("Chưa cấp lại được ảnh. Nhấn Tải lại ảnh để thử lại.");
        });
  }

  /** Recheck server access while a chapter stays open, including revocation on another device. */
  void checkAccess() {
    if (checkingAccess || preview || !visibleWorkspace || manifest == null || chapterId == null
        || !api.signedIn()) return;
    checkingAccess = true;
    long expected = generation;
    String chapter = chapterId;
    api.get("/chapters/" + chapter + "/access").whenComplete((reply, error) ->
        SwingUtilities.invokeLater(() -> {
          if (expected != generation || !chapter.equals(chapterId)) return;
          checkingAccess = false;
          if (error != null) return; // Transient network errors do not prove access was revoked.
          if (reply.body().path("allowed").asBoolean()) return;
          reset();
          message.setText("Quyền đọc chương đã thay đổi. Nhấn Tải lại ảnh để kiểm tra lại.");
          pages.revalidate();
          pages.repaint();
        }));
  }

  private void cancelImages() {
    imageEpoch++;
    imageRequests.values().forEach(request -> request.cancel(true));
    imageRequests.clear();
    cache.values().forEach(BufferedImage::flush);
    cache.clear();
  }

  private void settingsChanged() {
    if (adjusting) return;
    comfort.pause();
    ReaderLocation before = anchor;
    renderPages();
    if (before != null) restore(before);
  }

  private void jump(int index, double offset) {
    if (manifest == null || index < 0 || index >= manifest.path("pages").size()) return;
    adjusting = true;
    page.setValue(index + 1);
    adjusting = false;
    syncingProgress = true;
    progress.setValue(index + 1);
    pageCount.setText((index + 1) + " / " + views.size());
    syncingProgress = false;
    if (mode.getSelectedIndex() == 1) renderPages();
    SwingUtilities.invokeLater(
        () -> {
          if (index >= views.size()) return;
          PageView view = views.get(index);
          viewport
              .getViewport()
              .setViewPosition(
                  new Point(
                      0,
                      mode.getSelectedIndex() == 0
                          ? view.getY() + (int) (view.getHeight() * offset)
                          : 0));
          viewportTimer.restart();
        });
  }

  private void restore(ReaderLocation location) {
    if (manifest == null) return;
    for (int i = 0; i < manifest.path("pages").size(); i++)
      if (manifest.path("pages").get(i).path("pageId").asText().equals(location.pageId())) {
        jump(i, location.relativeOffset());
        return;
      }
    blocked = true;
    anchorUnavailable = true;
    saveMessage.setText("Trang được lưu không còn trong bản hiện tại. Chọn trang mới để đọc.");
  }

  private void userJump(int index) {
    if (anchorUnavailable) {
      anchorUnavailable = false;
      blocked = false;
      anchor = null;
    }
    jump(index, 0);
  }

  private void move(int delta) {
    if (direction.getSelectedIndex() == 1) delta = -delta;
    userJump((Integer) page.getValue() - 1 + delta);
  }

  private void adjacent(boolean next) {
    if (manifest == null || preview) return;
    String id = manifest.path(next ? "nextChapterId" : "previousChapterId").asText("");
    if (!id.isBlank()) navigate.accept(ReaderLocation.chapter(id));
    else message.setText("Không có chương liền kề.");
  }

  private void renew() {
    if (preview) {
      message.setText("Đóng và mở lại Xem trước để cấp lại URL.");
      return;
    }
    if (chapterId != null) open(anchor == null ? ReaderLocation.chapter(chapterId) : anchor);
  }

  private void savePreferences() {
    if (!api.signedIn()) {
      message.setText("Thiết lập của khách đã áp dụng trong phiên này.");
      return;
    }
    if (preferenceEtag == null) {
      message.setText("Tải lại chương để lấy thiết lập hiện tại.");
      return;
    }
    preferences.run(
        this,
        message,
        api.request(
            "PUT",
            "/me/reading-preferences",
            Map.of(
                "mode",
                mode.getSelectedIndex() == 0 ? "SCROLL" : "PAGED",
                "direction",
                direction.getSelectedIndex() == 0 ? "LTR" : "RTL",
                "fit",
                fit.getSelectedIndex() == 0 ? "WIDTH" : fit.getSelectedIndex() == 1 ? "HEIGHT" : "ORIGINAL"),
            preferenceEtag),
        reply -> {
          preferenceEtag = reply.etag();
          message.setText("Đã lưu thiết lập đọc.");
        });
  }

  private void save() {
    if (preview || !api.signedIn() || !progressReady || saving || blocked || chapterId == null)
      return;
    if (pending == null) {
      if (dirty == null) return;
      ReaderLocation value = dirty;
      dirty = null;
      boolean completed =
          (Integer) page.getValue() == views.size()
              && (mode.getSelectedIndex() == 1
                  || viewport.getViewport().getViewRect().getMaxY()
                      >= views.getLast().getY() + views.getLast().getHeight() - 24);
      pending =
          new PendingSave(
              value,
              Map.of(
                  "versionId",
                  value.versionId(),
                  "pageId",
                  value.pageId(),
                  "relativeOffset",
                  BigDecimal.valueOf(value.relativeOffset())
                      .setScale(5, java.math.RoundingMode.HALF_UP),
                  "completed",
                  completed,
                  "expectedRevision",
                  revision,
                  "mutationId",
                  UUID.randomUUID().toString(),
                  "historyGeneration",
                  historyGeneration));
    }
    PendingSave operation = pending;
    long expected = generation;
    saving = true;
    saveMessage.setText("Đang lưu vị trí…");
    api.request("PUT", "/me/progress/" + chapterId, operation.body(), null)
        .whenComplete(
            (reply, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (expected != generation) return;
                      saving = false;
                      if (error == null) {
                        revision = reply.body().path("revision").asLong();
                        pending = null;
                        if (Boolean.TRUE.equals(operation.body().get("completed")))
                          recordCompletion();
                        saveMessage.setText("Đã lưu vị trí đọc.");
                        if (dirty != null) saveTimer.restart();
                        return;
                      }
                      Throwable cause = ApiClient.unwrap(error);
                      if (cause instanceof ApiClient.ApiFailure failure
                          && failure.status() == 409) {
                        blocked = true;
                        pending = null;
                        conflict(failure.current(), operation.location());
                      } else
                        saveMessage.setText(
                            "Chưa lưu được vị trí. Nhấn Lưu lại vị trí để thử cùng mã thao tác.");
                    }));
  }

  private void saveExplicit() {
    if (anchorUnavailable) {
      if (JOptionPane.showConfirmDialog(
              this,
              "Thay vị trí không còn khả dụng bằng trang đang xem?",
              "Chọn vị trí mới",
              JOptionPane.OK_CANCEL_OPTION)
          != JOptionPane.OK_OPTION) return;
      anchorUnavailable = false;
      blocked = false;
      dirty = anchor;
    }
    save();
  }

  private void conflict(JsonNode current, ReaderLocation attempted) {
    if (!visibleWorkspace) {
      saveMessage.setText("Vị trí đã đổi trên phiên khác; tải lại trước khi lưu.");
      return;
    }
    int choice =
        JOptionPane.showOptionDialog(
            this,
            "Lịch sử hoặc vị trí đã thay đổi trên phiên khác. Chọn vị trí server, hoặc chủ động ghi"
                + " lại vị trí đang đọc.",
            "Xung đột vị trí đọc",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            new String[] {"Tải từ server", "Ghi vị trí đang đọc", "Để sau"},
            "Tải từ server");
    if (choice < 0 || choice == 2) return;
    if (current == null || current.isMissingNode() || current.isNull()) {
      open(ReaderLocation.chapter(chapterId));
      return;
    }
    revision = current.path("revision").asLong();
    historyGeneration = current.path("historyGeneration").asInt();
    blocked = false;
    dirty = null;
    if (choice == 0) {
      if (!current.path("anchorStatus").asText().equals("AVAILABLE")) {
        blocked = true;
        saveMessage.setText("Vị trí server không còn khả dụng; mở lại chương để chọn vị trí mới.");
      } else restore(location(current));
    } else {
      dirty = anchor == null ? attempted : anchor;
      save();
    }
  }

  private void bookmark() {
    if (preview || anchor == null || bookmarks.busy()) return;
    if (!api.signedIn()) {
      message.setText("Đăng nhập để đặt bookmark.");
      return;
    }
    String label =
        JOptionPane.showInputDialog(
            this, "Tên bookmark", "Đặt bookmark", JOptionPane.PLAIN_MESSAGE);
    if (label == null || label.isBlank()) return;
    ReaderLocation value = anchor;
    var body =
        Map.of(
            "chapterId",
            value.chapterId(),
            "versionId",
            value.versionId(),
            "pageId",
            value.pageId(),
            "relativeOffset",
            BigDecimal.valueOf(value.relativeOffset()),
            "label",
            label.strip());
    bookmarks.run(
        this,
        message,
        api.create("/me/bookmarks", body, bookmarkKey.forInput("bookmark", body)),
        reply -> {
          bookmarkKey.confirmed();
          message.setText("Đã lưu bookmark.");
        });
  }

  private void flush() {
    saveTimer.stop();
    save();
  }

  @Override
  public void onShown() {
    visibleWorkspace = true;
    viewportTimer.restart();
  }

  @Override
  public void onHidden() {
    comfort.close();
    flush();
    visibleWorkspace = false;
    cancelImages();
  }

  @Override
  public void sessionChanged() {
    comfort.close();
    generation++;
    reset();
    chapterId = null;
    title.setText("Bộ đọc");
    message.setText("Phiên tài khoản đã đổi. Mở lại chương để tiếp tục.");
    pages.revalidate();
    pages.repaint();
  }

  @Override
  public void close() {
    comfort.close();
    flush();
    generation++;
    reset();
    if (progressRequest != null) progressRequest.cancel(true);
  }

  private final class PageView extends JPanel {
    private final int index;
    private final JButton retry;
    private boolean failed;

    PageView(int index) {
      this.index = index;
      retry = button("Tải lại trang", this::retryImage);
      setOpaque(false);
      setLayout(new GridBagLayout());
      add(retry);
      retry.setVisible(false);
      setAlignmentX(CENTER_ALIGNMENT);
      getAccessibleContext().setAccessibleName("Trang " + (index + 1));
      addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
              if (failed) {
                failed = false;
                loadImage(index);
                repaint();
              }
            }
          });
    }

    private void retryImage() {
      failed = false;
      retry.setVisible(false);
      loadImage(index);
      repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      BufferedImage image = cache.get(index);
      if (image == null) {
        graphics.setColor(Theme.MUTED);
        graphics.setFont(Theme.label(14));
        graphics.drawString(
            failed
                ? "Không tải được trang " + (index + 1)
                : "Đang tải trang " + (index + 1) + "…",
            24,
            48);
        return;
      }
      JsonNode info = manifest.path("pages").get(index);
      double scale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
      int width = Math.max(1, (int) (image.getWidth() * scale));
      int height = Math.max(1, (int) (image.getHeight() * scale));
      int x = (getWidth() - width) / 2, y = (getHeight() - height) / 2;
      graphics.drawImage(image, x, y, width, height, null);
      comfort.paintShade(graphics, x, y, width, height);
    }
  }
}
