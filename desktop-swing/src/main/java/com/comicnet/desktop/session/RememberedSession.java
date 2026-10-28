package com.comicnet.desktop.session;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.IOException;
import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Windows-user-protected refresh cookie. The access token and password are never persisted. */
public final class RememberedSession {
  private final URI apiBase;
  private final Path file;
  private boolean enabled;

  public RememberedSession(URI apiBase) {
    this(apiBase, Path.of(System.getenv().getOrDefault("LOCALAPPDATA",
        System.getProperty("user.home")), "ComicNet", "session.dat"));
  }

  RememberedSession(URI apiBase, Path file) {
    this.apiBase = apiBase;
    this.file = file;
  }

  public boolean enabled() { return enabled; }

  public boolean restore(ClientSession session) {
    if (!windows() || !Files.isRegularFile(file)) return false;
    byte[] plain = null;
    try {
      plain = Crypt32Util.cryptUnprotectData(Files.readAllBytes(file));
      String value = new String(plain, StandardCharsets.UTF_8);
      String prefix = apiBase + "\n";
      if (!value.startsWith(prefix)) { forget(); return false; }
      String token = value.substring(prefix.length());
      if (token.isBlank() || token.contains("\n")) { forget(); return false; }
      HttpCookie cookie = new HttpCookie("comic_refresh", token);
      cookie.setPath("/api/v1/auth");
      cookie.setSecure("https".equalsIgnoreCase(apiBase.getScheme()));
      cookie.setHttpOnly(true);
      session.cookieManager().getCookieStore().add(apiBase, cookie);
      enabled = true;
      return true;
    } catch (Exception error) {
      forget();
      return false;
    } finally {
      if (plain != null) Arrays.fill(plain, (byte) 0);
    }
  }

  public boolean save(ClientSession session) {
    if (!windows()) return false;
    String token = session.cookieManager().getCookieStore().getCookies().stream()
        .filter(cookie -> "comic_refresh".equals(cookie.getName()) && !cookie.hasExpired())
        .map(HttpCookie::getValue).findFirst().orElse(null);
    if (token == null || token.isBlank()) return false;
    byte[] plain = (apiBase + "\n" + token).getBytes(StandardCharsets.UTF_8);
    try {
      byte[] encrypted = Crypt32Util.cryptProtectData(plain);
      Files.createDirectories(file.getParent());
      Path temporary = Files.createTempFile(file.getParent(), "session-", ".tmp");
      try {
        Files.write(temporary, encrypted);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
        Arrays.fill(encrypted, (byte) 0);
      }
      enabled = true;
      return true;
    } catch (Exception error) {
      return false;
    } finally {
      Arrays.fill(plain, (byte) 0);
    }
  }

  public void forget() {
    enabled = false;
    try { Files.deleteIfExists(file); } catch (IOException ignored) { /* Never expose credentials. */ }
  }

  private static boolean windows() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
  }
}
