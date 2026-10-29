package com.comicnet.entitlement.service;

import com.comicnet.entitlement.api.ReadingAccess;
import com.comicnet.entitlement.api.EntitlementGrants;
import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.PageResult;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService implements ReadingAccess, EntitlementGrants {
  public record HistoryItem(long id, long chapterId, String comicTitle, String chapterTitle,
      String kind, String state,
      LocalDateTime validFrom, LocalDateTime validTo) {}
  private final JdbcTemplate jdbc;

  public EntitlementService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  @Transactional(readOnly = true)
  public Decision decide(Identity actor, long chapterId, String policy) {
    if ("FREE".equals(policy)) return new Decision(true, "FREE", false);
    if (actor == null) return new Decision(false, "LOGIN_REQUIRED", true);
    return owned(actor.userId(), chapterId)
        ? new Decision(true, "ENTITLED", false)
        : new Decision(false, "PURCHASE_REQUIRED", false);
  }

  @Transactional(readOnly = true)
  public boolean owned(long userId, long chapterId) {
    Integer found = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.UserEntitlement"
        + " WHERE UserId=? AND ChapterId=? AND State='ACTIVE'"
        + " AND ValidFrom<=SYSUTCDATETIME()"
        + " AND (ValidTo IS NULL OR ValidTo>SYSUTCDATETIME())", Integer.class, userId, chapterId);
    return found != null && found > 0;
  }

  @Transactional(readOnly = true)
  public PageResult<HistoryItem> history(long userId, int page, int size) {
    PageResult.validate(page, size);
    long total = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.UserEntitlement WHERE UserId=?",
        Long.class, userId);
    var items = jdbc.query("SELECT e.Id,e.ChapterId,c.Title,ch.Title,e.Kind,e.State,e.ValidFrom,e.ValidTo"
        + " FROM dbo.UserEntitlement e"
        + " JOIN dbo.Chapter ch ON ch.Id=e.ChapterId"
        + " JOIN dbo.ComicTranslation t ON t.Id=ch.TranslationId"
        + " JOIN dbo.Comic c ON c.Id=t.ComicId"
        + " WHERE e.UserId=? ORDER BY e.Id DESC"
        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        (rs, row) -> new HistoryItem(rs.getLong(1), rs.getLong(2), rs.getString(3),
            rs.getString(4), rs.getString(5), rs.getString(6),
            rs.getObject(7, LocalDateTime.class), rs.getObject(8, LocalDateTime.class)),
        userId, PageResult.offset(page, size), size);
    return PageResult.of(items, page, size, total);
  }

  /** Called only by a purchase that has already locked the user in the same SQL transaction. */
  @Override
  @Transactional
  public void grantPurchase(long userId, long chapterId, long purchaseId) {
    long entitlementId = jdbc.queryForObject(
        "INSERT dbo.UserEntitlement(UserId,ChapterId,Kind,State)"
            + " OUTPUT INSERTED.Id VALUES(?,?,'PURCHASE','ACTIVE')",
        Long.class, userId, chapterId);
    jdbc.update("INSERT dbo.EntitlementGrant(EntitlementId,SourceType,SourceId)"
        + " VALUES(?,'PURCHASE',?)", entitlementId, purchaseId);
  }

  @Override
  @Transactional
  public boolean revokePurchase(long userId, long chapterId, long actorUserId, String reason) {
    var rows = jdbc.query(
        "SELECT Id FROM dbo.UserEntitlement WHERE UserId=? AND ChapterId=? AND Kind='PURCHASE' AND State='ACTIVE'",
        (rs, n) -> rs.getLong(1), userId, chapterId);
    if (rows.isEmpty()) return false;
    for (Long entitlementId : rows) {
      jdbc.update("UPDATE dbo.UserEntitlement SET State='REVOKED', ValidTo=SYSUTCDATETIME() WHERE Id=?", entitlementId);
      jdbc.update("INSERT dbo.EntitlementRevocation(EntitlementId,Reason,ActorUserId) VALUES(?,?,?)",
          entitlementId, reason, actorUserId);
    }
    return true;
  }
}
