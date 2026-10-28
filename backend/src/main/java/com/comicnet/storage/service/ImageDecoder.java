package com.comicnet.storage.service;

import com.comicnet.common.web.DomainException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ImageDecoder {
  private final Environment env;

  public ImageDecoder(Environment env) {
    this.env = env;
    ImageIO.setUseCache(false);
  }

  public record Decoded(int width, int height, String mime, byte[] jpeg, byte[] png) {}

  public Decoded decode(byte[] bytes, String declaredMime) {
    if (bytes.length == 0
        || bytes.length
            > env.getProperty("comicnet.upload.max-image-bytes", Long.class, 20L * 1024 * 1024))
      throw new DomainException(413, "IMAGE_SIZE_LIMIT", "Ảnh vượt giới hạn dung lượng.");
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw unsupported();
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = reader.getFormatName().toUpperCase(Locale.ROOT);
        String mime =
            switch (format) {
              case "JPEG", "JPG" -> "image/jpeg";
              case "PNG" -> "image/png";
              case "WEBP" -> "image/webp";
              default -> throw unsupported();
            };
        if (declaredMime != null
            && !declaredMime.equals("application/octet-stream")
            && !declaredMime.equalsIgnoreCase(mime)) throw unsupported();
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1
            || height < 1
            || width > 40000
            || height > 40000
            || (long) width * height
                > env.getProperty("comicnet.upload.max-pixels", Long.class, 40_000_000L))
          throw new DomainException(
              422, "IMAGE_DIMENSION_LIMIT", "Kích thước ảnh vượt giới hạn an toàn.");
        BufferedImage image = reader.read(0);
        if (image == null) throw unsupported();
        var png = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "PNG", png)) throw unsupported();
        var rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
          graphics.setColor(Color.WHITE);
          graphics.fillRect(0, 0, width, height);
          graphics.drawImage(image, 0, 0, null);
        } finally {
          graphics.dispose();
        }
        var jpeg = new ByteArrayOutputStream();
        if (!ImageIO.write(rgb, "JPEG", jpeg)) throw unsupported();
        image.flush();
        rgb.flush();
        return new Decoded(width, height, mime, jpeg.toByteArray(), png.toByteArray());
      } finally {
        reader.dispose();
      }
    } catch (DomainException e) {
      throw e;
    } catch (IOException | RuntimeException e) {
      throw new DomainException(422, "IMAGE_DECODE_FAILED", "Không thể giải mã ảnh hợp lệ.");
    }
  }

  private DomainException unsupported() {
    return new DomainException(
        415, "UNSUPPORTED_IMAGE", "Chỉ nhận ảnh JPEG, PNG hoặc WebP hợp lệ.");
  }
}
