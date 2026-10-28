package com.comicnet.desktop.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class NavigationButton extends JButton {
  NavigationButton(String text) {
    super(text);
    setContentAreaFilled(false);
    setOpaque(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setRolloverEnabled(true);
    setHorizontalAlignment(LEFT);
    setBorder(new EmptyBorder(14, 18, 14, 18));
    setFont(Theme.label(15));
    setForeground(Theme.TEXT);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D canvas = (Graphics2D) graphics.create();
    try {
      canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      canvas.setColor(
          isSelected()
              ? new Color(0xFFE5DF)
              : getModel().isRollover() ? Theme.ELEVATED : Theme.SURFACE);
      canvas.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
      if (isSelected()) {
        canvas.setColor(Theme.ARTWORK_TEAL);
        canvas.fillRoundRect(0, 12, 4, Math.max(8, getHeight() - 24), 4, 4);
      }
      if (hasFocus()) {
        canvas.setColor(Theme.ARTWORK_TEAL);
        canvas.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
      }
    } finally {
      canvas.dispose();
    }
    super.paintComponent(graphics);
  }
}
