package com.comicnet.notification.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.PageResult;
import com.comicnet.notification.dto.NotificationDtos.*;
import com.comicnet.notification.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
  private final NotificationService notifications;

  public NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @GetMapping("/notifications")
  public PageResult<NotificationView> inbox(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    return notifications.inbox(actor, page, size, unreadOnly);
  }

  @GetMapping("/notifications/summary")
  public UnreadSummary summary(@AuthenticationPrincipal Identity actor) {
    return notifications.summary(actor);
  }

  @PutMapping("/notifications/{id}/read")
  public NotificationView read(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    return notifications.read(actor, id, true);
  }

  @DeleteMapping("/notifications/{id}/read")
  public NotificationView unread(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    return notifications.read(actor, id, false);
  }

  @PostMapping("/notifications/read-all")
  public UnreadSummary readAll(@AuthenticationPrincipal Identity actor) {
    return notifications.readAll(actor);
  }

  @GetMapping("/notifications/preferences")
  public List<PreferenceView> preferences(@AuthenticationPrincipal Identity actor) {
    return notifications.preferences(actor);
  }

  @PutMapping("/notifications/preferences/{type}")
  public ResponseEntity<PreferenceView> preference(
      @AuthenticationPrincipal Identity actor,
      @PathVariable String type,
      @Valid @RequestBody PreferenceInput input) {
    return ResponseEntity.ok(notifications.preference(actor, type, input));
  }

  @PostMapping("/admin/announcements")
  public ResponseEntity<AnnouncementView> announce(
      @AuthenticationPrincipal Identity actor, @Valid @RequestBody AnnouncementInput input) {
    var created = notifications.announce(actor, input);
    return ResponseEntity.created(java.net.URI.create("/api/v1/admin/announcements/" + created.id()))
        .body(created);
  }

  @GetMapping("/admin/announcements")
  public PageResult<AnnouncementView> announcements(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return notifications.announcements(actor, page, size);
  }
}
