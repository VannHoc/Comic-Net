package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.common.web.*;
import com.comicnet.creator.api.CreatorDirectory;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "comicnet.mail.worker-enabled=false",
      "SETUP_ENABLED=true",
      "SETUP_TOKEN=JUnit-local-setup-capability-only-01234567890123456789"
    })
@Transactional
class CreatorAdministrationIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired CreatorDirectory teams;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;

  private Identity admin() {
    return new com.comicnet.support.SqlTestActors(auth, accounts, jdbc, entities)
        .administrator();
  }

  private long reader() {
    String email = "member-" + UUID.randomUUID() + "@comicnet.test";
    auth.register(new AuthRequests.Register(email, "Test-password-42!", "Member"));
    long id =
        jdbc.queryForObject("SELECT Id FROM dbo.[User] WHERE NormalizedEmail=?", Long.class, email);
    jdbc.update("UPDATE dbo.[User] SET EmailVerifiedAt=SYSUTCDATETIME() WHERE Id=?", id);
    entities.clear();
    return id;
  }

  @Test
  void lastAdministratorCannotBeSuspendedOrDemoted() {
    Identity admin = admin();
    String token = Etags.of(accounts.version(admin.userId()));
    int activeAfterSetup = jdbc.queryForObject(
        "SELECT COUNT(*) FROM dbo.[User] u JOIN dbo.UserRole ur ON ur.UserId=u.Id JOIN"
            + " dbo.[Role] r ON r.Id=ur.RoleId WHERE r.Code='ADMINISTRATOR' AND"
            + " u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)", Integer.class);
    if (activeAfterSetup > 1) {
      accounts.status(admin, admin.userId(), "SUSPENDED", "Test", token);
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM dbo.[User] u JOIN dbo.UserRole ur ON ur.UserId=u.Id JOIN"
                      + " dbo.[Role] r ON r.Id=ur.RoleId WHERE r.Code='ADMINISTRATOR' AND"
                      + " u.Status='ACTIVE' AND (u.Email IS NULL OR u.EmailVerifiedAt IS NOT NULL)",
                  Integer.class))
          .isEqualTo(activeAfterSetup - 1);
      return;
    }
    assertThatThrownBy(() -> accounts.status(admin, admin.userId(), "SUSPENDED", "Test", token))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo("LAST_ADMINISTRATOR"));
    assertThatThrownBy(() -> accounts.roles(admin, admin.userId(), Set.of("READER"), "Test", token))
        .isInstanceOf(DomainException.class);
    assertThat(auth.roles(admin.userId())).contains("ADMINISTRATOR");
  }

  @Test
  void usernameOnlyAccountRolesAndStatusPersistInSql() {
    Identity administrator = admin();
    String username = "qa" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    auth.register(new AuthRequests.Register(null, "Test-password-42!", "QA account", username, null));
    long id = jdbc.queryForObject("SELECT Id FROM dbo.[User] WHERE Username=?", Long.class, username);
    accounts.roles(administrator, id, Set.of("READER", "CREATOR"), "JUnit",
        Etags.of(accounts.version(id)));
    assertThat(jdbc.queryForList(
        "SELECT r.Code FROM dbo.UserRole ur JOIN dbo.[Role] r ON r.Id=ur.RoleId WHERE ur.UserId=?",
        String.class, id)).containsExactlyInAnyOrder("READER", "CREATOR");
    accounts.status(administrator, id, "SUSPENDED", "JUnit", Etags.of(accounts.version(id)));
    assertThat(jdbc.queryForObject("SELECT Status FROM dbo.[User] WHERE Id=?", String.class, id))
        .isEqualTo("SUSPENDED");
  }

  @Test
  void teamCreationIsIdempotentAndMembershipIsScoped() {
    Identity admin = admin();
    long owner = reader(), uploader = reader();
    accounts.roles(
        admin,
        owner,
        Set.of("READER", "CREATOR"),
        "Test creator",
        Etags.of(accounts.version(owner)));
    var input =
        new CreatorDirectory.TeamCreate("Nhóm kiểm thử", "test-" + UUID.randomUUID(), owner + "");
    String key = UUID.randomUUID().toString();
    var team = teams.create(admin, input, key);
    assertThat(teams.create(admin, input, key).id()).isEqualTo(team.id());
    long teamId = Long.parseLong(team.id());
    assertThat(teams.member(admin, teamId, owner).roles())
        .containsExactlyInAnyOrder("OWNER", "CREATOR");
    assertThatThrownBy(
            () ->
                teams.create(
                    admin,
                    new CreatorDirectory.TeamCreate(
                        "Other", "other-" + UUID.randomUUID(), owner + ""),
                    key))
        .isInstanceOf(DomainException.class);
    teams.setMember(admin, teamId, uploader, Set.of("UPLOADER"), "ACTIVE", null, "*");
    var uploaderIdentity = new Identity(uploader, 0, 0, auth.roles(uploader));
    assertThat(teams.has(uploaderIdentity, teamId, "chapter.upload")).isTrue();
    assertThat(teams.has(uploaderIdentity, teamId, "catalog.manage")).isFalse();
    assertThat(teams.has(uploaderIdentity, teamId + 1, "chapter.upload")).isFalse();
    assertThatThrownBy(
            () ->
                teams.setMember(
                    admin,
                    teamId,
                    uploader,
                    Set.of("OWNER"),
                    "ACTIVE",
                    Etags.of(teams.memberVersion(teamId, uploader)),
                    null))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                teams.setMember(
                    admin,
                    teamId,
                    owner,
                    Set.of("CREATOR"),
                    "ACTIVE",
                    Etags.of(teams.memberVersion(teamId, owner)),
                    null))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void statusChangeImmediatelyRevokesExistingJwtAndStaleEtagFails() {
    Identity admin = admin();
    long id = reader();
    String email = auth.user(id).email();
    var session = auth.login(new AuthRequests.Login(email, "Test-password-42!", "JUnit"));
    String original = Etags.of(accounts.version(id));
    var changed = accounts.status(admin, id, "SUSPENDED", "Test suspension", original);
    assertThat(changed.status()).isEqualTo("SUSPENDED");
    assertThatThrownBy(() -> auth.authenticate(session.accessToken()))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> accounts.status(admin, id, "ACTIVE", "Test", original))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(412));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.AuditLog WHERE TargetType='User' AND TargetId=? AND"
                    + " Action='USER_STATUS_SUSPENDED'",
                Integer.class,
                id + ""))
        .isEqualTo(1);
  }
}
