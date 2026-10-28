package com.comicnet.desktop.ui;

import java.awt.*;
import javax.swing.*;

public class SurfacePanel extends JPanel {
  private final int radius;

  public SurfacePanel(LayoutManager layout, int radius) {
    super(layout);
    this.radius = radius;
    setOpaque(false);
    setBackground(Theme.SURFACE);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D canvas = (Graphics2D) graphics.create();
    try {
      canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      canvas.setColor(getBackground());
      canvas.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2);
    } finally {
      canvas.dispose();
    }
    super.paintComponent(graphics);
  }
}
