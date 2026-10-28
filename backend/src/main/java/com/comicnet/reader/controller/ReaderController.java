package com.comicnet.reader.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.entitlement.api.ReadingAccess;
import com.comicnet.publishing.api.PublishingDtos.Manifest;
import com.comicnet.reader.service.ReaderService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chapters")
public class ReaderController {
  private final ReaderService reader;

  public ReaderController(ReaderService reader) {
    this.reader = reader;
  }

  @GetMapping("/{id}")
  public com.comicnet.reader.dto.ReadingDtos.ChapterContext context(@PathVariable long id) {
    return reader.context(id);
  }

  @GetMapping("/{id}/access")
  public ResponseEntity<ReadingAccess.Decision> access(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reader.access(actor, id));
  }

  @GetMapping("/{id}/manifest")
  public ResponseEntity<Manifest> manifest(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestParam(defaultValue = "JPEG") String displayFormat) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reader.manifest(actor, id, displayFormat));
  }
}
