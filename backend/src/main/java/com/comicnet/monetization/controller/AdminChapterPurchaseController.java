package com.comicnet.monetization.controller;

import com.comicnet.common.web.PageResult;
import com.comicnet.identity.api.Identity;
import com.comicnet.monetization.service.ChapterPurchaseService;
import com.comicnet.monetization.service.ChapterPurchaseService.PurchaseAdminRow;
import com.comicnet.monetization.service.ChapterPurchaseService.RefundResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/purchases")
public class AdminChapterPurchaseController {
  public record RefundRequest(String reason) {}

  private final ChapterPurchaseService service;

  public AdminChapterPurchaseController(ChapterPurchaseService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<PageResult<PurchaseAdminRow>> list(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(service.listPurchases(actor, page, size, q, status));
  }

  @PostMapping("/{id}/refund")
  public ResponseEntity<RefundResult> refund(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @RequestBody RefundRequest body) {
    String reason = body != null ? body.reason() : null;
    return ResponseEntity.ok(service.refund(actor, id, reason));
  }
}
