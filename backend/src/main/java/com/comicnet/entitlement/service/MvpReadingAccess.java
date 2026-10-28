package com.comicnet.entitlement.service;

import com.comicnet.identity.api.Identity;
import com.comicnet.entitlement.api.ReadingAccess;

/** Retained as a simple policy helper; the active Phase 4 service checks SQL grants. */
public class MvpReadingAccess implements ReadingAccess {
  public Decision decide(Identity actor, long chapterId, String policy) {
    return policy.equals("FREE")
        ? new Decision(true, "FREE", false)
        : new Decision(false, "RESTRICTED", false);
  }
}
