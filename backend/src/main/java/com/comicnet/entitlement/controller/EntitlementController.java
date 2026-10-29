package com.comicnet.entitlement.controller;

import com.comicnet.common.web.DomainException;
import com.comicnet.common.web.PageResult;
import com.comicnet.entitlement.service.EntitlementService;
import com.comicnet.identity.api.Identity;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/entitlements")
public class EntitlementController {
  private final EntitlementService entitlements;

  public EntitlementController(EntitlementService entitlements) { this.entitlements = entitlements; }

  @GetMapping
  public ResponseEntity<PageResult<EntitlementService.HistoryItem>> history(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (actor == null) throw DomainException.unauthorized();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(entitlements.history(actor.userId(), page, size));
  }
}
