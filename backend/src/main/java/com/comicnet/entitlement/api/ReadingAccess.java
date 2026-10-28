package com.comicnet.entitlement.api;

import com.comicnet.identity.api.Identity;

public interface ReadingAccess {
  record Decision(boolean allowed, String reason, boolean requiresLogin) {}

  Decision decide(Identity actor, long chapterId, String accessPolicy);
}
