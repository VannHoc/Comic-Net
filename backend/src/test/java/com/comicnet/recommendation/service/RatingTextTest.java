package com.comicnet.recommendation.service;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.common.web.DomainException;
import org.junit.jupiter.api.Test;

class RatingTextTest {
  @Test
  void acceptsUnicodePlainTextAndOptionalReviews() {
    assertThat(RatingService.plainReview("  Truyện hay 😊\nNét vẽ đẹp.  "))
        .isEqualTo("Truyện hay 😊\nNét vẽ đẹp.");
    assertThat(RatingService.plainReview(" \n ")).isNull();
  }

  @Test
  void rejectsMarkupControlsAndOversizedReviews() {
    for (String text : new String[] {"<script>alert(1)</script>", "abc\u0000def", "x".repeat(4001)})
      assertThatThrownBy(() -> RatingService.plainReview(text)).isInstanceOf(DomainException.class);
  }
}
