package com.comicnet.desktop.config;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import org.junit.jupiter.api.Test;

class DesktopConfigTest {
  @Test
  void validatesLocalAndSecureRemoteEndpoints() {
    for (String value :
        new String[] {
          "http://localhost:8080/api/v1",
          "http://127.0.0.1:8080/api/v1",
          "http://[::1]:8080/api/v1",
          "https://comic.example/api/v1"
        }) {
      assertDoesNotThrow(() -> new DesktopConfig(URI.create(value)));
    }
    for (String value :
        new String[] {
          "http://comic.example/api/v1",
          "file:///api/v1",
          "http://localhost:8080/",
          "https://user:password@example.com/api/v1",
          "http://localhost:8080/api/v1?token=secret",
          "http://localhost:8080/api/v1#fragment"
        }) {
      assertThrows(IllegalArgumentException.class, () -> new DesktopConfig(URI.create(value)));
    }
  }
}
