package com.comicnet.monetization.service;

import com.comicnet.common.web.DomainException;
import com.comicnet.common.web.PageResult;
import com.comicnet.entitlement.api.EntitlementGrants;
import com.comicnet.identity.api.Identity;
import com.comicnet.observability.api.AuditWriter;
import com.comicnet.publishing.api.PublishedChapters;
import com.comicnet.publishing.api.PublishingChapterAccess;
import com.comicnet.wallet.api.WalletOperations;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterPurchaseService {
  public record Price(long id, String currencyCode, long amount) {}
  public record PurchaseResult(Long purchaseId, String status, WalletOperations.Balance balance) {}
  public record RefundResult(long purchaseId, long userId, long chapterId, String currencyCode,
      long amount, WalletOperations.Balance balance) {}
  public record PurchaseAdminRow(long id, long userId, String username, long chapterId,
      String chapterTitle, String comicTitle, long amount, String currencyCode,
      String status, LocalDateTime createdAt, LocalDateTime revokedAt,
      String revokeReason, Long revokedByUserId) {}
  private record SavedPurchase(long id, long chapterId, long priceId) {}
  private record PurchaseRecord(long id, long userId, long chapterId, long amount,
      String currencyCode, String status, Long refundTxId) {}

  private final JdbcTemplate jdbc;
  private final PublishedChapters chapters;
  private final PublishingChapterAccess scope;
  private final WalletOperations wallet;
  private final EntitlementGrants entitlements;
  private final AuditWriter audit;

  public ChapterPurchaseService(JdbcTemplate jdbc, PublishedChapters chapters,
      PublishingChapterAccess scope, WalletOperations wallet, EntitlementGrants entitlements,
      AuditWriter audit) {
    this.jdbc = jdbc;
    this.chapters = chapters;
    this.scope = scope;
    this.wallet = wallet;
    this.entitlements = entitlements;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<Price> prices(long chapterId) {
    if (chapters.current(chapterId).isEmpty()) throw DomainException.missing();
    return currentPrices(chapterId);
  }

  private List<Price> currentPrices(long chapterId) {
    return jdbc.query("SELECT Id,CurrencyCode,Amount FROM dbo.ChapterPrice"
        + " WHERE ChapterId=? AND ValidFrom<=SYSUTCDATETIME()"
        + " AND (ValidTo IS NULL OR ValidTo>SYSUTCDATETIME()) ORDER BY CurrencyCode",
        (rs, row) -> new Price(rs.getLong(1), rs.getString(2), rs.getLong(3)), chapterId);
  }

  @Transactional
  public Price setPrice(Identity actor, long chapterId, String currency, long amount) {
    if (actor == null) throw DomainException.unauthorized();
    if ((!"PAID".equals(currency) && !"REWARD".equals(currency)) || amount <= 0)
      throw new DomainException(422, "INVALID_CHAPTER_PRICE", "Giá chương không hợp lệ.");
    scope.requireChapterEditor(actor, chapterId);
    jdbc.update("UPDATE dbo.ChapterPrice SET ValidTo=SYSUTCDATETIME()"
        + " WHERE ChapterId=? AND CurrencyCode=? AND ValidTo IS NULL", chapterId, currency);
    long id = jdbc.queryForObject("INSERT dbo.ChapterPrice(ChapterId,CurrencyCode,Amount)"
        + " OUTPUT INSERTED.Id VALUES(?,?,?)", Long.class, chapterId, currency, amount);
    return new Price(id, currency, amount);
  }

  @Transactional
  public PurchaseResult unlock(Identity actor, long chapterId, long priceId, String key) {
    if (actor == null) throw DomainException.unauthorized();
    if (key == null || key.isBlank() || key.length() > 100)
      throw new DomainException(422, "IDEMPOTENCY_KEY_REQUIRED", "Cần mã yêu cầu mua duy nhất.");
    // One user-row lock serializes concurrent purchases for this account.
    jdbc.queryForMap("SELECT Id FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", actor.userId());
    var replay = jdbc.query("SELECT Id,ChapterId,PriceId FROM dbo.Purchase"
        + " WHERE UserId=? AND IdempotencyKey=?",
        (rs, row) -> new SavedPurchase(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
        actor.userId(), key);
    if (!replay.isEmpty()) {
      var prior = replay.getFirst();
      if (prior.chapterId() != chapterId || prior.priceId() != priceId)
        throw new DomainException(409, "IDEMPOTENCY_CONFLICT", "Mã mua đã dùng cho yêu cầu khác.");
      return new PurchaseResult(prior.id(), "ALREADY_PURCHASED", wallet.balance(actor.userId()));
    }
    if (entitlements.owned(actor.userId(), chapterId))
      return new PurchaseResult(null, "ALREADY_ENTITLED", wallet.balance(actor.userId()));
    var snapshot = chapters.current(chapterId).orElseThrow(DomainException::missing);
    if (!"RESTRICTED".equals(snapshot.chapter().accessPolicy()))
      throw new DomainException(422, "CHAPTER_NOT_PRICED", "Chương này không cần mở khóa.");
    var found = currentPrices(chapterId).stream().filter(p -> p.id() == priceId).findFirst();
    Price price = found.orElseThrow(() -> new DomainException(
        409, "PRICE_CHANGED", "Giá đã thay đổi. Hãy tải lại giá chương."));
    var available = wallet.balance(actor.userId());
    if (("PAID".equals(price.currencyCode()) ? available.paidCoin() : available.rewardCoin())
        < price.amount())
      throw new DomainException(409, "INSUFFICIENT_COIN", "Số dư xu không đủ.");
    long purchaseId = jdbc.queryForObject("INSERT dbo.Purchase"
        + "(UserId,ChapterId,PriceId,PriceAmountSnapshot,CurrencyCode,IdempotencyKey,Status)"
        + " OUTPUT INSERTED.Id VALUES(?,?,?,?,?,?,'PENDING')",
        Long.class, actor.userId(), chapterId, price.id(), price.amount(), price.currencyCode(), key);
    WalletOperations.Balance balance = wallet.apply(actor.userId(), price.currencyCode(),
        -price.amount(), "CHAPTER_UNLOCK", "purchase:" + purchaseId);
    long walletTransactionId = jdbc.queryForObject("SELECT Id FROM dbo.WalletTransaction"
        + " WHERE UserId=? AND ReferenceKey=?", Long.class, actor.userId(), "purchase:" + purchaseId);
    entitlements.grantPurchase(actor.userId(), chapterId, purchaseId);
    jdbc.update("UPDATE dbo.Purchase SET WalletTransactionId=?,Status='COMPLETED' WHERE Id=?",
        walletTransactionId, purchaseId);
    return new PurchaseResult(purchaseId, "COMPLETED", balance);
  }

  @Transactional
  public RefundResult refund(Identity actor, long purchaseId, String reason) {
    if (actor == null) throw DomainException.unauthorized();
    if (!actor.administrator())
      throw new DomainException(403, "ADMIN_REQUIRED", "Cần quyền quản trị viên.");
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw new DomainException(422, "INVALID_REASON", "Lý do thu hồi không hợp lệ (tối đa 500 ký tự).");

    var purchases = jdbc.query(
        "SELECT Id,UserId,ChapterId,PriceAmountSnapshot,CurrencyCode,Status,RefundTransactionId"
            + " FROM dbo.Purchase WHERE Id=?",
        (rs, n) -> new PurchaseRecord(rs.getLong(1), rs.getLong(2), rs.getLong(3),
            rs.getLong(4), rs.getString(5), rs.getString(6), (Long) rs.getObject(7)),
        purchaseId);
    if (purchases.isEmpty()) throw DomainException.missing();
    var purchase = purchases.getFirst();

    if ("REFUNDED".equals(purchase.status())) {
      throw new DomainException(409, "ALREADY_REFUNDED", "Giao dịch mua này đã được hoàn xu trước đó.");
    }
    if (!"COMPLETED".equals(purchase.status())) {
      throw new DomainException(409, "INVALID_PURCHASE_STATUS", "Không thể hoàn tiền cho giao dịch chưa hoàn tất.");
    }

    // Lock user row to serialize wallet updates
    jdbc.queryForMap("SELECT Id FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", purchase.userId());

    // 1. Double-entry ledger refund
    String refundRef = "purchase-refund:" + purchaseId;
    WalletOperations.Balance newBalance = wallet.apply(
        purchase.userId(),
        purchase.currencyCode(),
        purchase.amount(),
        "CHAPTER_REFUND",
        refundRef);

    long refundTxId = jdbc.queryForObject(
        "SELECT Id FROM dbo.WalletTransaction WHERE UserId=? AND ReferenceKey=?",
        Long.class, purchase.userId(), refundRef);

    // 2. Revoke entitlement
    entitlements.revokePurchase(purchase.userId(), purchase.chapterId(), actor.userId(), reason);

    // 3. Mark purchase as REFUNDED
    jdbc.update(
        "UPDATE dbo.Purchase SET Status='REFUNDED', RevokedAt=SYSUTCDATETIME(), RevokedByUserId=?,"
            + " RevokeReason=?, RefundTransactionId=? WHERE Id=?",
        actor.userId(), reason.strip(), refundTxId, purchaseId);

    // 4. Audit
    audit.record(
        actor.userId(),
        "REVOKE_PURCHASE",
        "Purchase",
        String.valueOf(purchaseId),
        "SUCCESS",
        Map.of(
            "targetUserId", purchase.userId(),
            "chapterId", purchase.chapterId(),
            "currency", purchase.currencyCode(),
            "amount", purchase.amount(),
            "reason", reason.strip()));

    return new RefundResult(
        purchaseId,
        purchase.userId(),
        purchase.chapterId(),
        purchase.currencyCode(),
        purchase.amount(),
        newBalance);
  }

  @Transactional(readOnly = true)
  public PageResult<PurchaseAdminRow> listPurchases(Identity actor, int page, int size,
      String query, String status) {
    if (actor == null) throw DomainException.unauthorized();
    if (!actor.administrator())
      throw new DomainException(403, "ADMIN_REQUIRED", "Cần quyền quản trị viên.");
    PageResult.validate(page, size);

    StringBuilder sql = new StringBuilder(
        "FROM dbo.Purchase p"
            + " JOIN dbo.[User] u ON u.Id=p.UserId"
            + " JOIN dbo.Chapter ch ON ch.Id=p.ChapterId"
            + " JOIN dbo.ComicTranslation t ON t.Id=ch.TranslationId"
            + " JOIN dbo.Comic c ON c.Id=t.ComicId WHERE 1=1");
    List<Object> params = new ArrayList<>();

    if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
      sql.append(" AND p.Status=?");
      params.add(status.toUpperCase());
    }

    if (query != null && !query.isBlank()) {
      String q = query.strip();
      sql.append(" AND (p.Id=TRY_CONVERT(bigint,?) OR p.UserId=TRY_CONVERT(bigint,?)"
          + " OR CHARINDEX(?,u.Username)>0 OR CHARINDEX(?,c.Title)>0 OR CHARINDEX(?,ch.Title)>0)");
      params.add(q);
      params.add(q);
      params.add(q);
      params.add(q);
      params.add(q);
    }

    Long total = jdbc.queryForObject("SELECT COUNT_BIG(*) " + sql, Long.class, params.toArray());
    if (total == null || total == 0) {
      return PageResult.of(List.of(), page, size, 0);
    }

    String selectSql = "SELECT p.Id,p.UserId,u.Username,p.ChapterId,ch.Title,c.Title,"
        + " p.PriceAmountSnapshot,p.CurrencyCode,p.Status,p.CreatedAt,p.RevokedAt,"
        + " p.RevokeReason,p.RevokedByUserId "
        + sql
        + " ORDER BY p.Id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

    List<Object> queryParams = new ArrayList<>(params);
    queryParams.add(PageResult.offset(page, size));
    queryParams.add(size);

    var rows = jdbc.query(selectSql,
        (rs, n) -> new PurchaseAdminRow(
            rs.getLong(1),
            rs.getLong(2),
            rs.getString(3),
            rs.getLong(4),
            rs.getString(5),
            rs.getString(6),
            rs.getLong(7),
            rs.getString(8),
            rs.getString(9),
            rs.getObject(10, LocalDateTime.class),
            rs.getObject(11, LocalDateTime.class),
            rs.getString(12),
            (Long) rs.getObject(13)),
        queryParams.toArray());

    return PageResult.of(rows, page, size, total);
  }
}
