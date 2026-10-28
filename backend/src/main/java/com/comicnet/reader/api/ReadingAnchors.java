package com.comicnet.reader.api;

import com.comicnet.identity.api.Identity;

public interface ReadingAnchors {
  void requireValid(Identity actor, long chapter, long version, long page);

  boolean available(Identity actor, long chapter, long version, long page);
}
