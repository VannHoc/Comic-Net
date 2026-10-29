package com.comicnet.desktop.features.recommendation;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

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
  private final PrimaryButton save = new PrimaryButton("Lưu đánh giá");
  private final JButton delete = new JButton("Xóa đánh giá của tôi");
  private ApiClient.Reply current;
  private AutoCloseable sessionListener;
  private boolean mineLoaded;

  public RatingDialog(Component owner, ApiClient api, String comic) {
    super(
        SwingUtilities.getWindowAncestor(owner),
        "Đánh giá & Nhận xét truyện",
        Dialog.ModalityType.APPLICATION_MODAL);
    this.api = api;
    this.comic = comic;
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    body.setBackground(Theme.BACKGROUND);
    body.setBorder(new EmptyBorder(16, 20, 16, 20));

    // Summary badge top
    summary.setFont(Theme.heading(13));
    summary.setForeground(new Color(0xD9, 0x77, 0x06));
    summary.setBackground(new Color(0xFE, 0xF3, 0xC7));
    summary.setOpaque(true);
    summary.setBorder(new EmptyBorder(8, 14, 8, 14));
    summary.setIcon(UiIcons.star(14, new Color(0xF5, 0x9E, 0x0B)));
    body.add(summary, BorderLayout.NORTH);

    reviews =
        new RemoteTable(
            api,
            new String[] {"Người đọc", "Điểm", "Nhận xét"},
            page -> "/comics/" + comic + "/reviews?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("displayName").asText(),
                  row.path("score").asInt() + " / 5",
                  row.path("review").asText()
                });
    body.add(reviews, BorderLayout.CENTER);

    JPanel lower = new JPanel(new BorderLayout(0, 10));
    lower.setOpaque(true);
    lower.setBackground(Theme.SURFACE);
    lower.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(14, 16, 14, 16)));

    FormGrid form = new FormGrid();
    review.setLineWrap(true);
    review.setWrapStyleWord(true);
    review.setFont(Theme.label(13));
    score.setFont(Theme.heading(13));
    form.field("Điểm (1–5)", score);
    form.field("Nhận xét của bạn", new JScrollPane(review));
    lower.add(form, BorderLayout.CENTER);

    JPanel footer = new JPanel(new BorderLayout(0, 8));
    footer.setOpaque(false);
    message.setFont(Theme.label(12));
    message.setForeground(Theme.MUTED);
    footer.add(message, BorderLayout.NORTH);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    JButton reload = new JButton("Tải lại");
    reload.setFont(Theme.label(13));
    reload.setIcon(UiIcons.refresh(14, Theme.TEXT));
    JButton close = new JButton("Đóng");
    close.setFont(Theme.label(13));

    save.setIcon(UiIcons.check(14, Color.WHITE));
    delete.setFont(Theme.label(13));
    delete.setIcon(UiIcons.trash(14, Theme.DANGER));
    delete.setForeground(Theme.DANGER);

    buttons.add(reload);
    buttons.add(save);
    buttons.add(delete);
    buttons.add(close);
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
    setSize(900, 700);
    setMinimumSize(new Dimension(720, 540));
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
                "Điểm trung bình: "
                    + reply.body().path("average").asText("0.0")
                    + " / 5  ·  "
                    + reply.body().path("count").asLong(0)
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
          mineLoaded = true;
          save.setEnabled(true);
          delete.setEnabled(true);
          score.setValue(reply.body().path("score").asInt(5));
          review.setText(reply.body().path("review").asText(""));
          message.setText("Đã tải đánh giá của bạn. Bạn có thể cập nhật hoặc xóa.");
        },
        error -> {
          var unwrapped = ApiClient.unwrap(error);
          if (unwrapped instanceof ApiClient.ApiFailure failure && failure.status() == 404) {
            current = null;
            mineLoaded = true;
            save.setEnabled(true);
            delete.setEnabled(false);
            score.setValue(5);
            review.setText("");
            message.setText("Bạn chưa đánh giá truyện này.");
          } else message.setText("Không tải được đánh giá của bạn.");
        });
  }

  private void save() {
    if (!api.signedIn()) {
      message.setText("Đăng nhập để đánh giá truyện.");
      return;
    }
    if (!mineLoaded || actions.busy()) return;
    int value = (Integer) score.getValue();
    String text = review.getText().strip();
    Map<String, Object> payload =
        text.isBlank() ? Map.of("score", value) : Map.of("score", value, "review", text);
    Map<String, String> headers =
        current == null ? Map.of("If-None-Match", "*") : Map.of();
    actions.run(
        body,
        message,
        api.request(
            "PUT",
            "/comics/" + comic + "/rating",
            payload,
            current == null ? null : current.etag(),
            headers),
        reply -> {
          current = reply;
          delete.setEnabled(true);
          message.setText("Đã lưu đánh giá của bạn.");
          reload();
        });
  }

  private void delete() {
    if (!api.signedIn()) {
      message.setText("Đăng nhập để đánh giá truyện.");
      return;
    }
    if (!mineLoaded || current == null || actions.busy()) return;
    int choice =
        JOptionPane.showConfirmDialog(
            this,
            "Bạn có chắc muốn xóa đánh giá của mình?",
            "Xác nhận xóa đánh giá",
            JOptionPane.YES_NO_OPTION);
    if (choice != JOptionPane.YES_OPTION) return;
    actions.run(
        body,
        message,
        api.request("DELETE", "/comics/" + comic + "/rating", null, current.etag(), Map.of()),
        reply -> {
          current = null;
          delete.setEnabled(false);
          score.setValue(5);
          review.setText("");
          message.setText("Đã xóa đánh giá của bạn.");
          reload();
        });
  }
}
