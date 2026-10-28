package com.comicnet.observability.service;

import com.comicnet.observability.api.AuditWriter;
import com.comicnet.observability.web.RequestIdFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class SqlAuditWriter implements AuditWriter {
  private final JdbcTemplate jdbc;

  public SqlAuditWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void record(
      Long actor,
      String action,
      String type,
      String id,
      String outcome,
      java.util.Map<String, ?> safeMetadata) {
    var attributes = RequestContextHolder.getRequestAttributes();
    String requestId =
        attributes instanceof ServletRequestAttributes r
            ? String.valueOf(r.getRequest().getAttribute(RequestIdFilter.ATTRIBUTE))
            : "background";
    String metadata =
        tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(safeMetadata);
    if (metadata.length() > 16384)
      throw new IllegalArgumentException("Audit metadata exceeds its limit.");
    jdbc.update(
        "INSERT dbo.AuditLog(ActorUserId,Action,TargetType,TargetId,Outcome,RequestId,SafeMetadata)"
            + " VALUES(?,?,?,?,?,?,?)",
        actor,
        action,
        type,
        id,
        outcome,
        requestId,
        metadata);
  }
}
