package com.comicnet.finance.service;

import com.comicnet.common.web.DomainException;
import com.comicnet.common.web.PageResult;
import com.comicnet.wallet.api.WalletOperations;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local-only payment simulator; settlement records a provider event before crediting Paid Coin. */
@Service
public class LocalPaymentSandbox {
  public record CoinOffer(String code, long paidCoin, long amountVnd) {}
  public record ManagedOffer(String code, long paidCoin, long amountVnd, boolean active) {}
  public record PackageInput(long paidCoin, long amountVnd, boolean active) {}
  public record Order(long id, String packageCode, long paidCoin, long amountVnd, String status) {}
  public record OrderHistory(long id, String packageCode, long paidCoin, long amountVnd,
      String status, java.time.LocalDateTime createdAt, java.time.LocalDateTime settledAt) {}
  public record Reconciliation(long pendingOrders, long mismatchCount) {}
  public record AdminOrder(long id, long userId, String username, String packageCode,
      long paidCoin, long amountVnd, String status, java.time.LocalDateTime createdAt) {}
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

  @Transactional(readOnly = true)
  public List<ManagedOffer> managedOffers() {
    return jdbc.query("SELECT Code,PaidCoin,AmountVnd,Active FROM dbo.CoinPackage ORDER BY Code",
        (rs, row) -> new ManagedOffer(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getBoolean(4)));
  }

  @Transactional
  public ManagedOffer saveOffer(String code, PackageInput input) {
    if (code == null || !code.matches("[A-Z0-9_]{2,30}") || input == null
        || input.paidCoin() <= 0 || input.amountVnd() <= 0)
      throw new DomainException(422, "INVALID_PACKAGE", "Mã gói, số xu hoặc giá không hợp lệ.");
    // Lock the package key so concurrent edits cannot change the order snapshot mid-create.
    var found = jdbc.query("SELECT Code FROM dbo.CoinPackage WITH (UPDLOCK,HOLDLOCK) WHERE Code=?",
        (rs, row) -> rs.getString(1), code);
    if (found.isEmpty()) jdbc.update("INSERT dbo.CoinPackage(Code,PaidCoin,AmountVnd,Active) VALUES(?,?,?,?)",
        code, input.paidCoin(), input.amountVnd(), input.active());
    else jdbc.update("UPDATE dbo.CoinPackage SET PaidCoin=?,AmountVnd=?,Active=? WHERE Code=?",
        input.paidCoin(), input.amountVnd(), input.active(), code);
    return new ManagedOffer(code, input.paidCoin(), input.amountVnd(), input.active());
  }

  @Transactional
  public PageResult<AdminOrder> adminOrders(int page, int size) {
    PageResult.validate(page, size);
    expirePending();
    long total = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.PaymentOrder", Long.class);
    var items = jdbc.query("SELECT o.Id,o.UserId,u.Username,o.PackageCode,"
        + "o.PaidCoinSnapshot,o.AmountVndSnapshot,o.Status,o.CreatedAt"
        + " FROM dbo.PaymentOrder o JOIN dbo.[User] u ON u.Id=o.UserId ORDER BY o.Id DESC"
        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        (rs, row) -> new AdminOrder(rs.getLong(1),rs.getLong(2),rs.getString(3),
            rs.getString(4),rs.getLong(5),rs.getLong(6),rs.getString(7),
            rs.getObject(8,java.time.LocalDateTime.class)), PageResult.offset(page,size),size);
    return PageResult.of(items,page,size,total);
  }

  @Transactional
  public PageResult<OrderHistory> history(long userId, int page, int size) {
    PageResult.validate(page, size);
    expirePending();
    long total = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.PaymentOrder WHERE UserId=?",
        Long.class, userId);
    var items = jdbc.query("SELECT Id,PackageCode,PaidCoinSnapshot,AmountVndSnapshot,"
        + "Status,CreatedAt,SettledAt FROM dbo.PaymentOrder WHERE UserId=? ORDER BY Id DESC"
        + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        (rs, row) -> new OrderHistory(rs.getLong(1), rs.getString(2), rs.getLong(3),
            rs.getLong(4), rs.getString(5), rs.getObject(6, java.time.LocalDateTime.class),
            rs.getObject(7, java.time.LocalDateTime.class)),
        userId, PageResult.offset(page, size), size);
    return PageResult.of(items, page, size, total);
  }

  @Transactional
  public Order create(long userId, String packageCode, String key) {
    if (key == null || key.isBlank() || key.length() > 100)
      throw new DomainException(422, "IDEMPOTENCY_KEY_REQUIRED", "Cần mã yêu cầu duy nhất.");
    jdbc.queryForMap("SELECT Id FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", userId);
    expirePending();
    var prior = jdbc.query("SELECT Id,PackageCode,PaidCoinSnapshot,AmountVndSnapshot,Status"
        + " FROM dbo.PaymentOrder WHERE UserId=? AND IdempotencyKey=?",
        (rs, row) -> new Order(rs.getLong(1), rs.getString(2), rs.getLong(3),
            rs.getLong(4), rs.getString(5)), userId, key);
    if (!prior.isEmpty()) {
      if (!prior.getFirst().packageCode().equals(packageCode))
        throw new DomainException(409, "IDEMPOTENCY_CONFLICT", "Mã đơn đã dùng cho gói khác.");
      return prior.getFirst();
    }
    var available = jdbc.query("SELECT PaidCoin,AmountVnd FROM dbo.CoinPackage WITH (UPDLOCK,HOLDLOCK)"
        + " WHERE Code=? AND Active=1", (rs,row) -> new CoinOffer(packageCode,rs.getLong(1),rs.getLong(2)), packageCode);
    if (available.isEmpty()) throw new DomainException(422,"PACKAGE_UNAVAILABLE","Gói xu không khả dụng.");
    var offer = available.getFirst();
    long id = jdbc.queryForObject("INSERT dbo.PaymentOrder"
        + "(UserId,PackageCode,PaidCoinSnapshot,AmountVndSnapshot,IdempotencyKey,Status)"
        + " OUTPUT INSERTED.Id VALUES(?,?,?,?,?,'PENDING')",
        Long.class, userId, packageCode, offer.paidCoin(), offer.amountVnd(), key);
    return new Order(id, packageCode, offer.paidCoin(), offer.amountVnd(), "PENDING");
  }

  @Transactional
  public Order order(long userId, long orderId) {
    expirePending();
    var result = jdbc.query("SELECT Id,PackageCode,PaidCoinSnapshot,AmountVndSnapshot,Status"
        + " FROM dbo.PaymentOrder WHERE Id=? AND UserId=?",
        (rs, row) -> new Order(rs.getLong(1), rs.getString(2), rs.getLong(3),
            rs.getLong(4), rs.getString(5)), orderId, userId);
    if (result.isEmpty()) throw DomainException.missing();
    return result.getFirst();
  }

  @Transactional
  public Order settle(long userId, long orderId) {
    expirePending();
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
  public Order fail(long userId, long orderId) {
    expirePending();
    Order current = lockedOrder(orderId);
    if (current == null) throw DomainException.missing();
    long owner = jdbc.queryForObject("SELECT UserId FROM dbo.PaymentOrder WHERE Id=?",Long.class,orderId);
    if (owner != userId) throw DomainException.missing();
    if ("FAILED".equals(current.status())) return current;
    if (!"PENDING".equals(current.status()))
      throw new DomainException(409,"PAYMENT_NOT_PENDING","Đơn không còn chờ thanh toán.");
    jdbc.update("UPDATE dbo.PaymentOrder SET Status='FAILED' WHERE Id=?",orderId);
    jdbc.update("INSERT dbo.PaymentOrderEvent(OrderId,ActorUserId,EventType,Reason)"
        + " VALUES(?,?,'FAILED',N'Sandbox failure')",orderId,userId);
    return order(userId,orderId);
  }

  @Transactional
  public Order refund(long administratorId, long orderId, String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500)
      throw new DomainException(422,"REFUND_REASON_REQUIRED","Nhập lý do hoàn xu.");
    Order current = lockedOrder(orderId);
    if (current == null) throw DomainException.missing();
    long userId = jdbc.queryForObject("SELECT UserId FROM dbo.PaymentOrder WHERE Id=?",Long.class,orderId);
    if ("REFUNDED".equals(current.status())) return current;
    if (!"COMPLETED".equals(current.status()))
      throw new DomainException(409,"PAYMENT_NOT_COMPLETED","Chỉ hoàn đơn đã hoàn tất.");
    wallet.apply(userId,"PAID",-current.paidCoin(),"PAYMENT_REFUND","refund:"+orderId);
    jdbc.update("UPDATE dbo.PaymentOrder SET Status='REFUNDED' WHERE Id=?",orderId);
    jdbc.update("INSERT dbo.PaymentOrderEvent(OrderId,ActorUserId,EventType,Reason)"
        + " VALUES(?,?,'REFUNDED',?)",orderId,administratorId,reason.strip());
    return new Order(current.id(),current.packageCode(),current.paidCoin(),current.amountVnd(),"REFUNDED");
  }

  private Order lockedOrder(long orderId) {
    var rows = jdbc.query("SELECT Id,PackageCode,PaidCoinSnapshot,AmountVndSnapshot,Status"
        + " FROM dbo.PaymentOrder WITH (UPDLOCK,HOLDLOCK) WHERE Id=?",
        (rs,row) -> new Order(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getLong(4),rs.getString(5)),orderId);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private void expirePending() {
    var expired = jdbc.query("UPDATE dbo.PaymentOrder SET Status='EXPIRED'"
        + " OUTPUT INSERTED.Id WHERE Status='PENDING' AND ExpiresAt<=SYSUTCDATETIME()",
        (rs,row) -> rs.getLong(1));
    for (long id : expired)
      jdbc.update("INSERT dbo.PaymentOrderEvent(OrderId,EventType,Reason)"
          + " VALUES(?,'EXPIRED',N'Payment window elapsed')",id);
  }

  @Transactional
  public Reconciliation reconcile(long administratorId) {
    expirePending();
    long pending = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.PaymentOrder"
        + " WHERE Status='PENDING'", Long.class);
    long mismatches = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.PaymentOrder o"
        + " LEFT JOIN dbo.PaymentTransaction p ON p.OrderId=o.Id"
        + " LEFT JOIN dbo.WalletTransaction w ON w.Id=p.WalletTransactionId"
        + " LEFT JOIN dbo.WebhookInbox i ON i.OrderId=o.Id AND i.Provider='LOCAL_SANDBOX'"
        + " LEFT JOIN dbo.WalletTransaction r ON r.UserId=o.UserId"
        + " AND r.ReferenceKey=CONCAT('refund:',o.Id)"
        + " LEFT JOIN dbo.PaymentOrderEvent e ON e.OrderId=o.Id AND e.EventType='REFUNDED'"
        + " WHERE o.Status IN ('COMPLETED','REFUNDED') AND (p.Id IS NULL OR w.Id IS NULL OR i.Id IS NULL"
        + " OR w.Delta<>o.PaidCoinSnapshot OR p.AmountVnd<>o.AmountVndSnapshot"
        + " OR (o.Status='REFUNDED' AND (r.Id IS NULL OR r.Delta<>-o.PaidCoinSnapshot"
        + " OR e.Id IS NULL)) OR (o.Status='COMPLETED' AND r.Id IS NOT NULL))", Long.class);
    jdbc.update("INSERT dbo.ReconciliationRecord(Provider,PendingOrders,MismatchCount,CheckedBy)"
        + " VALUES('LOCAL_SANDBOX',?,?,?)", pending, mismatches, administratorId);
    return new Reconciliation(pending, mismatches);
  }
}
