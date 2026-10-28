package com.comicnet.desktop.session;

import static org.junit.jupiter.api.Assertions.*;

import java.net.HttpCookie;
import java.net.URI;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class RememberedSessionTest {
  @TempDir java.nio.file.Path folder;

  @Test
  void refreshCookieIsProtectedForCurrentWindowsUserAndCanBeRestored() throws Exception {
    URI base = URI.create("http://localhost:8080/api/v1");
    var file = folder.resolve("session.dat");
    var original = new ClientSession();
    var cookie = new HttpCookie("comic_refresh", "integration-only-secret");
    cookie.setPath("/api/v1/auth");
    original.cookieManager().getCookieStore().add(base, cookie);
    var remembered = new RememberedSession(base, file);
    assertTrue(remembered.save(original));
    assertFalse(new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1)
        .contains("integration-only-secret"));
    var restored = new ClientSession();
    assertTrue(new RememberedSession(base, file).restore(restored));
    assertTrue(restored.cookieManager().getCookieStore().getCookies().stream()
        .anyMatch(item -> item.getName().equals("comic_refresh")
            && item.getValue().equals("integration-only-secret")));
    remembered.forget();
    assertFalse(Files.exists(file));
  }
}
