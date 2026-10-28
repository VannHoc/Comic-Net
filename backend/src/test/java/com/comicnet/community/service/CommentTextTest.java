package com.comicnet.community.service;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.common.web.DomainException;
import org.junit.jupiter.api.Test;

class CommentTextTest {
  @Test
  void unicodeAndEmojiRemainPlainText() {
    assertThat(CommentText.clean("  Nội dung 😊\nDòng hai ", 4000))
        .isEqualTo("Nội dung 😊\nDòng hai");
  }

  @Test
  void rejectsExecutableMarkupControlsEmptyAndTooLong() {
    for (String value :
        new String[] {
          "<img src=x onerror=alert(1)>", "<html>markup", "a\u0000b", " ", "x".repeat(4001)
        })
      assertThatThrownBy(() -> CommentText.clean(value, 4000)).isInstanceOf(DomainException.class);
  }
}
