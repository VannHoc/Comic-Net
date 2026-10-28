package com.comicnet.config;

import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Validate before Flyway/DataSource creation: unresolved placeholders must never become SQL
 * credentials.
 */
@Configuration(proxyBeanMethods = false)
public class DatabaseConfigurationGuard {
  @Bean
  static BeanFactoryPostProcessor validateDatabaseEnvironment(Environment environment) {
    return factory -> {
      if (factory.containsBeanDefinition("dataSource")) validate(environment);
    };
  }

  static void validate(Environment environment) {
    for (String key :
        List.of("DB_USER", "DB_PASSWORD", "DB_MIGRATION_USER", "DB_MIGRATION_PASSWORD")) {
      String value;
      try {
        value = environment.getProperty(key);
      } catch (IllegalArgumentException error) {
        // Placeholder resolution errors can include raw property values; do not chain them.
        throw new IllegalStateException("Backend database configuration is invalid: " + key);
      }
      if (value == null
          || value.isBlank()
          || value.startsWith("${")
          || value.startsWith("REPLACE_")) {
        throw new IllegalStateException(
            "Backend database configuration is missing: "
                + key
                + ". Configure it locally; never put credentials in source or the desktop"
                + " process.");
      }
    }
    if ("sa".equalsIgnoreCase(environment.getProperty("DB_USER"))) {
      throw new IllegalStateException(
          "Backend runtime must use a dedicated application SQL login, not sa.");
    }
    if (environment
        .getProperty("DB_USER")
        .equalsIgnoreCase(environment.getProperty("DB_MIGRATION_USER"))) {
      throw new IllegalStateException("Runtime and migration SQL logins must be separate.");
    }
    String database = environment.getProperty("DB_NAME", "ComicNetDev");
    if (!"ComicNetDev".equals(database)) {
      boolean isolatedRestore =
          environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"))
              && environment.getProperty("RESTORE_VERIFICATION", Boolean.class, false)
              && database.matches("ComicNetRestoreQA_[0-9a-f]{12}")
              && !environment.getProperty("spring.flyway.enabled", Boolean.class, true)
              && !environment.getProperty("comicnet.mail.worker-enabled", Boolean.class, true)
              && !environment.getProperty("comicnet.upload.worker-enabled", Boolean.class, true)
              && isolatedStorage(environment.getProperty("S3_ENDPOINT", ""));
      if (!isolatedRestore)
        throw new IllegalStateException(
            "Development requires ComicNetDev or an explicitly isolated local restore"
                + " verification.");
    }
  }

  private static boolean isolatedStorage(String value) {
    try {
      var uri = java.net.URI.create(value);
      return "http".equals(uri.getScheme())
          && uri.getHost() != null
          && java.util.Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())
          && uri.getPort() > 0
          && uri.getPort() <= 65535
          && uri.getPort() != 9000
          && uri.getPort() != 9001
          && uri.getUserInfo() == null
          && uri.getQuery() == null
          && uri.getFragment() == null;
    } catch (IllegalArgumentException invalid) {
      return false;
    }
  }
}
