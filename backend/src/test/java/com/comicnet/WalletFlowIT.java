package com.comicnet;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.comicnet.common.web.DomainException;
import com.comicnet.identity.api.AccountAdministration;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.support.SqlTestActors;
import com.comicnet.wallet.service.WalletService;
import com.comicnet.wallet.api.WalletOperations;
import com.comicnet.finance.service.LocalPaymentSandbox;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "comicnet.mail.worker-enabled=false")
@AutoConfigureMockMvc
@Transactional
class WalletFlowIT {
  @Autowired WalletService wallet;
  @Autowired LocalPaymentSandbox payments;
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  @Autowired MockMvc mvc;

  @Test
  void balanceLedgerIdempotencyInsufficientFundsAndAccountIsolation() throws Exception {
    var actors = new SqlTestActors(auth, accounts, jdbc, entities);
    long user = actors.reader().userId();
    long other = actors.reader().userId();
    assertThat(wallet.balance(user)).isEqualTo(new WalletOperations.Balance(0, 0));
    wallet.apply(user, "PAID", 100, "SANDBOX_PAYMENT", "wallet-test-paid");
    wallet.apply(user, "PAID", 100, "SANDBOX_PAYMENT", "wallet-test-paid");
    assertThatThrownBy(() -> wallet.apply(user, "PAID", 200, "SANDBOX_PAYMENT", "wallet-test-paid"))
        .isInstanceOf(DomainException.class);
    wallet.apply(user, "REWARD", 30, "CHECK_IN", "wallet-test-reward");
    wallet.apply(user, "PAID", -40, "CHAPTER_UNLOCK", "wallet-test-spend");
    assertThat(wallet.balance(user)).isEqualTo(new WalletOperations.Balance(60, 30));
    assertThat(wallet.history(user, 0, 20).items()).hasSize(3);
    assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.LedgerEntry e"
        + " JOIN dbo.WalletTransaction t ON t.Id=e.TransactionId WHERE t.UserId=?",
        Long.class, user)).isEqualTo(6);
    assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM ("
        + " SELECT e.TransactionId,a.CurrencyCode"
        + " FROM dbo.LedgerEntry e JOIN dbo.WalletAccount a ON a.Id=e.AccountId"
        + " JOIN dbo.WalletTransaction t ON t.Id=e.TransactionId WHERE t.UserId=?"
        + " GROUP BY e.TransactionId,a.CurrencyCode"
        + " HAVING SUM(CASE WHEN e.Side='DEBIT' THEN e.Amount ELSE -e.Amount END)<>0"
        + ") imbalance", Long.class, user)).isZero();
    assertThat(jdbc.queryForObject("SELECT BalanceCache FROM dbo.WalletAccount"
        + " WHERE UserId=? AND CurrencyCode='PAID'", Long.class, user)).isEqualTo(60);
    assertThat(wallet.balance(other)).isEqualTo(new WalletOperations.Balance(0, 0));
    assertThat(wallet.history(other, 0, 20).items()).isEmpty();
    assertThatThrownBy(() -> wallet.apply(user, "REWARD", -31, "CHAPTER_UNLOCK", "too-much"))
        .isInstanceOf(DomainException.class);
    assertThat(wallet.balance(user)).isEqualTo(new WalletOperations.Balance(60, 30));
    assertThat(wallet.history(user, 0, 20).items()).hasSize(3);
    mvc.perform(get("/api/v1/me/wallet")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me/wallet/transactions")).andExpect(status().isUnauthorized());
  }

  @Test
  void localPaymentOrderSettlementCreditsOnceAndReconciles() throws Exception {
    var actors = new SqlTestActors(auth, accounts, jdbc, entities);
    long user = actors.reader().userId();
    long other = actors.reader().userId();
    var offer = payments.offers().getFirst();
    var order = payments.create(user, offer.code(), "sandbox-order-test");
    assertThat(order.status()).isEqualTo("PENDING");
    assertThat(payments.create(user, offer.code(), "sandbox-order-test").id()).isEqualTo(order.id());
    assertThatThrownBy(() -> payments.create(user, "DEMO_500", "sandbox-order-test"))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> payments.order(other, order.id()))
        .isInstanceOf(DomainException.class);
    assertThat(payments.settle(user, order.id()).status()).isEqualTo("COMPLETED");
    assertThat(payments.settle(user, order.id()).status()).isEqualTo("COMPLETED");
    assertThat(wallet.balance(user).paidCoin()).isEqualTo(offer.paidCoin());
    assertThat(wallet.balance(other).paidCoin()).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.PaymentTransaction WHERE OrderId=?",
        Integer.class, order.id())).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.WebhookInbox WHERE OrderId=?",
        Integer.class, order.id())).isEqualTo(1);
    assertThat(payments.reconcile(actors.administrator().userId()).mismatchCount()).isZero();
    mvc.perform(post("/api/v1/me/payment-orders").contentType("application/json")
        .content("{\"packageCode\":\"DEMO_100\"}"))
        .andExpect(status().isUnauthorized());
  }
}
