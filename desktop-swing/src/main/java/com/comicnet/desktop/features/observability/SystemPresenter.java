package com.comicnet.desktop.features.observability;

import com.comicnet.desktop.client.api.ProbeResult;
import com.comicnet.desktop.client.api.SystemApi;
import javax.swing.SwingUtilities;

/** All mutable presenter state belongs to the EDT; HTTP and JSON parsing do not. */
public final class SystemPresenter implements AutoCloseable {
  public interface View {
    void loading();

    void completed(ProbeResult result);

    void failed();

    void cancelled();
  }

  private final SystemApi api;
  private final View view;
  private long generation;
  private boolean closed;
  private SystemApi.ProbeTask active;

  public SystemPresenter(SystemApi api, View view) {
    this.api = api;
    this.view = view;
  }

  public void refresh() {
    requireEdt();
    if (closed) return;
    long current = ++generation;
    if (active != null) active.cancel().run();
    view.loading();
    active = api.probe();
    active
        .completion()
        .whenComplete(
            (result, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (closed || current != generation) return;
                      active = null;
                      if (error == null) view.completed(result);
                      else view.failed();
                    }));
  }

  public void cancel() {
    requireEdt();
    generation++;
    if (active != null) active.cancel().run();
    active = null;
    if (!closed) view.cancelled();
  }

  @Override
  public void close() {
    requireEdt();
    closed = true;
    cancel();
  }

  private static void requireEdt() {
    if (!SwingUtilities.isEventDispatchThread())
      throw new IllegalStateException("Presenter requires the Swing EDT.");
  }
}
