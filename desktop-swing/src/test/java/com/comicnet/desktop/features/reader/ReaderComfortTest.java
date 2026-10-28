package com.comicnet.desktop.features.reader;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class ReaderComfortTest {
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
