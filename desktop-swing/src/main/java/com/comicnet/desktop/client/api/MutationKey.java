package com.comicnet.desktop.client.api;

import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** Reuse a key after an uncertain response, until input changes or success is confirmed. */
public final class MutationKey {
  private final JsonMapper json =
      JsonMapper.builder()
          .enable(tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .build();
  private String fingerprint, key;

  public String forInput(String operation, Object payload) {
    String current = operation + json.writeValueAsString(payload);
    if (!current.equals(fingerprint)) {
      fingerprint = current;
      key = UUID.randomUUID().toString();
    }
    return key;
  }

  public void confirmed() {
    fingerprint = null;
    key = null;
  }
}
