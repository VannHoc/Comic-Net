package com.comicnet.reader.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.Etags;
import com.comicnet.reader.dto.ReadingDtos.*;
import com.comicnet.reader.service.ProgressService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class ReadingStateController {
  private final ProgressService reading;

  public ReadingStateController(ProgressService reading) {
    this.reading = reading;
  }

  @GetMapping("/progress/{chapter}")
  public Progress progress(@AuthenticationPrincipal Identity actor, @PathVariable long chapter) {
    return reading.get(actor, chapter);
  }

  @PutMapping("/progress/{chapter}")
  public Progress progress(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long chapter,
      @Valid @RequestBody ProgressPut body) {
    return reading.put(actor, chapter, body);
  }

  @GetMapping("/history")
  public History history(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return reading.history(actor, page, size);
  }

  @DeleteMapping("/history")
  public HistoryCleared clear(@AuthenticationPrincipal Identity actor) {
    return reading.clear(actor);
  }

  @GetMapping("/reading-preferences")
  public ResponseEntity<Preference> preference(@AuthenticationPrincipal Identity actor) {
    var result = reading.preference(actor);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(reading.preferenceToken(actor)))
        .body(result);
  }

  @PutMapping("/reading-preferences")
  public ResponseEntity<Preference> preference(
      @AuthenticationPrincipal Identity actor,
      @Valid @RequestBody Preference body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    var result = reading.preference(actor, body, etag);
    return ResponseEntity.ok().eTag(Etags.of(reading.preferenceToken(actor))).body(result);
  }
}
