package com.comicnet.wallet.controller;

import com.comicnet.common.web.DomainException;
import com.comicnet.common.web.PageResult;
import com.comicnet.identity.api.Identity;
import com.comicnet.identity.api.AccountAdministration;
import com.comicnet.observability.api.AuditWriter;
import com.comicnet.wallet.api.WalletOperations.Balance;
import com.comicnet.wallet.service.WalletService;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/wallets")
public class AdminWalletController {
  public record Adjustment(String tokenType, long amount, String direction, String reason) {}
  public record Result(long userId, Balance before, Balance after) {}
  public record AuditRow(long id, long actorUserId, String action, String metadata,
      java.time.LocalDateTime createdAt) {}
  public record WalletRow(long id, String username, String displayName, String email,
      String status, long paidCoin, long rewardCoin, long totalCoin) {}
  private final WalletService wallet;
  private final AccountAdministration accounts;
  private final AuditWriter audit;
  private final JdbcTemplate jdbc;

  public AdminWalletController(WalletService wallet, AccountAdministration accounts,
      AuditWriter audit, JdbcTemplate jdbc) {
    this.wallet = wallet;
    this.accounts = accounts;
    this.audit = audit;
    this.jdbc = jdbc;
  }

  private void requireAdmin(Identity actor) {
    if (actor == null) throw DomainException.unauthorized();
    if (!actor.administrator())
      throw new DomainException(403, "ADMIN_REQUIRED", "Cần quyền quản trị viên.");
  }

  @GetMapping
  public ResponseEntity<PageResult<WalletRow>> list(@AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q) {
    requireAdmin(actor);
    PageResult.validate(page, size);
    String query = q == null ? "" : q.strip();
    if (query.length() > 200)
      throw new DomainException(400, "INVALID_QUERY", "Từ khóa quá dài.");
    String filter = query.isEmpty() ? "" : " WHERE u.Id=TRY_CONVERT(bigint,?)"
        + " OR CHARINDEX(?,u.Username)>0 OR CHARINDEX(?,u.Email)>0"
        + " OR CHARINDEX(?,u.DisplayName)>0";
    Object[] args = query.isEmpty() ? new Object[0]
        : new Object[] {query, query, query, query};
    long total = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.[User] u" + filter,
        Long.class, args);
    Object[] pageArgs = java.util.stream.Stream.concat(java.util.Arrays.stream(args),
        java.util.stream.Stream.of(PageResult.offset(page, size), size)).toArray();
    var rows = jdbc.query("SELECT u.Id,u.Username,u.DisplayName,u.Email,u.Status,"
        + " COALESCE(w.PaidBalance,0),COALESCE(w.RewardBalance,0)"
        + " FROM dbo.[User] u LEFT JOIN dbo.Wallet w ON w.UserId=u.Id" + filter
        + " ORDER BY u.Id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        (rs, n) -> new WalletRow(rs.getLong(1), rs.getString(2), rs.getString(3),
            rs.getString(4), rs.getString(5), rs.getLong(6), rs.getLong(7),
            Math.addExact(rs.getLong(6), rs.getLong(7))), pageArgs);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(PageResult.of(rows, page, size, total));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Balance> balance(@AuthenticationPrincipal Identity actor,
      @PathVariable long id) {
    requireAdmin(actor);
    accounts.get(actor, id);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(wallet.balance(id));
  }

  @GetMapping("/{id}/transactions")
  public PageResult<WalletService.Entry> history(@AuthenticationPrincipal Identity actor,
      @PathVariable long id, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    requireAdmin(actor);
    accounts.get(actor, id);
    return wallet.history(id, page, size);
  }

  @GetMapping("/{id}/adjustments")
  public PageResult<AuditRow> adjustments(@AuthenticationPrincipal Identity actor,
      @PathVariable long id, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    requireAdmin(actor);
    accounts.get(actor, id);
    PageResult.validate(page, size);
    String target = Long.toString(id);
    long total = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.AuditLog"
        + " WHERE TargetType='WALLET' AND TargetId=? AND Outcome='SUCCESS'", Long.class, target);
    var rows = jdbc.query("SELECT Id,ActorUserId,Action,SafeMetadata,CreatedAt FROM dbo.AuditLog"
        + " WHERE TargetType='WALLET' AND TargetId=? AND Outcome='SUCCESS' ORDER BY Id DESC"
        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        (rs, n) -> new AuditRow(rs.getLong(1), rs.getLong(2), rs.getString(3),
            rs.getString(4), rs.getObject(5, java.time.LocalDateTime.class)),
        target, PageResult.offset(page, size), size);
    return PageResult.of(rows, page, size, total);
  }

  @PostMapping("/{id}/adjustments")
  @Transactional
  public ResponseEntity<Result> adjust(@AuthenticationPrincipal Identity actor,
      @PathVariable long id, @RequestBody Adjustment body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    requireAdmin(actor);
    accounts.get(actor, id);
    if (body == null || body.amount() <= 0
        || body.reason() == null || body.reason().isBlank() || body.reason().length() > 500
        || (!"GRANT".equals(body.direction()) && !"REVOKE".equals(body.direction()))
        || (!"PAID".equals(body.tokenType()) && !"REWARD".equals(body.tokenType())))
      throw new DomainException(422, "INVALID_ADJUSTMENT", "Loại xu, số xu hoặc lý do không hợp lệ.");
    if (key == null || key.isBlank() || key.length() > 60)
      throw new DomainException(422, "IDEMPOTENCY_KEY_REQUIRED", "Cần mã thao tác duy nhất.");
    // WalletService serializes transactions for the target account and rejects negative balances.
    jdbc.queryForMap("SELECT Id FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", id);
    Balance before = wallet.balance(id);
    String reference = "admin:" + actor.userId() + ":" + key;
    Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.WalletTransaction"
        + " WHERE UserId=? AND ReferenceKey=?", Integer.class, id, reference);
    if (existing != null && existing > 0) {
      Integer matchingAudit = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditLog"
          + " WHERE TargetType='WALLET' AND TargetId=? AND Action=?"
          + " AND JSON_VALUE(SafeMetadata,'$.reference')=?"
          + " AND JSON_VALUE(SafeMetadata,'$.reason')=?", Integer.class,
          Long.toString(id), "ADMIN_WALLET_" + body.direction(), reference,
          body.reason().strip());
      if (matchingAudit == null || matchingAudit != 1)
        throw new DomainException(409, "IDEMPOTENCY_CONFLICT", "Mã thao tác đã dùng cho nội dung khác.");
    }
    long delta = "GRANT".equals(body.direction()) ? body.amount() : -body.amount();
    Balance after = wallet.apply(id, body.tokenType(), delta,
        "GRANT".equals(body.direction()) ? "ADMIN_GRANT" : "ADMIN_REVOKE", reference);
    if (existing == 0) audit.record(actor.userId(), "ADMIN_WALLET_" + body.direction(), "WALLET",
        Long.toString(id), "SUCCESS", Map.of("tokenType", body.tokenType(),
            "amount", delta, "reason", body.reason().strip(),
            "before", before, "after", after, "reference", reference));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Result(id, before, after));
  }
}
