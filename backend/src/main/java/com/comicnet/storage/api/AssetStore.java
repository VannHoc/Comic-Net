package com.comicnet.storage.api;

import com.comicnet.identity.api.Identity;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface AssetStore {
  record AssetView(
      String id,
      String kind,
      String mimeType,
      long byteSize,
      int width,
      int height,
      String status) {}

  record DisplayAsset(String url, Instant expiresAt, int width, int height, String mimeType) {}

  AssetView uploadCatalog(
      Identity actor, String kind, byte[] bytes, String declaredMime, String key);

  AssetView storePage(long actor, byte[] bytes);

  AssetView get(long id);

  void requireOwned(long id, long owner, String kind);

  DisplayAsset display(long id, String format);

  Map<Long, DisplayAsset> displayMany(List<Long> ids, String format);
}
