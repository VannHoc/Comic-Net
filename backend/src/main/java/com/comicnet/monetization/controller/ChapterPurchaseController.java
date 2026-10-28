package com.comicnet.monetization.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.monetization.service.ChapterPurchaseService;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ChapterPurchaseController {
  public record PriceInput(String currencyCode, long amount) {}
  public record UnlockInput(long priceId) {}
  private final ChapterPurchaseService purchases;

  public ChapterPurchaseController(ChapterPurchaseService purchases) { this.purchases = purchases; }

  @GetMapping("/chapters/{id}/prices")
  public ResponseEntity<List<ChapterPurchaseService.Price>> prices(@PathVariable long id) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(purchases.prices(id));
  }

  @PutMapping("/publishing/chapters/{id}/prices")
  public ChapterPurchaseService.Price setPrice(@AuthenticationPrincipal Identity actor,
      @PathVariable long id, @RequestBody PriceInput body) {
    return purchases.setPrice(actor, id, body.currencyCode(), body.amount());
  }

  @PostMapping("/chapters/{id}/unlock")
  public ResponseEntity<ChapterPurchaseService.PurchaseResult> unlock(
      @AuthenticationPrincipal Identity actor, @PathVariable long id,
      @RequestBody UnlockInput body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(purchases.unlock(actor, id, body.priceId(), key));
  }
}
