package com.comicnet.finance.controller;

import com.comicnet.finance.service.LocalPaymentSandbox;
import com.comicnet.identity.api.Identity;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Available only with the local profile; no real-money settlement endpoint exists here. */
@Profile("local")
@RestController
@RequestMapping("/api/v1")
public class LocalPaymentSandboxController {
  public record OrderInput(String packageCode) {}
  private final LocalPaymentSandbox payments;
  public LocalPaymentSandboxController(LocalPaymentSandbox payments) { this.payments = payments; }

  @GetMapping("/me/coin-packages")
  public List<LocalPaymentSandbox.CoinOffer> offers() { return payments.offers(); }

  @PostMapping("/me/payment-orders")
  public ResponseEntity<LocalPaymentSandbox.Order> create(@AuthenticationPrincipal Identity actor,
      @RequestBody OrderInput body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(payments.create(actor.userId(), body.packageCode(), key));
  }

  @GetMapping("/me/payment-orders/{id}")
  public ResponseEntity<LocalPaymentSandbox.Order> order(@AuthenticationPrincipal Identity actor,
      @PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(payments.order(actor.userId(), id));
  }

  @PostMapping("/me/payment-orders/{id}/sandbox-settle")
  public ResponseEntity<LocalPaymentSandbox.Order> settle(@AuthenticationPrincipal Identity actor,
      @PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(payments.settle(actor.userId(), id));
  }

  @PostMapping("/admin/payment-reconciliation")
  public LocalPaymentSandbox.Reconciliation reconcile(@AuthenticationPrincipal Identity actor) {
    return payments.reconcile(actor.userId());
  }
}
