package com.comicnet.desktop.features.reader;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class ReaderComfortTest {
  @Test
  void speedChangesScrollDistanceAndPauseStopsTimer() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var viewport = new JScrollPane();
          viewport.getVerticalScrollBar().setModel(new DefaultBoundedRangeModel(0, 100, 0, 1000));
          try (var comfort = new ReaderComfort(new JPanel(), viewport, () -> true)) {
            var auto = comfort.autoControl();
            comfort.speedControl().setValue(20);
            auto.doClick();
            comfort.tick(System.nanoTime() + 100_000_000L);
            int slow = viewport.getVerticalScrollBar().getValue();
            assertTrue(slow > 0);
            comfort.pause();
            assertFalse(auto.isSelected());
            viewport.getVerticalScrollBar().setValue(0);
            comfort.speedControl().setValue(200);
            auto.doClick();
            comfort.tick(System.nanoTime() + 100_000_000L);
            assertTrue(viewport.getVerticalScrollBar().getValue() > slow);
            comfort.pause();
            assertFalse(auto.isSelected());
          }
        });
  }

  @Test
  void scrollIsBoundedAndStopsAtEndOrWhenUnavailable() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var allowed = new AtomicBoolean(true);
          var viewport = new JScrollPane();
          viewport.getVerticalScrollBar().setModel(new DefaultBoundedRangeModel(0, 100, 0, 1000));
          try (var comfort = new ReaderComfort(new JPanel(), viewport, allowed::get)) {
            var controls = comfort.controls();
            var toggle = (JToggleButton) controls[1];
            toggle.doClick();
            comfort.tick(System.nanoTime() + 10_000_000_000L);
            assertTrue(
                viewport.getVerticalScrollBar().getValue() <= 4,
                "A delayed EDT must not jump ten seconds of reading");
            assertTrue(viewport.getVerticalScrollBar().getValue() > 0);
            allowed.set(false);
            int before = viewport.getVerticalScrollBar().getValue();
            comfort.tick(System.nanoTime());
            assertFalse(toggle.isSelected());
            assertEquals(before, viewport.getVerticalScrollBar().getValue());
            allowed.set(true);
            viewport.getVerticalScrollBar().setValue(899);
            toggle.doClick();
            comfort.tick(System.nanoTime() + 1_000_000_000L);
            assertEquals(900, viewport.getVerticalScrollBar().getValue());
            assertFalse(toggle.isSelected());
          }
        });
  }

  @Test
  void longChapterAutoScrollMaintainsSmoothTickingAndPausesAtBoundaries() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var viewport = new JScrollPane();
          // Extent 1000, max 50000 representing very long 50-page webtoon
          viewport.getVerticalScrollBar().setModel(new DefaultBoundedRangeModel(0, 1000, 0, 50000));
          try (var comfort = new ReaderComfort(new JPanel(), viewport, () -> true)) {
            var auto = comfort.autoControl();
            comfort.speedControl().setValue(100);
            auto.doClick();
            assertTrue(auto.isSelected());

            long start = System.nanoTime();
            comfort.tick(start);

            // Tick 1: 50ms later
            comfort.tick(start + 50_000_000L);
            int pos1 = viewport.getVerticalScrollBar().getValue();
            assertTrue(pos1 > 0);

            // Tick 2: 50ms later
            comfort.tick(start + 100_000_000L);
            int pos2 = viewport.getVerticalScrollBar().getValue();
            assertTrue(pos2 > pos1);

            // User manually pauses
            comfort.pause();
            assertFalse(auto.isSelected());
            int pausedPos = viewport.getVerticalScrollBar().getValue();
            comfort.tick(start + 200_000_000L);
            assertEquals(pausedPos, viewport.getVerticalScrollBar().getValue());

            // Resume near boundary
            viewport.getVerticalScrollBar().setValue(48999);
            auto.doClick();
            assertTrue(auto.isSelected());
            comfort.tick(System.nanoTime());
            comfort.tick(System.nanoTime() + 1_000_000_000L);
            // Reaches maximum (50000 - 1000 = 49000) and auto-pauses
            assertEquals(49000, viewport.getVerticalScrollBar().getValue());
            assertFalse(auto.isSelected());
          }
        });
  }

  @Test
  void brightnessOnlyShadesImageRectangleAndCloseStopsScrolling() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var comfort = new ReaderComfort(new JPanel(), new JScrollPane(), () -> true);
          var controls = comfort.controls();
          ((JSlider) controls[5]).setValue(50);
          var rendered = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
          var graphics = rendered.createGraphics();
          graphics.setColor(Color.WHITE);
          graphics.fillRect(0, 0, 40, 20);
          comfort.paintShade(graphics, 10, 20, 20);
          graphics.dispose();
          assertEquals(Color.WHITE.getRGB(), rendered.getRGB(0, 0));
          int shaded = new Color(rendered.getRGB(15, 10)).getRed();
          assertTrue(shaded >= 126 && shaded <= 129);
          var toggle = (JToggleButton) controls[1];
          toggle.doClick();
          comfort.close();
          assertFalse(toggle.isSelected());
        });
  }
}
