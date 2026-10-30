package com.comicnet.publishing.repository;

import com.comicnet.common.web.DomainException;
import com.comicnet.publishing.api.PublishingDtos.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;

@Repository
public class PublishingRepository {
  private final JdbcTemplate jdbc;

  public PublishingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Instant instant(ResultSet rs, String column) throws SQLException {
    var value = rs.getObject(column, LocalDateTime.class);
    return value == null ? null : value.toInstant(ZoneOffset.UTC);
  }

  public String id(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value + "";
  }

  public RowMapper<Volume> volumeMapper() {
    return (rs, n) ->
        new Volume(
            id(rs, "Id"),
            id(rs, "TranslationId"),
            rs.getString("DisplayLabel"),
            rs.getBigDecimal("SortOrder").stripTrailingZeros().toPlainString());
  }

  public RowMapper<Chapter> chapterMapper() {
    return (rs, n) ->
        new Chapter(
            id(rs, "Id"),
            id(rs, "TranslationId"),
            id(rs, "VolumeId"),
            rs.getString("DisplayNumber"),
            rs.getString("Title"),
            rs.getBigDecimal("SortOrder").stripTrailingZeros().toPlainString(),
            rs.getString("AccessPolicy"),
            rs.getString("Visibility"),
            id(rs, "PublishedVersionId"),
            instant(rs, "PublishedAt"));
  }

  public RowMapper<UploadJob> jobMapper() {
    return (rs, n) ->
        new UploadJob(
            id(rs, "Id"),
            id(rs, "ChapterId"),
            id(rs, "VersionId"),
            rs.getString("Status"),
            rs.getInt("Attempts"),
            rs.getString("ErrorCode"),
            instant(rs, "CreatedAt"),
            instant(rs, "UpdatedAt"));
  }

  public Chapter chapter(long id) {
    return one(
        jdbc.query(
            "SELECT * FROM dbo.Chapter WHERE Id=? AND DeletedAt IS NULL", chapterMapper(), id));
  }

  public Volume volume(long id) {
    return one(jdbc.query("SELECT * FROM dbo.Volume WHERE Id=?", volumeMapper(), id));
  }

  public UploadJob job(long id) {
    return one(jdbc.query("SELECT * FROM dbo.UploadJob WHERE Id=?", jobMapper(), id));
  }

  public ChapterVersion version(long id) {
    return one(
        jdbc.query(
            "SELECT * FROM dbo.ChapterVersion WHERE Id=?",
            (rs, n) ->
                new ChapterVersion(
                    id(rs, "Id"),
                    id(rs, "ChapterId"),
                    rs.getInt("VersionNumber"),
                    rs.getString("State"),
                    pages(id),
                    id(rs, "CreatedBy"),
                    id(rs, "SubmittedBy"),
                    id(rs, "ReviewedBy"),
                    rs.getString("ReviewReason")),
            id));
  }

  public List<VersionPage> pages(long version) {
    return jdbc.query(
        "SELECT p.PageId,p.Position,p.AssetId,a.Status,a.Width,a.Height FROM dbo.ChapterVersionPage"
            + " p JOIN dbo.DigitalAsset a ON a.Id=p.AssetId WHERE p.VersionId=? ORDER BY"
            + " p.Position",
        (rs, n) ->
            new VersionPage(
                id(rs, "PageId"),
                rs.getInt("Position"),
                id(rs, "AssetId"),
                rs.getString("Status"),
                rs.getInt("Width"),
                rs.getInt("Height")),
        version);
  }

  public byte[] chapterToken(long id) {
    return token("Chapter", id);
  }

  public byte[] versionToken(long id) {
    return token("ChapterVersion", id);
  }

  public byte[] volumeToken(long id) {
    return token("Volume", id);
  }

  private byte[] token(String table, long id) {
    var rows = jdbc.queryForList("SELECT RowVersion FROM dbo." + table + " WHERE Id=?", id);
    if (rows.isEmpty()) throw DomainException.missing();
    return (byte[]) rows.getFirst().get("RowVersion");
  }

  public void touchVersion(long id) {
    jdbc.update("UPDATE dbo.ChapterVersion SET State=State WHERE Id=?", id);
  }

  public void state(long id, String state, boolean working) {
    jdbc.update(
        "UPDATE dbo.ChapterVersion SET State=?,IsWorkingCopy=? WHERE Id=?", state, working, id);
  }

  public boolean activeUpload(long version) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.UploadJob WHERE VersionId=? AND IsActive=1",
            Integer.class,
            version)
        > 0;
  }

  public void requireDraft(long version) {
    var state = version(version).state();
    if ((!state.equals("DRAFT") && !state.equals("NEEDS_CHANGES")) || activeUpload(version))
      throw new DomainException(
          409, "VERSION_NOT_EDITABLE", "Version đang xử lý hoặc đã khóa nội dung.");
  }

  private <T> T one(List<T> rows) {
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }
}
