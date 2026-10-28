package com.comicnet.identity.service;

import com.comicnet.identity.api.*;
import com.comicnet.common.web.*;
import com.comicnet.observability.api.AuditWriter;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SqlAccountAdministration implements AccountAdministration {
  private final JdbcTemplate jdbc;
  private final IdentityService identities;
  private final AuthenticationService auth;
  private final AuditWriter audit;

  public SqlAccountAdministration(
      JdbcTemplate jdbc,
      IdentityService identities,
      AuthenticationService auth,
      AuditWriter audit) {
    this.jdbc = jdbc;
    this.identities = identities;
    this.auth = auth;
    this.audit = audit;
  }

  public PageResult<UserView> list(Identity actor, int page, int size, String query) {
    identities.requireAdministrator(actor);
    PageResult.validate(page, size);
    String q = query == null ? "" : query.strip();
    if (q.length() > 200) throw new DomainException(400, "INVALID_QUERY", "Từ khóa quá dài.");
    String filter = q.isEmpty() ? "" : " WHERE CHARINDEX(?,Email)>0"
        + " OR CHARINDEX(?,DisplayName)>0 OR CHARINDEX(?,Username)>0";
    Object[] filters = q.isEmpty() ? new Object[0] : new Object[] {q, q, q};
    long total =
        jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.[User]" + filter, Long.class, filters);
    var ids =
        jdbc.queryForList(
            "SELECT Id FROM dbo.[User]"
                + filter
                + " ORDER BY Id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            Long.class,
            java.util.stream.Stream.concat(java.util.Arrays.stream(filters),
                java.util.stream.Stream.of(PageResult.offset(page, size), size)).toArray());
    return PageResult.of(ids.stream().map(identities::user).toList(), page, size, total);
  }

  public UserView get(Identity actor, long id) {
    identities.requireAdministrator(actor);
    return identities.user(id);
  }

  public byte[] version(long id) {
    var rows = jdbc.queryForList("SELECT RowVersion FROM dbo.[User] WHERE Id=?", id);
    if (rows.isEmpty()) throw DomainException.missing();
    return (byte[]) rows.getFirst().get("RowVersion");
  }

  private void lock(Identity actor, long id, String etag) {
    identities.requireAdministrator(actor);
    // Global invariant lock serializes every role/status change with setup and each other.
    jdbc.queryForMap("SELECT Id FROM dbo.BootstrapState WITH (UPDLOCK,HOLDLOCK) WHERE Id=1");
    var found =
        jdbc.queryForList(
            "SELECT RowVersion FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", id);
    if (found.isEmpty()) throw DomainException.missing();
    Etags.require(etag, (byte[]) found.getFirst().get("RowVersion"));
  }

  private void protectLastAdmin(long id, boolean losesAdmin) {
    if (!losesAdmin) return;
    int remaining =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.[User] u JOIN dbo.UserRole ur ON ur.UserId=u.Id JOIN"
                + " dbo.[Role] r ON r.Id=ur.RoleId WHERE r.Code='ADMINISTRATOR' AND"
                + " u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)"
                + " AND u.Id<>?",
            Integer.class,
            id);
    if (remaining == 0)
      throw new DomainException(
          409, "LAST_ADMINISTRATOR", "Không thể vô hiệu hóa quản trị viên hoạt động cuối cùng.");
  }

  @Transactional
  public UserView status(Identity actor, long id, String status, String reason, String etag) {
    lock(actor, id, etag);
    if (!Set.of("ACTIVE", "SUSPENDED").contains(status))
      throw new DomainException(400, "INVALID_STATUS", "Trạng thái không hợp lệ.");
    protectLastAdmin(
        id, status.equals("SUSPENDED") && identities.roles(id).contains("ADMINISTRATOR"));
    jdbc.update(
        "UPDATE dbo.[User] SET Status=?,AuthVersion=AuthVersion+1,LockVersion=LockVersion+1 WHERE"
            + " Id=?",
        status,
        id);
    auth.revokeAll(id);
    audit.record(
        actor.userId(),
        "USER_STATUS_" + status,
        "User",
        id + "",
        "SUCCESS",
        java.util.Map.of("status", status));
    return identities.user(id);
  }

  @Transactional
  public UserView roles(Identity actor, long id, Set<String> roles, String reason, String etag) {
    lock(actor, id, etag);
    if (roles == null
        || roles.isEmpty()
        || !Set.of("READER", "CREATOR", "MODERATOR", "ADMINISTRATOR").containsAll(roles))
      throw new DomainException(400, "INVALID_ROLES", "Vai trò không hợp lệ.");
    protectLastAdmin(
        id, identities.roles(id).contains("ADMINISTRATOR") && !roles.contains("ADMINISTRATOR"));
    jdbc.update("DELETE dbo.UserRole WHERE UserId=?", id);
    for (String role : roles)
      jdbc.update(
          "INSERT dbo.UserRole(UserId,RoleId) SELECT ?,Id FROM dbo.[Role] WHERE Code=?", id, role);
    jdbc.update(
        "UPDATE dbo.[User] SET AuthVersion=AuthVersion+1,LockVersion=LockVersion+1 WHERE Id=?", id);
    auth.revokeAll(id);
    audit.record(
        actor.userId(),
        "USER_ROLES_UPDATED",
        "User",
        id + "",
        "SUCCESS",
        java.util.Map.of("roles", roles));
    return identities.user(id);
  }
}
