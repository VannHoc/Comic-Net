package com.comicnet.storage.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.storage.api.AssetStore;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {
  private final AssetStore assets;

  public AssetController(AssetStore assets) {
    this.assets = assets;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AssetStore.AssetView> upload(
      @AuthenticationPrincipal Identity actor,
      @RequestPart MultipartFile file,
      @RequestParam String kind,
      @RequestHeader(value = "Idempotency-Key", required = false) String key)
      throws java.io.IOException {
    if (file.getSize() > 20L * 1024 * 1024)
      throw new com.comicnet.common.web.DomainException(
          413, "IMAGE_SIZE_LIMIT", "Ảnh vượt giới hạn 20 MiB.");
    var asset = assets.uploadCatalog(actor, kind, file.getBytes(), file.getContentType(), key);
    return ResponseEntity.created(URI.create("/api/v1/assets/" + asset.id())).body(asset);
  }
}
