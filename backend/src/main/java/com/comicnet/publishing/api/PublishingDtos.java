package com.comicnet.publishing.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class PublishingDtos {
  private PublishingDtos() {}

  public record Volume(String id, String translationId, String displayLabel, String sortOrder) {}

  public record VolumeInput(
      @NotBlank @Size(max = 100) String displayLabel,
      @NotBlank @Pattern(regexp = "[0-9]{1,8}(\\.[0-9]{1,4})?") String sortOrder) {}

  public record Chapter(
      String id,
      String translationId,
      String volumeId,
      String displayNumber,
      String title,
      String sortOrder,
      String accessPolicy,
      String visibility,
      String publishedVersionId,
      Instant publishedAt) {}

  public record ChapterSummary(
      String id,
      String displayNumber,
      String title,
      String sortOrder,
      int pageCount,
      String accessPolicy,
      Instant publishedAt) {}

  public record ChapterInput(
      String volumeId,
      @NotBlank @Size(max = 50) String displayNumber,
      @NotBlank @Size(max = 300) String title,
      @NotBlank @Pattern(regexp = "[0-9]{1,8}(\\.[0-9]{1,4})?") String sortOrder,
      @NotBlank @Pattern(regexp = "FREE|RESTRICTED") String accessPolicy) {}

  public record VersionPage(
      String pageId, int position, String assetId, String assetStatus, int width, int height) {}

  public record ChapterVersion(
      String id,
      String chapterId,
      int versionNumber,
      String state,
      List<VersionPage> pages,
      String createdBy,
      String submittedBy,
      String reviewedBy,
      String reviewReason) {}

  public record VersionCreate(String copyFromVersionId) {}

  public record Reorder(@NotEmpty @Size(max = 500) List<@NotBlank String> pageIds) {}

  public record Review(
      @NotBlank @Pattern(regexp = "APPROVE|REJECT|REQUEST_CHANGES|NEEDS_CHANGES") String decision,
      @Size(min = 1, max = 2000) String reason) {}

  public record Publish(@NotBlank String versionId) {}

  public record ScheduleInput(@NotBlank String versionId, @NotNull Instant publishAt) {}

  public record ScheduleView(
      String versionId, String chapterId, String state, Instant scheduledAt) {}

  public record RollbackInput(@NotBlank String versionId) {}

  public record Reason(@NotBlank @Size(max = 2000) String reason) {}

  public record UploadJob(
      String id,
      String chapterId,
      String versionId,
      String status,
      int attempts,
      String errorCode,
      Instant createdAt,
      Instant updatedAt) {}

  public record ManifestPage(
      String pageId,
      int position,
      String url,
      Instant expiresAt,
      int width,
      int height,
      String mimeType) {}

  public record Manifest(
      String chapterId,
      String versionId,
      List<ManifestPage> pages,
      String previousChapterId,
      String nextChapterId) {}
}
