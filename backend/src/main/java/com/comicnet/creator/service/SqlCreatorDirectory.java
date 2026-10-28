package com.comicnet.creator.service;

import com.comicnet.identity.api.*;
import com.comicnet.common.web.*;
import com.comicnet.creator.api.CreatorDirectory;
import com.comicnet.observability.api.AuditWriter;
import com.comicnet.observability.api.Idempotency;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SqlCreatorDirectory implements CreatorDirectory {
  private final JdbcTemplate jdbc;
  private final IdentityService identities;
  private final Idempotency idempotency;
  private final AuditWriter audit;
  private static final Set<String> TEAM_ROLES =
      Set.of("OWNER", "CREATOR", "TRANSLATOR", "EDITOR", "PROOFREADER", "UPLOADER");

  public SqlCreatorDirectory(
      JdbcTemplate jdbc, IdentityService identities, Idempotency idempotency, AuditWriter audit) {
    this.jdbc = jdbc;
    this.identities = identities;
    this.idempotency = idempotency;
    this.audit = audit;
  }

  private org.springframework.jdbc.core.RowMapper<Team> mapper() {
    return (rs, n) ->
        new Team(
            rs.getLong("Id") + "",
            rs.getString("Name"),
            rs.getString("Slug"),
            rs.getLong("OwnerUserId") + "",
            rs.getString("Status"));
  }

  public PageResult<Team> list(Identity actor, int page, int size, boolean admin) {
    if (actor == null) throw DomainException.unauthorized();
    PageResult.validate(page, size);
    if (admin) identities.requireAdministrator(actor);
    String filter =
        admin
            ? ""
            : " WHERE t.Status='ACTIVE' AND EXISTS(SELECT 1 FROM dbo.TeamMember m WHERE"
                  + " m.TeamId=t.Id AND m.UserId=? AND m.Status='ACTIVE')";
    Object[] countArgs = admin ? new Object[] {} : new Object[] {actor.userId()};
    long count =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.CreatorTeam t" + filter, Long.class, countArgs);
    Object[] args =
        admin
            ? new Object[] {PageResult.offset(page, size), size}
            : new Object[] {actor.userId(), PageResult.offset(page, size), size};
    return PageResult.of(
        jdbc.query(
            "SELECT t.* FROM dbo.CreatorTeam t"
                + filter
                + " ORDER BY t.Id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            mapper(),
            args),
        page,
        size,
        count);
  }

  public Team get(Identity actor, long id) {
    identities.requireAdministrator(actor);
    return team(id);
  }

  private Team team(long id) {
    var rows = jdbc.query("SELECT * FROM dbo.CreatorTeam WHERE Id=?", mapper(), id);
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  @Transactional
  public Team create(Identity actor, TeamCreate request, String key) {
    identities.requireAdministrator(actor);
    long owner;
    try {
      owner = Long.parseLong(request.ownerUserId());
      if (owner < 1) throw new IllegalArgumentException();
    } catch (Exception e) {
      throw new DomainException(400, "INVALID_OWNER", "Chủ nhóm không hợp lệ.");
    }
    if (!identities.active(owner))
      throw new DomainException(
          422, "OWNER_UNAVAILABLE", "Chủ nhóm phải là tài khoản đã xác minh và đang hoạt động.");
    if (!identities.roles(owner).contains("CREATOR"))
      throw new DomainException(
          422,
          "CREATOR_ROLE_REQUIRED",
          "Cấp vai trò hệ thống CREATOR cho chủ nhóm trước khi tạo nhóm.");
    long id =
        idempotency.create(
            actor.userId(),
            "createTeam",
            "teams",
            key,
            request,
            () -> {
              Long teamId =
                  jdbc.queryForObject(
                      "INSERT dbo.CreatorTeam(OwnerUserId,Name,Slug) OUTPUT INSERTED.Id"
                          + " VALUES(?,?,?)",
                      Long.class,
                      owner,
                      request.name().strip(),
                      request.slug());
              Long memberId =
                  jdbc.queryForObject(
                      "INSERT dbo.TeamMember(TeamId,UserId,Status) OUTPUT INSERTED.Id"
                          + " VALUES(?,?,'ACTIVE')",
                      Long.class,
                      teamId,
                      owner);
              jdbc.update(
                  "INSERT dbo.TeamMemberRole(MemberId,TeamRoleId) SELECT ?,Id FROM dbo.TeamRole"
                      + " WHERE Code IN ('OWNER','CREATOR')",
                  memberId);
              audit.record(actor.userId(), "TEAM_CREATED", "CreatorTeam", teamId + "", "SUCCESS");
              return teamId;
            });
    return team(id);
  }

  private Member membership(long teamId, long userId) {
    var rows =
        jdbc.queryForList(
            "SELECT Id,Status FROM dbo.TeamMember WHERE TeamId=? AND UserId=?", teamId, userId);
    if (rows.isEmpty()) throw DomainException.missing();
    var row = rows.getFirst();
    long id = ((Number) row.get("Id")).longValue();
    var roles =
        new TreeSet<>(
            jdbc.queryForList(
                "SELECT r.Code FROM dbo.TeamRole r JOIN dbo.TeamMemberRole mr ON mr.TeamRoleId=r.Id"
                    + " WHERE mr.MemberId=?",
                String.class,
                id));
    return new Member(id + "", teamId + "", userId + "", roles, (String) row.get("Status"));
  }

  public Member member(Identity actor, long teamId, long userId) {
    identities.requireAdministrator(actor);
    return membership(teamId, userId);
  }

  public byte[] teamVersion(long id) {
    return token("SELECT RowVersion FROM dbo.CreatorTeam WHERE Id=?", id);
  }

  public byte[] memberVersion(long team, long user) {
    return token("SELECT RowVersion FROM dbo.TeamMember WHERE TeamId=? AND UserId=?", team, user);
  }

  private byte[] token(String sql, Object... args) {
    var rows = jdbc.queryForList(sql, args);
    if (rows.isEmpty()) throw DomainException.missing();
    return (byte[]) rows.getFirst().get("RowVersion");
  }

  @Transactional
  public Member setMember(
      Identity actor,
      long teamId,
      long userId,
      Set<String> roles,
      String status,
      String etag,
      String noneMatch) {
    identities.requireAdministrator(actor);
    if (roles == null
        || roles.isEmpty()
        || !TEAM_ROLES.containsAll(roles)
        || !Set.of("ACTIVE", "REMOVED").contains(status))
      throw new DomainException(
          400, "INVALID_MEMBERSHIP", "Vai trò hoặc trạng thái thành viên không hợp lệ.");
    var teams =
        jdbc.queryForList(
            "SELECT OwnerUserId FROM dbo.CreatorTeam WITH (UPDLOCK,HOLDLOCK) WHERE Id=? AND"
                + " Status='ACTIVE'",
            teamId);
    if (teams.isEmpty()) throw DomainException.missing();
    long owner = ((Number) teams.getFirst().get("OwnerUserId")).longValue();
    if ((userId == owner && (!roles.contains("OWNER") || !status.equals("ACTIVE")))
        || (userId != owner && roles.contains("OWNER")))
      throw new DomainException(
          409,
          "OWNER_TRANSFER_NOT_SUPPORTED",
          "Không thể chuyển hoặc loại bỏ chủ nhóm bằng thao tác membership.");
    if (status.equals("ACTIVE") && !identities.active(userId))
      throw new DomainException(
          422, "MEMBER_UNAVAILABLE", "Thành viên phải đã xác minh và đang hoạt động.");
    var current =
        jdbc.queryForList(
            "SELECT Id,RowVersion FROM dbo.TeamMember WITH (UPDLOCK,HOLDLOCK) WHERE TeamId=? AND"
                + " UserId=?",
            teamId,
            userId);
    if (etag != null && noneMatch != null)
      throw new DomainException(400, "AMBIGUOUS_PRECONDITION", "Chỉ gửi một điều kiện cập nhật.");
    long memberId;
    if (current.isEmpty()) {
      if (!"*".equals(noneMatch))
        throw new DomainException(
            etag == null ? 428 : 412,
            "PRECONDITION_REQUIRED",
            "Thêm thành viên cần If-None-Match: *.");
      memberId =
          jdbc.queryForObject(
              "INSERT dbo.TeamMember(TeamId,UserId,Status) OUTPUT INSERTED.Id VALUES(?,?,?)",
              Long.class,
              teamId,
              userId,
              status);
    } else {
      if (noneMatch != null)
        throw new DomainException(
            412, "PRECONDITION_FAILED", "Thành viên đã tồn tại. Tải lại trước khi sửa.");
      Etags.require(etag, (byte[]) current.getFirst().get("RowVersion"));
      memberId = ((Number) current.getFirst().get("Id")).longValue();
      jdbc.update("UPDATE dbo.TeamMember SET Status=? WHERE Id=?", status, memberId);
      jdbc.update("DELETE dbo.TeamMemberRole WHERE MemberId=?", memberId);
    }
    for (String role : roles)
      jdbc.update(
          "INSERT dbo.TeamMemberRole(MemberId,TeamRoleId) SELECT ?,Id FROM dbo.TeamRole WHERE"
              + " Code=?",
          memberId,
          role);
    audit.record(
        actor.userId(),
        "TEAM_MEMBERSHIP_UPDATED",
        "TeamMember",
        memberId + "",
        "SUCCESS",
        Map.of("roles", roles, "status", status));
    return membership(teamId, userId);
  }

  public boolean has(Identity actor, long teamId, String permission) {
    if (actor == null) return false;
    if (actor.administrator()) return true;
    if (!identities.active(actor.userId())) return false;
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.TeamRole r JOIN dbo.TeamRolePermission rp ON"
                + " rp.TeamRoleId=r.Id JOIN dbo.Permission p ON p.Id=rp.PermissionId JOIN"
                + " dbo.TeamMemberRole mr ON mr.TeamRoleId=r.Id JOIN dbo.TeamMember m ON"
                + " m.Id=mr.MemberId JOIN dbo.CreatorTeam t ON t.Id=m.TeamId WHERE t.Id=? AND"
                + " t.Status='ACTIVE' AND m.UserId=? AND m.Status='ACTIVE' AND p.Code=?",
            Integer.class,
            teamId,
            actor.userId(),
            permission)
        > 0;
  }

  public void require(Identity actor, long team, String permission) {
    if (!has(actor, team, permission)) throw DomainException.missing();
  }

  public List<Long> teamIds(Identity actor, String permission) {
    if (actor == null || !identities.active(actor.userId())) return List.of();
    return jdbc.queryForList(
        "SELECT DISTINCT t.Id FROM dbo.CreatorTeam t JOIN dbo.TeamMember m ON m.TeamId=t.Id JOIN"
            + " dbo.TeamMemberRole mr ON mr.MemberId=m.Id JOIN dbo.TeamRolePermission rp ON"
            + " rp.TeamRoleId=mr.TeamRoleId JOIN dbo.Permission p ON p.Id=rp.PermissionId WHERE"
            + " t.Status='ACTIVE' AND m.Status='ACTIVE' AND m.UserId=? AND p.Code=?",
        Long.class,
        actor.userId(),
        permission);
  }
}
