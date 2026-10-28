package com.comicnet.search.service;

import com.comicnet.common.web.DomainException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchHistoryService {
  public record Entry(String query, Instant lastSearchedAt) {}

  private final JdbcTemplate jdbc;

  public SearchHistoryService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Entry> list(long user) {
    return jdbc.query(
        "SELECT TOP(50) Query,LastSearchedAt FROM dbo.SearchHistory WHERE UserId=? ORDER BY"
            + " LastSearchedAt DESC,Query",
        (rs, row) ->
            new Entry(
                rs.getString(1),
                rs.getObject(2, java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC)),
        user);
  }

  public static String normalize(String query) {
    if (query == null
        || query.isBlank()
        || query.length() > 200
        || query.codePoints().anyMatch(Character::isISOControl))
      throw new DomainException(
          422, "INVALID_SEARCH_QUERY", "Nhập từ khóa 1–200 ký tự, không chứa ký tự điều khiển.");
    return query.strip().replaceAll("\\s+", " ");
  }

  @Transactional
  public void remember(long user, String input) {
    String query = normalize(input);
    // Serialize one user's insert/trim, including their first search.
    jdbc.queryForList(
        "SELECT Query FROM dbo.SearchHistory WITH (UPDLOCK,HOLDLOCK) WHERE UserId=?",
        String.class,
        user);
    int updated =
        jdbc.update(
            "UPDATE dbo.SearchHistory SET LastSearchedAt=SYSUTCDATETIME() WHERE UserId=? AND"
                + " Query=?",
            user,
            query);
    if (updated == 0)
      jdbc.update("INSERT dbo.SearchHistory(UserId,Query) VALUES(?,?)", user, query);
    jdbc.update(
        "WITH recent AS (SELECT *,ROW_NUMBER() OVER(ORDER BY LastSearchedAt DESC,Query) AS n"
            + " FROM dbo.SearchHistory WHERE UserId=?) DELETE FROM recent WHERE n>50",
        user);
  }

  @Transactional
  public void delete(long user, String query) {
    if (query == null) jdbc.update("DELETE FROM dbo.SearchHistory WHERE UserId=?", user);
    else
      jdbc.update(
          "DELETE FROM dbo.SearchHistory WHERE UserId=? AND Query=?", user, normalize(query));
  }
}
