package com.comicnet.desktop.features.recommendation;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import javax.swing.*;

/** Real rating/review editor. Network work remains in ApiClient's asynchronous transport. */
public final class RatingDialog extends JDialog {
  private final ApiClient api;
  private final String comic;
  private final JPanel body = new JPanel(new BorderLayout(12, 12));
  private final JLabel summary = Theme.plainLabel("Đang tải đánh giá…");
  private final JLabel message = Theme.plainLabel(" ");
  private final JSpinner score = new JSpinner(new SpinnerNumberModel(5, 1, 5, 1));
  private final JTextArea review = new JTextArea(4, 48);
  private final UiTasks totals = new UiTasks(), mine = new UiTasks(), actions = new UiTasks();
  private final RemoteTable reviews;
  private final JButton save = new JButton("Lưu đánh giá"),
      delete = new JButton("Xóa đánh giá của tôi");
  private ApiClient.Reply current;
  private AutoCloseable sessionListener;
  private boolean mineLoaded;

  public RatingDialog(Component owner, ApiClient api, String comic) {
    super(
        SwingUtilities.getWindowAncestor(owner),
        "Đánh giá truyện",
        Dialog.ModalityType.APPLICATION_MODAL);
    this.api = api;
    this.comic = comic;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
    body.add(summary, BorderLayout.NORTH);
    reviews =
        new RemoteTable(
            api,
            new String[] {"Người đọc", "Điểm / 5", "Nhận xét"},
            page -> "/comics/" + comic + "/reviews?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("displayName").asText(),
                  row.path("score").asInt(),
                  row.path("review").asText()
                });
    body.add(reviews, BorderLayout.CENTER);
    JPanel lower = new JPanel(new BorderLayout(8, 8));
    FormGrid form = new FormGrid();
    review.setLineWrap(true);
    review.setWrapStyleWord(true);
    review.getAccessibleContext().setAccessibleName("Nhận xét của bạn, tối đa 4000 ký tự");
    score.getAccessibleContext().setAccessibleName("Điểm từ 1 đến 5");
    form.field("Điểm (1–5)", score);
    form.field("Nhận xét (văn bản thuần)", new JScrollPane(review));
    lower.add(form, BorderLayout.CENTER);
    JPanel footer = new JPanel(new BorderLayout());
    footer.add(message, BorderLayout.NORTH);
    JPanel buttons = new JPanel(new WrapLayout(FlowLayout.RIGHT, 8, 5));
    JButton reload = new JButton("Tải lại"), close = new JButton("Đóng");
    for (var button : new JButton[] {reload, save, delete, close}) buttons.add(button);
    footer.add(buttons, BorderLayout.SOUTH);
    lower.add(footer, BorderLayout.SOUTH);
    body.add(lower, BorderLayout.SOUTH);
    setContentPane(body);
    save.addActionListener(e -> save());
    delete.addActionListener(e -> delete());
    reload.addActionListener(e -> reload());
    close.addActionListener(e -> dispose());
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
            totals.close();
            mine.close();
            actions.close();
            reviews.close();
            try {
              sessionListener.close();
            } catch (Exception ignored) {
            }
          }
        });
    setSize(880, 680);
    setMinimumSize(new Dimension(650, 500));
    setLocationRelativeTo(owner);
    reload();
  }

  private void reload() {
    if (actions.busy()) return;
    totals.run(
        body,
        summary,
        api.get("/comics/" + comic + "/rating-summary"),
        reply ->
            summary.setText(
                reply.body().path("average").asText()
                    + " / 5 · "
                    + reply.body().path("count").asLong()
                    + " lượt đánh giá"));
    reviews.first();
    mineLoaded = false;
    save.setEnabled(false);
    delete.setEnabled(false);
    if (!api.signedIn()) {
      message.setText("Đăng nhập để đánh giá truyện.");
      return;
    }
    mine.run(
        body,
        message,
        api.get("/me/ratings/" + comic),
        reply -> {
          current = reply;
          score.setValue(reply.body().path("score").asInt());
          review.setText(reply.body().path("review").asText(""));
          ready();
        },
        error -> {
          Throwable cause = ApiClient.unwrap(error);
          if (cause instanceof ApiClient.ApiFailure failure && failure.status() == 404) {
            current = null;
            score.setValue(5);
            review.setText("");
            ready();
          } else message.setText(cause.getMessage());
        });
  }

  private void ready() {
    mineLoaded = true;
    save.setEnabled(true);
    delete.setEnabled(current != null);
    message.setText(
        current == null ? "Bạn chưa đánh giá truyện này." : "Đang sửa đánh giá của bạn.");
  }

  private void save() {
    if (!mineLoaded || !api.signedIn() || actions.busy()) return;
    var headers = current == null ? Map.of("If-None-Match", "*") : Map.<String, String>of();
    actions.run(
        body,
        message,
        api.request(
            "PUT",
            "/comics/" + comic + "/rating",
            Map.of("score", score.getValue(), "review", review.getText()),
            current == null ? null : current.etag(),
            headers),
        reply -> reload());
  }

  private void delete() {
    if (current == null || actions.busy()) return;
    if (JOptionPane.showConfirmDialog(
            this, "Xóa đánh giá của bạn?", "Xác nhận", JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    actions.run(
        body,
        message,
        api.request("DELETE", "/comics/" + comic + "/rating", null, current.etag()),
        reply -> reload());
  }
}
