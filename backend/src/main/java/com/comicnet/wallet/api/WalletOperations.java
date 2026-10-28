package com.comicnet.wallet.api;

public interface WalletOperations {
  record Balance(long paidCoin, long rewardCoin) {}
  Balance balance(long userId);
  Balance apply(long userId, String tokenType, long delta, String source, String reference);
}
