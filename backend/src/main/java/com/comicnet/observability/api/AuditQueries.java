package com.comicnet.observability.api;

import com.comicnet.common.web.PageResult;
import java.time.Instant;

public interface AuditQueries {
  record Entry(
      String id,
      String actorUserId,
      String action,
      String targetType,
      String targetId,
      String outcome,
      String requestId,
      Instant createdAt) {}

  PageResult<Entry> list(int page, int size, String targetType, String targetId);
}
