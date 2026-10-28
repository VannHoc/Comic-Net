package com.comicnet.infrastructure.database;

import org.flywaydb.core.Flyway;

/** Development-only Flyway entry point, before administrator table grants/JPA startup. */
public final class MigrationCommand {
  private MigrationCommand() {}

  public static void main(String[] args) {
    if (!"local".equals(System.getenv("SPRING_PROFILES_ACTIVE")))
      throw new IllegalStateException("Migration command requires the explicit local profile.");
    String name = System.getenv().getOrDefault("DB_NAME", "ComicNetDev");
    if (!name.equals("ComicNetDev"))
      throw new IllegalStateException("Development migration is restricted to ComicNetDev.");
    String host = System.getenv().getOrDefault("DB_HOST", "localhost"),
        port = System.getenv().getOrDefault("DB_PORT", "1433");
    if (!host.matches("[A-Za-z0-9._-]+") || !port.matches("[0-9]{1,5}"))
      throw new IllegalStateException("Invalid database endpoint.");
    String user = required("DB_MIGRATION_USER"), password = required("DB_MIGRATION_PASSWORD");
    if (user.equalsIgnoreCase("sa"))
      throw new IllegalStateException("Use the dedicated migration login.");
    var result =
        Flyway.configure()
            .dataSource(
                "jdbc:sqlserver://"
                    + host
                    + ":"
                    + port
                    + ";databaseName="
                    + name
                    + ";encrypt=true;trustServerCertificate=true;loginTimeout=5",
                user,
                password)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .baselineOnMigrate(false)
            .load()
            .migrate();
    System.out.println("Flyway migration complete: " + result.migrationsExecuted + " applied.");
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank())
      throw new IllegalStateException("Missing backend environment: " + name);
    return value;
  }
}
