package com.comicnet.observability.service;

import com.comicnet.common.web.*;
import com.comicnet.observability.api.AuditQueries;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SqlAuditQueries implements AuditQueries {
  private final JdbcTemplate jdbc;

  public SqlAuditQueries(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public PageResult<Entry> list(int page, int size, String type, String id) {
    PageResult.validate(page, size);
    if (type != null && type.length() > 50 || id != null && id.length() > 100)
      throw new DomainException(400, "INVALID_AUDIT_FILTER", "Bộ lọc nhật ký không hợp lệ.");
    String filter = " WHERE 1=1";
    List<Object> args = new ArrayList<>();
    if (type != null && !type.isBlank()) {
      filter += " AND TargetType=?";
      args.add(type);
    }
    if (id != null && !id.isBlank()) {
      filter += " AND TargetId=?";
      args.add(id);
    }
    long total =
        jdbc.queryForObject(
            "SELECT COUNT_BIG(*) FROM dbo.AuditLog" + filter, Long.class, args.toArray());
    args.add(PageResult.offset(page, size));
    args.add(size);
    var rows =
        jdbc.query(
            "SELECT Id,ActorUserId,Action,TargetType,TargetId,Outcome,RequestId,CreatedAt FROM"
                + " dbo.AuditLog"
                + filter
                + " ORDER BY CreatedAt DESC,Id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
            (rs, n) -> {
              long actor = rs.getLong("ActorUserId");
              String user = rs.wasNull() ? null : actor + "";
              return new Entry(
                  rs.getLong("Id") + "",
                  user,
                  rs.getString("Action"),
                  rs.getString("TargetType"),
                  rs.getString("TargetId"),
                  rs.getString("Outcome"),
                  rs.getString("RequestId"),
                  rs.getObject("CreatedAt", LocalDateTime.class).toInstant(ZoneOffset.UTC));
            },
            args.toArray());
    return PageResult.of(rows, page, size, total);
  }
}
