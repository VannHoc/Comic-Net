package com.comicnet.community.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public final class CommentDtos {
  private CommentDtos() {}

  public record CommentInput(
      @NotBlank String comicId,
      String chapterId,
      String pageId,
      BigDecimal relativeOffset,
      String parentId,
      @NotBlank @Size(max = 4000) String body,
      boolean spoiler) {}

  public record EditInput(@NotBlank @Size(max = 4000) String body, boolean spoiler) {}

  public record VoteInput(@Min(-1) @Max(1) int value) {}

  public record ReportInput(@NotBlank @Size(max = 1000) String reason) {}

  public record ModerationInput(boolean hidden, @NotBlank @Size(max = 1000) String reason) {}

  public record CommentView(
      String id,
      String comicId,
      String chapterId,
      String pageId,
      BigDecimal relativeOffset,
      String parentId,
      String authorId,
      String displayName,
      String body,
      boolean spoiler,
      boolean deleted,
      boolean hidden,
      boolean anchorAvailable,
      long votes,
      int myVote,
      Instant updatedAt,
      String etag) {}

  public record ReportView(
      String id, String commentId, String reason, String reporterId, Instant createdAt) {}

  public record BlockView(String userId, String displayName) {}
}
