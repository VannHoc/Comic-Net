package com.comicnet.identity.service;

import com.comicnet.identity.api.*;
import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.entity.UserAccount;
import com.comicnet.identity.repository.UserAccountRepository;
import com.comicnet.common.web.*;
import com.comicnet.observability.api.AuditWriter;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService implements IdentityService {
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
  private final UserAccountRepository users;
  private final JdbcTemplate jdbc;
  private final JwtTokens jwt;
  private final AccountMail mail;
  private final AuditWriter audit;
  private final PasswordEncoder passwords;
  private final String dummyHash;

  public AuthenticationService(
      UserAccountRepository users,
      JdbcTemplate jdbc,
      JwtTokens jwt,
      AccountMail mail,
      AuditWriter audit) {
    this.users = users;
    this.jdbc = jdbc;
    this.jwt = jwt;
    this.mail = mail;
    this.audit = audit;
    passwords =
        new DelegatingPasswordEncoder(
            "pbkdf2@SpringSecurity_v5_8",
            Map.of(
                "pbkdf2@SpringSecurity_v5_8",
                Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    dummyHash = passwords.encode(TokenSecrets.random());
  }

  public static String normalizeEmail(String email) {
    return Normalizer.normalize(email.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }

  private static String normalizeUsername(String username) {
    return username.strip().toLowerCase(Locale.ROOT);
  }

  private static java.time.LocalDateTime timestamp(Instant i) {
    return java.time.LocalDateTime.ofInstant(i, java.time.ZoneOffset.UTC);
  }

  public record SessionResult(String accessToken, String refreshToken, UserView user) {}

  public static final class RefreshReplay extends DomainException {
    public RefreshReplay() {
      super(401, "REFRESH_REPLAY", "Phiên đã bị thu hồi. Vui lòng đăng nhập lại.");
    }
  }

  @Transactional
  public void register(AuthRequests.Register request) {
    String email = request.email() == null || request.email().isBlank()
        ? null : request.email().strip();
    String username = request.username() == null || request.username().isBlank()
        ? null : request.username().strip();
    if (email == null && username == null)
      throw new DomainException(400, "ACCOUNT_IDENTIFIER_REQUIRED", "Cần tên tài khoản hoặc email.");
    if (username != null && !username.matches("[A-Za-z0-9_]{3,12}"))
      throw new DomainException(400, "INVALID_USERNAME", "Tên tài khoản không hợp lệ.");
    String normalizedEmail = email == null ? null : normalizeEmail(email);
    String normalizedUsername = username == null ? null : normalizeUsername(username);
    if (normalizedEmail != null) {
      jdbc.queryForList("SELECT Id FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK) WHERE NormalizedEmail=?",
          normalizedEmail);
      if (users.findByNormalizedEmail(normalizedEmail).isPresent()) return;
    }
    if (normalizedUsername != null) {
      jdbc.queryForList("SELECT Id FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK) WHERE NormalizedUsername=?",
          normalizedUsername);
      if (users.findByNormalizedUsername(normalizedUsername).isPresent())
        throw new DomainException(409, "USERNAME_TAKEN", "Tên tài khoản đã được sử dụng.");
    }
    var user =
        users.saveAndFlush(
            new UserAccount(
                email,
                normalizedEmail,
                username,
                normalizedUsername,
                request.phone(),
                passwords.encode(request.password()),
                request.displayName().strip()));
    jdbc.update(
        "INSERT dbo.UserRole(UserId,RoleId) SELECT ?,Id FROM dbo.[Role] WHERE Code='READER'",
        user.getId());
    if (email != null) action(user, "VERIFY_EMAIL");
    audit.record(user.getId(), "ACCOUNT_REGISTERED", "User", user.getId().toString(), "SUCCESS");
  }

  private void action(UserAccount user, String purpose) {
    Integer recent =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM dbo.AccountActionToken WHERE UserId=? AND Purpose=? AND"
                + " CreatedAt>DATEADD(second,-60,SYSUTCDATETIME())",
            Integer.class,
            user.getId(),
            purpose);
    if (recent != null && recent > 0) return;
    String token = TokenSecrets.random();
    Instant expires = Instant.now().plusSeconds(purpose.equals("VERIFY_EMAIL") ? 86400 : 1800);
    jdbc.update(
        "UPDATE dbo.AccountActionToken SET ConsumedAt=SYSUTCDATETIME() WHERE UserId=? AND Purpose=?"
            + " AND ConsumedAt IS NULL",
        user.getId(),
        purpose);
    jdbc.update(
        "INSERT dbo.AccountActionToken(UserId,Purpose,TokenHash,ExpiresAt) VALUES(?,?,?,?)",
        user.getId(),
        purpose,
        TokenSecrets.hash(token),
        timestamp(expires));
    String otp = TokenSecrets.sixDigitCode();
    int updated = jdbc.update(
        "UPDATE dbo.AccountEmailOtp SET CodeHash=?,ExpiresAt=?,Attempts=0,ConsumedAt=NULL,"
            + " SentAt=SYSUTCDATETIME() WHERE UserId=? AND Purpose=?",
        TokenSecrets.hash(otp), timestamp(expires), user.getId(), purpose);
    if (updated == 0) jdbc.update(
        "INSERT dbo.AccountEmailOtp(UserId,Purpose,CodeHash,ExpiresAt) VALUES(?,?,?,?)",
        user.getId(), purpose, TokenSecrets.hash(otp), timestamp(expires));
    mail.queue(user.getEmail(), purpose, otp + "\nMã tương thích cũ: " + token);
  }

  private UserAccount consumeOtp(String email, String code, String purpose) {
    var found = users.findByNormalizedEmail(normalizeEmail(email));
    if (found.isEmpty()) throw invalidOtp();
    var user = users.lockById(found.get().getId()).orElseThrow(this::invalidOtp);
    var rows = jdbc.queryForList(
        "SELECT CodeHash,Attempts,ExpiresAt,ConsumedAt FROM dbo.AccountEmailOtp"
            + " WITH (UPDLOCK,HOLDLOCK) WHERE UserId=? AND Purpose=?",
        user.getId(), purpose);
    if (rows.isEmpty()) throw invalidOtp();
    var row = rows.getFirst();
    int attempts = ((Number) row.get("Attempts")).intValue();
    if (attempts >= 5 || row.get("ConsumedAt") != null
        || !((java.sql.Timestamp) row.get("ExpiresAt")).toLocalDateTime()
            .toInstant(java.time.ZoneOffset.UTC).isAfter(Instant.now()))
      throw invalidOtp();
    if (!TokenSecrets.equal((String) row.get("CodeHash"), TokenSecrets.hash(code))) {
      jdbc.update("UPDATE dbo.AccountEmailOtp SET Attempts=Attempts+1 WHERE UserId=? AND Purpose=?",
          user.getId(), purpose);
      throw invalidOtp();
    }
    jdbc.update("UPDATE dbo.AccountEmailOtp SET ConsumedAt=SYSUTCDATETIME()"
        + " WHERE UserId=? AND Purpose=?", user.getId(), purpose);
    return user;
  }

  private DomainException invalidOtp() {
    return new DomainException(400, "INVALID_OTP", "Mã không hợp lệ, hết hạn hoặc đã dùng.");
  }

  @Transactional(noRollbackFor = DomainException.class)
  public void verifyOtp(String email, String code) {
    var user = consumeOtp(email, code, "VERIFY_EMAIL");
    if (!"ACTIVE".equals(user.getStatus())) throw invalidOtp();
    user.verify(Instant.now());
    users.saveAndFlush(user);
    jdbc.update("UPDATE dbo.AccountActionToken SET ConsumedAt=SYSUTCDATETIME()"
        + " WHERE UserId=? AND Purpose='VERIFY_EMAIL' AND ConsumedAt IS NULL", user.getId());
    audit.record(user.getId(), "EMAIL_VERIFIED", "User", user.getId().toString(), "SUCCESS");
  }

  @Transactional(noRollbackFor = DomainException.class)
  public void resetOtp(String email, String code, String password) {
    var user = consumeOtp(email, code, "RESET_PASSWORD");
    if (!"ACTIVE".equals(user.getStatus())) throw invalidOtp();
    user.changePassword(passwords.encode(password));
    users.saveAndFlush(user);
    jdbc.update("UPDATE dbo.AccountActionToken SET ConsumedAt=SYSUTCDATETIME()"
        + " WHERE UserId=? AND Purpose='RESET_PASSWORD' AND ConsumedAt IS NULL", user.getId());
    revokeAll(user.getId());
    audit.record(user.getId(), "PASSWORD_RESET", "User", user.getId().toString(), "SUCCESS");
  }

  @Transactional
  public void requestAction(String email, String purpose) {
    var found = users.findByNormalizedEmail(normalizeEmail(email));
    if (found.isEmpty()) return;
    var user = users.lockById(found.get().getId()).orElseThrow(DomainException::unauthorized);
    entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    if (!"ACTIVE".equals(user.getStatus())
        || (purpose.equals("VERIFY_EMAIL") && user.getEmailVerifiedAt() != null)) return;
    action(user, purpose);
  }

  private Map<String, Object> lockAction(String token, String purpose) {
    String hash = TokenSecrets.hash(token);
    var initial =
        jdbc.queryForList(
            "SELECT UserId FROM dbo.AccountActionToken WHERE TokenHash=? AND Purpose=?",
            hash,
            purpose);
    if (initial.isEmpty())
      throw new DomainException(
          400, "INVALID_ACTION_TOKEN", "Mã thao tác không hợp lệ hoặc đã hết hạn.");
    long userId = ((Number) initial.getFirst().get("UserId")).longValue();
    users.lockById(userId).orElseThrow(DomainException::unauthorized);
    var rows =
        jdbc.queryForList(
            "SELECT Id,UserId FROM dbo.AccountActionToken WITH (UPDLOCK,HOLDLOCK) WHERE TokenHash=?"
                + " AND Purpose=? AND ConsumedAt IS NULL AND ExpiresAt>SYSUTCDATETIME()",
            hash,
            purpose);
    if (rows.isEmpty())
      throw new DomainException(
          400, "INVALID_ACTION_TOKEN", "Mã thao tác không hợp lệ hoặc đã hết hạn.");
    return rows.getFirst();
  }

  @Transactional
  public void verify(String token) {
    var row = lockAction(token, "VERIFY_EMAIL");
    long id = ((Number) row.get("UserId")).longValue();
    var user = users.findById(id).orElseThrow(DomainException::unauthorized);
    if (!"ACTIVE".equals(user.getStatus()))
      throw new DomainException(
          400, "INVALID_ACTION_TOKEN", "Mã thao tác không hợp lệ hoặc đã hết hạn.");
    user.verify(Instant.now());
    users.saveAndFlush(user);
    jdbc.update(
        "UPDATE dbo.AccountActionToken SET ConsumedAt=SYSUTCDATETIME() WHERE Id=?", row.get("Id"));
    audit.record(id, "EMAIL_VERIFIED", "User", Long.toString(id), "SUCCESS");
  }

  @Transactional
  public void reset(String token, String password) {
    var row = lockAction(token, "RESET_PASSWORD");
    long id = ((Number) row.get("UserId")).longValue();
    var user = users.findById(id).orElseThrow(DomainException::unauthorized);
    if (!"ACTIVE".equals(user.getStatus()))
      throw new DomainException(
          400, "INVALID_ACTION_TOKEN", "Mã thao tác không hợp lệ hoặc đã hết hạn.");
    user.changePassword(passwords.encode(password));
    users.saveAndFlush(user);
    jdbc.update(
        "UPDATE dbo.AccountActionToken SET ConsumedAt=SYSUTCDATETIME() WHERE UserId=? AND"
            + " Purpose='RESET_PASSWORD' AND ConsumedAt IS NULL",
        id);
    revokeAll(id);
    audit.record(id, "PASSWORD_RESET", "User", Long.toString(id), "SUCCESS");
  }

  @Transactional
  public SessionResult login(AuthRequests.Login request) {
    String identifier = request.email().strip();
    var found = identifier.contains("@")
        ? users.findByNormalizedEmail(normalizeEmail(identifier))
        : users.findByNormalizedUsername(normalizeUsername(identifier));
    String hash = found.map(UserAccount::getPasswordHash).orElse(dummyHash);
    if (hash == null) hash = dummyHash;
    boolean valid = passwords.matches(request.password(), hash);
    if (found.isEmpty() || !valid) throw DomainException.unauthorized();
    var user = users.lockById(found.get().getId()).orElseThrow(DomainException::unauthorized);
    entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
    if (!Objects.equals(hash, user.getPasswordHash())
        && !passwords.matches(request.password(), user.getPasswordHash()))
      throw DomainException.unauthorized();
    if (!"ACTIVE".equals(user.getStatus())
        || (user.getEmail() != null && user.getEmailVerifiedAt() == null))
      throw DomainException.unauthorized();
    Instant now = Instant.now(), expires = now.plusSeconds(30L * 86400);
    Long sessionId =
        jdbc.queryForObject(
            "INSERT dbo.[Session](UserId,DeviceLabel,ExpiresAt,LastSeenAt) OUTPUT INSERTED.Id"
                + " VALUES(?,?,?,?)",
            Long.class,
            user.getId(),
            request.deviceLabel() == null ? "Comic Net Swing" : request.deviceLabel().strip(),
            timestamp(expires),
            timestamp(now));
    String refresh = TokenSecrets.random();
    jdbc.update(
        "INSERT dbo.RefreshToken(SessionId,TokenHash,ExpiresAt) VALUES(?,?,?)",
        sessionId,
        TokenSecrets.hash(refresh),
        timestamp(expires));
    String access = jwt.issue(user.getId(), sessionId, user.getAuthVersion());
    audit.record(user.getId(), "LOGIN", "Session", sessionId.toString(), "SUCCESS");
    return new SessionResult(access, refresh, user(user.getId()));
  }

  @Transactional(noRollbackFor = RefreshReplay.class)
  public SessionResult refresh(String raw) {
    if (raw == null || raw.length() > 1024) throw DomainException.unauthorized();
    String hash = TokenSecrets.hash(raw);
    var initial =
        jdbc.queryForList(
            "SELECT t.SessionId,s.UserId FROM dbo.RefreshToken t JOIN dbo.[Session] s ON"
                + " s.Id=t.SessionId WHERE t.TokenHash=?",
            hash);
    if (initial.isEmpty()) throw DomainException.unauthorized();
    long userId = ((Number) initial.getFirst().get("UserId")).longValue(),
        sid = ((Number) initial.getFirst().get("SessionId")).longValue();
    var user = users.lockById(userId).orElseThrow(DomainException::unauthorized);
    var sessions =
        jdbc.queryForList(
            "SELECT Id,ExpiresAt FROM dbo.[Session] WITH (UPDLOCK,HOLDLOCK) WHERE Id=? AND"
                + " RevokedAt IS NULL AND ExpiresAt>SYSUTCDATETIME()",
            sid);
    if (sessions.isEmpty()
        || !"ACTIVE".equals(user.getStatus())
        || (user.getEmail() != null && user.getEmailVerifiedAt() == null))
      throw DomainException.unauthorized();
    var row =
        jdbc.queryForMap(
            "SELECT Id,UsedAt,RevokedAt FROM dbo.RefreshToken WITH (UPDLOCK,HOLDLOCK) WHERE"
                + " TokenHash=?",
            hash);
    if (row.get("UsedAt") != null || row.get("RevokedAt") != null) {
      revokeSession(sid);
      audit.record(userId, "REFRESH_REPLAY", "Session", Long.toString(sid), "REVOKED");
      throw new RefreshReplay();
    }
    jdbc.update("UPDATE dbo.RefreshToken SET UsedAt=SYSUTCDATETIME() WHERE Id=?", row.get("Id"));
    String next = TokenSecrets.random();
    jdbc.update(
        "INSERT dbo.RefreshToken(SessionId,TokenHash,ParentTokenId,ExpiresAt) VALUES(?,?,?,?)",
        sid,
        TokenSecrets.hash(next),
        row.get("Id"),
        sessions.getFirst().get("ExpiresAt"));
    jdbc.update("UPDATE dbo.[Session] SET LastSeenAt=SYSUTCDATETIME() WHERE Id=?", sid);
    return new SessionResult(jwt.issue(userId, sid, user.getAuthVersion()), next, user(userId));
  }

  @Transactional
  public void logout(String raw) {
    if (raw == null || raw.length() > 1024) return;
    var rows =
        jdbc.queryForList(
            "SELECT SessionId FROM dbo.RefreshToken WHERE TokenHash=?", TokenSecrets.hash(raw));
    if (!rows.isEmpty()) revokeSession(((Number) rows.getFirst().get("SessionId")).longValue());
  }

  private void revokeSession(long id) {
    jdbc.update(
        "UPDATE dbo.[Session] SET RevokedAt=SYSUTCDATETIME() WHERE Id=? AND RevokedAt IS NULL", id);
    jdbc.update(
        "UPDATE dbo.RefreshToken SET RevokedAt=SYSUTCDATETIME() WHERE SessionId=? AND RevokedAt IS"
            + " NULL",
        id);
  }

  public void revokeAll(long userId) {
    jdbc.update(
        "UPDATE dbo.[Session] SET RevokedAt=SYSUTCDATETIME() WHERE UserId=? AND RevokedAt IS NULL",
        userId);
    jdbc.update(
        "UPDATE dbo.RefreshToken SET RevokedAt=SYSUTCDATETIME() WHERE SessionId IN (SELECT Id FROM"
            + " dbo.[Session] WHERE UserId=?) AND RevokedAt IS NULL",
        userId);
  }

  @Override
  public Identity authenticate(String bearer) {
    var token = jwt.decode(bearer);
    try {
      long userId = Long.parseLong(token.getSubject()),
          sid = Long.parseLong(token.getClaimAsString("sid"));
      long av = ((Number) token.getClaim("av")).longValue();
      Integer count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM dbo.[Session] s JOIN dbo.[User] u ON u.Id=s.UserId WHERE s.Id=?"
                  + " AND s.UserId=? AND s.RevokedAt IS NULL AND s.ExpiresAt>SYSUTCDATETIME() AND"
                  + " u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)"
                  + " AND u.AuthVersion=?",
              Integer.class,
              sid,
              userId,
              av);
      if (count == null || count != 1) throw DomainException.unauthorized();
      return new Identity(userId, sid, av, roles(userId));
    } catch (NumberFormatException | ClassCastException | NullPointerException e) {
      throw DomainException.unauthorized();
    }
  }

  @Override
  public UserView user(long id) {
    var rows =
        jdbc.query(
            "SELECT Id,Email,Username,DisplayName,Status,EmailVerifiedAt FROM dbo.[User] WHERE Id=?",
            (rs, n) ->
                new UserView(
                    rs.getLong("Id") + "",
                    rs.getString("Email"),
                    rs.getString("Username"),
                    rs.getString("DisplayName"),
                    rs.getString("Status"),
                    rs.getTimestamp("EmailVerifiedAt") != null,
                    roles(id).stream().sorted().toList()),
            id);
    if (rows.isEmpty()) throw DomainException.missing();
    return rows.getFirst();
  }

  @Override
  public Set<String> roles(long id) {
    return new HashSet<>(
        jdbc.queryForList(
            "SELECT r.Code FROM dbo.[Role] r JOIN dbo.UserRole ur ON ur.RoleId=r.Id WHERE"
                + " ur.UserId=?",
            String.class,
            id));
  }

  @Override
  public boolean active(long id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT CASE WHEN EXISTS(SELECT 1 FROM dbo.[User] WHERE Id=? AND Status='ACTIVE' AND"
                + " (Email IS NULL OR EmailVerifiedAt IS NOT NULL)) THEN 1 ELSE 0 END",
            Boolean.class,
            id));
  }

  @Override
  public void requireAdministrator(Identity actor) {
    if (actor == null || !actor.administrator()) throw DomainException.forbidden();
  }

  @Override
  public boolean permitted(Identity actor, String permission) {
    return actor != null
        && jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.UserRole ur JOIN dbo.RolePermission rp ON"
                    + " rp.RoleId=ur.RoleId JOIN dbo.Permission p ON p.Id=rp.PermissionId WHERE"
                    + " ur.UserId=? AND p.Code=?",
                Integer.class,
                actor.userId(),
                permission)
            > 0;
  }

  public byte[] userVersion(long id) {
    return jdbc.queryForObject("SELECT RowVersion FROM dbo.[User] WHERE Id=?", byte[].class, id);
  }

  @Transactional
  public UserView profile(Identity actor, String displayName, String etag) {
    if (!permitted(actor, "profile.self")) throw DomainException.forbidden();
    var u = users.lockById(actor.userId()).orElseThrow(DomainException::missing);
    Etags.require(etag, userVersion(actor.userId()));
    u.rename(displayName.strip());
    users.saveAndFlush(u);
    audit.record(actor.userId(), "PROFILE_UPDATED", "User", actor.getName(), "SUCCESS");
    return user(actor.userId());
  }

  @Transactional
  public UserView username(Identity actor, String username, String etag) {
    if (!permitted(actor, "profile.self")) throw DomainException.forbidden();
    if (username == null || !username.matches("[A-Za-z0-9_]{3,12}"))
      throw new DomainException(400, "INVALID_USERNAME", "Tên tài khoản không hợp lệ.");
    var user = users.lockById(actor.userId()).orElseThrow(DomainException::missing);
    Etags.require(etag, userVersion(actor.userId()));
    String normalized = normalizeUsername(username);
    var taken = jdbc.queryForList("SELECT Id FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK)"
        + " WHERE NormalizedUsername=? AND Id<>?", normalized, actor.userId());
    if (!taken.isEmpty())
      throw new DomainException(409, "USERNAME_TAKEN", "Tên tài khoản đã được sử dụng.");
    user.changeUsername(username, normalized);
    users.saveAndFlush(user);
    audit.record(actor.userId(), "USERNAME_UPDATED", "User", Long.toString(actor.userId()), "SUCCESS");
    return user(actor.userId());
  }

}
