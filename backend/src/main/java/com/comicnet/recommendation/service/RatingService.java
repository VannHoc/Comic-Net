package com.comicnet.recommendation.service;

import com.comicnet.identity.api.Identity;
import com.comicnet.identity.api.IdentityService;
import com.comicnet.catalog.api.CatalogAccess;
import com.comicnet.common.web.*;
import com.comicnet.observability.api.AuditWriter;
import com.comicnet.recommendation.dto.RatingDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RatingService {
  private final JdbcTemplate jdbc;
  private final CatalogAccess catalog;
  private final AuditWriter audit;
  private final IdentityService identities;

  public RatingService(
      JdbcTemplate jdbc, CatalogAccess catalog, AuditWriter audit, IdentityService identities) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.audit = audit;
    this.identities = identities;
  }

  private void visible(long comic) {
    if (!catalog.publicComic(comic)) throw DomainException.missing();
  }

  private void signedIn(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    if (!identities.permitted(actor, "rating.self")) throw DomainException.forbidden();
  }

  public RatingSummary summary(long comic) {
    visible(comic);
    return jdbc.queryForObject(
        "SELECT COUNT_BIG(*),COALESCE(CAST(AVG(CAST(r.Score AS decimal(10,2))) AS decimal(10,2)),0)"
            + " FROM dbo.ComicRating r JOIN dbo.[User] u ON u.Id=r.UserId WHERE r.ComicId=?"
            + " AND u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)",
        (row, n) -> new RatingSummary(row.getLong(1), row.getBigDecimal(2)),
        comic);
  }

  public PageResult<RatingView> reviews(long comic, int page, int size) {
    visible(comic);
    PageResult.validate(page, size);
    String filter =
        " FROM dbo.ComicRating r JOIN dbo.[User] u ON u.Id=r.UserId WHERE r.ComicId=?"
            + " AND u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL) AND r.Review IS NOT NULL";
    long total = jdbc.queryForObject("SELECT COUNT_BIG(*)" + filter, Long.class, comic);
    var rows =
        jdbc.query(
            "SELECT r.*,u.DisplayName"
                + filter
                + " ORDER BY r.UpdatedAt DESC,r.UserId DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            this::map,
            comic,
            PageResult.offset(page, size),
            size);
    return PageResult.of(rows, page, size, total);
  }

  public RatingView mine(Identity actor, long comic) {
    signedIn(actor);
    visible(comic);
    var rows =
        jdbc.query(
            "SELECT r.*,u.DisplayName FROM dbo.ComicRating r JOIN dbo.[User] u"
                + " ON u.Id=r.UserId WHERE r.ComicId=? AND r.UserId=?",
            this::map,
            comic,
            actor.userId());
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  public byte[] token(Identity actor, long comic) {
    mine(actor, comic);
    return jdbc.queryForObject(
        "SELECT RowVersion FROM dbo.ComicRating WHERE ComicId=? AND UserId=?",
        byte[].class,
        comic,
        actor.userId());
  }

  @Transactional
  public RatingView put(
      Identity actor, long comic, RatingInput input, String etag, String noneMatch) {
    signedIn(actor);
    visible(comic);
    if (input.score() < 1 || input.score() > 5)
      throw new DomainException(422, "INVALID_RATING", "Điểm đánh giá phải từ 1 đến 5.");
    String review = plainReview(input.review());
    var rows =
        jdbc.queryForList(
            "SELECT RowVersion FROM dbo.ComicRating WITH (UPDLOCK,HOLDLOCK)"
                + " WHERE ComicId=? AND UserId=?",
            comic,
            actor.userId());
    if (rows.isEmpty()) {
      if (!"*".equals(noneMatch) || etag != null)
        throw new DomainException(
            428, "IF_NONE_MATCH_REQUIRED", "Dùng If-None-Match: * để tạo đánh giá.");
      jdbc.update(
          "INSERT dbo.ComicRating(ComicId,UserId,Score,Review) VALUES(?,?,?,?)",
          comic,
          actor.userId(),
          input.score(),
          review);
    } else {
      if (noneMatch != null)
        throw new DomainException(412, "STALE_VERSION", "Đánh giá đã tồn tại. Hãy tải lại.");
      Etags.require(etag, (byte[]) rows.getFirst().get("RowVersion"));
      jdbc.update(
          "UPDATE dbo.ComicRating SET Score=?,Review=?,UpdatedAt=SYSUTCDATETIME()"
              + " WHERE ComicId=? AND UserId=?",
          input.score(),
          review,
          comic,
          actor.userId());
    }
    audit.record(actor.userId(), "RATING_SAVED", "Comic", comic + "", "SUCCESS");
    return mine(actor, comic);
  }

  @Transactional
  public void delete(Identity actor, long comic, String etag) {
    signedIn(actor);
    visible(comic);
    var rows =
        jdbc.queryForList(
            "SELECT RowVersion FROM dbo.ComicRating WITH (UPDLOCK,HOLDLOCK)"
                + " WHERE ComicId=? AND UserId=?",
            comic,
            actor.userId());
    if (rows.isEmpty()) throw DomainException.missing();
    Etags.require(etag, (byte[]) rows.getFirst().get("RowVersion"));
    jdbc.update("DELETE dbo.ComicRating WHERE ComicId=? AND UserId=?", comic, actor.userId());
    audit.record(actor.userId(), "RATING_DELETED", "Comic", comic + "", "SUCCESS");
  }

  static String plainReview(String value) {
    if (value == null || value.isBlank()) return null;
    String text = value.strip();
    if (text.length() > 4000
        || text.indexOf('<') >= 0
        || text.indexOf('>') >= 0
        || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
      throw new DomainException(
          422, "INVALID_REVIEW", "Nhận xét tối đa 4.000 ký tự văn bản thuần.");
    return text;
  }

  private RatingView map(ResultSet row, int index) throws SQLException {
    return new RatingView(
        row.getLong("ComicId") + "",
        row.getLong("UserId") + "",
        row.getString("DisplayName"),
        row.getInt("Score"),
        row.getString("Review"),
        row.getTimestamp("UpdatedAt").toInstant());
  }
}
