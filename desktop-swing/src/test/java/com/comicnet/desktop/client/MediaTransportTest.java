package com.comicnet.desktop.client;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.desktop.client.http.MediaTransport;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MediaTransportTest {
  @Test
  void mediaUsesNoSessionHeadersAndDecodesCompatibleImages() throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "PNG", bytes);
    AtomicBoolean clean = new AtomicBoolean();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/image",
        exchange -> {
          clean.set(
              exchange.getRequestHeaders().getFirst("Authorization") == null
                  && exchange.getRequestHeaders().getFirst("Cookie") == null);
          exchange.getResponseHeaders().set("Content-Type", "image/png");
          exchange.sendResponseHeaders(200, bytes.size());
          exchange.getResponseBody().write(bytes.toByteArray());
          exchange.close();
        });
    server.start();
    try (var media = new MediaTransport(Set.of("127.0.0.1"))) {
      var image =
          media
              .image(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/image"), 20)
              .get(5, TimeUnit.SECONDS);
      assertEquals(20, image.getWidth());
      assertEquals(30, image.getHeight());
      assertTrue(clean.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void mediaRejectsUnknownHostsAndMimeSpoofing() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/image",
        exchange -> {
          exchange.getResponseHeaders().set("Content-Type", "image/png");
          byte[] bytes = "<html>not an image</html>".getBytes();
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try (var media = new MediaTransport(Set.of("127.0.0.1"))) {
      assertThrows(
          IllegalArgumentException.class,
          () -> media.image(URI.create("https://unapproved.invalid/image"), 20));
      assertThrows(
          java.util.concurrent.ExecutionException.class,
          () ->
              media
                  .image(
                      URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/image"),
                      20)
                  .get(5, TimeUnit.SECONDS));
    } finally {
      server.stop(0);
    }
  }
}
