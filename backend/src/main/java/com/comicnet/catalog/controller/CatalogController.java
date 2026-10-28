package com.comicnet.catalog.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.catalog.api.CatalogDtos.*;
import com.comicnet.catalog.api.CatalogQueries.Filter;
import com.comicnet.catalog.service.CatalogService;
import com.comicnet.common.web.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final CatalogService catalog;

  public CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/comics")
  public PageResult<ComicSummary> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String genreId,
      @RequestParam(required = false) String tagId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "UPDATED_DESC") String sort) {
    return catalog.list(new Filter(null, type, genreId, tagId, status, sort), page, size);
  }

  @GetMapping("/comics/{id}")
  public ResponseEntity<ComicDetail> comic(@PathVariable long id) {
    var result = catalog.publicDetail(id);
    return ResponseEntity.ok().eTag(Etags.of(catalog.version(id))).body(result);
  }

  @GetMapping("/comics/by-slug/{slug}")
  public ResponseEntity<ComicDetail> slug(@PathVariable String slug) {
    var result = catalog.bySlug(slug);
    return ResponseEntity.ok().eTag(Etags.of(catalog.version(Ids.parse(result.id())))).body(result);
  }

  @GetMapping("/publishing/comics")
  public PageResult<ComicSummary> managed(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.managedList(actor, page, size);
  }

  @GetMapping("/publishing/comics/{id}")
  public ResponseEntity<ComicDetail> managed(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    var result = catalog.managedDetail(actor, id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(catalog.version(id)))
        .body(result);
  }

  @PostMapping("/comics")
  public ResponseEntity<ComicDetail> create(
      @AuthenticationPrincipal Identity actor,
      @Valid @RequestBody ComicInput body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var result = catalog.create(actor, body, key);
    return ResponseEntity.created(URI.create("/api/v1/comics/" + result.id()))
        .eTag(Etags.of(catalog.version(Ids.parse(result.id()))))
        .body(result);
  }

  @PatchMapping("/comics/{id}")
  public ResponseEntity<ComicDetail> patch(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestBody JsonNode body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    var result = catalog.patch(actor, id, body, etag);
    return ResponseEntity.ok().eTag(Etags.of(catalog.version(id))).body(result);
  }

  @DeleteMapping("/comics/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    catalog.softDelete(actor, id, etag);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/metadata")
  public Metadata metadata() {
    return catalog.metadata();
  }

  @PostMapping("/admin/metadata")
  public ResponseEntity<Lookup> metadata(
      @AuthenticationPrincipal Identity actor,
      @Valid @RequestBody MetadataCreate body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var result = catalog.createMetadata(actor, body, key);
    return ResponseEntity.created(URI.create("/api/v1/metadata#" + body.kind() + "-" + result.id()))
        .body(result);
  }

  @PutMapping("/admin/comics/{id}/visibility")
  public ResponseEntity<ComicDetail> visibility(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody VisibilityPut body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    var result = catalog.visibility(actor, id, body, etag);
    return ResponseEntity.ok().eTag(Etags.of(catalog.version(id))).body(result);
  }

  @GetMapping("/comics/{id}/translations")
  public PageResult<Translation> translations(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.translations(null, id, page, size, false);
  }

  @GetMapping("/publishing/comics/{id}/translations")
  public PageResult<Translation> translations(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalog.translations(actor, id, page, size, true);
  }

  @PostMapping("/comics/{id}/translations")
  public ResponseEntity<Translation> translation(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody TranslationCreate body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var result = catalog.createTranslation(actor, id, body, key);
    return ResponseEntity.created(URI.create("/api/v1/publishing/translations/" + result.id()))
        .eTag(Etags.of(catalog.translationVersion(Ids.parse(result.id()))))
        .body(result);
  }

  @GetMapping("/publishing/translations/{id}")
  public ResponseEntity<Translation> translation(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    var result = catalog.translation(actor, id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(catalog.translationVersion(id)))
        .body(result);
  }

  @PatchMapping("/translations/{id}")
  public ResponseEntity<Translation> translation(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestBody JsonNode body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    var result = catalog.patchTranslation(actor, id, body, etag);
    return ResponseEntity.ok().eTag(Etags.of(catalog.translationVersion(id))).body(result);
  }
}
