package com.comicnet.search.service;

import static org.junit.jupiter.api.Assertions.*;

import com.comicnet.common.web.DomainException;
import org.junit.jupiter.api.Test;

class SearchHistoryTest {
  @Test
  void normalizesUnicodeWithoutDestroyingVietnamese() {
    assertEquals("Truyện tranh", SearchHistoryService.normalize("  Truyện  tranh "));
    assertEquals("漫画", SearchHistoryService.normalize("漫画"));
  }

  @Test
  void rejectsEmptyOversizedAndControlInput() {
    for (String value : new String[] {"", " ", "a".repeat(201), "a\nb", "\u0000"})
      assertThrows(DomainException.class, () -> SearchHistoryService.normalize(value));
  }
}
