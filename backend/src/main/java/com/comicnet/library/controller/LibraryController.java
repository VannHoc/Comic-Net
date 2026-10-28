package com.comicnet.library.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.catalog.api.CatalogDtos.ComicSummary;
import com.comicnet.common.web.*;
import com.comicnet.library.dto.LibraryDtos.*;
import com.comicnet.library.service.LibraryService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/me")
public class LibraryController {
  private final LibraryService library;

  public LibraryController(LibraryService library) {
    this.library = library;
  }

  @GetMapping("/collections")
  public PageResult<CollectionView> collections(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return library.collections(actor, page, size);
  }

  @PostMapping("/collections")
  public ResponseEntity<CollectionView> createCollection(
      @AuthenticationPrincipal Identity actor,
      @Valid @RequestBody CollectionInput body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var collection = library.createCollection(actor, body, key);
    return ResponseEntity.created(URI.create("/api/v1/me/collections/" + collection.id()))
        .eTag(Etags.of(library.collectionToken(actor, Ids.parse(collection.id()))))
        .body(collection);
  }

  @GetMapping("/collections/{id}")
  public ResponseEntity<CollectionView> collection(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    var collection = library.collection(actor, id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(library.collectionToken(actor, id)))
        .body(collection);
  }

  @PatchMapping("/collections/{id}")
  public ResponseEntity<CollectionView> rename(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody CollectionInput body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    var collection = library.rename(actor, id, body, etag);
    return ResponseEntity.ok().eTag(Etags.of(library.collectionToken(actor, id))).body(collection);
  }

  @DeleteMapping("/collections/{id}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    library.deleteCollection(actor, id, etag);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/collections/{id}/items")
  public PageResult<CollectionItem> items(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return library.items(actor, id, page, size);
  }

  @GetMapping("/collections/{id}/items/{comic}")
  public ResponseEntity<CollectionItem> item(
      @AuthenticationPrincipal Identity actor, @PathVariable long id, @PathVariable long comic) {
    var item = library.item(actor, id, comic);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(library.itemToken(actor, id, comic)))
        .body(item);
  }

  @PutMapping("/collections/{id}/items/{comic}")
  public ResponseEntity<CollectionItem> item(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @PathVariable long comic,
      @RequestBody JsonNode body,
      @RequestHeader(value = "If-Match", required = false) String etag,
      @RequestHeader(value = "If-None-Match", required = false) String noneMatch) {
    var item = library.putItem(actor, id, comic, body, etag, noneMatch);
    return ResponseEntity.ok().eTag(Etags.of(library.itemToken(actor, id, comic))).body(item);
  }

  @DeleteMapping("/collections/{id}/items/{comic}")
  public ResponseEntity<Void> removeItem(
      @AuthenticationPrincipal Identity actor, @PathVariable long id, @PathVariable long comic) {
    library.removeItem(actor, id, comic);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/follows")
  public PageResult<ComicSummary> follows(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return library.follows(actor, page, size);
  }

  @PutMapping("/follows/{comic}")
  public ResponseEntity<Void> follow(
      @AuthenticationPrincipal Identity actor, @PathVariable long comic) {
    library.follow(actor, comic);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/follows/{comic}")
  public ResponseEntity<Void> unfollow(
      @AuthenticationPrincipal Identity actor, @PathVariable long comic) {
    library.unfollow(actor, comic);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/bookmarks")
  public PageResult<Bookmark> bookmarks(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return library.bookmarks(actor, page, size);
  }

  @PostMapping("/bookmarks")
  public ResponseEntity<Bookmark> createBookmark(
      @AuthenticationPrincipal Identity actor,
      @Valid @RequestBody BookmarkCreate body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var bookmark = library.createBookmark(actor, body, key);
    return ResponseEntity.created(URI.create("/api/v1/me/bookmarks/" + bookmark.id()))
        .eTag(Etags.of(library.bookmarkToken(actor, Ids.parse(bookmark.id()))))
        .body(bookmark);
  }

  @GetMapping("/bookmarks/{id}")
  public ResponseEntity<Bookmark> bookmark(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    var bookmark = library.bookmark(actor, id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(library.bookmarkToken(actor, id)))
        .body(bookmark);
  }

  @PatchMapping("/bookmarks/{id}")
  public ResponseEntity<Bookmark> bookmark(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody BookmarkPatch body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    var bookmark = library.patchBookmark(actor, id, body, etag);
    return ResponseEntity.ok().eTag(Etags.of(library.bookmarkToken(actor, id))).body(bookmark);
  }

  @DeleteMapping("/bookmarks/{id}")
  public ResponseEntity<Void> deleteBookmark(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    library.deleteBookmark(actor, id, etag);
    return ResponseEntity.noContent().build();
  }
}
