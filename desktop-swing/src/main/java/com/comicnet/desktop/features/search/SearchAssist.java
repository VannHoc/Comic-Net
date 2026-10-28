package com.comicnet.desktop.features.search;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;
import javax.swing.event.*;

/** Debounced public suggestions and account-private history; no local credential/data cache. */
public final class SearchAssist implements AutoCloseable {
  private final ApiClient api;
  private final JTextField query;
  private final Runnable search;
  private final JPopupMenu suggestions = new JPopupMenu();
  private final Timer delay;
  private long generation;
  private CompletableFuture<?> pending;
  private JDialog historyDialog;

  public SearchAssist(ApiClient api, JTextField query, Runnable search) {
    this.api = api;
    this.query = query;
    this.search = search;
    delay = new Timer(300, e -> suggest());
    delay.setRepeats(false);
    query
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              private void changed() {
                generation++;
                suggestions.setVisible(false);
                if (pending != null) pending.cancel(true);
                delay.restart();
              }

              public void insertUpdate(DocumentEvent e) {
                changed();
              }

              public void removeUpdate(DocumentEvent e) {
                changed();
              }

              public void changedUpdate(DocumentEvent e) {
                changed();
              }
            });
    query.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            delay.stop();
          }
        });
  }

  private static String encode(String text) {
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }

  private void suggest() {
    String text = query.getText().strip();
    if (!query.isFocusOwner() || text.length() < 2 || text.length() > 200) return;
    long expected = ++generation;
    pending =
        api.get("/search/suggestions?q=" + encode(text))
            .whenComplete(
                (reply, error) ->
                    SwingUtilities.invokeLater(
                        () -> {
                          if (generation != expected || !query.isShowing() || !query.isFocusOwner())
                            return;
                          suggestions.removeAll();
                          if (error != null)
                            return; // Search button remains usable when suggestion lookup fails.
                          for (var value : reply.body()) {
                            String title = value.path("title").asText();
                            JMenuItem item = new JMenuItem(title);
                            item.putClientProperty("html.disable", true);
                            item.addActionListener(
                                e -> {
                                  dismiss();
                                  query.setText(title);
                                  search.run();
                                });
                            suggestions.add(item);
                          }
                          if (suggestions.getComponentCount() > 0)
                            suggestions.show(query, 0, query.getHeight());
                        }));
  }

  public void remember(JLabel message) {
    dismiss();
    String value = query.getText().strip();
    if (!api.signedIn() || value.isEmpty() || value.length() > 200) return;
    long session = generation;
    api.request("POST", "/me/search-history", Map.of("query", value), null)
        .whenComplete(
            (reply, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error != null && generation == session)
                        message.setText("Tìm kiếm vẫn dùng được; chưa lưu được lịch sử.");
                    }));
  }

  public void history(Component owner) {
    if (!api.signedIn()) {
      JOptionPane.showMessageDialog(owner, "Đăng nhập để xem lịch sử tìm kiếm.");
      return;
    }
    if (historyDialog != null) {
      historyDialog.toFront();
      return;
    }
    JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), "Lịch sử tìm kiếm");
    historyDialog = dialog;
    var tasks = new UiTasks();
    var message = Theme.plainLabel(" ");
    var model = new DefaultListModel<String>();
    var list = new JList<>(model);
    var renderer = new DefaultListCellRenderer();
    renderer.putClientProperty("html.disable", true);
    list.setCellRenderer(renderer);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.getAccessibleContext().setAccessibleName("Lịch sử tìm kiếm riêng của tài khoản");
    JPanel body = new JPanel(new BorderLayout(8, 8));
    body.add(new JScrollPane(list));
    body.add(message, BorderLayout.NORTH);
    Runnable reload =
        () ->
            tasks.run(
                body,
                message,
                api.get("/me/search-history"),
                reply -> {
                  model.clear();
                  for (var row : reply.body()) model.addElement(row.path("query").asText());
                });
    JPanel buttons = new JPanel(new FlowLayout());
    JButton choose = new JButton("Tìm lại"),
        delete = new JButton("Xóa mục"),
        clear = new JButton("Xóa tất cả");
    choose.addActionListener(
        e -> {
          if (list.getSelectedValue() != null) {
            query.setText(list.getSelectedValue());
            dialog.dispose();
            search.run();
          }
        });
    delete.addActionListener(
        e -> {
          if (list.getSelectedValue() != null)
            tasks.run(
                body,
                message,
                api.request(
                    "DELETE",
                    "/me/search-history?query=" + encode(list.getSelectedValue()),
                    null,
                    null),
                reply -> reload.run());
        });
    clear.addActionListener(
        e -> {
          if (JOptionPane.showConfirmDialog(
                  dialog,
                  "Xóa toàn bộ lịch sử tìm kiếm của bạn?",
                  "Xác nhận",
                  JOptionPane.OK_CANCEL_OPTION)
              == JOptionPane.OK_OPTION)
            tasks.run(
                body,
                message,
                api.request("DELETE", "/me/search-history", null, null),
                reply -> reload.run());
        });
    buttons.add(choose);
    buttons.add(delete);
    buttons.add(clear);
    body.add(buttons, BorderLayout.SOUTH);
    dialog.add(body);
    dialog.setSize(550, 420);
    dialog.setLocationRelativeTo(owner);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog
        .getRootPane()
        .registerKeyboardAction(
            e -> dialog.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            tasks.close();
            if (historyDialog == dialog) historyDialog = null;
          }
        });
    reload.run();
    dialog.setVisible(true);
  }

  private void dismiss() {
    generation++;
    delay.stop();
    suggestions.setVisible(false);
    if (pending != null) pending.cancel(true);
  }

  @Override
  public void close() {
    dismiss();
    if (historyDialog != null) historyDialog.dispose();
  }
}
