package com.comicnet.storage.service;

import com.comicnet.storage.api.StagingStore;
import java.io.InputStream;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class S3StagingStore implements StagingStore {
  private final S3Gateway objects;

  public S3StagingStore(S3Gateway objects) {
    this.objects = objects;
  }

  private void check(String key) {
    if (key == null || !key.matches("staging/[a-zA-Z0-9-]{36}"))
      throw new IllegalArgumentException("Invalid server staging key.");
  }

  public void put(String key, InputStream stream, long size) {
    check(key);
    objects.putStream(key, stream, size);
  }

  public void download(String key, Path target) {
    check(key);
    objects.download(key, target);
  }

  public boolean exists(String key) {
    check(key);
    return objects.exists(key);
  }

  public void remove(String key) {
    check(key);
    objects.delete(key);
  }
}
