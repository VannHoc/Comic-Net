package com.comicnet.desktop.features.reader;

public record ReaderLocation(
    String chapterId,
    String versionId,
    String pageId,
    double relativeOffset,
    ReadMode mode) {

  public enum ReadMode {
    START_FROM_BEGINNING,
    RESUME
  }

  public ReaderLocation(String chapterId, String versionId, String pageId, double relativeOffset) {
    this(chapterId, versionId, pageId, relativeOffset, ReadMode.RESUME);
  }

  public static ReaderLocation chapter(String id) {
    return new ReaderLocation(id, null, null, 0, ReadMode.RESUME);
  }

  public static ReaderLocation startFromBeginning(String id) {
    return new ReaderLocation(id, null, null, 0, ReadMode.START_FROM_BEGINNING);
  }

  public static ReaderLocation resume(String id) {
    return new ReaderLocation(id, null, null, 0, ReadMode.RESUME);
  }

  public static ReaderLocation resume(
      String chapterId, String versionId, String pageId, double relativeOffset) {
    return new ReaderLocation(chapterId, versionId, pageId, relativeOffset, ReadMode.RESUME);
  }
}
