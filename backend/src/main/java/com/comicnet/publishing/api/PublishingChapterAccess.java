package com.comicnet.publishing.api;

import com.comicnet.identity.api.Identity;

public interface PublishingChapterAccess {
  void requireChapterEditor(Identity actor, long chapterId);
}
