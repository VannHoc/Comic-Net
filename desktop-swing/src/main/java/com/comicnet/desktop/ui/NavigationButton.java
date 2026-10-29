package com.comicnet.desktop.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class NavigationButton extends JButton {
  private boolean hasDot = false;

  NavigationButton(String text, Icon icon) {
    super(text, icon);
    init();
  }

  NavigationButton(String text) {
    super(text);
    init();
  }

  private void init() {
    setContentAreaFilled(false);
    setOpaque(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setRolloverEnabled(true);
    setHorizontalAlignment(LEFT);
    setIconTextGap(14);
    setBorder(new EmptyBorder(10, 16, 10, 16));
    setFont(Theme.label(14));
    setForeground(Theme.MUTED);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  void setHasDot(boolean hasDot) {
    this.hasDot = hasDot;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D canvas = (Graphics2D) graphics.create();
    try {
      canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int w = getWidth();
      int h = getHeight();

      if (isSelected()) {
        // Active Coral Tint
        canvas.setColor(Theme.ACTION_TINT);
        canvas.fillRoundRect(4, 2, w - 8, h - 4, 12, 12);
        // Active Left Indicator Bar
        canvas.setColor(Theme.ACTION);
        canvas.fillRoundRect(4, (h - 22) / 2, 4, 22, 4, 4);
        setForeground(Theme.ACTION);
        setFont(Theme.brand(14));
      } else if (getModel().isRollover()) {
        canvas.setColor(Theme.ELEVATED);
        canvas.fillRoundRect(4, 2, w - 8, h - 4, 12, 12);
        setForeground(Theme.TEXT);
        setFont(Theme.label(14));
      } else {
        setForeground(Theme.MUTED);
        setFont(Theme.label(14));
      }

      if (hasFocus()) {
        canvas.setColor(new Color(0xF6, 0x5D, 0x4E, 60));
        canvas.drawRoundRect(4, 2, w - 9, h - 5, 12, 12);
      }

      // Red notification dot
      if (hasDot) {
        canvas.setColor(Theme.ACTION);
        canvas.fillOval(w - 24, h / 2 - 4, 8, 8);
      }
    } finally {
      canvas.dispose();
    }
    super.paintComponent(graphics);
  }
}
