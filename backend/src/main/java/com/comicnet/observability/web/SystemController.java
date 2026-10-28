package com.comicnet.observability.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
  public record Ping(String status, String service) {}

  @GetMapping("/ping")
  public ResponseEntity<Ping> ping() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new Ping("UP", "comic-net-backend"));
  }
}
