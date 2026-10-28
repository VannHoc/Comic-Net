package com.comicnet.observability.api;

import java.util.function.LongSupplier;

public interface Idempotency {
  long create(
      long actor, String operation, String scope, String key, Object payload, LongSupplier create);
}
