package com.comicnet.catalog.entity;

import com.comicnet.catalog.api.CatalogDtos.ComicInput;
import com.comicnet.common.web.Ids;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "Comic", schema = "dbo")
public class ComicEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "OwnerUserId", nullable = false)
  private long ownerUserId;

  @Nationalized
  @Column(name = "Title", length = 300, nullable = false)
  private String title;

  @Column(name = "Slug", length = 200, nullable = false)
  private String slug;

  @Nationalized
  @Column(name = "Description", columnDefinition = "nvarchar(max)", nullable = false)
  private String description;

  @Column(name = "Type", length = 20, nullable = false)
  private String type;

  @JdbcTypeCode(java.sql.Types.CHAR)
  @Column(name = "CountryCode", length = 2, nullable = false)
  private String countryCode;

  @Column(name = "Status", length = 20, nullable = false)
  private String status;

  @Column(name = "AgeRating", length = 20, nullable = false)
  private String ageRating;

  @Column(name = "Visibility", length = 20, nullable = false)
  private String visibility = "HIDDEN";

  @Column(name = "CoverAssetId")
  private Long coverAssetId;

  @Column(name = "BannerAssetId")
  private Long bannerAssetId;

  @Column(name = "CreatedAt", insertable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "UpdatedAt", nullable = false)
  private Instant updatedAt;

  @Column(name = "DeletedAt")
  private Instant deletedAt;

  @Version
  @Column(name = "LockVersion")
  private long lockVersion;

  @JdbcTypeCode(java.sql.Types.BINARY)
  @Column(
      name = "RowVersion",
      insertable = false,
      updatable = false,
      columnDefinition = "rowversion",
      length = 8)
  private byte[] rowVersion;

  protected ComicEntity() {}

  public ComicEntity(long owner, ComicInput input) {
    ownerUserId = owner;
    apply(input);
  }

  public void apply(ComicInput input) {
    title = input.title().strip();
    slug = input.slug();
    description = input.description();
    type = input.type();
    countryCode = input.countryCode();
    status = input.status();
    ageRating = input.ageRating() == null || input.ageRating().isBlank() ? "ALL_AGE" : input.ageRating();
    coverAssetId = input.coverAssetId() == null ? null : Ids.parse(input.coverAssetId());
    bannerAssetId = input.bannerAssetId() == null ? null : Ids.parse(input.bannerAssetId());
    updatedAt = Instant.now();
  }

  public long id() {
    return id;
  }

  public long owner() {
    return ownerUserId;
  }

  public String visibility() {
    return visibility;
  }

  public void softDelete() {
    deletedAt = Instant.now();
    visibility = "ARCHIVED";
    updatedAt = deletedAt;
  }

  public void visibility(String value) {
    visibility = value;
    updatedAt = Instant.now();
  }
}
