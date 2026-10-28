package com.comicnet.identity.api;

import java.security.Principal;
import java.util.Set;

public record Identity(long userId, long sessionId, long authVersion, Set<String> roles)
    implements Principal {
  @Override
  public String getName() {
    return Long.toString(userId);
  }

  public boolean administrator() {
    return roles.contains("ADMINISTRATOR");
  }
}
