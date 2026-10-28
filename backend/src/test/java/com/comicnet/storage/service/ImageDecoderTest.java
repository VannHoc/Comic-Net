package com.comicnet.storage.service;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.common.web.DomainException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ImageDecoderTest {
  private final ImageDecoder decoder = new ImageDecoder(new MockEnvironment());

  @Test
  void createsActualCompatibleImagesAndRejectsMimeSpoofing() throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(8, 12, BufferedImage.TYPE_INT_ARGB), "PNG", bytes);
    var image = decoder.decode(bytes.toByteArray(), "image/png");
    assertThat(image.width()).isEqualTo(8);
    assertThat(image.height()).isEqualTo(12);
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(image.jpeg()))).isNotNull();
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(image.png()))).isNotNull();
    assertThatThrownBy(() -> decoder.decode(bytes.toByteArray(), "image/jpeg"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void rejectsUndecodableAndOversizedImages() {
    assertThatThrownBy(() -> decoder.decode("not an image".getBytes(), null))
        .isInstanceOf(DomainException.class);
    var limited =
        new ImageDecoder(
            new MockEnvironment().withProperty("comicnet.upload.max-image-bytes", "4"));
    assertThatThrownBy(() -> limited.decode(new byte[5], null))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(413));
  }

  @Test
  void decodesActualWebpIntoNativeClientFormats() throws Exception {
    byte[] webp =
        java.util.Base64.getDecoder()
            .decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=");
    var decoded = decoder.decode(webp, "image/webp");
    assertThat(decoded.width()).isEqualTo(1);
    assertThat(decoded.height()).isEqualTo(1);
    assertThat(decoded.mime()).isEqualTo("image/webp");
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(decoded.jpeg()))).isNotNull();
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(decoded.png()))).isNotNull();
  }
}
