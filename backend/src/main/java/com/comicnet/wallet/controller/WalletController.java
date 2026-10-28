package com.comicnet.wallet.controller;

import com.comicnet.common.web.DomainException;
import com.comicnet.common.web.Ids;
import com.comicnet.common.web.PageResult;
import com.comicnet.wallet.service.WalletService;
import com.comicnet.wallet.api.WalletOperations.Balance;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/wallet")
public class WalletController {
  private final WalletService wallet;
  public WalletController(WalletService wallet) { this.wallet = wallet; }

  private long user(Principal principal) {
    if (principal == null) throw DomainException.unauthorized();
    return Ids.parse(principal.getName());
  }

  @GetMapping
  public ResponseEntity<Balance> balance(Principal principal) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(wallet.balance(user(principal)));
  }

  @GetMapping("/transactions")
  public ResponseEntity<PageResult<WalletService.Entry>> transactions(Principal principal,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(wallet.history(user(principal), page, size));
  }
}
