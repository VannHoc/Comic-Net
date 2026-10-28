package com.comicnet.support;

import com.comicnet.identity.api.*;
import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.common.web.Etags;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real persisted actors, created inside each integration test's rollback transaction. */
public final class SqlTestActors {
  public static final String SETUP = "JUnit-local-setup-capability-only-01234567890123456789";
  private final AuthenticationService auth;
  private final AccountAdministration accounts;
  private final JdbcTemplate jdbc;
  private final EntityManager entities;

  public SqlTestActors(
      AuthenticationService auth,
      AccountAdministration accounts,
      JdbcTemplate jdbc,
      EntityManager entities) {
    this.auth = auth;
    this.accounts = accounts;
    this.jdbc = jdbc;
    this.entities = entities;
  }

  public Identity administrator() {
    var user = reader();
    jdbc.update(
        "INSERT dbo.UserRole(UserId,RoleId) SELECT ?,Id FROM dbo.[Role] WHERE Code='ADMINISTRATOR'",
        user.userId());
    return login(auth.user(user.userId()).email());
  }

  public Identity reader() {
    String email = "reader-" + UUID.randomUUID() + "@comicnet.test";
    auth.register(new AuthRequests.Register(email, "Test-password-42!", "Độc giả"));
    jdbc.update(
        "UPDATE dbo.[User] SET EmailVerifiedAt=SYSUTCDATETIME() WHERE NormalizedEmail=?", email);
    entities.clear();
    return login(email);
  }

  public Identity creator(Identity administrator) {
    var user = reader();
    accounts.roles(
        administrator,
        user.userId(),
        Set.of("READER", "CREATOR"),
        "JUnit creator fixture",
        Etags.of(accounts.version(user.userId())));
    return login(auth.user(user.userId()).email());
  }

  public Identity login(String email) {
    return auth.authenticate(
        auth.login(new AuthRequests.Login(email, "Test-password-42!", "JUnit SQL")).accessToken());
  }
}
