package com.comicnet.desktop.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Brand identity and application icon provider.
 * Loads crisp multi-resolution assets from classpath or renders fallback vectors.
 */
public final class AppIcons {
  private static final int[] SIZES = {16, 32, 48, 64, 128, 256};
  private static List<Image> cachedIcons;

  private AppIcons() {}

  public static synchronized List<Image> windowIcons() {
    if (cachedIcons != null && !cachedIcons.isEmpty()) {
      return cachedIcons;
    }

    List<Image> list = new ArrayList<>();
    for (int sz : SIZES) {
      try (InputStream in = AppIcons.class.getResourceAsStream("/icons/app-" + sz + ".png")) {
        if (in != null) {
          BufferedImage img = ImageIO.read(in);
          if (img != null) {
            list.add(img);
          }
        }
      } catch (Exception ignored) {
      }
    }

    if (list.isEmpty()) {
      for (int sz : SIZES) {
        list.add(renderFallback(sz));
      }
    }

    cachedIcons = Collections.unmodifiableList(list);
    return cachedIcons;
  }

  public static Image defaultIcon() {
    List<Image> icons = windowIcons();
    for (Image img : icons) {
      if (img.getWidth(null) == 32) return img;
    }
    return icons.isEmpty() ? renderFallback(32) : icons.get(0);
  }

  public static void applyTo(Window window) {
    if (window == null) return;
    try {
      List<Image> icons = windowIcons();
      if (!icons.isEmpty()) {
        window.setIconImages(icons);
      }
    } catch (Exception ignored) {
    }
  }

  private static BufferedImage renderFallback(int size) {
    BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = img.createGraphics();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    var icon = UiIcons.brandLogo(size, size);
    icon.paintIcon(null, g2, 0, 0);
    g2.dispose();
    return img;
  }
}
