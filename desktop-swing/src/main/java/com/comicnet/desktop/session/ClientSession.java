package com.comicnet.desktop.session;

import java.net.CookieManager;
import java.net.CookiePolicy;

/** One desktop session, never installed as the JVM global CookieHandler or persisted. */
public final class ClientSession implements AutoCloseable {
  private final CookieManager cookies =
      new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  private volatile String accessToken;
  private volatile long expiresAt;
  private long generation;
  private final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners =
      new java.util.concurrent.CopyOnWriteArrayList<>();

  public CookieManager cookieManager() {
    return cookies;
  }

  public String accessToken() {
    return accessToken;
  }

  public boolean expiresSoon() {
    return accessToken != null && System.currentTimeMillis() >= expiresAt - 30000;
  }

  public synchronized long generation() {
    return generation;
  }

  public synchronized boolean accept(long expectedGeneration, String token, int expiresIn) {
    if (expectedGeneration != generation) return false;
    boolean changed = accessToken == null;
    accessToken = token;
    expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
    if (changed) listeners.forEach(Runnable::run);
    return true;
  }

  public synchronized void clear() {
    boolean changed = accessToken != null;
    generation++;
    accessToken = null;
    expiresAt = 0;
    cookies.getCookieStore().removeAll();
    if (changed) listeners.forEach(Runnable::run);
  }

  public AutoCloseable onChanged(Runnable listener) {
    listeners.add(listener);
    return () -> listeners.remove(listener);
  }

  public synchronized void clearIfGeneration(long expected) {
    if (expected == generation) clear();
  }

  @Override
  public void close() {
    clear();
  }
}
