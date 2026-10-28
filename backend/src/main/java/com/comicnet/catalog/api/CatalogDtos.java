package com.comicnet.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class CatalogDtos {
  private CatalogDtos() {}

  public record AuthorCredit(
      @NotBlank String authorId,
      @NotBlank @Pattern(regexp = "AUTHOR|ARTIST") String contribution) {}

  public record ComicInput(
      @NotBlank @Size(max = 300) String title,
      @NotBlank @Size(max = 200) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
      @NotNull @Size(max = 10000) String description,
      @NotBlank @Pattern(regexp = "MANGA|MANHWA|MANHUA|COMIC") String type,
      @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
      @NotBlank @Pattern(regexp = "ONGOING|COMPLETED|HIATUS|DROPPED") String status,
      @NotBlank @Pattern(regexp = "ALL_AGE|13_PLUS|16_PLUS|18_PLUS") String ageRating,
      @NotNull @Size(max = 50) List<@NotBlank @Size(max = 300) String> alternativeTitles,
      @NotNull @Size(max = 50) List<@NotBlank String> genreIds,
      @NotNull @Size(max = 100) List<@NotBlank String> tagIds,
      @NotNull @Size(max = 50) List<@NotBlank String> warningIds,
      @NotNull @Size(max = 50) List<@Valid AuthorCredit> contributors,
      @NotNull @Size(max = 20) List<@NotBlank String> publisherIds,
      String coverAssetId,
      String bannerAssetId) {}

  public record ComicSummary(
      String id,
      String title,
      String slug,
      String type,
      String ageRating,
      String coverUrl,
      Instant updatedAt,
      Double rating,
      Long views) {}

  public record ComicDetail(
      String id,
      String title,
      String slug,
      String description,
      String type,
      String countryCode,
      String status,
      String ageRating,
      List<String> alternativeTitles,
      List<String> genreIds,
      List<String> tagIds,
      List<String> warningIds,
      List<AuthorCredit> contributors,
      List<String> publisherIds,
      String coverAssetId,
      String bannerAssetId,
      String visibility,
      String ownerUserId,
      Instant updatedAt,
      Double rating,
      Long views,
      String coverUrl,
      String bannerUrl) {}

  public record Translation(
      String id,
      String comicId,
      String teamId,
      String languageTag,
      String editionKey,
      String titleOverride,
      String visibility) {}

  public record TranslationCreate(
      @NotBlank String teamId,
      @NotBlank @Size(min = 2, max = 35) String languageTag,
      @NotBlank @Size(max = 50) String editionKey,
      @Size(min = 1, max = 300) String titleOverride) {}

  public record Lookup(String id, String code, String name) {}

  public record Metadata(
      List<Lookup> genres,
      List<Lookup> tags,
      List<Lookup> contentWarnings,
      List<Lookup> authors,
      List<Lookup> publishers) {}

  public record MetadataCreate(
      @NotBlank @Pattern(regexp = "GENRE|TAG|CONTENT_WARNING|AUTHOR|PUBLISHER") String kind,
      @NotBlank @Size(max = 50) String code,
      @NotBlank @Size(max = 200) String name) {}

  public record VisibilityPut(
      @NotBlank @Pattern(regexp = "PUBLIC|HIDDEN|ARCHIVED") String visibility,
      @NotBlank @Size(max = 2000) String reason) {}
}
