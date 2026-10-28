package com.comicnet.entitlement.service;

import com.comicnet.entitlement.api.ReadingAccess;
import com.comicnet.entitlement.api.EntitlementGrants;
import com.comicnet.identity.api.Identity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementService implements ReadingAccess, EntitlementGrants {
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

  /** Called only by a purchase that has already locked the user in the same SQL transaction. */
  @Transactional
  public void grantPurchase(long userId, long chapterId, long purchaseId) {
    long entitlementId = jdbc.queryForObject(
        "INSERT dbo.UserEntitlement(UserId,ChapterId,Kind,State)"
            + " OUTPUT INSERTED.Id VALUES(?,?,'PURCHASE','ACTIVE')",
        Long.class, userId, chapterId);
    jdbc.update("INSERT dbo.EntitlementGrant(EntitlementId,SourceType,SourceId)"
        + " VALUES(?,'PURCHASE',?)", entitlementId, purchaseId);
  }
}
