package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.client.api.*;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import tools.jackson.databind.JsonNode;

/** Catalog metadata forms share the same REST contracts as future clients. */
final class CatalogForms {
  private CatalogForms() {}

  private record Option(String id, String name) {
    @Override
    public String toString() {
      return name;
    }
  }

  private static String text(JsonNode value, String field, String fallback) {
    return value == null ? fallback : value.path(field).asText(fallback);
  }

  private static Map<String, Object> input() {
    return new LinkedHashMap<>();
  }

  static void comic(
      Component owner, ApiClient api, JsonNode metadata, ApiClient.Reply current, Runnable saved) {
    JsonNode value = current == null ? null : current.body();
    EditDialog dialog = new EditDialog(owner, current == null ? "Tạo truyện" : "Chỉnh sửa truyện");
    FormGrid form = dialog.form;
    MutationKey mutation = new MutationKey();
    JTextField title = EditDialog.text(text(value, "title", "")),
        slug = EditDialog.text(text(value, "slug", "")),
        country = EditDialog.text(text(value, "countryCode", "JP"));
    JTextArea description = FormGrid.text(5), aliases = FormGrid.text(3);
    description.setText(text(value, "description", ""));
    if (value != null) {
      List<String> names = new ArrayList<>();
      value.path("alternativeTitles").forEach(v -> names.add(v.asText()));
      aliases.setText(String.join("\n", names));
    }
    var type =
        EditDialog.options(text(value, "type", "MANGA"), "MANGA", "MANHWA", "MANHUA", "COMIC");
    var status =
        EditDialog.options(
            text(value, "status", "ONGOING"), "ONGOING", "COMPLETED", "HIATUS", "DROPPED");
    form.field("Tên truyện", title);
    form.field("Slug", slug);
    form.field("Mô tả", new JScrollPane(description));
    form.field("Loại", type);
    form.field("Quốc gia (ISO 2 ký tự)", country);
    form.field("Trạng thái", status);
    form.field("Tên khác (mỗi dòng một tên)", new JScrollPane(aliases));
    Map<String, JList<Option>> selectors = new LinkedHashMap<>();
    Map<String, String> kinds = new LinkedHashMap<>();
    kinds.put("genreIds", "genres");
    kinds.put("tagIds", "tags");
    kinds.put("warningIds", "contentWarnings");
    kinds.put("publisherIds", "publishers");
    for (var entry : kinds.entrySet()) {
      JList<Option> list = selection(metadata.path(entry.getValue()));
      if (value != null) select(list, value.path(entry.getKey()));
      selectors.put(entry.getKey(), list);
      form.field(
          switch (entry.getKey()) {
            case "genreIds" -> "Thể loại";
            case "tagIds" -> "Tags";
            case "warningIds" -> "Cảnh báo";
            default -> "Nhà xuất bản";
          },
          new JScrollPane(list));
    }
    JList<Option> authors = selection(metadata.path("authors")),
        artists = selection(metadata.path("authors"));
    if (value != null) {
      Set<String> a = new HashSet<>(), b = new HashSet<>();
      for (JsonNode credit : value.path("contributors"))
        (credit.path("contribution").asText().equals("AUTHOR") ? a : b)
            .add(credit.path("authorId").asText());
      select(authors, a);
      select(artists, b);
    }
    form.field("Tác giả (Ctrl chọn nhiều)", new JScrollPane(authors));
    form.field("Họa sĩ (Ctrl chọn nhiều)", new JScrollPane(artists));
    String[] assets = {text(value, "coverAssetId", ""), text(value, "bannerAssetId", "")};
    for (int i = 0; i < 2; i++) {
      int index = i;
      String kind = i == 0 ? "COVER" : "BANNER";
      JLabel label = Theme.plainLabel(assets[i].isBlank() ? "Chưa có ảnh" : "Đã có ảnh");
      JButton upload = new JButton(i == 0 ? "Chọn ảnh bìa" : "Chọn banner"),
          clear = new JButton("Gỡ ảnh");
      MutationKey fileKey = new MutationKey();
      upload.addActionListener(
          e -> {
            if (dialog.busy()) return;
            JFileChooser picker = new JFileChooser();
            if (picker.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) return;
            Path file = picker.getSelectedFile().toPath();
            dialog.operation(
                api.upload(
                    "/assets",
                    file,
                    Map.of("kind", kind),
                    fileKey.forInput(kind, file.toAbsolutePath().toString()),
                    null),
                reply -> {
                  assets[index] = reply.body().path("id").asText();
                  fileKey.confirmed();
                  label.setText("Đã tải ảnh thành công");
                });
          });
      clear.addActionListener(
          e -> {
            if (!dialog.busy()) {
              assets[index] = "";
              label.setText("Chưa có ảnh");
            }
          });
      JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
      row.add(upload);
      row.add(clear);
      row.add(label);
      form.full(row);
    }
    dialog.submit(
        () -> {
          Map<String, Object> body = input();
          body.put("title", title.getText().strip());
          body.put("slug", slug.getText().strip());
          body.put("description", description.getText());
          body.put("type", type.getSelectedItem());
          body.put("countryCode", country.getText().strip().toUpperCase(Locale.ROOT));
          body.put("status", status.getSelectedItem());
          body.put("ageRating", "ALL_AGE");
          body.put(
              "alternativeTitles",
              aliases.getText().lines().map(String::strip).filter(s -> !s.isEmpty()).toList());
          selectors.forEach(
              (name, list) ->
                  body.put(name, list.getSelectedValuesList().stream().map(Option::id).toList()));
          List<Map<String, String>> credits = new ArrayList<>();
          authors
              .getSelectedValuesList()
              .forEach(a -> credits.add(Map.of("authorId", a.id(), "contribution", "AUTHOR")));
          artists
              .getSelectedValuesList()
              .forEach(a -> credits.add(Map.of("authorId", a.id(), "contribution", "ARTIST")));
          body.put("contributors", credits);
          body.put("coverAssetId", assets[0].isBlank() ? null : assets[0]);
          body.put("bannerAssetId", assets[1].isBlank() ? null : assets[1]);
          return current == null
              ? api.create("/comics", body, mutation.forInput("createComic", body))
              : api.request("PATCH", "/comics/" + value.path("id").asText(), body, current.etag());
        },
        reply -> saved.run());
    dialog.setVisible(true);
  }

  private static JList<Option> selection(JsonNode values) {
    DefaultListModel<Option> model = new DefaultListModel<>();
    values.forEach(
        v -> model.addElement(new Option(v.path("id").asText(), v.path("name").asText())));
    JList<Option> list = new JList<>(model);
    list.setVisibleRowCount(3);
    var renderer = new DefaultListCellRenderer();
    renderer.putClientProperty("html.disable", Boolean.TRUE);
    list.setCellRenderer(renderer);
    return list;
  }

  private static void select(JList<Option> list, JsonNode values) {
    Set<String> ids = new HashSet<>();
    values.forEach(v -> ids.add(v.asText()));
    select(list, ids);
  }

  private static void select(JList<Option> list, Set<String> ids) {
    List<Integer> indices = new ArrayList<>();
    for (int i = 0; i < list.getModel().getSize(); i++)
      if (ids.contains(list.getModel().getElementAt(i).id())) indices.add(i);
    list.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
  }

  static void metadata(Component owner, ApiClient api, Runnable saved) {
    EditDialog dialog = new EditDialog(owner, "Tạo metadata");
    var kind = EditDialog.options(null, "GENRE", "TAG", "CONTENT_WARNING", "AUTHOR", "PUBLISHER");
    JTextField code = EditDialog.text(""), name = EditDialog.text("");
    dialog.form.field("Loại", kind);
    dialog.form.field("Mã", code);
    dialog.form.field("Tên", name);
    MutationKey key = new MutationKey();
    dialog.submit(
        () -> {
          var body =
              Map.of(
                  "kind",
                  kind.getSelectedItem(),
                  "code",
                  code.getText().strip(),
                  "name",
                  name.getText().strip());
          return api.create("/admin/metadata", body, key.forInput("metadata", body));
        },
        reply -> saved.run());
    dialog.setVisible(true);
  }

  static void translation(
      Component owner,
      ApiClient api,
      String comic,
      ApiClient.Reply current,
      boolean administrator,
      Runnable saved) {
    JsonNode value = current == null ? null : current.body();
    EditDialog dialog =
        new EditDialog(owner, current == null ? "Thêm bản dịch" : "Chỉnh sửa bản dịch");
    JTextField language = EditDialog.text(text(value, "languageTag", "vi")),
        edition = EditDialog.text(text(value, "editionKey", "main")),
        title = EditDialog.text(text(value, "titleOverride", ""));
    var visibility =
        EditDialog.options(text(value, "visibility", "HIDDEN"), "PUBLIC", "HIDDEN", "ARCHIVED");
    String[] team = {text(value, "teamId", "")};
    JLabel teamLabel = Theme.plainLabel(team[0].isEmpty() ? "Chưa chọn nhóm" : "Nhóm #" + team[0]);
    if (current == null) {
      JButton choose = new JButton("Chọn nhóm sở hữu");
      choose.addActionListener(
          e ->
              pickTeam(
                  dialog,
                  api,
                  administrator,
                  option -> {
                    team[0] = option.id();
                    teamLabel.setText(option.name());
                  }));
      dialog.form.full(choose);
      dialog.form.full(teamLabel);
      dialog.form.field("Mã ngôn ngữ (BCP 47)", language);
      dialog.form.field("Ấn bản", edition);
    }
    dialog.form.field("Tên bản dịch", title);
    if (current != null) dialog.form.field("Hiển thị", visibility);
    MutationKey key = new MutationKey();
    dialog.submit(
        () -> {
          Map<String, Object> body = input();
          if (current == null) {
            if (team[0].isEmpty()) throw new IllegalArgumentException("Chọn nhóm sở hữu trước.");
            body.put("teamId", team[0]);
            body.put("languageTag", language.getText().strip());
            body.put("editionKey", edition.getText().strip());
            body.put("titleOverride", title.getText().isBlank() ? null : title.getText().strip());
            return api.create(
                "/comics/" + comic + "/translations", body, key.forInput("translation", body));
          }
          if (!title.getText().isBlank()) body.put("titleOverride", title.getText().strip());
          body.put("visibility", visibility.getSelectedItem());
          return api.request(
              "PATCH", "/translations/" + value.path("id").asText(), body, current.etag());
        },
        reply -> saved.run());
    dialog.setVisible(true);
  }

  private static void pickTeam(
      Component owner,
      ApiClient api,
      boolean administrator,
      java.util.function.Consumer<Option> chosen) {
    JDialog dialog =
        new JDialog(
            SwingUtilities.getWindowAncestor(owner),
            "Chọn nhóm",
            Dialog.ModalityType.APPLICATION_MODAL);
    RemoteTable table =
        new RemoteTable(
            api,
            new String[] {"Tên nhóm", "Trạng thái"},
            p -> (administrator ? "/admin/teams" : "/me/teams") + "?page=" + p + "&size=20",
            v -> new Object[] {v.path("name").asText(), v.path("status").asText()});
    JButton select = new JButton("Chọn nhóm");
    select.addActionListener(
        e -> {
          if (table.selected() != null) {
            JsonNode value = table.selected();
            dialog.dispose();
            chosen.accept(new Option(value.path("id").asText(), value.path("name").asText()));
          }
        });
    dialog.add(table);
    dialog.add(select, BorderLayout.SOUTH);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosed(java.awt.event.WindowEvent e) {
            table.close();
          }
        });
    dialog.setSize(640, 460);
    dialog.setLocationRelativeTo(owner);
    table.first();
    dialog.setVisible(true);
  }
}
