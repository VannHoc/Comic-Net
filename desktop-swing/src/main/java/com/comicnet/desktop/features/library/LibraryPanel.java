package com.comicnet.desktop.features.library;

import com.comicnet.desktop.client.api.*;
import com.comicnet.desktop.features.catalog.ComicPicker;
import com.comicnet.desktop.features.reader.ReaderLocation;
import com.comicnet.desktop.ui.*;
import java.awt.*;
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
      actions = new UiTasks();
  private final JLabel message = Theme.plainLabel("Đăng nhập để xem thư viện.");
  private final RemoteTable collections, items, follows, history, bookmarks;
  private final List<RemoteTable> tables;
  private final JTextField collectionName = new JTextField(18), bookmarkLabel = new JTextField(20);
  private final JComboBox<String> status =
      new JComboBox<>(new String[] {"Muốn đọc", "Đang đọc", "Đã hoàn thành", "Đọc lại"});
  private final JCheckBox favorite = new JCheckBox("Yêu thích");
  private String collectionId, collectionEtag, itemComicId, itemEtag, bookmarkId, bookmarkEtag;
  private boolean systemCollection;
  private final MutationKey mutation = new MutationKey();
  private final Map<String,String> chapterNames=new LinkedHashMap<>();
  private final List<java.util.concurrent.CompletableFuture<?>> titleRequests=new ArrayList<>();
  private long titleGeneration;
  private static final List<String> STATUS_CODES=List.of("WANT_TO_READ","READING","COMPLETED","REREAD");

  public LibraryPanel(
      ApiClient api,
      Consumer<String> comic,
      Consumer<ReaderLocation> read,
      Runnable historyCleared) {
    super(new BorderLayout(0, 14));
    this.api = api;
    this.comic = comic;
    this.read = read;
    this.historyCleared = historyCleared;
    setOpaque(false);
    setBorder(new EmptyBorder(26, 30, 20, 30));
    JLabel heading = new JLabel("Thư viện của tôi");
    heading.setFont(Theme.heading(28));
    add(heading, BorderLayout.NORTH);
    collections =
        new RemoteTable(
            api,
            new String[] {"Bộ sưu tập", "Loại"},
            p -> "/me/collections?page=" + p + "&size=20",
            v ->
                new Object[] {
                  v.path("name").asText(),
                  v.path("isSystem").asBoolean() ? "Thư viện mặc định" : "Bộ sưu tập riêng"
                });
    items =
        new RemoteTable(
            api,
            new String[] {"Truyện", "Trạng thái", "Yêu thích"},
            p ->
                collectionId == null
                    ? null
                    : "/me/collections/" + collectionId + "/items?page=" + p + "&size=20",
            v ->
                new Object[] {
                  v.path("comic").path("title").asText(),
                  statusName(v.path("readingStatus").asText("")),
                  v.path("isFavorite").isMissingNode()
                      ? "—"
                      : v.path("isFavorite").asBoolean() ? "Có" : "Không"
                });
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
    tables = List.of(collections, items, follows, history, bookmarks);
    history.onLoaded(this::resolveTitles);bookmarks.onLoaded(this::resolveTitles);
    collections.onSelection(this::selectCollection);
    items.onSelection(this::selectItem);
    bookmarks.onSelection(this::selectBookmark);
    JPanel collectionView = new JPanel(new BorderLayout(0, 10));
    collectionView.setOpaque(false);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, collections, items);
    split.setResizeWeight(.35);
    split.setDividerLocation(310);
    collectionView.add(split);
    JPanel collectionTools = new JPanel(new GridLayout(0, 1));
    collectionTools.setOpaque(false);
    collectionTools.add(
        row(
            collectionName,
            button("Tạo bộ sưu tập", this::createCollection),
            button("Đổi tên", this::renameCollection),
            button("Xóa bộ sưu tập", this::deleteCollection)));
    collectionTools.add(
        row(
            button("Thêm truyện", this::addItem),
            button(
                "Mở chi tiết",
                () -> {
                  if (itemComicId != null) comic.accept(itemComicId);
                }),
            status,
            favorite,
            button("Lưu trạng thái", this::saveItem),
            button("Gỡ khỏi bộ sưu tập", this::removeItem)));
    collectionView.add(collectionTools, BorderLayout.SOUTH);
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Bộ sưu tập", collectionView);
    tabs.addTab(
        "Theo dõi",
        withActions(
            follows,
            button(
                "Mở truyện",
                () -> {
                  if (follows.selected() != null)
                    comic.accept(follows.selected().path("id").asText());
                }),
            button("Bỏ theo dõi", this::unfollow)));
    tabs.addTab(
        "Lịch sử",
        withActions(
            history,
            button("Tiếp tục đọc", () -> resume(history.selected())),
            button("Xóa lịch sử", this::clearHistory)));
    tabs.addTab(
        "Bookmark",
        withActions(
            bookmarks,
            bookmarkLabel,
            button("Mở vị trí", () -> resume(bookmarks.selected())),
            button("Đổi tên", this::renameBookmark),
            button("Xóa bookmark", this::deleteBookmark)));
    add(tabs);
    add(message, BorderLayout.SOUTH);
    tabs.addChangeListener(
        e -> {
          if (api.signedIn())
            switch (tabs.getSelectedIndex()) {
              case 0 -> collections.reload();
              case 1 -> follows.reload();
              case 2 -> history.reload();
              case 3 -> bookmarks.reload();
            }
        });
  }

  private boolean available(JsonNode value) {
    return value != null && value.path("anchorStatus").asText().equals("AVAILABLE");
  }

  private JPanel row(Component... components) {
    JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 7, 4));
    row.setOpaque(false);
    for (Component component : components) row.add(component);
    return row;
  }

  private JPanel withActions(JComponent body, Component... buttons) {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.setOpaque(false);
    panel.add(body);
    panel.add(row(buttons), BorderLayout.SOUTH);
    return panel;
  }

  private JButton button(String text, Runnable action) {
    JButton button = new JButton(text);
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

  private boolean confirm(String text) {
    return JOptionPane.showConfirmDialog(this, text, "Xác nhận", JOptionPane.OK_CANCEL_OPTION)
        == JOptionPane.OK_OPTION;
  }

  private void selectCollection(JsonNode value) {
    collectionDetails.close();
    collectionId = null;
    collectionEtag = null;
    itemComicId = null;
    itemEtag = null;
    items.clear();
    if (value == null) return;
    String id = value.path("id").asText();
    collectionDetails.run(
        this,
        message,
        api.get("/me/collections/" + id),
        reply -> {
          collectionId = id;
          collectionEtag = reply.etag();
          systemCollection = reply.body().path("isSystem").asBoolean();
          collectionName.setText(reply.body().path("name").asText());
          status.setEnabled(systemCollection);
          favorite.setEnabled(systemCollection);
          items.first();
        });
  }

  private void selectItem(JsonNode value) {
    itemDetails.close();
    itemComicId = null;
    itemEtag = null;
    if (value == null || collectionId == null) return;
    String id = value.path("comic").path("id").asText();
    String collection = collectionId;
    itemDetails.run(
        this,
        message,
        api.get("/me/collections/" + collection + "/items/" + id),
        reply -> {
          if (!collection.equals(collectionId)) return;
          itemComicId = id;
          itemEtag = reply.etag();
          status.setSelectedIndex(Math.max(0,STATUS_CODES.indexOf(reply.body().path("readingStatus").asText("WANT_TO_READ"))));
          favorite.setSelected(reply.body().path("isFavorite").asBoolean());
        });
  }

  private void createCollection() {
    String name = collectionName.getText().strip();
    if (name.isEmpty()) return;
    Map<String, String> input = Map.of("name", name);
    actions.run(
        this,
        message,
        api.create("/me/collections", input, mutation.forInput("createCollection", input)),
        reply -> {
          mutation.confirmed();
          collections.first();
          message.setText("Đã tạo bộ sưu tập.");
        });
  }

  private void renameCollection() {
    if (collectionId == null || collectionEtag == null || collectionName.getText().isBlank())
      return;
    actions.run(
        this,
        message,
        api.request(
            "PATCH",
            "/me/collections/" + collectionId,
            Map.of("name", collectionName.getText().strip()),
            collectionEtag),
        reply -> {
          collectionEtag = reply.etag();
          collections.reload();
        });
  }

  private void deleteCollection() {
    if (collectionId == null
        || collectionEtag == null
        || !confirm("Xóa bộ sưu tập đã chọn? Truyện vẫn được giữ.")) return;
    actions.run(
        this,
        message,
        api.request("DELETE", "/me/collections/" + collectionId, null, collectionEtag),
        reply -> collections.first());
  }

  private void addItem() {
    if (collectionId == null) {
      message.setText("Chọn bộ sưu tập trước.");
      return;
    }
    String collection = collectionId;
    ComicPicker.show(
        this,
        api,
        id -> {
          if (!collection.equals(collectionId)) return;
          actions.run(
              this,
              message,
              api.request(
                  "PUT",
                  "/me/collections/" + collection + "/items/" + id,
                  Map.of(),
                  null,
                  Map.of("If-None-Match", "*")),
              reply -> items.first());
        });
  }

  private void saveItem() {
    if (!systemCollection || itemComicId == null || itemEtag == null) return;
    actions.run(
        this,
        message,
        api.request(
            "PUT",
            "/me/collections/" + collectionId + "/items/" + itemComicId,
            Map.of("readingStatus", STATUS_CODES.get(status.getSelectedIndex()), "isFavorite", favorite.isSelected()),
            itemEtag),
        reply -> {
          itemEtag = reply.etag();
          items.reload();
        });
  }

  private void removeItem() {
    if (collectionId == null
        || itemComicId == null
        || !confirm("Gỡ truyện khỏi bộ sưu tập đã chọn?")) return;
    actions.run(
        this,
        message,
        api.request(
            "DELETE", "/me/collections/" + collectionId + "/items/" + itemComicId, null, null),
        reply -> items.reload());
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
      collections.reload();
      message.setText(" ");
    } else message.setText("Đăng nhập để xem thư viện.");
  }

  @Override
  public void sessionChanged() {
    titleGeneration++;titleRequests.forEach(request->request.cancel(true));titleRequests.clear();chapterNames.clear();
    collectionDetails.close();
    itemDetails.close();
    bookmarkDetails.close();
    actions.close();
    tables.forEach(RemoteTable::clear);
    collectionId = null;
    collectionEtag = null;
    itemComicId = null;
    itemEtag = null;
    bookmarkId = null;
    bookmarkEtag = null;
    collectionName.setText("");
    bookmarkLabel.setText("");
    favorite.setSelected(false);
    message.setText("Phiên tài khoản đã đổi. Tải lại để xem dữ liệu của bạn.");
  }

  @Override
  public void close() {
    sessionChanged();
    tables.forEach(RemoteTable::close);
  }
  private String statusName(String code){int index=STATUS_CODES.indexOf(code);return index<0?"—":status.getItemAt(index);}
  private String chapterName(JsonNode value){return !available(value)?"Nội dung không còn khả dụng":chapterNames.getOrDefault(value.path("chapterId").asText(),"Đang tải tên chương…");}
  private void resolveTitles(JsonNode result){List<String> ids=new ArrayList<>();for(JsonNode row:result.path("items")){String id=row.path("chapterId").asText();if(available(row)&&!chapterNames.containsKey(id)&&!ids.contains(id))ids.add(id);}resolveBatch(ids,0,titleGeneration);}
  private void resolveBatch(List<String> ids,int offset,long expected){
    if(offset>=ids.size()||expected!=titleGeneration)return;List<java.util.concurrent.CompletableFuture<?>> batch=new ArrayList<>();
    for(int index=offset;index<Math.min(offset+3,ids.size());index++){String id=ids.get(index);var request=api.get("/chapters/"+id).handle((reply,error)->{SwingUtilities.invokeLater(()->{if(expected!=titleGeneration)return;if(error==null){JsonNode chapter=reply.body();chapterNames.put(id,chapter.path("comicTitle").asText()+" · "+chapter.path("displayNumber").asText()+" — "+chapter.path("title").asText());if(chapterNames.size()>100)chapterNames.remove(chapterNames.keySet().iterator().next());history.refreshCells();bookmarks.refreshCells();}});return null;});batch.add(request);titleRequests.add(request);}
    java.util.concurrent.CompletableFuture.allOf(batch.toArray(java.util.concurrent.CompletableFuture[]::new)).whenComplete((ignored,error)->SwingUtilities.invokeLater(()->{titleRequests.removeAll(batch);resolveBatch(ids,offset+3,expected);}));
  }
}
