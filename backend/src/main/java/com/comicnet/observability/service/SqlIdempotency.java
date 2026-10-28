package com.comicnet.observability.service;

import com.comicnet.common.web.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.LongSupplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** Called inside the resource transaction: request reservation and resource commit together. */
@Component
public class SqlIdempotency implements com.comicnet.observability.api.Idempotency {
  private final JdbcTemplate jdbc;
  private final JsonMapper json =
      JsonMapper.builder()
          .enable(tools.jackson.databind.MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .enable(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();

  public SqlIdempotency(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long create(
      long actor, String operation, String scope, String key, Object payload, LongSupplier create) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Idempotency requires a transaction.");
    UUID uuid;
    try {
      uuid = UUID.fromString(key);
      if (!uuid.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new DomainException(
          400, "INVALID_IDEMPOTENCY_KEY", "Cần Idempotency-Key theo định dạng UUID.");
    }
    String hash = hash(json.writeValueAsString(payload));
    var rows =
        jdbc.queryForList(
            "SELECT RequestHash,ResourceId FROM dbo.IdempotencyRecord WITH (UPDLOCK,HOLDLOCK) WHERE"
                + " ActorId=? AND Operation=? AND ScopeId=? AND RequestKey=?",
            actor,
            operation,
            scope,
            uuid.toString());
    if (!rows.isEmpty()) {
      if (!hash.equals(rows.getFirst().get("RequestHash")))
        throw new DomainException(
            409, "IDEMPOTENCY_CONFLICT", "Mã thao tác đã được dùng cho dữ liệu khác.");
      return ((Number) rows.getFirst().get("ResourceId")).longValue();
    }
    long id = create.getAsLong();
    jdbc.update(
        "INSERT"
            + " dbo.IdempotencyRecord(ActorId,Operation,ScopeId,RequestKey,RequestHash,ResourceId,ResponseRef)"
            + " VALUES(?,?,?,?,?,?,?)",
        actor,
        operation,
        scope,
        uuid.toString(),
        hash,
        id,
        operation + ":" + id);
    return id;
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
