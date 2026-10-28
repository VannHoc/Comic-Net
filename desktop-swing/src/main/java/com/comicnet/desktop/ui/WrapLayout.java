package com.comicnet.desktop.ui;

import java.awt.*;

/** Flow layout whose preferred height includes wrapped rows at the current desktop width. */
public final class WrapLayout extends FlowLayout {
  public WrapLayout(int alignment, int horizontalGap, int verticalGap) {
    super(alignment, horizontalGap, verticalGap);
  }

  @Override
  public Dimension preferredLayoutSize(Container target) {
    return measured(target, false);
  }

  @Override
  public Dimension minimumLayoutSize(Container target) {
    Dimension size = measured(target, true);
    return new Dimension(0, size.height);
  }

  private Dimension measured(Container target, boolean minimum) {
    synchronized (target.getTreeLock()) {
      int width = target.getWidth();
      for (Container parent = target.getParent();
          width <= 0 && parent != null;
          parent = parent.getParent()) width = parent.getWidth();
      if (width <= 0) width = 900;
      Insets insets = target.getInsets();
      int limit = Math.max(1, width - insets.left - insets.right - 2 * getHgap()),
          rowWidth = 0,
          rowHeight = 0,
          totalHeight = 0,
          maxWidth = 0;
      for (Component component : target.getComponents())
        if (component.isVisible()) {
          Dimension size = minimum ? component.getMinimumSize() : component.getPreferredSize();
          int gap = rowWidth == 0 ? 0 : getHgap();
          if (rowWidth > 0 && rowWidth + gap + size.width > limit) {
            maxWidth = Math.max(maxWidth, rowWidth);
            totalHeight += rowHeight + getVgap();
            rowWidth = 0;
            rowHeight = 0;
            gap = 0;
          }
          rowWidth += gap + size.width;
          rowHeight = Math.max(rowHeight, size.height);
        }
      return new Dimension(
          Math.max(maxWidth, rowWidth) + insets.left + insets.right + 2 * getHgap(),
          totalHeight + rowHeight + insets.top + insets.bottom + 2 * getVgap());
    }
  }
}
