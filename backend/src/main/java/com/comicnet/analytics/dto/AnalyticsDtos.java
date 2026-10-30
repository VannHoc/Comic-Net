package com.comicnet.analytics.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {
  private AnalyticsDtos() {}

  public record ViewInput(@NotBlank String chapterId) {}

  public record ViewResult(boolean recorded, long totalViews) {}

  public record CompletionResult(boolean recorded, long completions) {}

  public record DailyPoint(LocalDate date, long views, long completions) {}

  public record ComicStats(
      String comicId,
      long totalViews,
      long totalCompletions,
      long viewers,
      BigDecimal rating,
      long ratings,
      List<DailyPoint> recent) {}

  public record ChapterStats(
      String chapterId,
      long readers,
      long completions,
      BigDecimal completionRate) {}

  public record RankingEntry(int rank, String comicId, String title, String coverUrl, long views, Double rating) {}
}
