package com.comicnet.desktop.ui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.Icon;

/**
 * High-performance, DPI-independent vector icons rendered via Graphics2D.
 * Eliminates missing glyph boxes ([]) and external image dependencies.
 */
public final class UiIcons {
  private UiIcons() {}

  public static Icon brandLogo(int width, int height) {
    return new VectorIcon(width, height, (g2, w, h) -> {
      // Open manga book with coral gradient
      int pad = 2;
      int bw = w - pad * 2;
      int bh = h - pad * 2;
      int cx = w / 2;

      // Left page
      Path2D left = new Path2D.Float();
      left.moveTo(cx - 2, h - pad);
      left.curveTo(cx - bw * 0.3f, h - pad - 4, cx - bw * 0.45f, h - pad - 1, pad, h - pad);
      left.lineTo(pad, pad + 3);
      left.curveTo(cx - bw * 0.45f, pad + 1, cx - bw * 0.3f, pad - 2, cx - 2, pad + 2);
      left.closePath();

      // Right page
      Path2D right = new Path2D.Float();
      right.moveTo(cx + 2, h - pad);
      right.curveTo(cx + bw * 0.3f, h - pad - 4, cx + bw * 0.45f, h - pad - 1, w - pad, h - pad);
      right.lineTo(w - pad, pad + 3);
      right.curveTo(cx + bw * 0.45f, pad + 1, cx + bw * 0.3f, pad - 2, cx + 2, pad + 2);
      right.closePath();

      GradientPaint gradLeft = new GradientPaint(pad, pad, new Color(0xF6, 0x5D, 0x4E), cx, h, new Color(0xE8, 0x4A, 0x3D));
      g2.setPaint(gradLeft);
      g2.fill(left);

      GradientPaint gradRight = new GradientPaint(cx, pad, new Color(0xF8, 0x71, 0x64), w, h, new Color(0xF6, 0x5D, 0x4E));
      g2.setPaint(gradRight);
      g2.fill(right);

      // Book spine line
      g2.setColor(Color.WHITE);
      g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawLine(cx, pad + 3, cx, h - pad - 2);

      // Bookmark ribbon / page folds
      g2.drawLine(pad + 4, pad + 7, cx - 4, pad + 6);
      g2.drawLine(cx + 4, pad + 6, w - pad - 4, pad + 7);
    });
  }

  public static Icon home(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D p = new Path2D.Float();
      p.moveTo(w * 0.15f, h * 0.45f);
      p.lineTo(w * 0.5f, h * 0.15f);
      p.lineTo(w * 0.85f, h * 0.45f);
      p.lineTo(w * 0.85f, h * 0.85f);
      p.lineTo(w * 0.58f, h * 0.85f);
      p.lineTo(w * 0.58f, h * 0.58f);
      p.lineTo(w * 0.42f, h * 0.58f);
      p.lineTo(w * 0.42f, h * 0.85f);
      p.lineTo(w * 0.15f, h * 0.85f);
      p.closePath();
      g2.draw(p);
    });
  }

  public static Icon bookOpen(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      int cx = w / 2;
      Path2D left = new Path2D.Float();
      left.moveTo(cx, h * 0.3f);
      left.curveTo(w * 0.35f, h * 0.22f, w * 0.25f, h * 0.25f, w * 0.12f, h * 0.24f);
      left.lineTo(w * 0.12f, h * 0.76f);
      left.curveTo(w * 0.25f, h * 0.77f, w * 0.35f, h * 0.74f, cx, h * 0.82f);
      left.closePath();
      g2.draw(left);

      Path2D right = new Path2D.Float();
      right.moveTo(cx, h * 0.3f);
      right.curveTo(w * 0.65f, h * 0.22f, w * 0.75f, h * 0.25f, w * 0.88f, h * 0.24f);
      right.lineTo(w * 0.88f, h * 0.76f);
      right.curveTo(w * 0.75f, h * 0.77f, w * 0.65f, h * 0.74f, cx, h * 0.82f);
      right.closePath();
      g2.draw(right);
    });
  }

  public static Icon library(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // 3 books standing
      g2.drawRoundRect((int)(w * 0.15f), (int)(h * 0.2f), (int)(w * 0.2f), (int)(h * 0.65f), 3, 3);
      g2.drawRoundRect((int)(w * 0.4f), (int)(h * 0.14f), (int)(w * 0.2f), (int)(h * 0.71f), 3, 3);
      // tilted book
      Path2D tilted = new Path2D.Float();
      tilted.moveTo(w * 0.68f, h * 0.85f);
      tilted.lineTo(w * 0.84f, h * 0.3f);
      tilted.lineTo(w * 0.94f, h * 0.36f);
      tilted.lineTo(w * 0.78f, h * 0.88f);
      tilted.closePath();
      g2.draw(tilted);
    });
  }

  public static Icon wallet(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawRoundRect((int)(w * 0.12f), (int)(h * 0.25f), (int)(w * 0.76f), (int)(h * 0.55f), 6, 6);
      g2.drawLine((int)(w * 0.12f), (int)(h * 0.38f), (int)(w * 0.88f), (int)(h * 0.38f));
      // Pocket latch
      g2.drawRoundRect((int)(w * 0.62f), (int)(h * 0.44f), (int)(w * 0.26f), (int)(h * 0.22f), 4, 4);
      g2.fillOval((int)(w * 0.68f), (int)(h * 0.52f), 3, 3);
    });
  }

  public static Icon gift(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // Box
      g2.drawRoundRect((int)(w * 0.2f), (int)(h * 0.45f), (int)(w * 0.6f), (int)(h * 0.45f), 4, 4);
      // Lid
      g2.drawRoundRect((int)(w * 0.14f), (int)(h * 0.34f), (int)(w * 0.72f), (int)(h * 0.14f), 3, 3);
      // Ribbon vertical
      g2.drawLine((int)(w * 0.5f), (int)(h * 0.34f), (int)(w * 0.5f), (int)(h * 0.9f));
      // Bow loops
      Path2D bow = new Path2D.Float();
      bow.moveTo(w * 0.5f, h * 0.34f);
      bow.curveTo(w * 0.35f, h * 0.15f, w * 0.2f, h * 0.25f, w * 0.5f, h * 0.34f);
      bow.curveTo(w * 0.65f, h * 0.15f, w * 0.8f, h * 0.25f, w * 0.5f, h * 0.34f);
      g2.draw(bow);
    });
  }

  public static Icon barChart(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // 3 podium bars
      g2.drawRoundRect((int)(w * 0.15f), (int)(h * 0.5f), (int)(w * 0.2f), (int)(h * 0.38f), 3, 3);
      g2.drawRoundRect((int)(w * 0.40f), (int)(h * 0.25f), (int)(w * 0.2f), (int)(h * 0.63f), 3, 3);
      g2.drawRoundRect((int)(w * 0.65f), (int)(h * 0.4f), (int)(w * 0.2f), (int)(h * 0.48f), 3, 3);
    });
  }

  public static Icon bell(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D b = new Path2D.Float();
      b.moveTo(w * 0.5f, h * 0.18f);
      b.curveTo(w * 0.35f, h * 0.18f, w * 0.28f, h * 0.35f, w * 0.28f, h * 0.58f);
      b.lineTo(w * 0.18f, h * 0.72f);
      b.lineTo(w * 0.82f, h * 0.72f);
      b.lineTo(w * 0.72f, h * 0.58f);
      b.curveTo(w * 0.72f, h * 0.35f, w * 0.65f, h * 0.18f, w * 0.5f, h * 0.18f);
      g2.draw(b);
      // clapper
      g2.drawArc((int)(w * 0.42f), (int)(h * 0.72f), (int)(w * 0.16f), (int)(h * 0.14f), 0, -180);
    });
  }

  public static Icon user(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // Head
      g2.drawOval((int)(w * 0.32f), (int)(h * 0.16f), (int)(w * 0.36f), (int)(h * 0.36f));
      // Shoulders
      Path2D sh = new Path2D.Float();
      sh.moveTo(w * 0.16f, h * 0.82f);
      sh.curveTo(w * 0.2f, h * 0.62f, w * 0.8f, h * 0.62f, w * 0.84f, h * 0.82f);
      g2.draw(sh);
    });
  }

  public static Icon search(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      int r = (int)(w * 0.65f);
      g2.drawOval((int)(w * 0.15f), (int)(h * 0.15f), r, r);
      g2.drawLine((int)(w * 0.65f), (int)(h * 0.65f), (int)(w * 0.85f), (int)(h * 0.85f));
    });
  }

  public static Icon star(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      int cx = w / 2, cy = h / 2;
      double outerR = w * 0.45;
      double innerR = outerR * 0.42;
      Path2D s = new Path2D.Float();
      for (int i = 0; i < 10; i++) {
        double r = (i % 2 == 0) ? outerR : innerR;
        double angle = i * Math.PI / 5.0 - Math.PI / 2.0;
        float x = (float)(cx + r * Math.cos(angle));
        float y = (float)(cy + r * Math.sin(angle));
        if (i == 0) s.moveTo(x, y);
        else s.lineTo(x, y);
      }
      s.closePath();
      g2.fill(s);
    });
  }

  public static Icon eye(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D e = new Path2D.Float();
      e.moveTo(w * 0.12f, h * 0.5f);
      e.curveTo(w * 0.35f, h * 0.25f, w * 0.65f, h * 0.25f, w * 0.88f, h * 0.5f);
      e.curveTo(w * 0.65f, h * 0.75f, w * 0.35f, h * 0.75f, w * 0.12f, h * 0.5f);
      e.closePath();
      g2.draw(e);
      g2.fillOval((int)(w * 0.41f), (int)(h * 0.41f), (int)(w * 0.18f), (int)(h * 0.18f));
    });
  }

  public static Icon coin(int size) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      int s = Math.min(w, h) - 2;
      int x = (w - s) / 2;
      int y = (h - s) / 2;
      GradientPaint p = new GradientPaint(x, y, new Color(0xFDE047), x + s, y + s, new Color(0xF59E0B));
      g2.setPaint(p);
      g2.fillOval(x, y, s, s);

      g2.setColor(new Color(0xD97706));
      g2.setStroke(new BasicStroke(1.5f));
      g2.drawOval(x, y, s, s);

      g2.setColor(new Color(0xFEF08A));
      g2.setStroke(new BasicStroke(1f));
      g2.drawOval(x + 2, y + 2, s - 4, s - 4);

      g2.setColor(new Color(0x92400E));
      g2.setFont(Theme.heading(s >= 18 ? 9 : 8));
      FontMetrics fm = g2.getFontMetrics();
      String t = "X";
      int tw = fm.stringWidth(t);
      g2.drawString(t, x + (s - tw) / 2, y + (s + fm.getAscent() - 2) / 2);
    });
  }

  public static Icon trophy(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // Cup bowl
      Path2D cup = new Path2D.Float();
      cup.moveTo(w * 0.28f, h * 0.18f);
      cup.lineTo(w * 0.72f, h * 0.18f);
      cup.lineTo(w * 0.68f, h * 0.52f);
      cup.curveTo(w * 0.65f, h * 0.64f, w * 0.35f, h * 0.64f, w * 0.32f, h * 0.52f);
      cup.closePath();
      g2.draw(cup);
      // Handles
      g2.drawArc((int)(w * 0.16f), (int)(h * 0.22f), (int)(w * 0.18f), (int)(h * 0.24f), 90, 180);
      g2.drawArc((int)(w * 0.66f), (int)(h * 0.22f), (int)(w * 0.18f), (int)(h * 0.24f), 90, -180);
      // Stem & Base
      g2.drawLine((int)(w * 0.5f), (int)(h * 0.64f), (int)(w * 0.5f), (int)(h * 0.78f));
      g2.drawLine((int)(w * 0.32f), (int)(h * 0.78f), (int)(w * 0.68f), (int)(h * 0.78f));
    });
  }

  public static Icon feather(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D f = new Path2D.Float();
      f.moveTo(w * 0.85f, h * 0.15f);
      f.curveTo(w * 0.45f, h * 0.2f, w * 0.25f, h * 0.5f, w * 0.2f, h * 0.85f);
      f.lineTo(w * 0.35f, h * 0.8f);
      f.curveTo(w * 0.5f, h * 0.55f, w * 0.7f, h * 0.35f, w * 0.85f, h * 0.15f);
      g2.draw(f);
      g2.drawLine((int)(w * 0.85f), (int)(h * 0.15f), (int)(w * 0.35f), (int)(h * 0.65f));
    });
  }

  public static Icon shield(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D s = new Path2D.Float();
      s.moveTo(w * 0.5f, h * 0.15f);
      s.lineTo(w * 0.82f, h * 0.28f);
      s.lineTo(w * 0.82f, h * 0.55f);
      s.curveTo(w * 0.82f, h * 0.75f, w * 0.5f, h * 0.88f, w * 0.5f, h * 0.88f);
      s.curveTo(w * 0.5f, h * 0.88f, w * 0.18f, h * 0.75f, w * 0.18f, h * 0.55f);
      s.lineTo(w * 0.18f, h * 0.28f);
      s.closePath();
      g2.draw(s);
    });
  }

  public static Icon settings(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawOval((int)(w * 0.32f), (int)(h * 0.32f), (int)(w * 0.36f), (int)(h * 0.36f));
      // Outer spokes
      for (int i = 0; i < 6; i++) {
        double angle = i * Math.PI / 3.0;
        int x1 = (int)(w * 0.5f + w * 0.32f * Math.cos(angle));
        int y1 = (int)(h * 0.5f + h * 0.32f * Math.sin(angle));
        int x2 = (int)(w * 0.5f + w * 0.44f * Math.cos(angle));
        int y2 = (int)(h * 0.5f + h * 0.44f * Math.sin(angle));
        g2.drawLine(x1, y1, x2, y2);
      }
    });
  }

  public static Icon edit(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D p = new Path2D.Float();
      p.moveTo(w * 0.65f, h * 0.18f);
      p.lineTo(w * 0.82f, h * 0.35f);
      p.lineTo(w * 0.35f, h * 0.82f);
      p.lineTo(w * 0.18f, h * 0.82f);
      p.lineTo(w * 0.18f, h * 0.65f);
      p.closePath();
      g2.draw(p);
    });
  }

  public static Icon chevronDown(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D c = new Path2D.Float();
      c.moveTo(w * 0.25f, h * 0.38f);
      c.lineTo(w * 0.5f, h * 0.62f);
      c.lineTo(w * 0.75f, h * 0.38f);
      g2.draw(c);
    });
  }

  public static Icon chevronRight(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D c = new Path2D.Float();
      c.moveTo(w * 0.38f, h * 0.25f);
      c.lineTo(w * 0.62f, h * 0.5f);
      c.lineTo(w * 0.38f, h * 0.75f);
      g2.draw(c);
    });
  }

  public static Icon check(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      Path2D c = new Path2D.Float();
      c.moveTo(w * 0.2f, h * 0.52f);
      c.lineTo(w * 0.42f, h * 0.74f);
      c.lineTo(w * 0.8f, h * 0.28f);
      g2.draw(c);
    });
  }

  public static Icon crown(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      Path2D p = new Path2D.Float();
      p.moveTo(w * 0.15f, h * 0.78f);
      p.lineTo(w * 0.85f, h * 0.78f);
      p.lineTo(w * 0.82f, h * 0.35f);
      p.lineTo(w * 0.65f, h * 0.52f);
      p.lineTo(w * 0.50f, h * 0.25f);
      p.lineTo(w * 0.35f, h * 0.52f);
      p.lineTo(w * 0.18f, h * 0.35f);
      p.closePath();
      g2.fill(p);
      g2.fillOval((int)(w * 0.14f), (int)(h * 0.26f), (int)(w * 0.09f), (int)(w * 0.09f));
      g2.fillOval((int)(w * 0.455f), (int)(h * 0.16f), (int)(w * 0.09f), (int)(w * 0.09f));
      g2.fillOval((int)(w * 0.77f), (int)(h * 0.26f), (int)(w * 0.09f), (int)(w * 0.09f));
    });
  }

  public static Icon lock(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawArc((int)(w * 0.28f), (int)(h * 0.16f), (int)(w * 0.44f), (int)(h * 0.44f), 0, 180);
      g2.fillRoundRect((int)(w * 0.22f), (int)(h * 0.44f), (int)(w * 0.56f), (int)(h * 0.44f), 6, 6);
      g2.setColor(Color.WHITE);
      g2.fillOval((int)(w * 0.44f), (int)(h * 0.56f), (int)(w * 0.12f), (int)(h * 0.12f));
      g2.fillRect((int)(w * 0.47f), (int)(h * 0.62f), (int)(w * 0.06f), (int)(h * 0.12f));
    });
  }

  public static Icon heart(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      int cx = w / 2;
      Path2D p = new Path2D.Float();
      p.moveTo(cx, h * 0.82f);
      p.curveTo(cx - w * 0.45f, h * 0.55f, cx - w * 0.5f, h * 0.22f, cx - w * 0.24f, h * 0.22f);
      p.curveTo(cx - w * 0.1f, h * 0.22f, cx, h * 0.35f, cx, h * 0.42f);
      p.curveTo(cx, h * 0.35f, cx + w * 0.1f, h * 0.22f, cx + w * 0.24f, h * 0.22f);
      p.curveTo(cx + w * 0.5f, h * 0.22f, cx + w * 0.45f, h * 0.55f, cx, h * 0.82f);
      p.closePath();
      g2.fill(p);
    });
  }

  public static Icon bookmark(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      Path2D p = new Path2D.Float();
      p.moveTo(w * 0.24f, h * 0.16f);
      p.lineTo(w * 0.76f, h * 0.16f);
      p.lineTo(w * 0.76f, h * 0.84f);
      p.lineTo(w * 0.5f, h * 0.65f);
      p.lineTo(w * 0.24f, h * 0.84f);
      p.closePath();
      g2.fill(p);
    });
  }

  public static Icon trash(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawLine((int)(w * 0.2f), (int)(h * 0.26f), (int)(w * 0.8f), (int)(h * 0.26f));
      g2.drawArc((int)(w * 0.38f), (int)(h * 0.16f), (int)(w * 0.24f), (int)(h * 0.2f), 0, 180);
      Path2D b = new Path2D.Float();
      b.moveTo(w * 0.26f, h * 0.26f);
      b.lineTo(w * 0.3f, h * 0.84f);
      b.lineTo(w * 0.7f, h * 0.84f);
      b.lineTo(w * 0.74f, h * 0.26f);
      g2.draw(b);
      g2.drawLine((int)(w * 0.42f), (int)(h * 0.38f), (int)(w * 0.42f), (int)(h * 0.72f));
      g2.drawLine((int)(w * 0.58f), (int)(h * 0.38f), (int)(w * 0.58f), (int)(h * 0.72f));
    });
  }

  public static Icon refresh(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawArc((int)(w * 0.18f), (int)(h * 0.18f), (int)(w * 0.64f), (int)(h * 0.64f), 45, 270);
      Path2D arrow = new Path2D.Float();
      arrow.moveTo(w * 0.62f, h * 0.12f);
      arrow.lineTo(w * 0.84f, h * 0.24f);
      arrow.lineTo(w * 0.64f, h * 0.36f);
      arrow.closePath();
      g2.fill(arrow);
    });
  }

  public static Icon plus(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawLine((int)(w * 0.5f), (int)(h * 0.2f), (int)(w * 0.5f), (int)(h * 0.8f));
      g2.drawLine((int)(w * 0.2f), (int)(h * 0.5f), (int)(w * 0.8f), (int)(h * 0.5f));
    });
  }

  public static Icon logout(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // Door frame
      Path2D d = new Path2D.Float();
      d.moveTo(w * 0.55f, h * 0.2f);
      d.lineTo(w * 0.22f, h * 0.2f);
      d.lineTo(w * 0.22f, h * 0.8f);
      d.lineTo(w * 0.55f, h * 0.8f);
      g2.draw(d);
      // Arrow
      g2.drawLine((int)(w * 0.42f), (int)(h * 0.5f), (int)(w * 0.82f), (int)(h * 0.5f));
      g2.drawLine((int)(w * 0.68f), (int)(h * 0.36f), (int)(w * 0.82f), (int)(h * 0.5f));
      g2.drawLine((int)(w * 0.68f), (int)(h * 0.64f), (int)(w * 0.82f), (int)(h * 0.5f));
    });
  }

  public static Icon fire(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      Path2D f = new Path2D.Float();
      f.moveTo(w * 0.5f, h * 0.15f);
      f.curveTo(w * 0.62f, h * 0.28f, w * 0.78f, h * 0.44f, w * 0.78f, h * 0.65f);
      f.curveTo(w * 0.78f, h * 0.82f, w * 0.65f, h * 0.88f, w * 0.5f, h * 0.88f);
      f.curveTo(w * 0.35f, h * 0.88f, w * 0.22f, h * 0.82f, w * 0.22f, h * 0.65f);
      f.curveTo(w * 0.22f, h * 0.48f, w * 0.35f, h * 0.36f, w * 0.38f, h * 0.36f);
      f.curveTo(w * 0.38f, h * 0.48f, w * 0.44f, h * 0.55f, w * 0.52f, h * 0.55f);
      f.curveTo(w * 0.52f, h * 0.38f, w * 0.45f, h * 0.26f, w * 0.5f, h * 0.15f);
      f.closePath();
      g2.fill(f);
    });
  }

  public static Icon creditCard(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawRoundRect((int)(w * 0.12f), (int)(h * 0.22f), (int)(w * 0.76f), (int)(h * 0.56f), 5, 5);
      g2.fillRect((int)(w * 0.12f), (int)(h * 0.35f), (int)(w * 0.76f), (int)(h * 0.12f));
      g2.fillRect((int)(w * 0.22f), (int)(h * 0.58f), (int)(w * 0.2f), (int)(h * 0.08f));
    });
  }

  public static Icon image(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawRoundRect((int)(w * 0.12f), (int)(h * 0.14f), (int)(w * 0.76f), (int)(h * 0.72f), 5, 5);
      g2.fillOval((int)(w * 0.28f), (int)(h * 0.3f), (int)(w * 0.14f), (int)(h * 0.14f));
      Path2D m = new Path2D.Float();
      m.moveTo(w * 0.14f, h * 0.76f);
      m.lineTo(w * 0.42f, h * 0.48f);
      m.lineTo(w * 0.62f, h * 0.64f);
      m.lineTo(w * 0.74f, h * 0.54f);
      m.lineTo(w * 0.86f, h * 0.76f);
      g2.draw(m);
    });
  }

  public static Icon upload(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // Tray
      Path2D tray = new Path2D.Float();
      tray.moveTo(w * 0.18f, h * 0.62f);
      tray.lineTo(w * 0.18f, h * 0.82f);
      tray.lineTo(w * 0.82f, h * 0.82f);
      tray.lineTo(w * 0.82f, h * 0.62f);
      g2.draw(tray);
      // Up arrow
      g2.drawLine((int)(w * 0.5f), (int)(h * 0.68f), (int)(w * 0.5f), (int)(h * 0.18f));
      Path2D arrow = new Path2D.Float();
      arrow.moveTo(w * 0.32f, h * 0.34f);
      arrow.lineTo(w * 0.5f, h * 0.18f);
      arrow.lineTo(w * 0.68f, h * 0.34f);
      g2.draw(arrow);
    });
  }

  public static Icon alertCircle(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawOval((int)(w * 0.1f), (int)(h * 0.1f), (int)(w * 0.8f), (int)(h * 0.8f));
      g2.drawLine((int)(w * 0.5f), (int)(h * 0.3f), (int)(w * 0.5f), (int)(h * 0.58f));
      g2.fillOval((int)(w * 0.46f), (int)(h * 0.68f), (int)(w * 0.08f), (int)(h * 0.08f));
    });
  }

  public static Icon close(int size, Color color) {
    return new VectorIcon(size, size, (g2, w, h) -> {
      g2.setColor(color);
      g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.drawLine((int)(w * 0.25f), (int)(h * 0.25f), (int)(w * 0.75f), (int)(h * 0.75f));
      g2.drawLine((int)(w * 0.75f), (int)(h * 0.25f), (int)(w * 0.25f), (int)(h * 0.75f));
    });
  }

  @FunctionalInterface
  private interface IconPainter {
    void paint(Graphics2D g2, int width, int height);
  }

  private static final class VectorIcon implements Icon {
    private final int width;
    private final int height;
    private final IconPainter painter;

    VectorIcon(int width, int height, IconPainter painter) {
      this.width = width;
      this.height = height;
      this.painter = painter;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.translate(x, y);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      painter.paint(g2, width, height);
      g2.dispose();
    }

    @Override
    public int getIconWidth() {
      return width;
    }

    @Override
    public int getIconHeight() {
      return height;
    }
  }
}
