package com.comicnet.reader.dto;

import com.comicnet.common.web.PageResult.PageInfo;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReadingDtos {
  private ReadingDtos() {}

  public record ChapterContext(
      String id,
      String comicId,
      String translationId,
      String displayNumber,
      String title,
      String comicTitle,
      String accessPolicy) {}

  public record Progress(
      String chapterId,
      String versionId,
      String pageId,
      BigDecimal relativeOffset,
      boolean completed,
      long revision,
      int historyGeneration,
      Instant lastReadAt,
      String anchorStatus) {}

  public record ProgressPut(
      @NotBlank String versionId,
      @NotBlank String pageId,
      @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal relativeOffset,
      @NotNull Boolean completed,
      @NotNull @Min(0) Long expectedRevision,
      @NotBlank String mutationId,
      @NotNull @Min(0) Integer historyGeneration) {}

  public record History(List<Progress> items, PageInfo page, int historyGeneration) {}

  public record HistoryCleared(int historyGeneration) {}

  public record Preference(
      @NotBlank @Pattern(regexp = "SCROLL|PAGED") String mode,
      @NotBlank @Pattern(regexp = "LTR|RTL") String direction,
      @NotBlank @Pattern(regexp = "WIDTH|HEIGHT") String fit) {}
}
