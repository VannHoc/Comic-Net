package com.comicnet.observability.api;

import java.util.Map;

public interface EventOutbox {
  void append(String type, String aggregateId, Map<String, ?> safePayload);
}
