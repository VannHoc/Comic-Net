package com.comicnet.finance.service;

import com.comicnet.common.web.DomainException;
import com.comicnet.wallet.api.WalletOperations;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local-only payment simulator; settlement records a provider event before crediting Paid Coin. */
@Service
public class LocalPaymentSandbox {
  public record CoinOffer(String code, long paidCoin, long amountVnd) {}
  public record Order(long id, String packageCode, long paidCoin, long amountVnd, String status) {}
  public record Reconciliation(long pendingOrders, long mismatchCount) {}
  private final JdbcTemplate jdbc;
  private final WalletOperations wallet;

  public LocalPaymentSandbox(JdbcTemplate jdbc, WalletOperations wallet) {
    this.jdbc = jdbc;
    this.wallet = wallet;
  }

  @Transactional(readOnly = true)
  public List<CoinOffer> offers() {
    return jdbc.query("SELECT Code,PaidCoin,AmountVnd FROM dbo.CoinPackage"
        + " WHERE Active=1 ORDER BY PaidCoin",
        (rs, row) -> new CoinOffer(rs.getString(1), rs.getLong(2), rs.getLong(3)));
  }

  @Transactional
  public Order create(long userId, String packageCode, String key) {
    if (key == null || key.isBlank() || key.length() > 100)
      throw new DomainException(422, "IDEMPOTENCY_KEY_REQUIRED", "Cần mã yêu cầu duy nhất.");
    jdbc.queryForMap("SELECT Id FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", userId);
    var prior = jdbc.query("SELECT Id,PackageCode,PaidCoinSnapshot,AmountVndSnapshot,Status"
        + " FROM dbo.PaymentOrder WHERE UserId=? AND IdempotencyKey=?",
        (rs, row) -> new Order(rs.getLong(1), rs.getString(2), rs.getLong(3),
            rs.getLong(4), rs.getString(5)), userId, key);
    if (!prior.isEmpty()) {
      if (!prior.getFirst().packageCode().equals(packageCode))
        throw new DomainException(409, "IDEMPOTENCY_CONFLICT", "Mã đơn đã dùng cho gói khác.");
      return prior.getFirst();
    }
    var offer = offers().stream().filter(p -> p.code().equals(packageCode)).findFirst()
        .orElseThrow(() -> new DomainException(422, "PACKAGE_UNAVAILABLE", "Gói xu không tồn tại."));
    long id = jdbc.queryForObject("INSERT dbo.PaymentOrder"
        + "(UserId,PackageCode,PaidCoinSnapshot,AmountVndSnapshot,IdempotencyKey,Status)"
        + " OUTPUT INSERTED.Id VALUES(?,?,?,?,?,'PENDING')",
        Long.class, userId, packageCode, offer.paidCoin(), offer.amountVnd(), key);
    return new Order(id, packageCode, offer.paidCoin(), offer.amountVnd(), "PENDING");
  }

  @Transactional(readOnly = true)
  public Order order(long userId, long orderId) {
    var result = jdbc.query("SELECT Id,PackageCode,PaidCoinSnapshot,AmountVndSnapshot,Status"
        + " FROM dbo.PaymentOrder WHERE Id=? AND UserId=?",
        (rs, row) -> new Order(rs.getLong(1), rs.getString(2), rs.getLong(3),
            rs.getLong(4), rs.getString(5)), orderId, userId);
    if (result.isEmpty()) throw DomainException.missing();
    return result.getFirst();
  }

  @Transactional
  public Order settle(long userId, long orderId) {
    var rows = jdbc.query("SELECT Id,PackageCode,PaidCoinSnapshot,AmountVndSnapshot,Status"
        + " FROM dbo.PaymentOrder WITH (UPDLOCK,HOLDLOCK) WHERE Id=? AND UserId=?",
        (rs, row) -> new Order(rs.getLong(1), rs.getString(2), rs.getLong(3),
            rs.getLong(4), rs.getString(5)), orderId, userId);
    if (rows.isEmpty()) throw DomainException.missing();
    Order current = rows.getFirst();
    if ("COMPLETED".equals(current.status())) return current;
    if (!"PENDING".equals(current.status()))
      throw new DomainException(409, "PAYMENT_NOT_PENDING", "Đơn không còn chờ thanh toán.");
    String providerRef = "local-sandbox:" + orderId;
    jdbc.update("INSERT dbo.WebhookInbox(Provider,EventId,OrderId,Status)"
        + " VALUES('LOCAL_SANDBOX',?,?,'PROCESSED')", providerRef, orderId);
    wallet.apply(userId, "PAID", current.paidCoin(), "SANDBOX_PAYMENT", "payment:" + orderId);
    long walletTransaction = jdbc.queryForObject("SELECT Id FROM dbo.WalletTransaction"
        + " WHERE UserId=? AND ReferenceKey=?", Long.class, userId, "payment:" + orderId);
    jdbc.update("INSERT dbo.PaymentTransaction"
        + "(OrderId,Provider,ProviderReference,AmountVnd,WalletTransactionId)"
        + " VALUES(?,'LOCAL_SANDBOX',?,?,?)",
        orderId, providerRef, current.amountVnd(), walletTransaction);
    jdbc.update("UPDATE dbo.PaymentOrder SET Status='COMPLETED',SettledAt=SYSUTCDATETIME()"
        + " WHERE Id=?", orderId);
    return order(userId, orderId);
  }

  @Transactional
  public Reconciliation reconcile(long administratorId) {
    long pending = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.PaymentOrder"
        + " WHERE Status='PENDING'", Long.class);
    long mismatches = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.PaymentOrder o"
        + " LEFT JOIN dbo.PaymentTransaction p ON p.OrderId=o.Id"
        + " LEFT JOIN dbo.WalletTransaction w ON w.Id=p.WalletTransactionId"
        + " LEFT JOIN dbo.WebhookInbox i ON i.OrderId=o.Id AND i.Provider='LOCAL_SANDBOX'"
        + " WHERE o.Status='COMPLETED' AND (p.Id IS NULL OR w.Id IS NULL OR i.Id IS NULL"
        + " OR w.Delta<>o.PaidCoinSnapshot OR p.AmountVnd<>o.AmountVndSnapshot)", Long.class);
    jdbc.update("INSERT dbo.ReconciliationRecord(Provider,PendingOrders,MismatchCount,CheckedBy)"
        + " VALUES('LOCAL_SANDBOX',?,?,?)", pending, mismatches, administratorId);
    return new Reconciliation(pending, mismatches);
  }
}
