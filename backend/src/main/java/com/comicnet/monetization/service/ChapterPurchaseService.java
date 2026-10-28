package com.comicnet.monetization.service;

import com.comicnet.common.web.DomainException;
import com.comicnet.entitlement.api.EntitlementGrants;
import com.comicnet.identity.api.Identity;
import com.comicnet.publishing.api.PublishedChapters;
import com.comicnet.publishing.api.PublishingChapterAccess;
import com.comicnet.wallet.api.WalletOperations;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterPurchaseService {
  public record Price(long id, String currencyCode, long amount) {}
  public record PurchaseResult(Long purchaseId, String status, WalletOperations.Balance balance) {}
  private record SavedPurchase(long id, long chapterId, long priceId) {}
  private final JdbcTemplate jdbc;
  private final PublishedChapters chapters;
  private final PublishingChapterAccess scope;
  private final WalletOperations wallet;
  private final EntitlementGrants entitlements;

  public ChapterPurchaseService(JdbcTemplate jdbc, PublishedChapters chapters,
      PublishingChapterAccess scope, WalletOperations wallet, EntitlementGrants entitlements) {
    this.jdbc = jdbc;
    this.chapters = chapters;
    this.scope = scope;
    this.wallet = wallet;
    this.entitlements = entitlements;
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
    if (( !"PAID".equals(currency) && !"REWARD".equals(currency)) || amount <= 0)
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
}
