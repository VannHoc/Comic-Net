package com.comicnet.publishing.service;

import com.comicnet.catalog.api.CatalogAccess;
import com.comicnet.common.web.*;
import com.comicnet.publishing.api.PublishedChapters;
import com.comicnet.publishing.repository.PublishingRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SqlPublishedChapters implements PublishedChapters {
  private final JdbcTemplate jdbc;
  private final CatalogAccess catalog;
  private final PublishingRepository data;

  public SqlPublishedChapters(JdbcTemplate jdbc, CatalogAccess catalog, PublishingRepository data) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.data = data;
  }

  @Transactional(readOnly = true)
  public Optional<Snapshot> current(long id) {
    try {
      var initial = data.chapter(id);
      long translation = Ids.parse(initial.translationId());
      var parent = catalog.publicTranslation(translation, true);
      var rows =
          jdbc.query(
              "SELECT c.* FROM dbo.Chapter c WITH (HOLDLOCK) JOIN dbo.ChapterVersion v WITH"
                  + " (HOLDLOCK) ON v.Id=c.PublishedVersionId WHERE c.Id=? AND c.DeletedAt IS NULL"
                  + " AND c.Visibility='PUBLIC' AND v.State='PUBLISHED'",
              data.chapterMapper(),
              id);
      if (rows.isEmpty()) return Optional.empty();
      var chapter = rows.getFirst();
      long version = Ids.parse(chapter.publishedVersionId());
      var pages = data.pages(version);
      if (pages.isEmpty() || pages.stream().anyMatch(page -> !page.assetStatus().equals("READY")))
        throw new DomainException(503, "PUBLISHED_ASSET_UNAVAILABLE", "Ảnh chapter chưa sẵn sàng.");
      return Optional.of(
          new Snapshot(
              chapter,
              parent.comicId(),
              parent.teamId(),
              parent.comicTitle(),
              pages,
              neighbor(translation, chapter.sortOrder(), true),
              neighbor(translation, chapter.sortOrder(), false)));
    } catch (DomainException e) {
      if (e.status() == 404) return Optional.empty();
      throw e;
    }
  }

  private String neighbor(long translation, String order, boolean previous) {
    var rows =
        jdbc.queryForList(
            "SELECT TOP(1) c.Id FROM dbo.Chapter c JOIN dbo.ChapterVersion v ON"
                + " v.Id=c.PublishedVersionId WHERE c.TranslationId=? AND c.DeletedAt IS NULL AND"
                + " c.Visibility='PUBLIC' AND v.State='PUBLISHED' AND c.SortOrder"
                + (previous ? "<" : ">")
                + "? ORDER BY c.SortOrder "
                + (previous ? "DESC" : "ASC")
                + ",c.Id "
                + (previous ? "DESC" : "ASC"),
            Long.class,
            translation,
            new java.math.BigDecimal(order));
    return rows.isEmpty() ? null : rows.getFirst() + "";
  }

  public boolean historicalAnchor(long chapter, long version, long page) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.ChapterVersion v JOIN dbo.ChapterVersionPage p ON"
                + " p.VersionId=v.Id WHERE v.ChapterId=? AND v.Id=? AND p.PageId=? AND v.State IN"
                + " ('PUBLISHED','UNPUBLISHED')",
            Integer.class,
            chapter,
            version,
            page)
        == 1;
  }
}
