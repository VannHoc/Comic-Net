package com.comicnet.analytics.controller;

import com.comicnet.analytics.dto.AnalyticsDtos.*;
import com.comicnet.analytics.service.AnalyticsService;
import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.PageResult;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {
  private final AnalyticsService analytics;

  public AnalyticsController(AnalyticsService analytics) {
    this.analytics = analytics;
  }

  @PostMapping("/comics/{comic}/views")
  public ViewResult record(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long comic,
      @Valid @RequestBody ViewInput input) {
    return analytics.record(actor, comic, input);
  }

  @PostMapping("/chapters/{chapter}/completion")
  public CompletionResult complete(
      @AuthenticationPrincipal Identity actor, @PathVariable long chapter) {
    return analytics.complete(actor, chapter);
  }

  @GetMapping("/rankings")
  public PageResult<RankingEntry> ranking(
      @RequestParam(defaultValue = "WEEKLY") String period,
      @RequestParam(defaultValue = "20") int size) {
    return analytics.ranking(period, size);
  }

  @GetMapping("/comics/{comic}/stats")
  public ComicStats stats(@AuthenticationPrincipal Identity actor, @PathVariable long comic) {
    return analytics.stats(actor, comic);
  }

  @GetMapping("/chapters/{id}/stats")
  public ChapterStats chapterStats(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    return analytics.chapterStats(actor, id);
  }
}
