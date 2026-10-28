package com.comicnet.desktop.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Figma primary color/radius, adapted to native focus, keyboard and disabled states. */
public final class PrimaryButton extends JButton {
  public PrimaryButton(String text) {
    super(text);
    setFont(Theme.label(15));
    setForeground(Color.WHITE);
    setOpaque(false);
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setRolloverEnabled(true);
    setBorder(new EmptyBorder(12, 20, 12, 20));
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D canvas = (Graphics2D) graphics.create();
    try {
      canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Color fill =
          !isEnabled()
              ? new Color(0xDDDDE7)
              : getModel().isPressed()
                  ? Theme.ACTION.darker()
              : getModel().isRollover() ? Theme.ACTION.brighter() : Theme.ACTION;
      canvas.setColor(fill);
      canvas.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 20, 20);
      if (hasFocus()) {
        canvas.setColor(Theme.TEXT);
        canvas.setStroke(new BasicStroke(2));
        canvas.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 18, 18);
      }
    } finally {
      canvas.dispose();
    }
    super.paintComponent(graphics);
  }
}
