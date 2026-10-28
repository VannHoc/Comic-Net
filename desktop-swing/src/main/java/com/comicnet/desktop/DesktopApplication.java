package com.comicnet.desktop;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.api.SystemApi;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.client.http.MediaTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.features.observability.SystemPresenter;
import com.comicnet.desktop.session.ClientSession;
import com.comicnet.desktop.ui.MainFrame;
import com.comicnet.desktop.ui.Theme;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class DesktopApplication {
  private DesktopApplication() {}

  public static void main(String[] args) {
    final DesktopConfig config;
    try {
      config = DesktopConfig.fromEnvironment();
    } catch (IllegalArgumentException error) {
      SwingUtilities.invokeLater(
          () ->
              JOptionPane.showMessageDialog(
                  null,
                  "COMICNET_API_BASE_URL không hợp lệ. Dùng http://localhost:8080/api/v1 cho"
                      + " Development.",
                  "Comic Net — Cấu hình",
                  JOptionPane.ERROR_MESSAGE));
      return;
    }
    var session = new ClientSession();
    var transport = new ApiTransport(session);
    var api = new SystemApi(config, transport);
    var client = new ApiClient(config, transport, session);
    var media = new MediaTransport(config.mediaHosts());
    Theme.prepareFonts();
    SwingUtilities.invokeLater(
        () -> {
          try {
            Theme.install();
          } catch (Exception error) {
            /* Platform-independent Swing LAF remains available. */
          }
          var frame = new MainFrame(config, client, media);
          var presenter = new SystemPresenter(api, frame);
          frame.bind(presenter);
          frame.addWindowListener(
              new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                  presenter.close();
                  frame.close();
                  frame.setEnabled(false);
                  var logout =
                      client.signedIn() && !client.rememberEnabled()
                          ? client.logout()
                          : java.util.concurrent.CompletableFuture.<Void>completedFuture(null);
                  logout
                      .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                      .whenComplete(
                          (ignored, error) -> {
                            session.close();
                            transport.close();
                            media.close();
                            SwingUtilities.invokeLater(frame::dispose);
                          });
                }
              });
          frame.setVisible(true);
          presenter.refresh();
          client.restoreRemembered();
        });
  }
}
