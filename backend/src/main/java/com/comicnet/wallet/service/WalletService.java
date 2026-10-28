package com.comicnet.wallet.service;

import com.comicnet.common.web.DomainException;
import com.comicnet.common.web.PageResult;
import com.comicnet.wallet.api.WalletOperations;
import com.comicnet.wallet.api.WalletOperations.Balance;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService implements WalletOperations {
  public record Entry(long id, String tokenType, long delta, long balanceAfter,
      String source, String status, Instant createdAt) {}
  private record Replay(String tokenType, long delta, String source) {}
  private final JdbcTemplate jdbc;

  public WalletService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional(readOnly = true)
  public Balance balance(long userId) {
    var rows = jdbc.query("SELECT PaidBalance,RewardBalance FROM dbo.Wallet WHERE UserId=?",
        (rs, row) -> new Balance(rs.getLong(1), rs.getLong(2)), userId);
    return rows.isEmpty() ? new Balance(0, 0) : rows.getFirst();
  }

  @Transactional(readOnly = true)
  public PageResult<Entry> history(long userId, int page, int size) {
    PageResult.validate(page, size);
    long total = jdbc.queryForObject(
        "SELECT COUNT_BIG(*) FROM dbo.WalletTransaction WHERE UserId=?", Long.class, userId);
    var items = jdbc.query(
        "SELECT Id,TokenType,Delta,BalanceAfter,Source,Status,CreatedAt"
            + " FROM dbo.WalletTransaction WHERE UserId=? ORDER BY Id DESC"
            + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
        (rs, row) -> new Entry(rs.getLong(1), rs.getString(2), rs.getLong(3),
            rs.getLong(4), rs.getString(5), rs.getString(6),
            rs.getObject(7, LocalDateTime.class).toInstant(ZoneOffset.UTC)),
        userId, PageResult.offset(page, size), size);
    return PageResult.of(items, page, size, total);
  }

  /** Trusted payment/reward services call this; no public credit endpoint exists. */
  @Transactional
  public Balance apply(long userId, String tokenType, long delta, String source, String reference) {
    if (!"PAID".equals(tokenType) && !"REWARD".equals(tokenType))
      throw new DomainException(422, "INVALID_TOKEN", "Loại xu không hợp lệ.");
    if (delta == 0 || delta == Long.MIN_VALUE || source == null || source.isBlank()
        || source.length() > 40 || reference == null || reference.isBlank()
        || reference.length() > 100)
      throw new DomainException(422, "INVALID_WALLET_TRANSACTION", "Giao dịch ví không hợp lệ.");
    // The user-row lock serializes wallet creation and concurrent spend.
    Integer locked = jdbc.queryForObject(
        "SELECT COUNT(*) FROM dbo.[User] WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", Integer.class, userId);
    if (locked == null || locked != 1) throw DomainException.unauthorized();
    var existing = jdbc.query("SELECT TokenType,Delta,Source FROM dbo.WalletTransaction"
            + " WHERE UserId=? AND ReferenceKey=?",
        (rs, row) -> new Replay(rs.getString(1), rs.getLong(2), rs.getString(3)),
        userId, reference);
    if (!existing.isEmpty()) {
      if (!existing.getFirst().equals(new Replay(tokenType, delta, source)))
        throw new DomainException(409, "IDEMPOTENCY_CONFLICT", "Mã giao dịch đã dùng cho nội dung khác.");
      return balance(userId);
    }
    jdbc.update("IF NOT EXISTS (SELECT 1 FROM dbo.Wallet WHERE UserId=?)"
        + " INSERT dbo.Wallet(UserId) VALUES(?)", userId, userId);
    jdbc.update("INSERT dbo.WalletAccount(UserId,CurrencyCode,BalanceCache)"
        + " SELECT ?,c.Code,0 FROM dbo.CurrencyDefinition c"
        + " WHERE NOT EXISTS (SELECT 1 FROM dbo.WalletAccount a"
        + " WHERE a.UserId=? AND a.CurrencyCode=c.Code)", userId, userId);
    Balance before = balance(userId);
    long after;
    try {
      after = Math.addExact("PAID".equals(tokenType) ? before.paidCoin() : before.rewardCoin(), delta);
    } catch (ArithmeticException overflow) {
      throw new DomainException(422, "WALLET_OVERFLOW", "Số dư vượt giới hạn.");
    }
    if (after < 0) throw new DomainException(409, "INSUFFICIENT_COIN", "Số dư xu không đủ.");
    jdbc.update("UPDATE dbo.Wallet SET " + ("PAID".equals(tokenType) ? "PaidBalance" : "RewardBalance")
        + "=?,UpdatedAt=SYSUTCDATETIME() WHERE UserId=?", after, userId);
    jdbc.update("INSERT dbo.WalletTransaction(UserId,TokenType,Delta,BalanceAfter,Source,ReferenceKey,Status)"
        + " VALUES(?,?,?,?,?,?,'COMPLETED')", userId, tokenType, delta, after, source, reference);
    long transactionId = jdbc.queryForObject("SELECT Id FROM dbo.WalletTransaction"
        + " WHERE UserId=? AND ReferenceKey=?", Long.class, userId, reference);
    int updated = jdbc.update("UPDATE dbo.WalletAccount SET BalanceCache=?"
        + " WHERE UserId=? AND CurrencyCode=?", after, userId, tokenType);
    if (updated != 1) throw new IllegalStateException("Wallet account not found");
    jdbc.update("UPDATE dbo.WalletAccount SET BalanceCache=BalanceCache-?"
        + " WHERE SystemAccountCode='PLATFORM_CLEARING' AND CurrencyCode=?", delta, tokenType);
    jdbc.update("INSERT dbo.LedgerEntry(TransactionId,AccountId,Side,Amount)"
        + " SELECT ?,Id,?,? FROM dbo.WalletAccount WHERE UserId=? AND CurrencyCode=?",
        transactionId, delta > 0 ? "CREDIT" : "DEBIT", Math.abs(delta), userId, tokenType);
    jdbc.update("INSERT dbo.LedgerEntry(TransactionId,AccountId,Side,Amount)"
        + " SELECT ?,Id,?,? FROM dbo.WalletAccount"
        + " WHERE SystemAccountCode='PLATFORM_CLEARING' AND CurrencyCode=?",
        transactionId, delta > 0 ? "DEBIT" : "CREDIT", Math.abs(delta), tokenType);
    return balance(userId);
  }
}
