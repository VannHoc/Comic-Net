package com.comicnet.desktop.ui;

import com.comicnet.desktop.client.api.ApiClient;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.*;

/** Each mounted view owns one cancellable request; stale results never touch the view. */
public final class UiTasks implements AutoCloseable {
  private long generation;
  private CompletableFuture<?> pending;
  private JComponent busyView;

  public boolean busy() {
    return pending != null;
  }

  public <T> void run(
      JComponent view, JLabel message, CompletableFuture<T> request, Consumer<T> success) {
    run(
        view,
        message,
        request,
        success,
        error -> {
          Throwable cause = ApiClient.unwrap(error);
          message.setText(
              cause instanceof ApiClient.ApiFailure failure
                  ? failure.getMessage() + " · " + failure.requestId()
                  : "Không thể kết nối. Hãy kiểm tra backend rồi thử lại.");
        });
  }

  public <T> void run(
      JComponent view,
      JLabel message,
      CompletableFuture<T> request,
      Consumer<T> success,
      Consumer<Throwable> failure) {
    if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("EDT required.");
    long expected = ++generation;
    if (pending != null) pending.cancel(true);
    pending = request;
    busyView = view;
    view.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    message.setText("Đang xử lý…");
    request.whenComplete(
        (result, error) ->
            SwingUtilities.invokeLater(
                () -> {
                  if (generation != expected) return;
                  pending = null;
                  busyView = null;
                  view.setCursor(Cursor.getDefaultCursor());
                  if (error == null) {
                    message.setText(" ");
                    success.accept(result);
                  } else failure.accept(ApiClient.unwrap(error));
                }));
  }

  @Override
  public void close() {
    generation++;
    if (pending != null) pending.cancel(true);
    pending = null;
    if (busyView != null) busyView.setCursor(Cursor.getDefaultCursor());
    busyView = null;
  }
}
