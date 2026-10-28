package com.comicnet.desktop.features.community;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.api.MutationKey;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.util.*;
import javax.swing.*;

public final class CommentDialog extends JDialog {
  private final ApiClient api;
  private final String comic, chapter, pageId;
  private final BigDecimal offset;
  private final JPanel body = new JPanel(new BorderLayout(10, 10));
  private final JLabel message = Theme.plainLabel(" ");
  private final JTextArea content = new JTextArea(4, 50);
  private final JCheckBox reveal = new JCheckBox("Hiện nội dung spoiler");
  private final UiTasks actions = new UiTasks(), actorTasks = new UiTasks();
  private final RemoteTable comments, blocks, reports;
  private final MutationKey createKey = new MutationKey();
  private final JTabbedPane tabs = new JTabbedPane();
  private AutoCloseable sessionListener;

  public CommentDialog(
      Component owner,
      ApiClient api,
      String comic,
      String chapter,
      String pageId,
      BigDecimal offset) {
    super(
        SwingUtilities.getWindowAncestor(owner),
        pageId != null
            ? "Bình luận trang"
            : chapter != null ? "Bình luận chương" : "Bình luận truyện",
        Dialog.ModalityType.APPLICATION_MODAL);
    this.api = api;
    this.comic = comic;
    this.chapter = chapter;
    this.pageId = pageId;
    this.offset = offset;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    body.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
    String context =
        "comicId="
            + comic
            + (chapter == null ? "" : "&chapterId=" + chapter)
            + (pageId == null ? "" : "&pageId=" + pageId);
    comments =
        new RemoteTable(
            api,
            new String[] {"ID", "Người viết", "Trả lời", "Nội dung", "Vote"},
            p -> "/community/comments?" + context + "&page=" + p + "&size=20",
            row ->
                new Object[] {
                  row.path("id").asText(),
                  row.path("displayName").asText(),
                  row.path("parentId").asText("—"),
                  row.path("deleted").asBoolean()
                      ? "Đã xóa"
                      : row.path("hidden").asBoolean()
                          ? "Đã ẩn"
                          : row.path("spoiler").asBoolean()
                              ? "[Spoiler]"
                              : row.path("body").asText(),
                  row.path("votes").asLong()
                });
    blocks =
        new RemoteTable(
            api,
            new String[] {"User ID", "Người đã chặn"},
            p -> "/me/blocks?page=" + p + "&size=20",
            row -> new Object[] {row.path("userId").asText(), row.path("displayName").asText()});
    reports =
        new RemoteTable(
            api,
            new String[] {"Report", "Comment", "Lý do"},
            p -> "/community/moderation/reports?page=" + p + "&size=20",
            row ->
                new Object[] {
                  row.path("id").asText(),
                  row.path("commentId").asText(),
                  row.path("reason").asText()
                });
    comments.onSelection(row -> showSelected());
    content.setEditable(false);
    content.setLineWrap(true);
    content.setWrapStyleWord(true);
    content.getAccessibleContext().setAccessibleName("Nội dung bình luận đang chọn");
    reveal.addActionListener(e -> showSelected());
    JPanel commentTab = new JPanel(new BorderLayout(8, 8));
    commentTab.add(comments);
    JPanel lower = new JPanel(new BorderLayout());
    lower.add(new JScrollPane(content));
    JPanel buttons = row();
    buttons.add(reveal);
    buttons.add(button("Viết", () -> edit(false, false)));
    buttons.add(button("Trả lời", () -> edit(true, false)));
    buttons.add(button("Sửa", () -> edit(false, true)));
    buttons.add(button("Xóa", this::delete));
    buttons.add(button("+1", () -> vote(1)));
    buttons.add(button("−1", () -> vote(-1)));
    buttons.add(button("Bỏ vote", () -> vote(null)));
    buttons.add(button("Báo cáo", this::report));
    buttons.add(button("Chặn người viết", this::block));
    lower.add(buttons, BorderLayout.SOUTH);
    commentTab.add(lower, BorderLayout.SOUTH);
    tabs.addTab("Bình luận", commentTab);
    if (api.signedIn()) {
      JPanel blocked = new JPanel(new BorderLayout());
      blocked.add(blocks);
      JPanel b = row();
      b.add(
          button(
              "Bỏ chặn người đã chọn",
              () -> {
                var selected = blocks.selected();
                if (selected == null) return;
                actions.run(
                    body,
                    message,
                    api.request(
                        "DELETE", "/me/blocks/" + selected.path("userId").asText(), null, null),
                    reply -> {
                      blocks.reload();
                      comments.reload();
                    });
              }));
      blocked.add(b, BorderLayout.SOUTH);
      tabs.addTab("Đã chặn", blocked);
      actorTasks.run(
          body,
          message,
          api.get("/me"),
          reply -> {
            boolean staff = false;
            for (var role : reply.body().path("roles"))
              if (role.asText().equals("ADMINISTRATOR") || role.asText().equals("MODERATOR"))
                staff = true;
            if (staff) {
              JPanel moderation = new JPanel(new BorderLayout());
              moderation.add(reports);
              JPanel m = row();
              m.add(button("Xử lý báo cáo", this::moderate));
              moderation.add(m, BorderLayout.SOUTH);
              tabs.addTab("Moderation", moderation);
            }
          });
    }
    tabs.addChangeListener(
        e -> {
          if (tabs.getSelectedIndex() == 1) blocks.first();
          if (tabs.getSelectedIndex() == 2) reports.first();
        });
    body.add(tabs);
    body.add(message, BorderLayout.SOUTH);
    setContentPane(body);
    getRootPane()
        .registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    sessionListener = api.onSessionChanged(() -> SwingUtilities.invokeLater(this::dispose));
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            actions.close();
            actorTasks.close();
            comments.close();
            blocks.close();
            reports.close();
            try {
              sessionListener.close();
            } catch (Exception ignored) {
            }
          }
        });
    setSize(1080, 730);
    setMinimumSize(new Dimension(720, 520));
    setLocationRelativeTo(owner);
    comments.first();
  }

  private JPanel row() {
    return new JPanel(new WrapLayout(FlowLayout.LEFT, 7, 4));
  }

  private JButton button(String text, Runnable action) {
    JButton b = new JButton(text);
    b.addActionListener(e -> action.run());
    return b;
  }

  private void showSelected() {
    var selected = comments.selected();
    content.setText(
        selected == null
            ? ""
            : selected.path("deleted").asBoolean()
                ? "Bình luận đã xóa; các trả lời vẫn được giữ."
                : selected.path("hidden").asBoolean()
                    ? "Bình luận đã bị moderator ẩn."
                    : selected.path("spoiler").asBoolean() && !reveal.isSelected()
                        ? "Bật Hiện nội dung spoiler để đọc."
                        : selected.path("body").asText());
    content.setCaretPosition(0);
  }

  private void edit(boolean reply, boolean editing) {
    if (!api.signedIn()) {
      message.setText("Đăng nhập để bình luận.");
      return;
    }
    var selected = comments.selected();
    if ((reply || editing) && selected == null) return;
    EditDialog dialog =
        new EditDialog(
            body, editing ? "Sửa bình luận" : reply ? "Trả lời bình luận" : "Viết bình luận");
    JTextArea text = new JTextArea(editing ? selected.path("body").asText() : "", 6, 45);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    JCheckBox spoiler =
        new JCheckBox("Có spoiler", editing && selected.path("spoiler").asBoolean());
    dialog.form.field("Nội dung, tối đa 4.000 ký tự", new JScrollPane(text));
    dialog.form.field("Spoiler", spoiler);
    dialog.submit(
        () -> {
          Map<String, Object> payload = new LinkedHashMap<>();
          payload.put("body", text.getText());
          payload.put("spoiler", spoiler.isSelected());
          if (editing)
            return api.request(
                "PATCH",
                "/community/comments/" + selected.path("id").asText(),
                payload,
                selected.path("etag").asText());
          payload.put("comicId", comic);
          if (chapter != null) payload.put("chapterId", chapter);
          if (pageId != null) {
            payload.put("pageId", pageId);
            payload.put("relativeOffset", offset);
          }
          if (reply) payload.put("parentId", selected.path("id").asText());
          return api.create("/community/comments", payload, createKey.forInput("comment", payload));
        },
        result -> {
          createKey.confirmed();
          comments.reload();
        });
    dialog.setVisible(true);
  }

  private void delete() {
    var selected = comments.selected();
    if (selected == null || !api.signedIn() || actions.busy()) return;
    if (JOptionPane.showConfirmDialog(
            this,
            "Xóa nội dung bình luận? Các trả lời vẫn được giữ.",
            "Xóa bình luận",
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    actions.run(
        body,
        message,
        api.request(
            "DELETE",
            "/community/comments/" + selected.path("id").asText(),
            null,
            selected.path("etag").asText()),
        r -> comments.reload());
  }

  private void vote(Integer value) {
    var selected = comments.selected();
    if (selected == null || actions.busy()) return;
    actions.run(
        body,
        message,
        api.request(
            value == null ? "DELETE" : "PUT",
            "/community/comments/" + selected.path("id").asText() + "/vote",
            value == null ? null : Map.of("value", value),
            null),
        r -> comments.reload());
  }

  private void report() {
    var selected = comments.selected();
    if (selected == null || !api.signedIn()) return;
    EditDialog dialog = new EditDialog(body, "Báo cáo bình luận");
    JTextArea reason = new JTextArea(4, 45);
    dialog.form.field("Lý do", new JScrollPane(reason));
    dialog.submit(
        () ->
            api.request(
                "POST",
                "/community/comments/" + selected.path("id").asText() + "/reports",
                Map.of("reason", reason.getText()),
                null),
        r -> message.setText("Đã gửi báo cáo."));
    dialog.setVisible(true);
  }

  private void block() {
    var selected = comments.selected();
    if (selected == null || actions.busy()) return;
    actions.run(
        body,
        message,
        api.request("PUT", "/me/blocks/" + selected.path("authorId").asText(), null, null),
        r -> comments.reload());
  }

  private void moderate() {
    var report = reports.selected();
    if (report == null || actions.busy()) return;
    String comment = report.path("commentId").asText();
    actions.run(
        body,
        message,
        api.get("/community/comments/" + comment),
        current -> {
          EditDialog dialog = new EditDialog(body, "Xử lý bình luận #" + comment);
          JTextArea reason = new JTextArea(4, 45);
          JCheckBox hidden =
              new JCheckBox("Ẩn bình luận", current.body().path("hidden").asBoolean());
          JTextArea original =
              new JTextArea(
                  current.body().path("body").asText("Nội dung không còn hiển thị"), 4, 45);
          original.setEditable(false);
          dialog.form.field("Nội dung", new JScrollPane(original));
          dialog.form.field("Kết luận", hidden);
          dialog.form.field("Lý do", new JScrollPane(reason));
          dialog.submit(
              () ->
                  api.request(
                      "POST",
                      "/community/comments/" + comment + "/moderation",
                      Map.of("hidden", hidden.isSelected(), "reason", reason.getText()),
                      current.etag()),
              result -> {
                reports.reload();
                comments.reload();
              });
          dialog.setVisible(true);
        });
  }
}
