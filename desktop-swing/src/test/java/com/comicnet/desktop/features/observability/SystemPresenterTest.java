package com.comicnet.desktop.features.observability;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.api.ProbeResult;
import com.comicnet.desktop.client.api.SystemApi;
import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.config.DesktopConfig;
import com.comicnet.desktop.session.ClientSession;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SystemPresenterTest {
  @Test
  void edtRemainsResponsiveAndCancelledCompletionCannotOverwriteView() throws Exception {
    var release = new CountDownLatch(1);
    var received = new CountDownLatch(1);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor();
        var session = new ClientSession();
        var transport = new ApiTransport(session)) {
      server.setExecutor(executor);
      server.createContext(
          "/",
          exchange -> {
            received.countDown();
            try {
              release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
              Thread.currentThread().interrupt();
            }
            String body =
                exchange.getRequestURI().getPath().endsWith("ping")
                    ? "{\"status\":\"UP\",\"service\":\"comic-net-backend\"}"
                    : "{\"status\":\"UP\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            try {
              exchange.sendResponseHeaders(200, bytes.length);
              exchange.getResponseBody().write(bytes);
            } finally {
              exchange.close();
            }
          });
      server.start();
      var completed = new AtomicInteger();
      var cancelled = new AtomicInteger();
      var view =
          new SystemPresenter.View() {
            public void loading() {
              assertTrue(SwingUtilities.isEventDispatchThread());
            }

            public void completed(ProbeResult result) {
              completed.incrementAndGet();
            }

            public void failed() {
              completed.incrementAndGet();
            }

            public void cancelled() {
              assertTrue(SwingUtilities.isEventDispatchThread());
              cancelled.incrementAndGet();
            }
          };
      var api =
          new SystemApi(
              new DesktopConfig(
                  URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1")),
              transport);
      var presenter = new SystemPresenter(api, view);
      SwingUtilities.invokeAndWait(presenter::refresh);
      assertTrue(received.await(2, TimeUnit.SECONDS));
      var heartbeat = new CountDownLatch(1);
      SwingUtilities.invokeLater(heartbeat::countDown);
      assertTrue(heartbeat.await(1, TimeUnit.SECONDS), "Network I/O blocked EDT");
      SwingUtilities.invokeAndWait(presenter::cancel);
      release.countDown();
      SwingUtilities.invokeAndWait(presenter::close);
      SwingUtilities.invokeAndWait(() -> {});
      assertEquals(1, cancelled.get());
      assertEquals(0, completed.get());
      assertThrows(IllegalStateException.class, presenter::refresh);
    } finally {
      release.countDown();
      server.stop(0);
    }
  }
}
