package com.comicnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit -Psql-integration. Requires real SQL 2025 and all four backend secret env vars. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SqlFoundationIT {
  @Autowired JdbcTemplate jdbc;
  @LocalServerPort int port;

  @Test
  void migrationSqlVersionAndRuntimePrivilegesAreReal() {
    assertThat(
            jdbc.queryForObject(
                "SELECT CONVERT(int, SERVERPROPERTY('ProductMajorVersion'))", Integer.class))
        .isEqualTo(17);
    assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class)).isEqualTo("ComicNetDev");
    assertThat(
            jdbc.queryForObject(
                "SELECT SchemaVersion FROM dbo.SystemFoundation WHERE Id = 1", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT IS_SRVROLEMEMBER('sysadmin')", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT IS_MEMBER('db_owner')", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT HAS_PERMS_BY_NAME('dbo.SystemFoundation','OBJECT','ALTER')", Integer.class))
        .isZero();
  }

  @Test
  void actualTomcatPingReadinessAndFirewall() throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
      var ping = get(client, "/api/v1/system/ping");
      assertThat(ping.statusCode()).isEqualTo(200);
      assertThat(ping.body()).contains("\"status\":\"UP\"", "comic-net-backend");
      assertThat(ping.headers().firstValue("X-Request-ID")).isPresent();
      var readiness = get(client, "/actuator/health/readiness");
      assertThat(readiness.statusCode()).isEqualTo(200);
      assertThat(readiness.body()).isEqualTo("{\"status\":\"UP\"}");
      var privateRoute = get(client, "/api/v1/admin/users");
      assertThat(privateRoute.statusCode()).isEqualTo(401);
      assertThat(privateRoute.body()).doesNotContain("jdbc:", "password", "stackTrace");
    }
  }

  private HttpResponse<String> get(HttpClient client, String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
