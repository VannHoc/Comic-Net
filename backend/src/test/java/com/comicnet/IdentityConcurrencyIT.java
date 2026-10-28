package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.*;
import com.comicnet.common.web.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Separate committed SQL transactions are necessary to exercise actual lock contention. */
@SpringBootTest
class IdentityConcurrencyIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transactions;
  @Autowired org.springframework.core.env.Environment environment;
  private final List<Long> users = new ArrayList<>();

  private record Actor(long id, String email) {}

  private Actor actor(boolean administrator) {
    String email = "concurrency-" + UUID.randomUUID() + "@comicnet.test";
    return transactions.execute(
        status -> {
          auth.register(
              new AuthRequests.Register(email, "Test-password-42!", "SQL concurrency fixture"));
          long id =
              jdbc.queryForObject(
                  "SELECT Id FROM dbo.[User] WHERE NormalizedEmail=?", Long.class, email);
          users.add(id);
          jdbc.update("UPDATE dbo.[User] SET EmailVerifiedAt=SYSUTCDATETIME() WHERE Id=?", id);
          if (administrator)
            jdbc.update(
                "INSERT dbo.UserRole(UserId,RoleId) SELECT ?,Id FROM dbo.[Role] WHERE"
                    + " Code='ADMINISTRATOR'",
                id);
          return new Actor(id, email);
        });
  }

  private <T> List<T> race(Supplier<T> first, Supplier<T> second) throws Exception {
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a =
          executor.submit(
              () -> {
                gate.await();
                return first.get();
              });
      var b =
          executor.submit(
              () -> {
                gate.await();
                return second.get();
              });
      gate.countDown();
      return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
    }
  }

  @BeforeEach
  void removeInterruptedOwnedFixtures() {
    var owned =
        jdbc.queryForList(
            "SELECT Id FROM dbo.[User] WHERE DisplayName=N'SQL concurrency fixture' AND"
                + " NormalizedEmail LIKE 'concurrency-%@comicnet.test' AND Id NOT IN (SELECT"
                + " AdministratorUserId FROM dbo.BootstrapState WHERE AdministratorUserId IS NOT"
                + " NULL)",
            Long.class);
    cleanup(owned);
  }

  @AfterEach
  void removeOnlyOwnedFixtures() {
    cleanup(users);
  }

  private void cleanup(List<Long> ids) {
    if (ids.isEmpty()) return;
    String username = environment.getRequiredProperty("DB_MIGRATION_USER"),
        url = environment.getRequiredProperty("spring.datasource.url");
    if (!username.equals("comicnet_migrator") || !url.contains("databaseName=ComicNetDev;"))
      throw new IllegalStateException(
          "Fixture cleanup requires the scoped development migration account.");
    var source =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            url, username, environment.getRequiredProperty("DB_MIGRATION_PASSWORD"));
    var cleanup = new JdbcTemplate(source);
    var transaction =
        new TransactionTemplate(
            new org.springframework.jdbc.support.JdbcTransactionManager(source));
    transaction.executeWithoutResult(
        status -> {
          for (long id : ids) {
            if (cleanup.queryForObject(
                    "SELECT COUNT(*) FROM dbo.[User] WHERE Id=? AND DisplayName=N'SQL concurrency"
                        + " fixture' AND NormalizedEmail LIKE 'concurrency-%@comicnet.test'",
                    Integer.class, id)
                != 1) continue;
            cleanup.update(
                "DELETE dbo.RefreshToken WHERE SessionId IN (SELECT Id FROM dbo.[Session] WHERE"
                    + " UserId=?)",
                id);
            cleanup.update("DELETE dbo.[Session] WHERE UserId=?", id);
            cleanup.update("DELETE dbo.AccountActionToken WHERE UserId=?", id);
            cleanup.update(
                "DELETE dbo.AccountMailJob WHERE Recipient=(SELECT Email FROM dbo.[User] WHERE"
                    + " Id=?)",
                id);
            cleanup.update(
                "DELETE dbo.AuditLog WHERE ActorUserId=? OR (TargetType='User' AND TargetId=?)",
                id,
                id + "");
            cleanup.update("DELETE dbo.UserRole WHERE UserId=?", id);
            cleanup.update("DELETE dbo.[User] WHERE Id=?", id);
          }
        });
  }

  @Test
  void concurrentRefreshRotatesOnceThenRevokesTheFamilyOnReplay() throws Exception {
    var actor = actor(false);
    var session =
        auth.login(
            new AuthRequests.Login(actor.email(), "Test-password-42!", "Concurrent refresh"));
    Supplier<String> attempt =
        () -> {
          try {
            auth.refresh(session.refreshToken());
            return "ROTATED";
          } catch (DomainException error) {
            return error.code();
          }
        };
    assertThat(race(attempt, attempt)).containsExactlyInAnyOrder("ROTATED", "REFRESH_REPLAY");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.RefreshToken t JOIN dbo.[Session] s ON s.Id=t.SessionId"
                    + " WHERE s.UserId=?",
                Integer.class,
                actor.id()))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.[Session] WHERE UserId=? AND RevokedAt IS NULL",
                Integer.class,
                actor.id()))
        .isZero();
  }

  @Test
  void concurrentAdministratorDemotionsPreserveTheLastActiveAdministrator() throws Exception {
    int baseline = activeAdmins();
    var first = actor(true);
    var second = actor(true);
    Identity a = new Identity(first.id(), 0, 0, Set.of("ADMINISTRATOR", "READER")),
        b = new Identity(second.id(), 0, 0, Set.of("ADMINISTRATOR", "READER"));
    String aTag = Etags.of(accounts.version(a.userId())),
        bTag = Etags.of(accounts.version(b.userId()));
    var results = race(() -> demote(a, aTag), () -> demote(b, bTag));
    if (baseline == 0) {
      assertThat(results).containsExactlyInAnyOrder("UPDATED", "LAST_ADMINISTRATOR");
      assertThat(activeAdmins()).isEqualTo(1);
    } else {
      assertThat(results).containsOnly("UPDATED");
      assertThat(activeAdmins()).isEqualTo(baseline);
    }
  }

  private int activeAdmins() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM dbo.[User] u JOIN dbo.UserRole ur ON ur.UserId=u.Id JOIN dbo.[Role] r"
            + " ON r.Id=ur.RoleId WHERE r.Code='ADMINISTRATOR' AND u.Status='ACTIVE' AND"
            + " u.EmailVerifiedAt IS NOT NULL",
        Integer.class);
  }

  private String demote(Identity actor, String etag) {
    try {
      accounts.roles(actor, actor.userId(), Set.of("READER"), "SQL concurrency fixture", etag);
      return "UPDATED";
    } catch (DomainException error) {
      return error.code();
    }
  }
}
