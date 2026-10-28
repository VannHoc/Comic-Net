package com.comicnet.community.controller;

import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.PageResult;
import com.comicnet.community.dto.CommentDtos.*;
import com.comicnet.community.service.CommentService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommentController {
  private final CommentService comments;

  public CommentController(CommentService comments) {
    this.comments = comments;
  }

  @GetMapping("/community/comments")
  public PageResult<CommentView> list(
      @AuthenticationPrincipal Identity actor,
      @RequestParam long comicId,
      @RequestParam(required = false) Long chapterId,
      @RequestParam(required = false) Long pageId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return comments.list(actor, comicId, chapterId, pageId, page, size);
  }

  @PostMapping("/community/comments")
  public ResponseEntity<CommentView> create(
      @AuthenticationPrincipal Identity actor,
      @Valid @RequestBody CommentInput input,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var result = comments.create(actor, input, key);
    return ResponseEntity.created(URI.create("/api/v1/community/comments/" + result.id()))
        .eTag(result.etag())
        .body(result);
  }

  @GetMapping("/community/comments/{comment}")
  public ResponseEntity<CommentView> get(
      @AuthenticationPrincipal Identity actor, @PathVariable long comment) {
    var result = comments.get(actor, comment);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(result.etag())
        .body(result);
  }

  @PatchMapping("/community/comments/{comment}")
  public ResponseEntity<CommentView> edit(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long comment,
      @Valid @RequestBody EditInput input,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    var result = comments.edit(actor, comment, input, etag);
    return ResponseEntity.ok().eTag(result.etag()).body(result);
  }

  @DeleteMapping("/community/comments/{comment}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long comment,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    comments.delete(actor, comment, etag);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/community/comments/{comment}/vote")
  public CommentView vote(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long comment,
      @Valid @RequestBody VoteInput input) {
    return comments.vote(actor, comment, input.value());
  }

  @DeleteMapping("/community/comments/{comment}/vote")
  public CommentView unvote(@AuthenticationPrincipal Identity actor, @PathVariable long comment) {
    return comments.vote(actor, comment, null);
  }

  @PostMapping("/community/comments/{comment}/reports")
  public ResponseEntity<Void> report(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long comment,
      @Valid @RequestBody ReportInput input) {
    comments.report(actor, comment, input.reason());
    return ResponseEntity.accepted().build();
  }

  @PutMapping("/me/blocks/{user}")
  public ResponseEntity<Void> block(
      @AuthenticationPrincipal Identity actor, @PathVariable long user) {
    comments.block(actor, user, true);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me/blocks")
  public PageResult<BlockView> blocks(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return comments.blocks(actor, page, size);
  }

  @DeleteMapping("/me/blocks/{user}")
  public ResponseEntity<Void> unblock(
      @AuthenticationPrincipal Identity actor, @PathVariable long user) {
    comments.block(actor, user, false);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/community/moderation/reports")
  public PageResult<ReportView> reports(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return comments.reports(actor, page, size);
  }

  @PostMapping("/community/comments/{comment}/moderation")
  public CommentView moderate(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long comment,
      @Valid @RequestBody ModerationInput input,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    return comments.moderate(actor, comment, input, etag);
  }
}
