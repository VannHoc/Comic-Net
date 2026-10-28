package com.comicnet.desktop.client.api;

import com.comicnet.desktop.client.http.ApiTransport;
import com.comicnet.desktop.config.DesktopConfig;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.json.JsonMapper;

public final class SystemApi {
  private final DesktopConfig config;
  private final ApiTransport transport;
  private final JsonMapper json = JsonMapper.builder().build();

  public SystemApi(DesktopConfig config, ApiTransport transport) {
    this.config = config;
    this.transport = transport;
  }

  public ProbeTask probe() {
    var ping = transport.get(config.pingUri());
    var ready = transport.get(config.readinessUri());
    var completion = ping.thenCombineAsync(ready, this::parse);
    return new ProbeTask(
        completion,
        () -> {
          ping.cancel(true);
          ready.cancel(true);
          completion.cancel(true);
        });
  }

  private ProbeResult parse(HttpResponse<String> ping, HttpResponse<String> readiness) {
    if (ping.statusCode() != 200 || ping.body().length() > 4096) {
      throw new IllegalStateException("API ping failed.");
    }
    var body = json.readTree(ping.body());
    String service = body.path("service").asString("");
    if (!"UP".equals(body.path("status").asString(""))
        || service.isBlank()
        || service.length() > 100) {
      throw new IllegalStateException("Invalid ping response.");
    }
    if (readiness.body().length() > 4096
        || (readiness.statusCode() != 200 && readiness.statusCode() != 503)) {
      throw new IllegalStateException("Invalid readiness response.");
    }
    String status = json.readTree(readiness.body()).path("status").asString("");
    if (!("UP".equals(status) || "DOWN".equals(status) || "OUT_OF_SERVICE".equals(status))) {
      throw new IllegalStateException("Invalid readiness status.");
    }
    String requestId =
        ping.headers()
            .firstValue("X-Request-ID")
            .filter(value -> value.matches("[A-Za-z0-9._-]{1,64}"))
            .orElse("—");
    return new ProbeResult(
        true, readiness.statusCode() == 200 && "UP".equals(status), service, requestId);
  }

  public record ProbeTask(CompletableFuture<ProbeResult> completion, Runnable cancel) {}
}
