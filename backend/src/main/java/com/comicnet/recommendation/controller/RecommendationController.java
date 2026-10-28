package com.comicnet.recommendation.controller;

import com.comicnet.catalog.api.CatalogDtos.ComicSummary;
import com.comicnet.common.web.PageResult;
import com.comicnet.recommendation.service.RecommendationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {
  private final RecommendationService recommendations;

  public RecommendationController(RecommendationService recommendations) {
    this.recommendations = recommendations;
  }

  @GetMapping("/recommendations")
  public PageResult<ComicSummary> discover(@RequestParam(defaultValue = "20") int size) {
    return recommendations.discover(size);
  }

  @GetMapping("/comics/{comic}/similar")
  public PageResult<ComicSummary> similar(
      @PathVariable long comic, @RequestParam(defaultValue = "20") int size) {
    return recommendations.similar(comic, size);
  }
}
