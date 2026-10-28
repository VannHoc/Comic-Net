package com.comicnet.recommendation.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class RatingDtos {
  private RatingDtos() {}

  public record RatingInput(@Min(1) @Max(5) int score, @Size(max = 4000) String review) {}

  public record RatingView(
      String comicId,
      String userId,
      String displayName,
      int score,
      String review,
      Instant updatedAt) {}

  public record RatingSummary(long count, BigDecimal average) {}
}
