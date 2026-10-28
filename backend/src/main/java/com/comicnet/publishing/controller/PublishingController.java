package com.comicnet.publishing.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.*;
import com.comicnet.publishing.api.PublishingDtos.*;
import com.comicnet.publishing.service.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
public class PublishingController {
  private final PublishingService publishing;
  private final UploadCoordinator uploads;

  public PublishingController(PublishingService publishing, UploadCoordinator uploads) {
    this.publishing = publishing;
    this.uploads = uploads;
  }

  @GetMapping("/translations/{id}/chapters")
  public PageResult<ChapterSummary> publicChapters(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return publishing.publicChapters(id, page, size);
  }

  @GetMapping("/translations/{id}/volumes")
  public PageResult<Volume> publicVolumes(
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return publishing.volumes(null, id, page, size, false);
  }

  @GetMapping("/publishing/translations/{id}/chapters")
  public PageResult<Chapter> chapters(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return publishing.chapters(actor, id, page, size);
  }

  @GetMapping("/publishing/translations/{id}/volumes")
  public PageResult<Volume> volumes(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return publishing.volumes(actor, id, page, size, true);
  }

  @GetMapping("/publishing/chapters/{id}")
  public ResponseEntity<Chapter> chapter(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    return chapterResponse(publishing.chapter(actor, id));
  }

  @GetMapping("/publishing/volumes/{id}")
  public ResponseEntity<Volume> volume(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    return volumeResponse(publishing.volume(actor, id));
  }

  @PostMapping("/translations/{id}/volumes")
  public ResponseEntity<Volume> createVolume(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody VolumeInput body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var volume = publishing.createVolume(actor, id, body, key);
    return ResponseEntity.created(URI.create("/api/v1/publishing/volumes/" + volume.id()))
        .eTag(Etags.of(publishing.volumeToken(Ids.parse(volume.id()))))
        .body(volume);
  }

  @PatchMapping("/volumes/{id}")
  public ResponseEntity<Volume> patchVolume(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestBody JsonNode body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return volumeResponse(publishing.patchVolume(actor, id, body, etag));
  }

  @PostMapping("/translations/{id}/chapters")
  public ResponseEntity<Chapter> createChapter(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody ChapterInput body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var chapter = publishing.createChapter(actor, id, body, key);
    return ResponseEntity.created(URI.create("/api/v1/publishing/chapters/" + chapter.id()))
        .eTag(Etags.of(publishing.chapterToken(Ids.parse(chapter.id()))))
        .body(chapter);
  }

  @PatchMapping("/chapters/{id}")
  public ResponseEntity<Chapter> patchChapter(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestBody JsonNode body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return chapterResponse(publishing.patchChapter(actor, id, body, etag));
  }

  @GetMapping("/publishing/chapters/{id}/versions")
  public PageResult<ChapterVersion> versions(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return publishing.versions(actor, id, page, size);
  }

  @PostMapping("/chapters/{id}/versions")
  public ResponseEntity<ChapterVersion> createVersion(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody VersionCreate body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var version = publishing.createVersion(actor, id, body, key);
    return ResponseEntity.created(URI.create("/api/v1/publishing/versions/" + version.id()))
        .eTag(Etags.of(publishing.versionToken(Ids.parse(version.id()))))
        .body(version);
  }

  @GetMapping("/publishing/versions/{id}")
  public ResponseEntity<ChapterVersion> version(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    return versionResponse(publishing.version(actor, id));
  }

  @PostMapping(
      value = "/publishing/versions/{id}/uploads",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UploadJob> upload(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestPart MultipartFile file,
      @RequestParam(required = false) String pageId,
      @RequestHeader(value = "If-Match", required = false) String etag,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return jobResponse(uploads.accept(actor, id, file, pageId, etag, key));
  }

  @GetMapping("/publishing/uploads/{id}")
  public UploadJob job(@AuthenticationPrincipal Identity actor, @PathVariable long id) {
    return uploads.get(actor, id);
  }

  @PostMapping("/publishing/uploads/{id}/retry")
  public ResponseEntity<UploadJob> retry(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return jobResponse(uploads.retry(actor, id, key));
  }

  @PutMapping("/publishing/versions/{id}/pages/order")
  public ResponseEntity<ChapterVersion> reorder(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody Reorder body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return versionResponse(publishing.reorder(actor, id, body, etag));
  }

  @DeleteMapping("/publishing/versions/{id}/pages/{page}")
  public ResponseEntity<Void> remove(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @PathVariable long page,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    publishing.removePage(actor, id, page, etag);
    return ResponseEntity.noContent().eTag(Etags.of(publishing.versionToken(id))).build();
  }

  @PostMapping("/publishing/versions/{id}/submit")
  public ResponseEntity<ChapterVersion> submit(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return versionResponse(publishing.submit(actor, id, etag));
  }

  @PostMapping("/publishing/versions/{id}/review")
  public ResponseEntity<ChapterVersion> review(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody Review body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return versionResponse(publishing.review(actor, id, body, etag));
  }

  @GetMapping("/publishing/review-queue")
  public PageResult<ChapterVersion> queue(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return publishing.reviewQueue(actor, page, size);
  }

  @GetMapping("/publishing/versions/{id}/preview")
  public ResponseEntity<Manifest> preview(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestParam(defaultValue = "JPEG") String displayFormat) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(publishing.preview(actor, id, displayFormat));
  }

  @PostMapping("/publishing/chapters/{id}/publish")
  public ResponseEntity<Chapter> publish(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody Publish body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return chapterResponse(publishing.publish(actor, id, body, etag));
  }

  @PostMapping("/publishing/chapters/{id}/unpublish")
  public ResponseEntity<Chapter> unpublish(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody Reason body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return chapterResponse(publishing.unpublish(actor, id, body, etag));
  }

  @PostMapping("/chapters/{id}/schedule")
  public ResponseEntity<ScheduleView> schedule(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody ScheduleInput body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(publishing.chapterToken(id)))
        .body(publishing.schedule(actor, id, body, etag));
  }

  @DeleteMapping("/chapters/{id}/schedule")
  public ResponseEntity<Void> cancelSchedule(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    publishing.cancelSchedule(actor, id, etag);
    return ResponseEntity.noContent().eTag(Etags.of(publishing.chapterToken(id))).build();
  }

  @PostMapping("/chapters/{id}/rollback")
  public ResponseEntity<Chapter> rollback(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody RollbackInput body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return chapterResponse(publishing.rollback(actor, id, body, etag));
  }

  private ResponseEntity<Volume> volumeResponse(Volume volume) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(publishing.volumeToken(Ids.parse(volume.id()))))
        .body(volume);
  }

  private ResponseEntity<Chapter> chapterResponse(Chapter chapter) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(publishing.chapterToken(Ids.parse(chapter.id()))))
        .body(chapter);
  }

  private ResponseEntity<ChapterVersion> versionResponse(ChapterVersion version) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(publishing.versionToken(Ids.parse(version.id()))))
        .body(version);
  }

  private ResponseEntity<UploadJob> jobResponse(UploadJob job) {
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/publishing/uploads/" + job.id()))
        .cacheControl(CacheControl.noStore())
        .body(job);
  }
}
