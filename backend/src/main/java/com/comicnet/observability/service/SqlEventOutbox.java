package com.comicnet.observability.service;

import com.comicnet.observability.api.EventOutbox;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SqlEventOutbox implements EventOutbox {
  private final JdbcTemplate jdbc;
  private final JsonMapper json = JsonMapper.builder().build();

  public SqlEventOutbox(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void append(String type, String aggregateId, Map<String, ?> payload) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Domain events require the aggregate transaction.");
    jdbc.update(
        "INSERT dbo.OutboxEvent(EventId,Type,SchemaVersion,AggregateId,Payload) VALUES(?,?,1,?,?)",
        UUID.randomUUID().toString(),
        type,
        aggregateId,
        json.writeValueAsString(payload));
  }
}
