package com.comicnet.catalog.service;

import com.comicnet.identity.api.*;
import com.comicnet.catalog.api.*;
import com.comicnet.catalog.api.CatalogDtos.*;
import com.comicnet.catalog.entity.ComicEntity;
import com.comicnet.catalog.repository.ComicRepository;
import com.comicnet.common.web.*;
import com.comicnet.creator.api.CreatorDirectory;
import com.comicnet.observability.api.*;
import com.comicnet.storage.api.AssetStore;
import jakarta.validation.Validator;
import java.sql.*;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CatalogService implements CatalogAccess, CatalogQueries {
  private final JdbcTemplate jdbc;
  private final ComicRepository comics;
  private final CreatorDirectory teams;
  private final IdentityService identities;
  private final AssetStore assets;
  private final Idempotency idempotency;
  private final AuditWriter audit;
  private final Validator validator;
  private final JsonMapper json = JsonMapper.builder().build();
  private static final Map<String, String> LOOKUPS =
      Map.of(
          "GENRE",
          "Genre",
          "TAG",
          "Tag",
          "CONTENT_WARNING",
          "ContentWarning",
          "AUTHOR",
          "Author",
          "PUBLISHER",
          "Publisher");
  private static final Set<String> INPUT =
      Set.of(
          "title",
          "slug",
          "description",
          "type",
          "countryCode",
          "status",
          "ageRating",
          "alternativeTitles",
          "genreIds",
          "tagIds",
          "warningIds",
          "contributors",
          "publisherIds",
          "coverAssetId",
          "bannerAssetId");

  public CatalogService(
      JdbcTemplate jdbc,
      ComicRepository comics,
      CreatorDirectory teams,
      IdentityService identities,
      AssetStore assets,
      Idempotency idempotency,
      AuditWriter audit,
      Validator validator) {
    this.jdbc = jdbc;
    this.comics = comics;
    this.teams = teams;
    this.identities = identities;
    this.assets = assets;
    this.idempotency = idempotency;
    this.audit = audit;
    this.validator = validator;
  }

  private boolean reviewer(Identity actor) {
    return actor != null && actor.administrator();
  }

  private String nullableId(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value + "";
  }

  private String cover(Long id, boolean ageAllowed) {
    return id == null ? null : assets.display(id, "JPEG").url();
  }

  private Instant instant(ResultSet rs, String field) throws SQLException {
    return rs.getObject(field, java.time.LocalDateTime.class).toInstant(ZoneOffset.UTC);
  }

  private List<String> ids(String table, String column, long comic) {
    return jdbc
        .queryForList(
            "SELECT " + column + " FROM dbo." + table + " WHERE ComicId=? ORDER BY " + column,
            Long.class,
            comic)
        .stream()
        .map(Object::toString)
        .toList();
  }

  private ComicDetail detail(long id) {
    var rows =
        jdbc.query(
            "SELECT c.*" + AGGREGATES + " FROM dbo.Comic c WHERE c.Id=? AND c.DeletedAt IS NULL",
            (rs, n) -> {
              String coverId = nullableId(rs, "CoverAssetId"),
                  bannerId = nullableId(rs, "BannerAssetId");
              boolean allAge = true;
              java.math.BigDecimal average = rs.getBigDecimal("Rating");
              long readValue = rs.getLong("Views");
              Long views = rs.wasNull() ? null : readValue;
              return new ComicDetail(
                  rs.getLong("Id") + "",
                  rs.getString("Title"),
                  rs.getString("Slug"),
                  rs.getString("Description"),
                  rs.getString("Type"),
                  rs.getString("CountryCode"),
                  rs.getString("Status"),
                  rs.getString("AgeRating"),
                  jdbc.queryForList(
                      "SELECT Title FROM dbo.ComicAlternativeTitle WHERE ComicId=? ORDER BY Id",
                      String.class,
                      id),
                  ids("ComicGenre", "GenreId", id),
                  ids("ComicTag", "TagId", id),
                  ids("ComicContentWarning", "ContentWarningId", id),
                  jdbc.query(
                      "SELECT AuthorId,ContributionType FROM dbo.ComicAuthor WHERE ComicId=? ORDER"
                          + " BY AuthorId,ContributionType",
                      (credit, i) -> new AuthorCredit(credit.getLong(1) + "", credit.getString(2)),
                      id),
                  ids("ComicPublisher", "PublisherId", id),
                  coverId,
                  bannerId,
                  rs.getString("Visibility"),
                  rs.getLong("OwnerUserId") + "",
                  instant(rs, "UpdatedAt"),
                  average == null ? null : average.doubleValue(),
                  views,
                  cover(coverId == null ? null : Long.valueOf(coverId), allAge),
                  cover(bannerId == null ? null : Long.valueOf(bannerId), allAge));
            },
            id);
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  public boolean publicComic(long id) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.Comic WHERE Id=? AND Visibility='PUBLIC' AND DeletedAt IS"
                + " NULL",
            Integer.class,
            id)
        == 1;
  }

  public ComicDetail publicDetail(long id) {
    if (!publicComic(id)) throw DomainException.missing();
    return detail(id);
  }

  public ComicDetail bySlug(String slug) {
    var ids =
        jdbc.queryForList(
            "SELECT Id FROM dbo.Comic WHERE Slug=? AND Visibility='PUBLIC' AND DeletedAt IS NULL",
            Long.class,
            slug);
    if (ids.isEmpty()) throw DomainException.missing();
    return detail(ids.getFirst());
  }

  public byte[] version(long id) {
    var rows =
        jdbc.queryForList("SELECT RowVersion FROM dbo.Comic WHERE Id=? AND DeletedAt IS NULL", id);
    if (rows.isEmpty()) throw DomainException.missing();
    return (byte[]) rows.getFirst().get("RowVersion");
  }

  private void owner(Identity actor, long id) {
    if (actor == null) throw DomainException.unauthorized();
    var rows =
        jdbc.queryForList("SELECT OwnerUserId FROM dbo.Comic WHERE Id=? AND DeletedAt IS NULL", id);
    if (rows.isEmpty()) throw DomainException.missing();
    if (!actor.administrator()
        && (!actor.roles().contains("CREATOR")
            || ((Number) rows.getFirst().get("OwnerUserId")).longValue() != actor.userId()))
      throw DomainException.missing();
  }

  public ComicDetail managedDetail(Identity actor, long id) {
    if (reviewer(actor)) return detail(id);
    if (actor == null) throw DomainException.unauthorized();
    var owner =
        jdbc.queryForList(
            "SELECT OwnerUserId FROM dbo.Comic WHERE Id=? AND DeletedAt IS NULL", Long.class, id);
    if (owner.isEmpty()) throw DomainException.missing();
    boolean own = actor.roles().contains("CREATOR") && owner.getFirst() == actor.userId();
    boolean member =
        jdbc
            .queryForList("SELECT TeamId FROM dbo.ComicTranslation WHERE ComicId=?", Long.class, id)
            .stream()
            .anyMatch(team -> teams.has(actor, team, "chapter.preview"));
    if (!own && !member) throw DomainException.missing();
    return detail(id);
  }

  private void createAllowed(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    if (!actor.administrator() && !actor.roles().contains("CREATOR"))
      throw DomainException.forbidden();
  }

  @Transactional
  public ComicDetail create(Identity actor, ComicInput input, String key) {
    createAllowed(actor);
    // Translations and the chapter permissions creators need are team-scoped, so a creator who
    // owns no active team receives a personal workspace instead of a 403 on the whole workflow.
    if (!actor.administrator()) teams.ensurePersonalTeam(actor);
    validateInput(actor, actor.userId(), input);
    long id =
        idempotency.create(
            actor.userId(),
            "createComic",
            "comics",
            key,
            input,
            () -> {
              var comic = comics.saveAndFlush(new ComicEntity(actor.userId(), input));
              links(comic.id(), input);
              audit.record(actor.userId(), "COMIC_CREATED", "Comic", comic.id() + "", "SUCCESS");
              return comic.id();
            });
    return detail(id);
  }

  @Transactional
  public ComicDetail patch(Identity actor, long id, JsonNode patch, String etag) {
    owner(actor, id);
    ComicEntity comic = comics.lockById(id).orElseThrow(DomainException::missing);
    Etags.require(etag, version(id));
    if (!patch.isObject() || patch.isEmpty())
      throw new DomainException(400, "INVALID_PATCH", "Cần ít nhất một trường hợp lệ.");
    var merged = json.createObjectNode();
    JsonNode current = json.valueToTree(detail(id));
    for (String field : INPUT) merged.set(field, current.get(field));
    for (var entry : patch.properties()) {
      if (!INPUT.contains(entry.getKey()))
        throw new DomainException(400, "INVALID_PATCH", "Trường cập nhật không được hỗ trợ.");
      merged.set(entry.getKey(), entry.getValue());
    }
    ComicInput input;
    try {
      input = json.treeToValue(merged, ComicInput.class);
    } catch (Exception e) {
      throw new DomainException(400, "INVALID_PATCH", "Dữ liệu cập nhật không hợp lệ.");
    }
    validateInput(actor, comic.owner(), input);
    comic.apply(input);
    comics.saveAndFlush(comic);
    links(id, input);
    audit.record(actor.userId(), "COMIC_UPDATED", "Comic", id + "", "SUCCESS");
    return detail(id);
  }

  private void validateInput(Identity actor, long owner, ComicInput input) {
    if (!validator.validate(input).isEmpty())
      throw new DomainException(400, "VALIDATION_FAILED", "Thông tin truyện không hợp lệ.");
    checkRefs("Genre", input.genreIds());
    checkRefs("Tag", input.tagIds());
    checkRefs("ContentWarning", input.warningIds());
    checkRefs("Publisher", input.publisherIds());
    if (input.contributors().stream().distinct().count() != input.contributors().size())
      throw new DomainException(400, "DUPLICATE_CREDIT", "Tác giả và vai trò không được trùng.");
    for (var credit : input.contributors()) checkRefs("Author", List.of(credit.authorId()));
    var titles = input.alternativeTitles().stream().map(this::normalized).toList();
    if (titles.stream().distinct().count() != titles.size())
      throw new DomainException(400, "DUPLICATE_TITLE", "Tên khác bị trùng.");
    if (input.coverAssetId() != null)
      ownedAsset(actor, Ids.parse(input.coverAssetId()), owner, "COVER");
    if (input.bannerAssetId() != null)
      ownedAsset(actor, Ids.parse(input.bannerAssetId()), owner, "BANNER");
  }

  private void ownedAsset(Identity actor, long asset, long owner, String kind) {
    try {
      assets.requireOwned(asset, owner, kind);
    } catch (DomainException e) {
      if (actor.administrator() && actor.userId() != owner)
        assets.requireOwned(asset, actor.userId(), kind);
      else throw e;
    }
  }

  private String normalized(String text) {
    return Normalizer.normalize(text.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }

  private void checkRefs(String table, List<String> values) {
    if (values.stream().distinct().count() != values.size())
      throw new DomainException(400, "DUPLICATE_REFERENCE", "Mã tham chiếu bị trùng.");
    for (String value : values)
      if (jdbc.queryForObject(
              "SELECT COUNT(*) FROM dbo." + table + " WHERE Id=?", Integer.class, Ids.parse(value))
          != 1)
        throw new DomainException(
            422, "METADATA_NOT_FOUND", "Thông tin phân loại hoặc tác giả không tồn tại.");
  }

  private void links(long id, ComicInput input) {
    for (String table :
        List.of(
            "ComicAlternativeTitle",
            "ComicAuthor",
            "ComicGenre",
            "ComicTag",
            "ComicContentWarning",
            "ComicPublisher")) jdbc.update("DELETE dbo." + table + " WHERE ComicId=?", id);
    for (String title : input.alternativeTitles())
      jdbc.update(
          "INSERT dbo.ComicAlternativeTitle(ComicId,Title,NormalizedTitle) VALUES(?,?,?)",
          id,
          title.strip(),
          normalized(title));
    join(id, "ComicGenre", "GenreId", input.genreIds());
    join(id, "ComicTag", "TagId", input.tagIds());
    join(id, "ComicContentWarning", "ContentWarningId", input.warningIds());
    join(id, "ComicPublisher", "PublisherId", input.publisherIds());
    for (var credit : input.contributors())
      jdbc.update(
          "INSERT dbo.ComicAuthor(ComicId,AuthorId,ContributionType) VALUES(?,?,?)",
          id,
          Ids.parse(credit.authorId()),
          credit.contribution());
  }

  private void join(long comic, String table, String column, List<String> ids) {
    for (String id : ids)
      jdbc.update(
          "INSERT dbo." + table + "(ComicId," + column + ") VALUES(?,?)", comic, Ids.parse(id));
  }

  @Transactional
  public ComicDetail visibility(Identity actor, long id, VisibilityPut input, String etag) {
    identities.requireAdministrator(actor);
    var comic = comics.lockById(id).orElseThrow(DomainException::missing);
    Etags.require(etag, version(id));
    comic.visibility(input.visibility());
    comics.saveAndFlush(comic);
    audit.record(
        actor.userId(),
        "COMIC_VISIBILITY_UPDATED",
        "Comic",
        id + "",
        "SUCCESS",
        Map.of("visibility", input.visibility(), "reason", input.reason()));
    return detail(id);
  }

  @Transactional
  public void softDelete(Identity actor, long id, String etag) {
    if (actor == null) throw DomainException.unauthorized();
    var comic = comics.lockById(id).orElseThrow(DomainException::missing);
    owner(actor, id);
    Etags.require(etag, version(id));
    comic.softDelete();
    comics.saveAndFlush(comic);
    audit.record(actor.userId(), "COMIC_SOFT_DELETED", "Comic", id + "", "SUCCESS");
  }

  public Metadata metadata() {
    return new Metadata(
        lookups("Genre"),
        lookups("Tag"),
        lookups("ContentWarning"),
        lookups("Author"),
        lookups("Publisher"));
  }

  private List<Lookup> lookups(String table) {
    return jdbc.query(
        "SELECT TOP(500) Id,Code,Name FROM dbo." + table + " ORDER BY Name,Id",
        (rs, n) -> new Lookup(rs.getLong(1) + "", rs.getString(2), rs.getString(3)));
  }

  @Transactional
  public Lookup createMetadata(Identity actor, MetadataCreate input, String key) {
    identities.requireAdministrator(actor);
    String table = LOOKUPS.get(input.kind());
    if (table == null || !validator.validate(input).isEmpty())
      throw new DomainException(400, "INVALID_METADATA", "Thông tin metadata không hợp lệ.");
    long id =
        idempotency.create(
            actor.userId(),
            "createMetadata",
            input.kind(),
            key,
            input,
            () -> {
              if (jdbc.queryForObject("SELECT COUNT(*) FROM dbo." + table, Integer.class) >= 500)
                throw new DomainException(
                    409, "METADATA_LIMIT", "Đã đạt giới hạn 500 mục cho loại metadata này.");
              long created =
                  jdbc.queryForObject(
                      "INSERT dbo." + table + "(Code,Name) OUTPUT INSERTED.Id VALUES(?,?)",
                      Long.class,
                      input.code(),
                      input.name().strip());
              audit.record(actor.userId(), "METADATA_CREATED", table, created + "", "SUCCESS");
              return created;
            });
    return jdbc.queryForObject(
        "SELECT Id,Code,Name FROM dbo." + table + " WHERE Id=?",
        (rs, n) -> new Lookup(rs.getLong(1) + "", rs.getString(2), rs.getString(3)),
        id);
  }

  @Transactional(readOnly = true)
  public PageResult<ComicSummary> list(Filter filter, int page, int size) {
    boolean fullText =
        filter.query() != null
            && !filter.query().isBlank()
            && jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys.fulltext_indexes WHERE"
                        + " object_id=OBJECT_ID('dbo.Comic') AND is_enabled=1",
                    Integer.class)
                > 0;
    if (fullText) {
      try {
        return searchList(filter, page, size, true);
      } catch (org.springframework.dao.DataAccessException unavailable) {
        // Index/service temporarily unavailable: the parameterized baseline remains authoritative.
        return searchList(filter, page, size, false);
      }
    }
    return searchList(filter, page, size, false);
  }

  private PageResult<ComicSummary> searchList(Filter filter, int page, int size, boolean fullText) {
    PageResult.validate(page, size);
    List<Object> args = new ArrayList<>();
    StringBuilder where =
        new StringBuilder(
            " WHERE c.DeletedAt IS NULL AND c.Visibility='PUBLIC'");
    String query = filter.query() == null ? "" : filter.query().strip();
    if (query.length() > 200)
      throw new DomainException(400, "INVALID_QUERY", "Từ khóa tìm kiếm quá dài.");
    if (!query.isBlank()) {
      where.append(
          " AND (CHARINDEX(?,c.Title)>0 OR EXISTS(SELECT 1 FROM dbo.ComicAlternativeTitle a WHERE"
              + " a.ComicId=c.Id AND CHARINDEX(?,a.Title)>0) OR EXISTS(SELECT 1 FROM"
              + " dbo.ComicAuthor ca JOIN dbo.Author a ON a.Id=ca.AuthorId WHERE ca.ComicId=c.Id"
              + " AND CHARINDEX(?,a.Name)>0) OR EXISTS(SELECT 1 FROM dbo.ComicGenre cg JOIN"
              + " dbo.Genre g ON g.Id=cg.GenreId WHERE cg.ComicId=c.Id AND"
              + " CHARINDEX(?,g.Name)>0)"
              + (fullText
                  ? " OR c.Id IN (SELECT [KEY] FROM FREETEXTTABLE(dbo.Comic,(Title,Description),?))"
                  : "")
              + ")");
      args.add(query);
      args.add(query);
      args.add(query);
      args.add(query);
      if (fullText) args.add(query);
    }
    if (filter.type() != null) {
      if (!Set.of("MANGA", "MANHWA", "MANHUA", "COMIC").contains(filter.type()))
        throw new DomainException(400, "INVALID_TYPE", "Loại truyện không hợp lệ.");
      where.append(" AND c.Type=?");
      args.add(filter.type());
    }
    if (filter.status() != null) {
      if (!Set.of("ONGOING", "COMPLETED", "HIATUS", "DROPPED").contains(filter.status()))
        throw new DomainException(400, "INVALID_STATUS", "Trạng thái truyện không hợp lệ.");
      where.append(" AND c.Status=?");
      args.add(filter.status());
    }
    if (filter.genreId() != null) {
      where.append(
          " AND EXISTS(SELECT 1 FROM dbo.ComicGenre g WHERE g.ComicId=c.Id AND g.GenreId=?)");
      args.add(Ids.parse(filter.genreId()));
    }
    if (filter.tagId() != null) {
      where.append(" AND EXISTS(SELECT 1 FROM dbo.ComicTag t WHERE t.ComicId=c.Id AND t.TagId=?)");
      args.add(Ids.parse(filter.tagId()));
    }
    String sort = filter.sort() == null ? "UPDATED_DESC" : filter.sort();
    if (!Set.of("UPDATED_DESC", "TITLE_ASC", "VIEWS_DESC", "RATING_DESC", "POPULARITY_DESC")
        .contains(sort))
      throw new DomainException(400, "INVALID_SORT", "Thứ tự sắp xếp không hợp lệ.");
    long total =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.Comic c" + where, Long.class, args.toArray());
    args.add(PageResult.offset(page, size));
    args.add(size);
    return PageResult.of(
        jdbc.query(
            "SELECT c.*"
                + AGGREGATES
                + " FROM dbo.Comic c"
                + where
                + " ORDER BY "
                + switch (sort) {
                  case "TITLE_ASC" -> "c.Title,c.Id";
                  case "VIEWS_DESC" -> VIEWS_ORDER + " DESC,c.Id DESC";
                  case "RATING_DESC" -> RATING_ORDER + " DESC,c.Id DESC";
                  case "POPULARITY_DESC" ->
                      "("
                          + VIEWS_ORDER
                          + "+3*"
                          + COMPLETION_ORDER
                          + "+5*"
                          + RATING_ORDER
                          + ") DESC,c.Id DESC";
                  default -> "c.UpdatedAt DESC,c.Id DESC";
                }
                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            summaryMapper(),
            args.toArray()),
        page,
        size,
        total);
  }

  /** Live rating average and all-time valid read count, published next to every summary. */
  private static final String AGGREGATES =
      ",(SELECT COALESCE(CAST(AVG(CAST(r.Score AS decimal(10,2))) AS decimal(10,2)),0) FROM"
          + " dbo.ComicRating r JOIN dbo.[User] u ON u.Id=r.UserId WHERE r.ComicId=c.Id AND"
          + " u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)) AS Rating,(SELECT"
          + " COALESCE(SUM(s.Views),0) FROM dbo.ComicStatDaily s WHERE s.ComicId=c.Id) AS Views";

  private static final String RATING_ORDER =
      "(SELECT COALESCE(AVG(CAST(r.Score AS decimal(10,2))),0) FROM dbo.ComicRating r JOIN"
          + " dbo.[User] u ON u.Id=r.UserId WHERE r.ComicId=c.Id AND u.Status='ACTIVE' AND"
          + " (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL))";
  private static final String VIEWS_ORDER =
      "(SELECT COALESCE(SUM(s.Views),0) FROM dbo.ComicStatDaily s WHERE s.ComicId=c.Id)";
  private static final String COMPLETION_ORDER =
      "(SELECT COALESCE(SUM(s.Completions),0) FROM dbo.ComicStatDaily s WHERE s.ComicId=c.Id)";

  private org.springframework.jdbc.core.RowMapper<ComicSummary> summaryMapper() {
    return (rs, n) -> {
      long asset = rs.getLong("CoverAssetId");
      Long coverId = rs.wasNull() ? null : asset;
      Double rating = null;
      java.math.BigDecimal average = rs.getBigDecimal("Rating");
      if (average != null) rating = average.doubleValue();
      long viewValue = rs.getLong("Views");
      Long views = rs.wasNull() ? null : viewValue;
      String visibility = rs.getString("Visibility");
      String publicationState = "PUBLIC".equals(visibility) ? "PUBLISHED" : "DRAFT";
      return new ComicSummary(
          rs.getLong("Id") + "",
          rs.getString("Title"),
          rs.getString("Slug"),
          rs.getString("Type"),
          rs.getString("AgeRating"),
          visibility,
          publicationState,
          cover(coverId, true),
          instant(rs, "UpdatedAt"),
          rating,
          views);
    };
  }

  public List<ComicSummary> similar(long id, int size) {
    if (size < 1 || size > 20)
      throw new DomainException(400, "INVALID_PAGE", "Số gợi ý phải từ 1 đến 20.");
    summary(id);
    return jdbc.query(
        "SELECT TOP(?) c.*"
            + AGGREGATES
            + " FROM dbo.Comic c CROSS JOIN dbo.Comic source WHERE source.Id=? AND"
            + " c.Id<>source.Id AND c.Visibility='PUBLIC' AND c.DeletedAt IS NULL AND"
            + " ORDER BY (4*(SELECT COUNT(*) FROM dbo.ComicGenre a JOIN"
            + " dbo.ComicGenre b ON b.GenreId=a.GenreId WHERE a.ComicId=c.Id AND"
            + " b.ComicId=source.Id) +2*(SELECT COUNT(*) FROM dbo.ComicTag a JOIN dbo.ComicTag b ON"
            + " b.TagId=a.TagId WHERE a.ComicId=c.Id AND b.ComicId=source.Id) +CASE WHEN"
            + " c.Type=source.Type THEN 1 ELSE 0 END) DESC,c.UpdatedAt DESC,c.Id DESC",
        summaryMapper(),
        size,
        id);
  }

  public ComicSummary summary(long id) {
    var rows =
        jdbc.query(
            "SELECT c.*"
                + AGGREGATES
                + " FROM dbo.Comic c WHERE c.Id=? AND c.Visibility='PUBLIC' AND c.DeletedAt IS NULL",
            summaryMapper(),
            id);
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  public PageResult<ComicSummary> managedList(Identity actor, int page, int size) {
    if (actor == null) throw DomainException.unauthorized();
    PageResult.validate(page, size);
    List<Object> args = new ArrayList<>();
    String filter = " WHERE c.DeletedAt IS NULL";
    if (!reviewer(actor)) {
      var teamIds = teams.teamIds(actor, "chapter.preview");
      filter += " AND (c.OwnerUserId=? AND ?=1";
      args.add(actor.userId());
      args.add(actor.roles().contains("CREATOR") ? 1 : 0);
      if (!teamIds.isEmpty()) {
        filter +=
            " OR EXISTS(SELECT 1 FROM dbo.ComicTranslation t WHERE t.ComicId=c.Id AND t.TeamId IN ("
                + String.join(",", Collections.nCopies(teamIds.size(), "?"))
                + "))";
        args.addAll(teamIds);
      }
      filter += ")";
    }
    long total =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.Comic c" + filter, Long.class, args.toArray());
    args.add(PageResult.offset(page, size));
    args.add(size);
    return PageResult.of(
        jdbc.query(
            "SELECT c.*"
                + AGGREGATES
                + " FROM dbo.Comic c"
                + filter
                + " ORDER BY c.UpdatedAt DESC,c.Id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            summaryMapper(),
            args.toArray()),
        page,
        size,
        total);
  }

  private org.springframework.jdbc.core.RowMapper<Translation> translationMapper() {
    return (rs, n) ->
        new Translation(
            rs.getLong("Id") + "",
            rs.getLong("ComicId") + "",
            rs.getLong("TeamId") + "",
            rs.getString("LanguageTag"),
            rs.getString("EditionKey"),
            rs.getString("TitleOverride"),
            rs.getString("Visibility"));
  }

  public PageResult<Translation> translations(
      Identity actor, long comic, int page, int size, boolean managed) {
    if (managed) managedDetail(actor, comic);
    else if (!publicComic(comic)) throw DomainException.missing();
    PageResult.validate(page, size);
    var all =
        jdbc.query(
            "SELECT * FROM dbo.ComicTranslation WHERE ComicId=?"
                + (managed ? "" : " AND Visibility='PUBLIC'")
                + " ORDER BY Id",
            translationMapper(),
            comic);
    var visible =
        managed && !reviewer(actor)
            ? all.stream()
                .filter(t -> teams.has(actor, Ids.parse(t.teamId()), "chapter.preview"))
                .toList()
            : all;
    long offset = PageResult.offset(page, size);
    return PageResult.of(
        visible.stream().skip(offset).limit(size).toList(), page, size, visible.size());
  }

  public byte[] translationVersion(long id) {
    var rows = jdbc.queryForList("SELECT RowVersion FROM dbo.ComicTranslation WHERE Id=?", id);
    if (rows.isEmpty()) throw DomainException.missing();
    return (byte[]) rows.getFirst().get("RowVersion");
  }

  public Translation translation(Identity actor, long id) {
    scopedTranslation(actor, id, "chapter.preview", false);
    return translationDetail(id);
  }

  private Translation translationDetail(long id) {
    var rows = jdbc.query("SELECT * FROM dbo.ComicTranslation WHERE Id=?", translationMapper(), id);
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  @Transactional
  public Translation createTranslation(
      Identity actor, long comic, TranslationCreate input, String key) {
    owner(actor, comic);
    comics.lockById(comic).orElseThrow(DomainException::missing);
    long team = Ids.parse(input.teamId());
    teams.require(actor, team, "team.manage");
    if (actor.administrator() && !teams.get(actor, team).status().equals("ACTIVE"))
      throw DomainException.missing();
    String language;
    try {
      language = new Locale.Builder().setLanguageTag(input.languageTag()).build().toLanguageTag();
      if (language.equals("und")) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new DomainException(400, "INVALID_LANGUAGE", "Mã ngôn ngữ không hợp lệ.");
    }
    long id =
        idempotency.create(
            actor.userId(),
            "createTranslation",
            comic + "",
            key,
            input,
            () -> {
              long created =
                  jdbc.queryForObject(
                      "INSERT"
                          + " dbo.ComicTranslation(ComicId,TeamId,LanguageTag,EditionKey,TitleOverride)"
                          + " OUTPUT INSERTED.Id VALUES(?,?,?,?,?)",
                      Long.class,
                      comic,
                      team,
                      language,
                      input.editionKey().strip(),
                      input.titleOverride());
              audit.record(
                  actor.userId(),
                  "TRANSLATION_CREATED",
                  "ComicTranslation",
                  created + "",
                  "SUCCESS");
              return created;
            });
    return translationDetail(id);
  }

  @Transactional
  public Translation patchTranslation(Identity actor, long id, JsonNode patch, String etag) {
    scopedTranslation(actor, id, "catalog.manage", true);
    Etags.require(etag, translationVersion(id));
    var current = translationDetail(id);
    if (!patch.isObject() || patch.isEmpty())
      throw new DomainException(400, "INVALID_PATCH", "Cần dữ liệu cập nhật.");
    String title = current.titleOverride(), visibility = current.visibility();
    for (var entry : patch.properties())
      switch (entry.getKey()) {
        case "titleOverride" -> {
          if (!entry.getValue().isString()
              || entry.getValue().asText().isBlank()
              || entry.getValue().asText().length() > 300)
            throw new DomainException(400, "INVALID_TITLE", "Tên bản dịch không hợp lệ.");
          title = entry.getValue().asText().strip();
        }
        case "visibility" -> {
          if (!Set.of("PUBLIC", "HIDDEN", "ARCHIVED").contains(entry.getValue().asText()))
            throw new DomainException(
                400, "INVALID_VISIBILITY", "Trạng thái hiển thị không hợp lệ.");
          visibility = entry.getValue().asText();
        }
        default ->
            throw new DomainException(400, "INVALID_PATCH", "Trường cập nhật không được hỗ trợ.");
      }
    jdbc.update(
        "UPDATE dbo.ComicTranslation SET TitleOverride=?,Visibility=? WHERE Id=?",
        title,
        visibility,
        id);
    audit.record(actor.userId(), "TRANSLATION_UPDATED", "ComicTranslation", id + "", "SUCCESS");
    return translationDetail(id);
  }

  public TranslationScope scopedTranslation(
      Identity actor, long id, String permission, boolean lock) {
    var scope = scope(id, lock, false);
    if (actor == null) throw DomainException.unauthorized();
    if (!actor.administrator()
        && !(Set.of("chapter.preview", "chapter.publish", "content.hide").contains(permission)
            && identities.permitted(actor, permission)))
      teams.require(actor, scope.teamId(), permission);
    return scope;
  }

  public TranslationScope publicTranslation(long id, boolean lock) {
    var scope = scope(id, lock, true);
    if (!scope.comicVisibility().equals("PUBLIC")
        || !scope.translationVisibility().equals("PUBLIC")
        ) throw DomainException.missing();
    return scope;
  }

  public TranslationScope lockForJob(long id) {
    return scope(id, true, false, true);
  }

  public void publishedContentChanged(long comicId) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      throw new IllegalStateException("Catalog updates require the publishing transaction.");
    jdbc.update("UPDATE dbo.Comic SET UpdatedAt=SYSUTCDATETIME() WHERE Id=?", comicId);
  }

  private TranslationScope scope(long id, boolean lock, boolean readOnly) {
    return scope(id, lock, readOnly, false);
  }

  // Only the trusted durable-job cleanup entry point may lock a soft-deleted parent.
  private TranslationScope scope(long id, boolean lock, boolean readOnly, boolean includeDeleted) {
    if (lock
        && !org.springframework.transaction.support.TransactionSynchronizationManager
            .isActualTransactionActive())
      throw new IllegalStateException("Resource locking requires a transaction.");
    var parents =
        jdbc.queryForList("SELECT ComicId FROM dbo.ComicTranslation WHERE Id=?", Long.class, id);
    if (parents.isEmpty()) throw DomainException.missing();
    String hint = lock ? (readOnly ? " WITH (HOLDLOCK)" : " WITH (UPDLOCK,HOLDLOCK)") : "";
    var parent =
        jdbc.queryForList(
            "SELECT Visibility,AgeRating,Title FROM dbo.Comic"
                + hint
                + " WHERE Id=?"
                + (includeDeleted ? "" : " AND DeletedAt IS NULL"),
            parents.getFirst());
    if (parent.isEmpty()) throw DomainException.missing();
    var rows =
        jdbc.query(
            "SELECT * FROM dbo.ComicTranslation" + hint + " WHERE Id=?",
            (rs, n) ->
                new TranslationScope(
                    rs.getLong("Id"),
                    rs.getLong("ComicId"),
                    rs.getLong("TeamId"),
                    (String) parent.getFirst().get("Visibility"),
                    rs.getString("Visibility"),
                    (String) parent.getFirst().get("AgeRating"),
                    (String) parent.getFirst().get("Title")),
            id);
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }
}
