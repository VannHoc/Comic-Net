package com.comicnet.recommendation.service;

import com.comicnet.catalog.api.CatalogDtos.ComicSummary;
import com.comicnet.catalog.api.CatalogQueries;
import com.comicnet.common.web.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Small SQL/content based feed with an explicit cold-start fallback. No separate recommendation
 * system.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {
  private final JdbcTemplate jdbc;
  private final CatalogQueries catalog;

  public RecommendationService(JdbcTemplate jdbc, CatalogQueries catalog) {
    this.jdbc = jdbc;
    this.catalog = catalog;
  }

  private void size(int value) {
    if (value < 1 || value > 20)
      throw new DomainException(400, "INVALID_PAGE", "Số gợi ý phải từ 1 đến 20.");
  }

  public PageResult<ComicSummary> similar(long comic, int size) {
    size(size);
    var values = catalog.similar(comic, size);
    return PageResult.of(values, 0, size, values.size());
  }

  public PageResult<ComicSummary> discover(int size) {
    size(size);
    var result = new LinkedHashMap<String, ComicSummary>();
    var candidates =
        jdbc.queryForList(
            "SELECT TOP(100) r.ComicId FROM dbo.ComicRating r JOIN dbo.[User] u ON u.Id=r.UserId"
                + " WHERE r.UpdatedAt>DATEADD(day,-30,SYSUTCDATETIME()) AND u.Status='ACTIVE' AND"
                + " (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL) GROUP BY r.ComicId ORDER BY COUNT_BIG(*)"
                + " DESC,AVG(CAST(r.Score AS decimal(10,2))) DESC,r.ComicId DESC",
            Long.class);
    for (long candidate : candidates) {
      try {
        var comic = catalog.summary(candidate);
        result.putIfAbsent(comic.id(), comic);
      } catch (DomainException unavailable) {
        if (unavailable.status() != 404) throw unavailable;
      }
      if (result.size() == size) break;
    }
    // Empty/new accounts and unrated catalogs still get real visible content.
    for (int page = 0; result.size() < size; page++) {
      var fallback =
          catalog.list(
              new CatalogQueries.Filter(null, null, null, null, null, "UPDATED_DESC"), page, 20);
      for (var comic : fallback.items()) {
        result.putIfAbsent(comic.id(), comic);
        if (result.size() == size) break;
      }
      if (page + 1 >= fallback.page().totalPages()) break;
    }
    return PageResult.of(List.copyOf(result.values()), 0, size, result.size());
  }
}
