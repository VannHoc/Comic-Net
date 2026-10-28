package com.comicnet.library.dto;

import com.comicnet.catalog.api.CatalogDtos.ComicSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class LibraryDtos {
  private LibraryDtos() {}

  public record CollectionView(String id, String name, boolean isSystem, String systemKey) {}

  public record CollectionInput(@NotBlank @Size(max = 100) String name) {}

  public record CollectionItem(
      ComicSummary comic,
      @JsonInclude(JsonInclude.Include.NON_NULL) String readingStatus,
      @JsonInclude(JsonInclude.Include.NON_NULL) Boolean isFavorite,
      Instant addedAt) {}

  public record Bookmark(
      String id,
      String chapterId,
      String versionId,
      String pageId,
      BigDecimal relativeOffset,
      String label,
      String anchorStatus) {}

  public record BookmarkCreate(
      @NotBlank String chapterId,
      @NotBlank String versionId,
      @NotBlank String pageId,
      @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal relativeOffset,
      @Size(min = 1, max = 200) String label) {}

  public record BookmarkPatch(@NotBlank @Size(max = 200) String label) {}
}
