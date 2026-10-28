package com.comicnet.identity.api;

import com.comicnet.common.web.PageResult;
import java.util.Set;

public interface AccountAdministration {
  PageResult<UserView> list(Identity actor, int page, int size, String query);

  UserView get(Identity actor, long userId);

  byte[] version(long userId);

  UserView status(Identity actor, long userId, String status, String reason, String etag);

  UserView roles(Identity actor, long userId, Set<String> roles, String reason, String etag);
}
