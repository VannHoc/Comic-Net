package com.comicnet.recommendation.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.Etags;
import com.comicnet.common.web.PageResult;
import com.comicnet.recommendation.dto.RatingDtos.*;
import com.comicnet.recommendation.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class RatingController {
  private final RatingService ratings;

  public RatingController(RatingService ratings) {
    this.ratings = ratings;
  }

  @GetMapping("/comics/{comic}/rating-summary")
  public RatingSummary summary(@PathVariable long comic) {
    return ratings.summary(comic);
  }

  @GetMapping("/comics/{comic}/reviews")
  public PageResult<RatingView> reviews(
      @PathVariable long comic,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ratings.reviews(comic, page, size);
  }

  @GetMapping("/me/ratings/{comic}")
  public ResponseEntity<RatingView> mine(
      @AuthenticationPrincipal Identity actor, @PathVariable long comic) {
    var value = ratings.mine(actor, comic);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(ratings.token(actor, comic)))
        .body(value);
  }

  @PutMapping("/comics/{comic}/rating")
  public ResponseEntity<RatingView> put(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long comic,
      @Valid @RequestBody RatingInput body,
      @RequestHeader(value = "If-Match", required = false) String etag,
      @RequestHeader(value = "If-None-Match", required = false) String noneMatch) {
    var value = ratings.put(actor, comic, body, etag, noneMatch);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(ratings.token(actor, comic)))
        .body(value);
  }

  @DeleteMapping("/comics/{comic}/rating")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long comic,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    ratings.delete(actor, comic, etag);
    return ResponseEntity.noContent().build();
  }
}
