package com.comicnet.observability.api;

public interface AuditWriter {
  default void record(
      Long actor, String action, String targetType, String targetId, String outcome) {
    record(actor, action, targetType, targetId, outcome, java.util.Map.of());
  }

  void record(
      Long actor,
      String action,
      String targetType,
      String targetId,
      String outcome,
      java.util.Map<String, ?> safeMetadata);
}
