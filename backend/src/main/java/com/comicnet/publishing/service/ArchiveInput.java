package com.comicnet.publishing.service;

import com.comicnet.common.web.DomainException;
import java.io.*;
import java.math.BigInteger;
import java.nio.*;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.apache.commons.compress.archivers.zip.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Bounded archive reader. Entry names are never resolved to filesystem paths. */
@Component
public class ArchiveInput {
  private final Environment env;
  private static final Pattern PARTS = Pattern.compile("[0-9]+|[^0-9]+");

  public ArchiveInput(Environment env) {
    this.env = env;
  }

  public final class Batch implements AutoCloseable {
    private final Path file;
    private final ZipFile zip;
    private final List<ZipArchiveEntry> entries;
    private final List<String> names;

    private Batch(Path file, ZipFile zip, List<ZipArchiveEntry> entries, List<String> names) {
      this.file = file;
      this.zip = zip;
      this.entries = entries;
      this.names = names;
    }

    public int size() {
      return names.size();
    }

    public String name(int index) {
      return names.get(index);
    }

    public byte[] read(int index) {
      long maximum =
          env.getProperty("comicnet.upload.max-image-bytes", Long.class, 20L * 1024 * 1024);
      try (InputStream input =
          zip == null ? Files.newInputStream(file) : zip.getInputStream(entries.get(index))) {
        byte[] bytes = input.readNBytes(Math.toIntExact(maximum + 1));
        if (bytes.length > maximum)
          throw limit("IMAGE_SIZE_LIMIT", "Ảnh trong lô vượt giới hạn dung lượng.");
        if (zip != null) {
          var entry = entries.get(index);
          CRC32 crc = new CRC32();
          crc.update(bytes);
          if (bytes.length != entry.getSize() || crc.getValue() != entry.getCrc())
            throw invalid("ZIP_CORRUPT", "Tệp ZIP bị hỏng.");
        }
        return bytes;
      } catch (IOException e) {
        throw invalid("UPLOAD_READ_FAILED", "Không thể đọc tệp tải lên.");
      }
    }

    @Override
    public void close() {
      if (zip != null)
        try {
          zip.close();
        } catch (IOException ignored) {
          /* temporary file is still removed by caller */
        }
    }
  }

  public Batch open(Path file, int remainingPages) {
    if (remainingPages < 1) throw limit("PAGE_LIMIT", "Version đã đạt giới hạn số trang.");
    try {
      long size = Files.size(file);
      if (size == 0
          || size
              > env.getProperty(
                  "comicnet.upload.max-request-bytes", Long.class, 100L * 1024 * 1024))
        throw limit("UPLOAD_SIZE_LIMIT", "Tệp tải lên vượt giới hạn dung lượng.");
      byte[] magic;
      try (var input = Files.newInputStream(file)) {
        magic = input.readNBytes(4);
      }
      boolean archive =
          magic.length == 4
              && magic[0] == 'P'
              && magic[1] == 'K'
              && ((magic[2] == 3 && magic[3] == 4) || (magic[2] == 5 && magic[3] == 6));
      if (!archive) return new Batch(file, null, List.of(), List.of("image"));
      preflight(file);
      ZipFile zip = ZipFile.builder().setPath(file).get();
      try {
        List<ZipArchiveEntry> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        long expanded = 0;
        int entryCount = 0;
        var enumeration = zip.getEntries();
        while (enumeration.hasMoreElements()) {
          var entry = enumeration.nextElement();
          if (++entryCount > 5000) throw limit("ZIP_ENTRY_LIMIT", "ZIP chứa quá nhiều mục.");
          String name = safeName(entry.getName());
          if (entry.isUnixSymlink()
              || entry.getGeneralPurposeBit().usesEncryption()
              || !zip.canReadEntryData(entry))
            throw invalid(
                "ZIP_UNSUPPORTED_ENTRY", "ZIP chứa liên kết hoặc kiểu nén không được hỗ trợ.");
          if (entry.isDirectory()) continue;
          String lower = name.toLowerCase(Locale.ROOT);
          if (!(lower.endsWith(".jpg")
              || lower.endsWith(".jpeg")
              || lower.endsWith(".png")
              || lower.endsWith(".webp")))
            throw new DomainException(
                415,
                "ZIP_IMAGES_ONLY",
                "ZIP chỉ được chứa ảnh JPEG, PNG hoặc WebP; không nhận archive lồng nhau.");
          if (!names.add(lower))
            throw invalid("ZIP_DUPLICATE_ENTRY", "Tên tệp trong ZIP bị trùng.");
          if (entry.getSize() < 1
              || entry.getSize()
                  > env.getProperty(
                      "comicnet.upload.max-image-bytes", Long.class, 20L * 1024 * 1024))
            throw limit("IMAGE_SIZE_LIMIT", "Ảnh trong ZIP vượt giới hạn dung lượng.");
          expanded = Math.addExact(expanded, entry.getSize());
          if (expanded
              > env.getProperty(
                  "comicnet.upload.max-expanded-bytes", Long.class, 500L * 1024 * 1024))
            throw limit("ZIP_EXPANSION_LIMIT", "Tổng dung lượng giải nén vượt giới hạn.");
          long compressed = entry.getCompressedSize();
          if (compressed < 1 || entry.getSize() > compressed * 100L)
            throw limit("ZIP_RATIO_LIMIT", "Tỷ lệ nén ZIP vượt giới hạn an toàn.");
          entries.add(entry);
          if (entries.size() > Math.min(500, remainingPages))
            throw limit("PAGE_LIMIT", "Lô ảnh vượt số trang còn lại của version.");
        }
        if (entries.isEmpty()) throw invalid("ZIP_EMPTY", "ZIP không có ảnh.");
        entries.sort((a, b) -> natural(safeName(a.getName()), safeName(b.getName())));
        return new Batch(
            file,
            zip,
            List.copyOf(entries),
            entries.stream().map(e -> safeName(e.getName())).toList());
      } catch (RuntimeException e) {
        zip.close();
        throw e;
      }
    } catch (DomainException e) {
      throw e;
    } catch (IOException | ArithmeticException e) {
      throw invalid("ZIP_INVALID", "Không thể đọc tệp ZIP hợp lệ.");
    }
  }

  private String safeName(String original) {
    String name = Normalizer.normalize(original.replace('\\', '/'), Normalizer.Form.NFC);
    if (name.length() > 400
        || name.startsWith("/")
        || name.contains(":")
        || name.chars().anyMatch(c -> c < 32)
        || Arrays.stream(name.split("/", -1))
            .anyMatch(part -> part.equals(".") || part.equals("..")))
      throw invalid("ZIP_UNSAFE_PATH", "ZIP chứa đường dẫn không an toàn.");
    return name;
  }

  /** Bound central-directory allocation before the library parses any entry objects. */
  private void preflight(Path file) throws IOException {
    try (var channel = Files.newByteChannel(file)) {
      long size = channel.size();
      int length = (int) Math.min(size, 65557);
      ByteBuffer tail = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
      channel.position(size - length);
      while (tail.hasRemaining() && channel.read(tail) >= 0) {}
      for (int i = length - 22; i >= 0; i--) {
        if (tail.getInt(i) != 0x06054b50) continue;
        int comment = Short.toUnsignedInt(tail.getShort(i + 20));
        if (i + 22 + comment != length) continue;
        int disk = Short.toUnsignedInt(tail.getShort(i + 4)),
            directoryDisk = Short.toUnsignedInt(tail.getShort(i + 6));
        int diskEntries = Short.toUnsignedInt(tail.getShort(i + 8)),
            entries = Short.toUnsignedInt(tail.getShort(i + 10));
        long directorySize = Integer.toUnsignedLong(tail.getInt(i + 12)),
            offset = Integer.toUnsignedLong(tail.getInt(i + 16));
        if (disk != 0 || directoryDisk != 0 || diskEntries != entries || entries == 65535)
          throw invalid("ZIP_LAYOUT_UNSUPPORTED", "Không nhận ZIP chia nhiều phần hoặc ZIP64.");
        if (entries > 5000 || directorySize > 16L * 1024 * 1024)
          throw limit("ZIP_ENTRY_LIMIT", "ZIP chứa quá nhiều mục.");
        if (offset + directorySize > size - length + i)
          throw invalid("ZIP_CORRUPT", "Thư mục ZIP không hợp lệ.");
        return;
      }
      throw invalid("ZIP_CORRUPT", "Không tìm thấy thư mục ZIP hợp lệ.");
    }
  }

  static int natural(String left, String right) {
    var a = PARTS.matcher(left);
    var b = PARTS.matcher(right);
    while (a.find() && b.find()) {
      String x = a.group(), y = b.group();
      int comparison =
          Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))
              ? new BigInteger(x).compareTo(new BigInteger(y))
              : x.compareToIgnoreCase(y);
      if (comparison != 0) return comparison;
    }
    return left.compareTo(right);
  }

  private DomainException limit(String code, String message) {
    return new DomainException(413, code, message);
  }

  private DomainException invalid(String code, String message) {
    return new DomainException(422, code, message);
  }
}
