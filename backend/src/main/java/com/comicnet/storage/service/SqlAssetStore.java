package com.comicnet.storage.service;

import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.DomainException;
import com.comicnet.observability.api.AuditWriter;
import com.comicnet.observability.api.Idempotency;
import com.comicnet.storage.api.AssetStore;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class SqlAssetStore implements AssetStore {
  private final JdbcTemplate jdbc;
  private final S3Gateway objects;
  private final ImageDecoder decoder;
  private final Idempotency idempotency;
  private final AuditWriter audit;

  public SqlAssetStore(
      JdbcTemplate jdbc,
      S3Gateway objects,
      ImageDecoder decoder,
      Idempotency idempotency,
      AuditWriter audit) {
    this.jdbc = jdbc;
    this.objects = objects;
    this.decoder = decoder;
    this.idempotency = idempotency;
    this.audit = audit;
  }

  @Transactional
  public AssetView uploadCatalog(
      Identity actor, String kind, byte[] bytes, String mime, String key) {
    if (actor == null || (!actor.administrator() && !actor.roles().contains("CREATOR")))
      throw DomainException.forbidden();
    if (!Set.of("COVER", "BANNER").contains(kind))
      throw new DomainException(400, "INVALID_ASSET_KIND", "Loại ảnh không hợp lệ.");
    String hash = hash(bytes);
    long id =
        idempotency.create(
            actor.userId(),
            "uploadCatalogAsset",
            kind,
            key,
            Map.of(
                "hash",
                hash,
                "kind",
                kind,
                "declaredMime",
                mime == null ? "application/octet-stream" : mime),
            () -> store(actor.userId(), kind, bytes, mime));
    return get(id);
  }

  @Transactional
  public AssetView storePage(long actor, byte[] bytes) {
    return get(store(actor, "CHAPTER_PAGE", bytes, null));
  }

  private long store(long actor, String kind, byte[] bytes, String declaredMime) {
    var image = decoder.decode(bytes, declaredMime);
    String prefix = "assets/" + UUID.randomUUID() + "/";
    List<String> keys = List.of(prefix + "source", prefix + "display.jpg", prefix + "display.png");
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK)
              for (String objectKey : keys)
                try {
                  objects.delete(objectKey);
                } catch (Exception ignored) {
                  org.slf4j.LoggerFactory.getLogger(SqlAssetStore.class)
                      .warn("Asset rollback cleanup pending; object group={}", prefix);
                }
            else if (status == STATUS_UNKNOWN)
              org.slf4j.LoggerFactory.getLogger(SqlAssetStore.class)
                  .warn(
                      "Asset commit outcome unknown; retain object group={} for verification",
                      prefix);
          }
        });
    objects.put(keys.get(0), bytes, image.mime());
    objects.put(keys.get(1), image.jpeg(), "image/jpeg");
    objects.put(keys.get(2), image.png(), "image/png");
    long id =
        jdbc.queryForObject(
            "INSERT"
                + " dbo.DigitalAsset(UploadedBy,Kind,ObjectKey,SHA256,DetectedMime,ByteSize,Width,Height,Status,Bucket)"
                + " OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,?,?,'READY',?)",
            Long.class,
            actor,
            kind,
            keys.get(0),
            hash(bytes),
            image.mime(),
            bytes.length,
            image.width(),
            image.height(),
            objects.bucket());
    jdbc.update(
        "INSERT dbo.AssetVariant(AssetId,Format,ObjectKey,Width,Height,MimeType,ByteSize)"
            + " VALUES(?,'JPEG',?,?,?,'image/jpeg',?),(?,'PNG',?,?,?,'image/png',?)",
        id,
        keys.get(1),
        image.width(),
        image.height(),
        image.jpeg().length,
        id,
        keys.get(2),
        image.width(),
        image.height(),
        image.png().length);
    jdbc.update(
        "INSERT dbo.AssetAccessPolicy(AssetId,Policy,UrlTtlSeconds) VALUES(?,'PRIVATE',120)", id);
    audit.record(actor, "ASSET_STORED", "Asset", id + "", "SUCCESS");
    return id;
  }

  public AssetView get(long id) {
    var rows =
        jdbc.query(
            "SELECT * FROM dbo.DigitalAsset WHERE Id=?",
            (rs, n) ->
                new AssetView(
                    rs.getLong("Id") + "",
                    rs.getString("Kind"),
                    rs.getString("DetectedMime"),
                    rs.getLong("ByteSize"),
                    rs.getInt("Width"),
                    rs.getInt("Height"),
                    rs.getString("Status")),
            id);
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  public void requireOwned(long id, long owner, String kind) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.DigitalAsset a WHERE a.Id=? AND UploadedBy=? AND Kind=? AND"
                + " Status='READY' AND (SELECT COUNT(DISTINCT d.Format) FROM dbo.AssetVariant d"
                + " WHERE d.AssetId=a.Id AND d.Format IN ('JPEG','PNG'))=2",
            Integer.class,
            id,
            owner,
            kind)
        != 1)
      throw new DomainException(
          422, "ASSET_UNAVAILABLE", "Ảnh chưa sẵn sàng hoặc không thuộc tài khoản hiện tại.");
  }

  public DisplayAsset display(long id, String format) {
    if (!Set.of("JPEG", "PNG").contains(format))
      throw new DomainException(400, "INVALID_DISPLAY_FORMAT", "Định dạng hiển thị không hợp lệ.");
    var rows =
        jdbc.queryForList(
            "SELECT TOP(1) d.ObjectKey,d.Width,d.Height,d.MimeType,p.UrlTtlSeconds FROM"
                + " dbo.AssetVariant d JOIN dbo.DigitalAsset a ON a.Id=d.AssetId JOIN"
                + " dbo.AssetAccessPolicy p ON p.AssetId=a.Id WHERE a.Id=? AND a.Status='READY' AND"
                + " d.Format=? ORDER BY d.Width DESC,d.Height DESC",
            id,
            format);
    if (rows.isEmpty())
      throw new DomainException(503, "DERIVATIVE_UNAVAILABLE", "Ảnh hiển thị chưa sẵn sàng.");
    var row = rows.getFirst();
    long ttl = Math.min(120, ((Number) row.get("UrlTtlSeconds")).longValue());
    return new DisplayAsset(
        objects.sign((String) row.get("ObjectKey"), Duration.ofSeconds(ttl)),
        Instant.now().plusSeconds(ttl),
        ((Number) row.get("Width")).intValue(),
        ((Number) row.get("Height")).intValue(),
        (String) row.get("MimeType"));
  }

  public Map<Long, DisplayAsset> displayMany(List<Long> ids, String format) {
    if (!Set.of("JPEG", "PNG").contains(format))
      throw new DomainException(400, "INVALID_DISPLAY_FORMAT", "Định dạng hiển thị không hợp lệ.");
    if (ids.isEmpty()) return Map.of();
    var unique = new LinkedHashSet<>(ids);
    if (unique.size() > 500) throw new DomainException(413, "PAGE_LIMIT", "Version vượt giới hạn 500 trang.");
    var placeholders = String.join(",", Collections.nCopies(unique.size(), "?"));
    var params = new ArrayList<Object>();
    params.add(format);
    params.addAll(unique);
    var rows = jdbc.queryForList(
        "SELECT d.AssetId,d.ObjectKey,d.Width,d.Height,d.MimeType,p.UrlTtlSeconds FROM"
            + " dbo.AssetVariant d JOIN dbo.DigitalAsset a ON a.Id=d.AssetId JOIN"
            + " dbo.AssetAccessPolicy p ON p.AssetId=a.Id WHERE a.Status='READY' AND"
            + " d.Format=? AND d.AssetId IN (" + placeholders + ") ORDER BY d.AssetId,d.Width DESC,d.Height DESC",
        params.toArray());
    var result = new HashMap<Long, DisplayAsset>();
    for (var row : rows) {
      long id = ((Number) row.get("AssetId")).longValue();
      if (result.containsKey(id)) continue;
      long ttl = Math.min(120, ((Number) row.get("UrlTtlSeconds")).longValue());
      result.put(id, new DisplayAsset(
          objects.sign((String) row.get("ObjectKey"), Duration.ofSeconds(ttl)),
          Instant.now().plusSeconds(ttl),
          ((Number) row.get("Width")).intValue(),
          ((Number) row.get("Height")).intValue(),
          (String) row.get("MimeType")));
    }
    if (result.size() != unique.size())
      throw new DomainException(503, "DERIVATIVE_UNAVAILABLE", "Ảnh hiển thị chưa sẵn sàng.");
    return result;
  }

  private String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
