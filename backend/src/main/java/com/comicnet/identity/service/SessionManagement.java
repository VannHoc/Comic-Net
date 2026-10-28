package com.comicnet.identity.service;

import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.*;
import com.comicnet.observability.api.AuditWriter;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionManagement {
  public record SessionView(
      String id,
      String deviceLabel,
      Instant createdAt,
      Instant lastSeenAt,
      Instant expiresAt,
      boolean current) {}

  private final JdbcTemplate jdbc;
  private final AuditWriter audit;

  public SessionManagement(JdbcTemplate jdbc, AuditWriter audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  public PageResult<SessionView> list(Identity actor, int page, int size) {
    if (actor == null) throw DomainException.unauthorized();
    PageResult.validate(page, size);
    String where = " WHERE UserId=? AND RevokedAt IS NULL AND ExpiresAt>SYSUTCDATETIME()";
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.[Session]" + where, Long.class, actor.userId());
    return PageResult.of(
        jdbc.query(
            "SELECT * FROM dbo.[Session]"
                + where
                + " ORDER BY CreatedAt DESC,Id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            (rs, row) ->
                new SessionView(
                    rs.getLong("Id") + "",
                    rs.getString("DeviceLabel"),
                    rs.getObject("CreatedAt", java.time.LocalDateTime.class)
                        .toInstant(java.time.ZoneOffset.UTC),
                    rs.getObject("LastSeenAt", java.time.LocalDateTime.class)
                        .toInstant(java.time.ZoneOffset.UTC),
                    rs.getObject("ExpiresAt", java.time.LocalDateTime.class)
                        .toInstant(java.time.ZoneOffset.UTC),
                    rs.getLong("Id") == actor.sessionId()),
            actor.userId(),
            PageResult.offset(page, size),
            size),
        page,
        size,
        count);
  }

  @Transactional
  public void revoke(Identity actor, long session) {
    if (actor == null) throw DomainException.unauthorized();
    var own =
        jdbc.queryForList(
            "SELECT Id FROM dbo.[Session] WITH (UPDLOCK,HOLDLOCK) WHERE Id=? AND UserId=?",
            Long.class,
            session,
            actor.userId());
    if (own.isEmpty()) throw DomainException.missing();
    int changed =
        jdbc.update(
            "UPDATE dbo.[Session] SET RevokedAt=SYSUTCDATETIME() WHERE Id=? AND UserId=? AND"
                + " RevokedAt IS NULL",
            session,
            actor.userId());
    jdbc.update(
        "UPDATE dbo.RefreshToken SET RevokedAt=SYSUTCDATETIME() WHERE SessionId=? AND RevokedAt IS"
            + " NULL",
        session);
    if (changed > 0)
      audit.record(actor.userId(), "SESSION_REVOKED", "Session", session + "", "SUCCESS");
  }

  @Transactional
  public void revokeOthers(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    var sessions =
        jdbc.queryForList(
            "SELECT Id FROM dbo.[Session] WITH (UPDLOCK,HOLDLOCK) WHERE UserId=? AND Id<>? AND"
                + " RevokedAt IS NULL ORDER BY Id",
            Long.class,
            actor.userId(),
            actor.sessionId());
    for (long session : sessions) revoke(actor, session);
  }
}
