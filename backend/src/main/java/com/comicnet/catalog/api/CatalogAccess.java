package com.comicnet.catalog.api;

import com.comicnet.identity.api.Identity;

public interface CatalogAccess {
  record TranslationScope(
      long translationId,
      long comicId,
      long teamId,
      String comicVisibility,
      String translationVisibility,
      String ageRating,
      String comicTitle) {}

  TranslationScope scopedTranslation(Identity actor, long id, String permission, boolean lock);

  TranslationScope publicTranslation(long id, boolean lock);

  boolean publicComic(long id);

  /** Trusted job cleanup only, including soft-deleted parents. Caller must own the job lease. */
  TranslationScope lockForJob(long translationId);

  /** Called after acquiring the comic root lock, in the publish transaction. */
  void publishedContentChanged(long comicId);
}
