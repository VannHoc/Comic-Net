package com.comicnet.publishing.api;

import com.comicnet.publishing.api.PublishingDtos.*;
import java.util.List;
import java.util.Optional;

/** Authorized public metadata/bindings only; reader signs images after its access decision. */
public interface PublishedChapters {
  record Snapshot(
      Chapter chapter,
      long comicId,
      long teamId,
      String comicTitle,
      List<VersionPage> pages,
      String previousChapterId,
      String nextChapterId) {}

  Optional<Snapshot> current(long chapterId);

  boolean historicalAnchor(long chapterId, long versionId, long pageId);
}
