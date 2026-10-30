package com.comicnet.creator.api;

import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.PageResult;
import java.util.Set;

public interface CreatorDirectory {
  record Team(String id, String name, String slug, String ownerUserId, String status) {}

  record Member(String id, String teamId, String userId, Set<String> roles, String status) {}

  record TeamCreate(String name, String slug, String ownerUserId) {}

  PageResult<Team> list(Identity actor, int page, int size, boolean admin);

  Team get(Identity actor, long teamId);

  Team create(Identity actor, TeamCreate request, String key);

  /** Personal creator workspace; idempotent and only for accounts holding the CREATOR role. */
  long ensurePersonalTeam(Identity actor);

  Member member(Identity actor, long teamId, long userId);

  Member setMember(
      Identity actor,
      long teamId,
      long userId,
      Set<String> roles,
      String status,
      String etag,
      String noneMatch);

  byte[] teamVersion(long teamId);

  byte[] memberVersion(long teamId, long userId);

  boolean has(Identity actor, long teamId, String permission);

  void require(Identity actor, long teamId, String permission);

  java.util.List<Long> teamIds(Identity actor, String permission);
}
