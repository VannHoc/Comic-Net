package com.comicnet.catalog.api;

import com.comicnet.catalog.api.CatalogDtos.ComicSummary;
import com.comicnet.common.web.PageResult;

public interface CatalogQueries {
  record Filter(
      String query, String type, String genreId, String tagId, String status, String sort) {}

  PageResult<ComicSummary> list(Filter filter, int page, int size);

  ComicSummary summary(long comicId);

  java.util.List<ComicSummary> similar(long comicId, int size);
}
