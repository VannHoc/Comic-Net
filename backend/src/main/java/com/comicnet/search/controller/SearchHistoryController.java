package com.comicnet.search.controller;

import com.comicnet.common.web.*;
import com.comicnet.search.service.SearchHistoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/search-history")
public class SearchHistoryController {
  public record Input(@NotBlank @Size(max = 200) String query) {}

  private final SearchHistoryService history;

  public SearchHistoryController(SearchHistoryService history) {
    this.history = history;
  }

  private long user(Principal actor) {
    if (actor == null) throw DomainException.unauthorized();
    return Ids.parse(actor.getName());
  }

  @GetMapping
  public ResponseEntity<List<SearchHistoryService.Entry>> list(Principal actor) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.list(user(actor)));
  }

  @PostMapping
  public ResponseEntity<Void> remember(Principal actor, @Valid @RequestBody Input body) {
    history.remember(user(actor), body.query());
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(
      Principal actor, @RequestParam(required = false) String query) {
    history.delete(user(actor), query);
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }
}
