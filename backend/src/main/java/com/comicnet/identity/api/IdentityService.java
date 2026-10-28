package com.comicnet.identity.api;

import java.util.Set;

public interface IdentityService {
  UserView user(long id);

  Set<String> roles(long id);

  boolean active(long id);

  void requireAdministrator(Identity actor);

  boolean permitted(Identity actor, String permission);

  Identity authenticate(String bearer);
}
