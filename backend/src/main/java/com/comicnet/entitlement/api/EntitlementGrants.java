package com.comicnet.entitlement.api;

public interface EntitlementGrants {
  boolean owned(long userId, long chapterId);
  void grantPurchase(long userId, long chapterId, long purchaseId);
}
