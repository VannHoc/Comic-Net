package com.comicnet.publishing.service;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.common.web.DomainException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class ArchiveInputTest {
  @TempDir Path temporary;
  private final ArchiveInput archives = new ArchiveInput(new MockEnvironment());

  private byte[] png() throws IOException {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "PNG", bytes);
    return bytes.toByteArray();
  }

  private Path archive(String... names) throws IOException {
    Path file = Files.createTempFile(temporary, "pages-", ".zip");
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      for (String name : names) {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(png());
        zip.closeEntry();
      }
    }
    return file;
  }

  @Test
  void sortsNaturalNamesAndReadsActualImages() throws Exception {
    try (var batch = archives.open(archive("page10.png", "page2.png", "page1.png"), 500)) {
      assertThat(List.of(batch.name(0), batch.name(1), batch.name(2)))
          .containsExactly("page1.png", "page2.png", "page10.png");
      assertThat(ImageIO.read(new ByteArrayInputStream(batch.read(0)))).isNotNull();
    }
  }

  @Test
  void rejectsTraversalNestedArchivesAndPageOverflow() throws Exception {
    for (String unsafe :
        List.of(
            "../outside.png",
            "C:/outside.png",
            "/root.png",
            "folder/../../outside.png",
            "nested.zip")) {
      Path file = archive(unsafe);
      assertThatThrownBy(() -> archives.open(file, 500)).isInstanceOf(DomainException.class);
    }
    Path tooMany = archive("1.png", "2.png");
    assertThatThrownBy(() -> archives.open(tooMany, 1))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo("PAGE_LIMIT"));
  }

  @Test
  void rejectsHighlyCompressedPayloadBeforeDecoding() throws Exception {
    Path file = Files.createTempFile(temporary, "bomb-", ".zip");
    try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {
      zip.putNextEntry(new ZipEntry("page.png"));
      zip.write(new byte[100_000]);
      zip.closeEntry();
    }
    assertThatThrownBy(() -> archives.open(file, 500))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo("ZIP_RATIO_LIMIT"));
  }

  @Test
  void rejectsUnixSymlinksBeforeOpeningImagePayload() throws Exception {
    Path file = Files.createTempFile(temporary, "symlink-", ".zip");
    try (var zip = new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(file)) {
      var entry = new org.apache.commons.compress.archivers.zip.ZipArchiveEntry("page.png");
      entry.setUnixMode(0120777);
      zip.putArchiveEntry(entry);
      zip.write("../outside".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zip.closeArchiveEntry();
    }
    assertThatThrownBy(() -> archives.open(file, 500)).isInstanceOf(DomainException.class);
  }
}
