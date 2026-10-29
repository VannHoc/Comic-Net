package com.comicnet.finance.controller;

import com.comicnet.finance.service.LocalPaymentSandbox;
import com.comicnet.common.web.DomainException;
import com.comicnet.common.web.PageResult;
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
  public record RefundInput(String reason) {}
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

  @GetMapping("/me/payment-orders")
  public ResponseEntity<com.comicnet.common.web.PageResult<LocalPaymentSandbox.OrderHistory>> history(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(payments.history(actor.userId(), page, size));
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

  @PostMapping("/me/payment-orders/{id}/sandbox-fail")
  public ResponseEntity<LocalPaymentSandbox.Order> fail(@AuthenticationPrincipal Identity actor,
      @PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(payments.fail(actor.userId(), id));
  }

  @GetMapping("/admin/coin-packages")
  public List<LocalPaymentSandbox.ManagedOffer> managedOffers(@AuthenticationPrincipal Identity actor) {
    requireAdmin(actor);
    return payments.managedOffers();
  }

  @PutMapping("/admin/coin-packages/{code}")
  public LocalPaymentSandbox.ManagedOffer saveOffer(@AuthenticationPrincipal Identity actor,
      @PathVariable String code, @RequestBody LocalPaymentSandbox.PackageInput input) {
    requireAdmin(actor);
    return payments.saveOffer(code, input);
  }

  @GetMapping("/admin/payment-orders")
  public ResponseEntity<PageResult<LocalPaymentSandbox.AdminOrder>> adminOrders(
      @AuthenticationPrincipal Identity actor, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    requireAdmin(actor);
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payments.adminOrders(page,size));
  }

  @PostMapping("/admin/payment-orders/{id}/refund")
  public LocalPaymentSandbox.Order refund(@AuthenticationPrincipal Identity actor,
      @PathVariable long id, @RequestBody RefundInput input) {
    requireAdmin(actor);
    return payments.refund(actor.userId(), id, input.reason());
  }

  @PostMapping("/admin/payment-reconciliation")
  public LocalPaymentSandbox.Reconciliation reconcile(@AuthenticationPrincipal Identity actor) {
    requireAdmin(actor);
    return payments.reconcile(actor.userId());
  }

  private void requireAdmin(Identity actor) {
    if (actor == null || !actor.administrator()) throw DomainException.forbidden();
  }
}
