package com.comicnet.analytics.service;

import com.comicnet.analytics.dto.AnalyticsDtos.*;
import com.comicnet.identity.api.*;
import com.comicnet.catalog.api.*;
import com.comicnet.common.web.*;
import com.comicnet.observability.api.*;
import com.comicnet.publishing.api.PublishedChapters;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valid read counting, ranking and analytics for M07.
 *
 * <p>A "valid read" is one authenticated open of a chapter that is published under a public comic.
 * Duplicate opens of the same chapter by the same reader on the same UTC day are ignored, which
 * keeps refreshes, retries and reader re-entry from inflating views. A reader cannot record more
 * than {@value #HOURLY_READ_LIMIT} fresh chapter reads per hour.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {
  private static final int HOURLY_READ_LIMIT = 150;
  private static final int STATS_WINDOW_DAYS = 30;
  private static final Set<String> PERIODS = Set.of("DAILY", "WEEKLY", "MONTHLY", "TRENDING");

  private final JdbcTemplate jdbc;
  private final CatalogAccess catalog;
  private final CatalogQueries comics;
  private final PublishedChapters chapters;
  private final IdentityService identities;
  private final AuditWriter audit;

  public AnalyticsService(
      JdbcTemplate jdbc,
      CatalogAccess catalog,
      CatalogQueries comics,
      PublishedChapters chapters,
      IdentityService identities,
      AuditWriter audit) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.comics = comics;
    this.chapters = chapters;
    this.identities = identities;
    this.audit = audit;
  }

  private void self(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    if (!identities.permitted(actor, "analytics.self")) throw DomainException.forbidden();
  }

  private void reader(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    if (!identities.permitted(actor, "analytics.read")) throw DomainException.forbidden();
  }

  private long comicOfChapter(long chapter) {
    var snapshot = chapters.current(chapter).orElseThrow(DomainException::missing);
    return snapshot.comicId();
  }

  private void count(String column, long comic) {
    int updated =
        jdbc.update(
            "UPDATE dbo.ComicStatDaily SET "
                + column
                + "="
                + column
                + "+1 WHERE ComicId=? AND StatDate=CAST(SYSUTCDATETIME() AS date)",
            comic);
    if (updated == 0)
      jdbc.update(
          "INSERT dbo.ComicStatDaily(ComicId,StatDate,"
              + column
              + ") VALUES(?,CAST(SYSUTCDATETIME() AS date),1)",
          comic);
    else if (updated > 1) throw new IllegalStateException("Daily statistic row duplicated.");
  }

  @Transactional
  public ViewResult record(Identity actor, long comic, ViewInput input) {
    self(actor);
    long chapter = Ids.parse(input.chapterId());
    if (!catalog.publicComic(comic)) throw DomainException.missing();
    if (comicOfChapter(chapter) != comic) throw DomainException.missing();
    long recent =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.ComicView WHERE UserId=? AND"
                + " ViewedAt>DATEADD(hour,-1,SYSUTCDATETIME())",
            Long.class,
            actor.userId());
    if (recent >= HOURLY_READ_LIMIT)
      throw new DomainException(
          429, "VIEW_RATE_LIMIT", "Bạn đã đọc quá nhiều chương trong một giờ. Thử lại sau.");
    boolean recorded;
    try {
      jdbc.update(
          "INSERT dbo.ComicView(ComicId,ChapterId,UserId,ViewedOn) VALUES(?,?,?,CAST(SYSUTCDATETIME() AS date))",
          comic,
          chapter,
          actor.userId());
      recorded = true;
    } catch (DuplicateKeyException duplicate) {
      recorded = false;
    }
    if (recorded) count("Views", comic);
    return new ViewResult(recorded, totalViews(comic));
  }

  @Transactional
  public CompletionResult complete(Identity actor, long chapter) {
    self(actor);
    long comic = comicOfChapter(chapter);
    if (!catalog.publicComic(comic)) throw DomainException.missing();
    boolean recorded;
    try {
      jdbc.update(
          "INSERT dbo.ChapterCompletion(UserId,ChapterId,ComicId) VALUES(?,?,?)",
          actor.userId(),
          chapter,
          comic);
      recorded = true;
    } catch (DuplicateKeyException duplicate) {
      recorded = false;
    }
    if (recorded) count("Completions", comic);
    return new CompletionResult(
        recorded,
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.ChapterCompletion WHERE ComicId=?", Long.class, comic));
  }

  private long totalViews(long comic) {
    return jdbc.queryForObject(
        "SELECT COALESCE(SUM(Views),0) FROM dbo.ComicStatDaily WHERE ComicId=?", Long.class, comic);
  }

  public ComicStats stats(Identity actor, long comic) {
    reader(actor);
    if (!catalog.publicComic(comic)) throw DomainException.missing();
    long views = totalViews(comic);
    long completions =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(Completions),0) FROM dbo.ComicStatDaily WHERE ComicId=?",
            Long.class,
            comic);
    long readers =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(DISTINCT UserId) FROM dbo.ComicView WHERE ComicId=?",
            Long.class,
            comic);
    BigDecimal rating =
        jdbc.queryForObject(
            "SELECT COALESCE(CAST(AVG(CAST(r.Score AS decimal(10,2))) AS decimal(10,2)),0)"
                + " FROM dbo.ComicRating r JOIN dbo.[User] u ON u.Id=r.UserId WHERE r.ComicId=?"
                + " AND u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)",
            BigDecimal.class,
            comic);
    long ratings =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.ComicRating r JOIN dbo.[User] u ON u.Id=r.UserId"
                + " WHERE r.ComicId=? AND u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)",
            Long.class,
            comic);
    var series =
        jdbc.query(
            "SELECT StatDate,Views,Completions FROM dbo.ComicStatDaily WHERE ComicId=? AND"
                + " StatDate>=DATEADD(day,-?,CAST(SYSUTCDATETIME() AS date)) ORDER BY StatDate",
            (row, n) ->
                new DailyPoint(
                    row.getDate("StatDate").toLocalDate(),
                    row.getLong("Views"),
                    row.getLong("Completions")),
            comic,
            STATS_WINDOW_DAYS - 1);
    return new ComicStats(
        comic + "", views, completions, readers, rating, ratings, series);
  }

  public ChapterStats chapterStats(Identity actor, long chapter) {
    reader(actor);
    long comic = comicOfChapter(chapter);
    if (!catalog.publicComic(comic)) throw DomainException.missing();
    long readers =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(DISTINCT UserId) FROM dbo.ComicView WHERE ChapterId=?",
            Long.class,
            chapter);
    long completions =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.ChapterCompletion WHERE ChapterId=?",
            Long.class,
            chapter);
    BigDecimal rate =
        readers == 0
            ? BigDecimal.ZERO.setScale(2)
            : BigDecimal.valueOf(completions * 100L)
                .divide(BigDecimal.valueOf(readers), 2, java.math.RoundingMode.HALF_UP);
    return new ChapterStats(chapter + "", readers, completions, rate);
  }

  /**
   * Period ranking over daily aggregates. Trending additionally weights completions and the live
   * rating aggregate so a widely read but rarely finished comic does not outrank an engaging one.
   */
  public PageResult<RankingEntry> ranking(String period, int size) {
    String window = period == null ? "WEEKLY" : period.strip().toUpperCase(Locale.ROOT);
    if (!PERIODS.contains(window))
      throw new DomainException(400, "INVALID_PERIOD", "Khoảng xếp hạng không hợp lệ.");
    if (size < 1 || size > 50)
      throw new DomainException(400, "INVALID_PAGE", "Số lượng xếp hạng phải từ 1 đến 50.");
    int days = window.equals("DAILY") ? 1 : window.equals("MONTHLY") ? 30 : 7;
    String order =
        window.equals("TRENDING")
            ? "(SUM(s.Views)+3*SUM(s.Completions)) DESC,rating DESC,s.ComicId DESC"
            : "SUM(s.Views) DESC,s.ComicId DESC";
    String sql =
        "SELECT TOP(?) s.ComicId,SUM(s.Views) AS Views,(SELECT COALESCE(CAST(AVG(CAST(r.Score AS"
            + " decimal(10,2))) AS decimal(10,2)),0) FROM dbo.ComicRating r JOIN dbo.[User] u ON"
            + " u.Id=r.UserId WHERE r.ComicId=s.ComicId AND u.Status='ACTIVE' AND"
            + " (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)) AS rating FROM dbo.ComicStatDaily s JOIN dbo.Comic c"
            + " ON c.Id=s.ComicId WHERE s.StatDate>=DATEADD(day,-?,CAST(SYSUTCDATETIME() AS date))"
            + " AND c.Visibility='PUBLIC' AND c.DeletedAt IS NULL AND c.AgeRating='ALL_AGE'"
            + " GROUP BY s.ComicId ORDER BY "
            + order;
    var rows =
        jdbc.query(
            sql,
            (row, n) -> Map.entry(row.getLong("ComicId"), row.getLong("Views")),
            size,
            days - 1);
    var entries = new ArrayList<RankingEntry>();
    for (var row : rows) {
      try {
        var comic = comics.summary(row.getKey());
        entries.add(
            new RankingEntry(
                entries.size() + 1,
                comic.id(),
                comic.title(),
                comic.coverUrl(),
                row.getValue()));
      } catch (DomainException unavailable) {
        if (unavailable.status() != 404) throw unavailable;
      }
    }
    return PageResult.of(entries, 0, Math.max(1, size), entries.size());
  }
}
