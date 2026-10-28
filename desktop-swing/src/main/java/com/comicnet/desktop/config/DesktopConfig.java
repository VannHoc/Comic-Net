package com.comicnet.desktop.config;

import java.net.URI;
import java.util.Set;

public record DesktopConfig(URI apiBase) {
  public DesktopConfig {
    if (apiBase == null
        || apiBase.getHost() == null
        || apiBase.getUserInfo() != null
        || apiBase.getQuery() != null
        || apiBase.getFragment() != null
        || !"/api/v1".equals(apiBase.getPath())) {
      throw new IllegalArgumentException(
          "COMICNET_API_BASE_URL must end in /api/v1 without credentials/query/fragment.");
    }
    boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(apiBase.getHost());
    if (!("https".equals(apiBase.getScheme()) || (local && "http".equals(apiBase.getScheme())))) {
      throw new IllegalArgumentException(
          "HTTP is permitted only for localhost development. Remote API requires HTTPS.");
    }
  }

  public static DesktopConfig fromEnvironment() {
    String value = System.getenv("COMICNET_API_BASE_URL");
    return new DesktopConfig(
        URI.create(
            value == null || value.isBlank()
                ? "http://localhost:8080/api/v1"
                : value.strip().replaceAll("/+$", "")));
  }

  public URI pingUri() {
    return URI.create(apiBase + "/system/ping");
  }

  public URI readinessUri() {
    return apiBase.resolve("/actuator/health/readiness");
  }

  public Set<String> mediaHosts() {
    var hosts = new java.util.HashSet<String>();
    hosts.add(apiBase.getHost());
    if (Set.of("localhost", "127.0.0.1", "[::1]").contains(apiBase.getHost())) {
      hosts.add("localhost");
      hosts.add("127.0.0.1");
      hosts.add("[::1]");
    }
    String configured = System.getenv("COMICNET_MEDIA_HOSTS");
    if (configured != null)
      for (String value : configured.split(",")) {
        String host = value.strip();
        if (!host.matches("[a-zA-Z0-9.:-]+"))
          throw new IllegalArgumentException("Invalid media host.");
        hosts.add(host);
      }
    return Set.copyOf(hosts);
  }
}
