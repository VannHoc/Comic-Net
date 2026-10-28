package com.comicnet.identity.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.identity.service.SessionManagement;
import com.comicnet.common.web.PageResult;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/sessions")
public class SessionController {
  private final SessionManagement sessions;

  public SessionController(SessionManagement sessions) {
    this.sessions = sessions;
  }

  @GetMapping
  public ResponseEntity<PageResult<SessionManagement.SessionView>> list(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(sessions.list(actor, page, size));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    sessions.revoke(actor, id);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @PostMapping("/revoke-others")
  public ResponseEntity<Void> others(@AuthenticationPrincipal Identity actor) {
    sessions.revokeOthers(actor);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
