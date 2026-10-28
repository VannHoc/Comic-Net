package com.comicnet.storage.api;

import java.io.InputStream;
import java.nio.file.Path;

/** Server-generated staging keys only; never exposed through reader/catalog DTOs. */
public interface StagingStore {
  void put(String key, InputStream stream, long size);

  void download(String key, Path target);

  boolean exists(String key);

  void remove(String key);
}
