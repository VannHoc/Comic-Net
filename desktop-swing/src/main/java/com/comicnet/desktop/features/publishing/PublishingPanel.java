package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.client.api.*;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

public final class PublishingPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final Consumer<JsonNode> preview;
  private final UiTasks details = new UiTasks(),
      actions = new UiTasks(),
      uploads = new UiTasks(),
      polling = new UiTasks();
  private final JLabel message = Theme.plainLabel("Đăng nhập bằng tài khoản có quyền xuất bản."),
      versionLabel = Theme.plainLabel("Chọn version để biên tập."),
      jobLabel = Theme.plainLabel("Chưa có upload.");
  private final JTextField jobId = new JTextField(10);
  private final JProgressBar uploadProgress = new JProgressBar();
  private final RemoteTable comics, translations, chapters, volumes, versions, queue;
  private final List<RemoteTable> tables;
  private final List<JsonNode> pageRows = new ArrayList<>();
  private final DefaultTableModel pageModel =
      new DefaultTableModel(new Object[] {"Thứ tự", "Page ID", "Kích thước", "Asset"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final JTable pageTable = new JTable(pageModel);
  private String comicId, translationId, chapterId;
  private ApiClient.Reply version, versionChapter;
  private boolean administrator, visibleWorkspace;
  private int pollDelay = 1800;
  private final javax.swing.Timer pollTimer;
  private final MutationKey mutation = new MutationKey(), uploadKey = new MutationKey();

  public PublishingPanel(ApiClient api, Consumer<JsonNode> preview) {
    super(new BorderLayout(0, 12));
    this.api = api;
    this.preview = preview;
    setOpaque(false);
    setBorder(new EmptyBorder(26, 30, 20, 30));
    JLabel heading = new JLabel("Xưởng xuất bản");
    heading.setFont(Theme.heading(28));
    add(heading, BorderLayout.NORTH);
    comics =
        new RemoteTable(
            api,
            new String[] {"Truyện quản lý", "Loại"},
            p -> "/publishing/comics?page=" + p + "&size=20",
            v -> new Object[] {v.path("title").asText(), v.path("type").asText()});
    translations =
        new RemoteTable(
            api,
            new String[] {"Bản dịch", "Ấn bản", "Hiển thị"},
            p ->
                comicId == null
                    ? null
                    : "/publishing/comics/" + comicId + "/translations?page=" + p + "&size=20",
            v ->
                new Object[] {
                  v.path("languageTag").asText(),
                  v.path("editionKey").asText(),
                  v.path("visibility").asText()
                });
    chapters =
        new RemoteTable(
            api,
            new String[] {"Chương", "Tên", "Hiển thị"},
            p ->
                translationId == null
                    ? null
                    : "/publishing/translations/"
                        + translationId
                        + "/chapters?page="
                        + p
                        + "&size=20",
            v ->
                new Object[] {
                  v.path("displayNumber").asText(),
                  v.path("title").asText(),
                  v.path("visibility").asText()
                });
    volumes =
        new RemoteTable(
            api,
            new String[] {"Volume", "Thứ tự"},
            p ->
                translationId == null
                    ? null
                    : "/publishing/translations/"
                        + translationId
                        + "/volumes?page="
                        + p
                        + "&size=20",
            v -> new Object[] {v.path("displayLabel").asText(), v.path("sortOrder").asText()});
    versions =
        new RemoteTable(
            api,
            new String[] {"Version", "Trạng thái", "Số trang"},
            p ->
                chapterId == null
                    ? null
                    : "/publishing/chapters/" + chapterId + "/versions?page=" + p + "&size=20",
            v ->
                new Object[] {
                  v.path("versionNumber").asInt(), v.path("state").asText(), v.path("pages").size()
                });
    queue =
        new RemoteTable(
            api,
            new String[] {"Chương", "Version", "Người gửi"},
            p -> "/publishing/review-queue?page=" + p + "&size=20",
            v ->
                new Object[] {
                  v.path("chapterId").asText(),
                  v.path("versionNumber").asInt(),
                  v.path("submittedBy").asText()
                });
    tables = List.of(comics, translations, chapters, volumes, versions, queue);
    comics.onSelection(
        v -> {
          comicId = v == null ? null : v.path("id").asText();
          translations.clear();
          if (v != null) translations.first();
        });
    translations.onSelection(
        v -> {
          translationId = v == null ? null : v.path("id").asText();
          chapters.clear();
          volumes.clear();
          if (v != null) {
            chapters.first();
            volumes.first();
          }
        });
    chapters.onSelection(
        v -> {
          chapterId = v == null ? null : v.path("id").asText();
          versions.clear();
          if (v != null) versions.first();
        });
    versions.onSelection(
        v -> {
          if (v == null) clearVersion();
          else loadVersion(v.path("id").asText());
        });
    JTabbedPane tabs = new JTabbedPane();
    JPanel catalog = new JPanel(new GridLayout(1, 2, 12, 0));
    catalog.setOpaque(false);
    catalog.add(
        withActions(
            comics,
            button("Tạo truyện", () -> comicForm(false)),
            button("Sửa truyện", () -> comicForm(true)),
            button("Xóa mềm truyện", this::deleteComic),
            button("Hiển thị truyện", this::visibility),
            button("Tạo metadata", () -> CatalogForms.metadata(this, api, comics::reload))));
    catalog.add(
        withActions(
            translations,
            button("Thêm bản dịch", () -> translationForm(false)),
            button("Sửa bản dịch", () -> translationForm(true))));
    tabs.addTab("Truyện / Bản dịch", catalog);
    JSplitPane organizing =
        new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            withActions(
                volumes,
                button("Thêm volume", () -> volumeForm(false)),
                button("Sửa volume", () -> volumeForm(true))),
            withActions(
                chapters,
                button("Thêm chương", () -> chapterForm(false)),
                button("Sửa chương", () -> chapterForm(true)),
                button("Đặt giá xu", this::chapterPrice)));
    organizing.setResizeWeight(.35);
    organizing.setDividerLocation(300);
    tabs.addTab("Volume / Chương", organizing);
    JPanel versionView = new JPanel(new BorderLayout(0, 10));
    versionView.setOpaque(false);
    JSplitPane versionSplit =
        new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            withActions(
                versions,
                button("Tạo bản nháp", () -> createVersion(false)),
                button("Nhân bản snapshot", () -> createVersion(true))),
            pageEditor());
    versionSplit.setResizeWeight(.28);
    versionSplit.setDividerLocation(285);
    versionView.add(versionSplit);
    versionView.add(uploadTools(), BorderLayout.SOUTH);
    tabs.addTab("Version / Trang", versionView);
    tabs.addTab(
        "Hàng đợi duyệt",
        withActions(
            queue,
            button(
                "Mở version",
                () -> {
                  if (queue.selected() != null) {
                    loadVersion(queue.selected().path("id").asText());
                    tabs.setSelectedIndex(2);
                  }
                })));
    tabs.addChangeListener(
        e -> {
          if (api.signedIn() && tabs.getSelectedIndex() == 3) queue.reload();
        });
    add(tabs);
    add(message, BorderLayout.SOUTH);
    pollTimer = new javax.swing.Timer(pollDelay, e -> poll());
    pollTimer.setRepeats(false);
    Theme.plainTable(pageTable);
    pageTable.setRowHeight(34);
    pageTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
            message.setText("Đăng nhập để sử dụng xưởng xuất bản.");
            return;
          }
          if (!actions.busy()) action.run();
        });
    return button;
  }

  private JPanel pageEditor() {
    JPanel editor = new JPanel(new BorderLayout(0, 8));
    editor.setOpaque(false);
    editor.add(versionLabel, BorderLayout.NORTH);
    editor.add(new JScrollPane(pageTable));
    JPanel controls = new JPanel(new GridLayout(0, 1));
    controls.setOpaque(false);
    controls.add(
        row(
            button("Lên", () -> movePage(-1)),
            button("Xuống", () -> movePage(1)),
            button("Lưu thứ tự", this::reorder),
            button("Thay ảnh", () -> upload(true)),
            button("Gỡ trang", this::removePage)));
    controls.add(
        row(
            button("Xem trước", this::preview),
            button("Gửi duyệt", this::submit),
            button("Duyệt", () -> review(true)),
            button("Từ chối", () -> review(false)),
            button("Xuất bản", this::publish),
            button("Lên lịch", this::schedule),
            button("Quay lại bản cũ", this::rollback),
            button("Ẩn chương", this::unpublish)));
    editor.add(controls, BorderLayout.SOUTH);
    return editor;
  }

  private JPanel uploadTools() {
    JPanel tools = new JPanel(new GridLayout(0, 1));
    tools.setOpaque(false);
    tools.add(row(button("Tải ảnh / ZIP", () -> upload(false)), uploadProgress, jobLabel));
    tools.add(
        row(
            new JLabel("Upload job"),
            jobId,
            button(
                "Theo dõi job",
                () -> {
                  pollDelay = 1800;
                  poll();
                }),
            button(
                "Dừng theo dõi",
                () -> {
                  pollTimer.stop();
                  polling.close();
                  jobLabel.setText("Đã dừng theo dõi; job backend vẫn tiếp tục.");
                }),
            button("Thử lại job", this::retryJob)));
    uploadProgress.setPreferredSize(new Dimension(110, 18));
    uploadProgress.setVisible(false);
    return tools;
  }

  private void comicForm(boolean edit) {
    if (edit && comicId == null) return;
    actions.run(
        this,
        message,
        api.get("/metadata")
            .thenCompose(
                meta ->
                    edit
                        ? api.get("/publishing/comics/" + comicId)
                            .thenApply(current -> new FormData(meta.body(), current))
                        : java.util.concurrent.CompletableFuture.completedFuture(
                            new FormData(meta.body(), null))),
        data -> CatalogForms.comic(this, api, data.metadata(), data.current(), comics::reload));
  }

  private void deleteComic() {
    if (comicId == null) return;
    String id = comicId;
    actions.run(
        this,
        message,
        api.get("/publishing/comics/" + id),
        current -> {
          if (!id.equals(comicId)) return;
          int answer =
              JOptionPane.showConfirmDialog(
                  this,
                  "Xóa mềm truyện này? Truyện sẽ ngừng truy cập; chapter, trang và tiến độ vẫn được"
                      + " giữ.",
                  "Xác nhận xóa mềm",
                  JOptionPane.OK_CANCEL_OPTION,
                  JOptionPane.WARNING_MESSAGE);
          if (answer != JOptionPane.OK_OPTION) return;
          actions.run(
              this,
              message,
              api.request("DELETE", "/comics/" + id, null, current.etag()),
              ignored -> {
                pollTimer.stop();
                polling.close();
                jobId.setText("");
                jobLabel.setText("Chưa có upload.");
                comics.reload();
                queue.reload();
                message.setText("Đã xóa mềm truyện. Dữ liệu nội dung và tiến độ được giữ nguyên.");
              });
        });
  }

  private record FormData(JsonNode metadata, ApiClient.Reply current) {}

  private void translationForm(boolean edit) {
    if (comicId == null) return;
    if (edit) {
      if (translations.selected() == null) return;
      actions.run(
          this,
          message,
          api.get("/publishing/translations/" + translations.selected().path("id").asText()),
          current ->
              CatalogForms.translation(
                  this, api, comicId, current, administrator, translations::reload));
    } else CatalogForms.translation(this, api, comicId, null, administrator, translations::reload);
  }

  private void visibility() {
    if (comicId == null) return;
    String id = comicId;
    actions.run(
        this,
        message,
        api.get("/publishing/comics/" + id),
        current -> {
          EditDialog dialog = new EditDialog(this, "Hiển thị truyện");
          var state =
              EditDialog.options(
                  current.body().path("visibility").asText(), "PUBLIC", "HIDDEN", "ARCHIVED");
          JTextField reason = EditDialog.text("");
          dialog.form.field("Hiển thị", state);
          dialog.form.field("Lý do", reason);
          dialog.submit(
              () ->
                  api.request(
                      "PUT",
                      "/admin/comics/" + id + "/visibility",
                      Map.of(
                          "visibility",
                          state.getSelectedItem(),
                          "reason",
                          reason.getText().strip()),
                      current.etag()),
              reply -> comics.reload());
          dialog.setVisible(true);
        });
  }

  private void volumeForm(boolean edit) {
    if (translationId == null || edit && volumes.selected() == null) return;
    String translation = translationId;
    if (edit)
      actions.run(
          this,
          message,
          api.get("/publishing/volumes/" + volumes.selected().path("id").asText()),
          current -> volumeDialog(translation, current));
    else volumeDialog(translation, null);
  }

  private void volumeDialog(String translation, ApiClient.Reply current) {
    EditDialog dialog = new EditDialog(this, current == null ? "Thêm volume" : "Sửa volume");
    JTextField
        label =
            EditDialog.text(current == null ? "" : current.body().path("displayLabel").asText()),
        order = EditDialog.text(current == null ? "1" : current.body().path("sortOrder").asText());
    dialog.form.field("Nhãn volume", label);
    dialog.form.field("Thứ tự", order);
    MutationKey key = new MutationKey();
    dialog.submit(
        () -> {
          var body =
              Map.of("displayLabel", label.getText().strip(), "sortOrder", order.getText().strip());
          return current == null
              ? api.create(
                  "/translations/" + translation + "/volumes", body, key.forInput("volume", body))
              : api.request(
                  "PATCH", "/volumes/" + current.body().path("id").asText(), body, current.etag());
        },
        reply -> volumes.reload());
    dialog.setVisible(true);
  }

  private void chapterForm(boolean edit) {
    if (translationId == null || edit && chapterId == null) return;
    String translation = translationId;
    if (edit)
      actions.run(
          this,
          message,
          api.get("/publishing/chapters/" + chapterId),
          current -> chapterDialog(translation, current));
    else chapterDialog(translation, null);
  }

  private void chapterDialog(String translation, ApiClient.Reply current) {
    EditDialog dialog = new EditDialog(this, current == null ? "Thêm chương" : "Sửa chương");
    JsonNode value = current == null ? null : current.body();
    JTextField number = EditDialog.text(value == null ? "" : value.path("displayNumber").asText()),
        title = EditDialog.text(value == null ? "" : value.path("title").asText()),
        order = EditDialog.text(value == null ? "1" : value.path("sortOrder").asText());
    var access =
        EditDialog.options(
            value == null ? "FREE" : value.path("accessPolicy").asText(), "FREE", "RESTRICTED");
    String[] volume = {value == null ? null : value.path("volumeId").asText(null)};
    JLabel volumeLabel =
        Theme.plainLabel(volume[0] == null ? "Không thuộc volume" : "Volume #" + volume[0]);
    JButton choose = new JButton("Chọn volume"), clear = new JButton("Bỏ volume");
    choose.addActionListener(
        e ->
            pickVolume(
                dialog,
                translation,
                v -> {
                  volume[0] = v.path("id").asText();
                  volumeLabel.setText(v.path("displayLabel").asText());
                }));
    clear.addActionListener(
        e -> {
          volume[0] = null;
          volumeLabel.setText("Không thuộc volume");
        });
    dialog.form.field("Số chương", number);
    dialog.form.field("Tên chương", title);
    dialog.form.field("Thứ tự", order);
    dialog.form.field("Quyền đọc", access);
    dialog.form.full(row(choose, clear, volumeLabel));
    MutationKey key = new MutationKey();
    dialog.submit(
        () -> {
          Map<String, Object> body = new LinkedHashMap<>();
          body.put("volumeId", volume[0]);
          body.put("displayNumber", number.getText().strip());
          body.put("title", title.getText().strip());
          body.put("sortOrder", order.getText().strip());
          body.put("accessPolicy", access.getSelectedItem());
          return current == null
              ? api.create(
                  "/translations/" + translation + "/chapters", body, key.forInput("chapter", body))
              : api.request(
                  "PATCH", "/chapters/" + value.path("id").asText(), body, current.etag());
        },
        reply -> chapters.reload());
    dialog.setVisible(true);
  }

  private void chapterPrice() {
    if (chapterId == null) {
      message.setText("Chọn chương trước khi đặt giá.");
      return;
    }
    String selectedChapter = chapterId;
    EditDialog dialog = new EditDialog(this, "Giá mở khóa chương");
    JComboBox<String> currency = EditDialog.options("PAID", "PAID", "REWARD");
    JTextField amount = EditDialog.text("10");
    dialog.form.field("Loại xu", currency);
    dialog.form.field("Số xu", amount);
    dialog.submit(() -> {
      long value;
      try { value = Long.parseLong(amount.getText().strip()); }
      catch (NumberFormatException error) { throw new IllegalArgumentException("Số xu phải là số nguyên dương."); }
      if (value <= 0) throw new IllegalArgumentException("Số xu phải lớn hơn 0.");
      return api.request("PUT", "/publishing/chapters/" + selectedChapter + "/prices",
          Map.of("currencyCode", currency.getSelectedItem(), "amount", value), null);
    }, reply -> message.setText("Đã cập nhật giá chương. Độc giả sẽ thấy giá mới khi mở khóa."));
    dialog.setSize(440, 280);
    dialog.setVisible(true);
  }

  private void pickVolume(Component owner, String translation, Consumer<JsonNode> selected) {
    JDialog dialog =
        new JDialog(
            SwingUtilities.getWindowAncestor(owner),
            "Chọn volume",
            Dialog.ModalityType.APPLICATION_MODAL);
    RemoteTable table =
        new RemoteTable(
            api,
            new String[] {"Volume", "Thứ tự"},
            p -> "/publishing/translations/" + translation + "/volumes?page=" + p + "&size=20",
            v -> new Object[] {v.path("displayLabel").asText(), v.path("sortOrder").asText()});
    JButton choose = new JButton("Chọn");
    choose.addActionListener(
        e -> {
          if (table.selected() != null) {
            JsonNode value = table.selected();
            dialog.dispose();
            selected.accept(value);
          }
        });
    dialog.add(table);
    dialog.add(choose, BorderLayout.SOUTH);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosed(java.awt.event.WindowEvent e) {
            table.close();
          }
        });
    dialog.setSize(620, 430);
    dialog.setLocationRelativeTo(owner);
    table.first();
    dialog.setVisible(true);
  }

  private void createVersion(boolean copy) {
    if (chapterId == null) return;
    Map<String, Object> body = new LinkedHashMap<>();
    if (copy) {
      if (version == null) {
        message.setText("Chọn snapshot nguồn trước.");
        return;
      }
      body.put("copyFromVersionId", version.body().path("id").asText());
    }
    String chapter = chapterId;
    actions.run(
        this,
        message,
        api.create(
            "/chapters/" + chapter + "/versions",
            body,
            mutation.forInput("version:" + chapter, body)),
        reply -> {
          mutation.confirmed();
          if (chapter.equals(chapterId)) {
            versions.firstSelecting(reply.body().path("id").asText());
          }
        });
  }

  private void clearVersion() {
    if (details.busy()) message.setText(" ");
    details.close();
    version = null;
    versionChapter = null;
    pageRows.clear();
    pageModel.setRowCount(0);
    versionLabel.setText("Chọn version để biên tập.");
  }

  private void loadVersion(String id) {
    clearVersion();
    details.run(
        this,
        message,
        api.get("/publishing/versions/" + id)
            .thenCompose(
                current ->
                    api.get("/publishing/chapters/" + current.body().path("chapterId").asText())
                        .thenApply(parent -> new VersionData(current, parent))),
        data -> {
          version = data.version();
          versionChapter = data.chapter();
          renderVersion();
        });
  }

  private record VersionData(ApiClient.Reply version, ApiClient.Reply chapter) {}

  private void renderVersion() {
    versions.replaceRow(version.body());
    chapters.replaceRow(versionChapter.body());
    pageRows.clear();
    pageModel.setRowCount(0);
    for (JsonNode page : version.body().path("pages")) pageRows.add(page);
    refreshPageRows();
    versionLabel.setText(
        "Version "
            + version.body().path("versionNumber").asInt()
            + " · "
            + version.body().path("state").asText()
            + " · "
            + versionChapter.body().path("title").asText()
            + " · "
            + version.body().path("reviewReason").asText(""));
  }

  private void refreshPageRows() {
    pageModel.setRowCount(0);
    for (int i = 0; i < pageRows.size(); i++) {
      JsonNode page = pageRows.get(i);
      pageModel.addRow(
          new Object[] {
            i + 1,
            page.path("pageId").asText(),
            page.path("width").asInt() + " × " + page.path("height").asInt(),
            page.path("assetStatus").asText()
          });
    }
  }

  private boolean editable() {
    if (version == null) return false;
    if (!Set.of("DRAFT", "FAILED").contains(version.body().path("state").asText())) {
      message.setText("Snapshot đã khóa. Tạo hoặc nhân bản bản nháp để sửa.");
      return false;
    }
    return true;
  }

  private void movePage(int delta) {
    if (!editable()) return;
    int from = pageTable.getSelectedRow(), to = from + delta;
    if (from < 0 || to < 0 || to >= pageRows.size()) return;
    Collections.swap(pageRows, from, to);
    refreshPageRows();
    pageTable.setRowSelectionInterval(to, to);
    message.setText("Thứ tự chưa lưu. Nhấn Lưu thứ tự.");
  }

  private String versionPath() {
    return "/publishing/versions/" + version.body().path("id").asText();
  }

  private void reorder() {
    if (!editable() || pageRows.isEmpty()) return;
    actions.run(
        this,
        message,
        api.request(
            "PUT",
            versionPath() + "/pages/order",
            Map.of("pageIds", pageRows.stream().map(v -> v.path("pageId").asText()).toList()),
            version.etag()),
        this::updatedVersion);
  }

  private void updatedVersion(ApiClient.Reply reply) {
    if (version == null
        || !version.body().path("id").asText().equals(reply.body().path("id").asText())) return;
    version = reply;
    renderVersion();
  }

  private void removePage() {
    if (!editable() || pageTable.getSelectedRow() < 0) return;
    if (JOptionPane.showConfirmDialog(
            this,
            "Gỡ trang khỏi bản nháp? Trang trong snapshot cũ được giữ.",
            "Gỡ trang",
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    String id = version.body().path("id").asText();
    String page = pageRows.get(pageTable.getSelectedRow()).path("pageId").asText();
    actions.run(
        this,
        message,
        api.request("DELETE", versionPath() + "/pages/" + page, null, version.etag()),
        reply -> {
          if (version != null && version.body().path("id").asText().equals(id)) loadVersion(id);
        });
  }

  private void upload(boolean replace) {
    if (!editable() || uploads.busy()) return;
    String page = null;
    if (replace) {
      if (pageTable.getSelectedRow() < 0) return;
      page = pageRows.get(pageTable.getSelectedRow()).path("pageId").asText();
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(replace ? "Chọn một ảnh thay thế" : "Chọn ảnh hoặc ZIP trang truyện");
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
    Path file = chooser.getSelectedFile().toPath();
    String id = version.body().path("id").asText();
    Map<String, String> fields = page == null ? Map.of() : Map.of("pageId", page);
    String key =
        uploadKey.forInput(
            "upload:" + id,
            Map.of("file", file.toAbsolutePath().toString(), "pageId", page == null ? "" : page));
    uploadProgress.setVisible(true);
    uploadProgress.setIndeterminate(true);
    uploads.run(
        this,
        message,
        api.upload(versionPath() + "/uploads", file, fields, key, version.etag()),
        reply -> {
          uploadProgress.setIndeterminate(false);
          uploadProgress.setVisible(false);
          boolean stagingFailed = "STAGING_FAILED".equals(reply.body().path("errorCode").asText());
          if (!stagingFailed) uploadKey.confirmed();
          jobId.setText(reply.body().path("id").asText());
          jobLabel.setText("Job " + jobId.getText() + " · " + reply.body().path("status").asText());
          if (stagingFailed)
            message.setText("Staging thất bại. Chọn lại cùng tệp để thử cùng mã thao tác.");
          if (version != null && version.body().path("id").asText().equals(id)) loadVersion(id);
          pollDelay = 1800;
          pollTimer.setInitialDelay(pollDelay);
          pollTimer.restart();
        },
        error -> {
          uploadProgress.setIndeterminate(false);
          uploadProgress.setVisible(false);
          message.setText("Upload chưa được xác nhận. Chọn lại cùng tệp để thử cùng mã thao tác.");
        });
  }

  private void poll() {
    if (!api.signedIn() || polling.busy()) return;
    String id = jobId.getText().strip();
    if (!id.matches("[1-9][0-9]{0,18}")) {
      message.setText("Mã job không hợp lệ.");
      return;
    }
    polling.run(
        this,
        message,
        api.get("/publishing/uploads/" + id),
        reply -> {
          String status = reply.body().path("status").asText();
          jobLabel.setText(
              "Job "
                  + id
                  + " · "
                  + status
                  + " · Lần "
                  + reply.body().path("attempts").asInt()
                  + " · "
                  + reply.body().path("errorCode").asText(""));
          if (Set.of("SUCCEEDED", "FAILED").contains(status)) {
            pollTimer.stop();
            if (version != null
                && version
                    .body()
                    .path("id")
                    .asText()
                    .equals(reply.body().path("versionId").asText()))
              loadVersion(version.body().path("id").asText());
          } else if (visibleWorkspace) {
            pollDelay = Math.min(15000, pollDelay + 1200);
            pollTimer.setInitialDelay(pollDelay);
            pollTimer.restart();
          }
        },
        error -> {
          jobLabel.setText(
              "Mất kết nối theo dõi job. Nhấn Theo dõi job để tải trạng thái bền vững.");
        });
  }

  private void retryJob() {
    String id = jobId.getText().strip();
    if (!id.matches("[1-9][0-9]{0,18}")) return;
    actions.run(
        this,
        message,
        api.create("/publishing/uploads/" + id + "/retry", null, mutation.forInput("retryJob", id)),
        reply -> {
          mutation.confirmed();
          pollDelay = 1800;
          poll();
        });
  }

  private void preview() {
    if (version == null) return;
    actions.run(
        this,
        message,
        api.get(versionPath() + "/preview?displayFormat=JPEG"),
        reply -> preview.accept(reply.body()));
  }

  private void submit() {
    if (!editable()) return;
    actions.run(
        this,
        message,
        api.request("POST", versionPath() + "/submit", null, version.etag()),
        this::updatedVersion);
  }

  private void review(boolean approve) {
    if (version == null) return;
    String reason =
        JOptionPane.showInputDialog(
            this, approve ? "Ghi chú duyệt (có thể để trống)" : "Lý do từ chối (bắt buộc)");
    if (reason == null) return;
    if (!approve && reason.isBlank()) {
      message.setText("Cần nhập lý do từ chối.");
      return;
    }
    Map<String, String> input = reviewInput(approve, reason);
    actions.run(
        this,
        message,
        api.request("POST", versionPath() + "/review", input, version.etag()),
        reply -> {
          updatedVersion(reply);
          queue.reload();
        });
  }

  static Map<String, String> reviewInput(boolean approve, String reason) {
    return approve && reason.isBlank()
        ? Map.of("decision", "APPROVE")
        : Map.of("decision", approve ? "APPROVE" : "REJECT", "reason", reason.strip());
  }

  private void publish() {
    if (version == null || versionChapter == null) return;
    if (JOptionPane.showConfirmDialog(
            this, "Xuất bản snapshot đã duyệt này?", "Xuất bản", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    String selected = version.body().path("id").asText();
    actions.run(
        this,
        message,
        api.request(
            "POST",
            "/publishing/chapters/" + versionChapter.body().path("id").asText() + "/publish",
            Map.of("versionId", selected),
            versionChapter.etag()),
        reply -> {
          if (version != null && version.body().path("id").asText().equals(selected)) {
            versionChapter = reply;
            loadVersion(selected);
          }
          chapters.replaceRow(reply.body());
        });
  }

  private void schedule() {
    if (version == null || versionChapter == null || chapterId == null) return;
    String selected = version.body().path("id").asText();
    String input =
        JOptionPane.showInputDialog(
            this, "Thời điểm xuất bản (yyyy-MM-dd HH:mm, giờ máy):", "Lên lịch xuất bản",
            JOptionPane.PLAIN_MESSAGE);
    if (input == null || input.isBlank()) return;
    java.time.Instant at;
    try {
      at =
          java.time.LocalDateTime.parse(
                  input.strip(),
                  java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
              .atZone(java.time.ZoneId.systemDefault())
              .toInstant();
    } catch (java.time.format.DateTimeParseException invalid) {
      message.setText("Thời điểm không hợp lệ. Dùng dạng yyyy-MM-dd HH:mm.");
      return;
    }
    actions.run(
        this,
        message,
        api.request(
            "POST",
            "/chapters/" + chapterId + "/schedule",
            Map.of("versionId", selected, "publishAt", at.toString()),
            versionChapter.etag()),
        reply -> {
          message.setText("Đã lên lịch xuất bản lúc " + input.strip() + ".");
          loadVersion(selected);
          versions.reload();
        });
  }

  private void rollback() {
    if (version == null || versionChapter == null || chapterId == null) return;
    String selected = version.body().path("id").asText();
    if (JOptionPane.showConfirmDialog(
            this,
            "Quay lại snapshot đã chọn làm bản xuất bản hiện tại?",
            "Quay lại bản cũ",
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    actions.run(
        this,
        message,
        api.request(
            "POST",
            "/chapters/" + chapterId + "/rollback",
            Map.of("versionId", selected),
            versionChapter.etag()),
        reply -> {
          versionChapter = reply;
          loadVersion(selected);
          versions.reload();
          chapters.replaceRow(reply.body());
          message.setText("Đã quay lại snapshot đã chọn.");
        });
  }

  private void unpublish() {
    if (versionChapter == null) return;
    String reason = JOptionPane.showInputDialog(this, "Lý do ẩn chương");
    if (reason == null || reason.isBlank()) return;
    actions.run(
        this,
        message,
        api.request(
            "POST",
            "/publishing/chapters/" + versionChapter.body().path("id").asText() + "/unpublish",
            Map.of("reason", reason.strip()),
            versionChapter.etag()),
        reply -> {
          versionChapter = reply;
          message.setText("Đã ẩn chương.");
          chapters.replaceRow(reply.body());
        });
  }

  @Override
  public void onShown() {
    visibleWorkspace = true;
    if (api.signedIn()) {
      message.setText(" ");
      comics.reload();
      api.get("/me")
          .whenComplete(
              (reply, error) ->
                  SwingUtilities.invokeLater(
                      () -> {
                        if (error == null) {
                          administrator = false;
                          for (JsonNode role : reply.body().path("roles"))
                            if (role.asText().equals("ADMINISTRATOR")) administrator = true;
                        }
                      }));
    }
  }

  @Override
  public void onHidden() {
    visibleWorkspace = false;
    pollTimer.stop();
    polling.close();
  }

  @Override
  public void sessionChanged() {
    onHidden();
    actions.close();
    uploads.close();
    tables.forEach(RemoteTable::clear);
    clearVersion();
    administrator = false;
    comicId = null;
    translationId = null;
    chapterId = null;
    jobId.setText("");
    jobLabel.setText("Chưa có upload.");
    uploadProgress.setVisible(false);
    message.setText("Phiên tài khoản đã đổi. Tải lại xưởng xuất bản.");
  }

  @Override
  public void close() {
    sessionChanged();
  }
}
