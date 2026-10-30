package com.comicnet.admin.controller;

import com.comicnet.identity.api.*;
import com.comicnet.common.web.*;
import com.comicnet.creator.api.CreatorDirectory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdministrationController {
  private final AccountAdministration accounts;
  private final CreatorDirectory teams;

  public AdministrationController(AccountAdministration accounts, CreatorDirectory teams) {
    this.accounts = accounts;
    this.teams = teams;
  }

  public record StatusPut(
      @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status) {}

  public record RolesPut(
      @NotEmpty @Size(max = 4)
          List<@Pattern(regexp = "READER|CREATOR|ADMINISTRATOR") String> roles) {}

  public record TeamCreate(
      @NotBlank @Size(max = 200) String name,
      @NotBlank @Size(max = 200) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
      @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String ownerUserId) {}

  public record MemberPut(
      @NotEmpty @Size(max = 6)
          List<@Pattern(regexp = "OWNER|CREATOR|TRANSLATOR|EDITOR|PROOFREADER|UPLOADER") String>
              roles,
      @NotBlank @Pattern(regexp = "ACTIVE|REMOVED") String status) {}

  private Set<String> distinct(List<String> roles) {
    Set<String> result = new TreeSet<>(roles);
    if (result.size() != roles.size())
      throw new DomainException(400, "DUPLICATE_ROLE", "Vai trò không được trùng nhau.");
    return result;
  }

  @GetMapping("/admin/users")
  public PageResult<UserView> users(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q) {
    return accounts.list(actor, page, size, q);
  }

  @GetMapping("/admin/users/{id}")
  public ResponseEntity<UserView> user(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    var user = accounts.get(actor, id);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .eTag(Etags.of(accounts.version(id)))
        .body(user);
  }

  @PutMapping("/admin/users/{id}/status")
  public ResponseEntity<UserView> status(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody StatusPut body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    var user = accounts.status(actor, id, body.status(), "", etag);
    return ResponseEntity.ok().eTag(Etags.of(accounts.version(id))).body(user);
  }

  @PutMapping("/admin/users/{id}/roles")
  public ResponseEntity<UserView> roles(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long id,
      @Valid @RequestBody RolesPut body,
      @RequestHeader(value = "If-Match", required = false) String etag) {
    var user = accounts.roles(actor, id, distinct(body.roles()), "", etag);
    return ResponseEntity.ok().eTag(Etags.of(accounts.version(id))).body(user);
  }

  @GetMapping("/admin/teams")
  public PageResult<CreatorDirectory.Team> teams(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return teams.list(actor, page, size, true);
  }

  @GetMapping("/me/teams")
  public PageResult<CreatorDirectory.Team> mine(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return teams.list(actor, page, size, false);
  }

  @GetMapping("/admin/teams/{id}")
  public ResponseEntity<CreatorDirectory.Team> team(
      @AuthenticationPrincipal Identity actor, @PathVariable long id) {
    var team = teams.get(actor, id);
    return ResponseEntity.ok().eTag(Etags.of(teams.teamVersion(id))).body(team);
  }

  @PostMapping("/admin/teams")
  public ResponseEntity<CreatorDirectory.Team> create(
      @AuthenticationPrincipal Identity actor,
      @Valid @RequestBody TeamCreate body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var team =
        teams.create(
            actor,
            new CreatorDirectory.TeamCreate(body.name(), body.slug(), body.ownerUserId()),
            key);
    return ResponseEntity.created(URI.create("/api/v1/admin/teams/" + team.id()))
        .eTag(Etags.of(teams.teamVersion(Long.parseLong(team.id()))))
        .body(team);
  }

  @GetMapping("/admin/teams/{teamId}/members/{userId}")
  public ResponseEntity<CreatorDirectory.Member> member(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long teamId,
      @PathVariable long userId) {
    var member = teams.member(actor, teamId, userId);
    return ResponseEntity.ok().eTag(Etags.of(teams.memberVersion(teamId, userId))).body(member);
  }

  @PutMapping("/admin/teams/{teamId}/members/{userId}")
  public ResponseEntity<CreatorDirectory.Member> member(
      @AuthenticationPrincipal Identity actor,
      @PathVariable long teamId,
      @PathVariable long userId,
      @Valid @RequestBody MemberPut body,
      @RequestHeader(value = "If-Match", required = false) String etag,
      @RequestHeader(value = "If-None-Match", required = false) String noneMatch) {
    var member =
        teams.setMember(
            actor, teamId, userId, distinct(body.roles()), body.status(), etag, noneMatch);
    return ResponseEntity.ok().eTag(Etags.of(teams.memberVersion(teamId, userId))).body(member);
  }
}
