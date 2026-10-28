package com.comicnet.desktop.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class ThemeTest {
  @Test
  void usesTheSpecifiedLightPalette() {
    assertEquals(0xFFF8FAFC, Theme.BACKGROUND.getRGB());
    assertEquals(0xFF111827, Theme.TEXT.getRGB());
  }

  @Test
  void usesFontThatCanRenderVietnameseHeadings() {
    Theme.prepareFonts();
    assertEquals(-1, Theme.heading(18).canDisplayUpTo("MỚI CẬP NHẬT KHÁM PHÁ THỂ LOẠI TRUYỆN DÀNH CHO BẠN"));
  }
}
