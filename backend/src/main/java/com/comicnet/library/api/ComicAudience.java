package com.comicnet.library.api;

import java.util.List;

/** Read-only audience of a comic, used by notification fan-out. */
public interface ComicAudience {
  List<Long> followers(long comicId);
}
