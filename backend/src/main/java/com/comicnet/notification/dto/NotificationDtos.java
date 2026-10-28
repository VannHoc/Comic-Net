package com.comicnet.notification.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class NotificationDtos {
  private NotificationDtos() {}

  public record NotificationView(
      String id,
      String type,
      String title,
      String body,
      String linkType,
      String linkId,
      boolean read,
      Instant createdAt) {}

  public record UnreadSummary(long unread, long total) {}

  public record PreferenceView(String type, boolean inApp, boolean email) {}

  public record PreferenceInput(boolean inApp, boolean email) {}

  public record AnnouncementInput(
      @NotBlank @Size(max = 200) String title, @NotBlank @Size(max = 2000) String body) {}

  public record AnnouncementView(
      String id, String title, String body, String createdBy, Instant createdAt, int recipients) {}
}
