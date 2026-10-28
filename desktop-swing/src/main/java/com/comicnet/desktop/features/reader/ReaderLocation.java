package com.comicnet.desktop.features.reader;

public record ReaderLocation(
    String chapterId, String versionId, String pageId, double relativeOffset) {
  public static ReaderLocation chapter(String id) {
    return new ReaderLocation(id, null, null, 0);
  }
}
