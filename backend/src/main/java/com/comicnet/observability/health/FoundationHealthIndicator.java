package com.comicnet.observability.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Readiness requires the migrated schema, not merely a listening database socket. */
@Component("foundation")
public class FoundationHealthIndicator implements HealthIndicator {
  private final JdbcTemplate jdbc;

  public FoundationHealthIndicator(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Health health() {
    try {
      Integer version =
          jdbc.queryForObject(
              "SELECT SchemaVersion FROM dbo.SystemFoundation WHERE Id = 1", Integer.class);
      return version != null && version >= 1 ? Health.up().build() : Health.down().build();
    } catch (RuntimeException error) {
      // No JDBC message/host/database/user exposed in the public health response.
      return Health.down().build();
    }
  }
}
