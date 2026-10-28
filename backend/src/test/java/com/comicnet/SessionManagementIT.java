package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.*;
import com.comicnet.common.web.DomainException;
import com.comicnet.support.SqlTestActors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "comicnet.mail.worker-enabled=false")
@Transactional
class SessionManagementIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  @Autowired SessionManagement sessions;

  @Test
  void ownSessionsCanBeRevokedWithoutAffectingOtherUsers() {
    var actors = new SqlTestActors(auth, accounts, jdbc, entities);
    var owner = actors.reader();
    var stranger = actors.reader();
    var tokens =
        auth.login(
            new AuthRequests.Login(
                auth.user(owner.userId()).email(), "Test-password-42!", "Another device"));
    var otherDevice = auth.authenticate(tokens.accessToken());
    assertThat(sessions.list(owner, 0, 20).items()).hasSize(2);
    assertThat(
            java.time.Duration.between(
                    sessions.list(owner, 0, 20).items().getFirst().createdAt(),
                    java.time.Instant.now())
                .abs()
                .toSeconds())
        .isLessThan(5);
    assertThat(sessions.list(owner, 0, 20).items())
        .filteredOn(SessionManagement.SessionView::current)
        .hasSize(1);
    assertThatThrownBy(() -> sessions.revoke(stranger, otherDevice.sessionId()))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(404));
    sessions.revoke(owner, otherDevice.sessionId());
    sessions.revoke(owner, otherDevice.sessionId());
    assertThatThrownBy(() -> auth.authenticate(tokens.accessToken()))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> auth.refresh(tokens.refreshToken()))
        .isInstanceOf(DomainException.class);
    assertThat(sessions.list(owner, 0, 20).items()).hasSize(1);
    assertThat(sessions.list(stranger, 0, 20).items()).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.AuditLog WHERE Action='SESSION_REVOKED' AND TargetId=?",
                Integer.class,
                otherDevice.sessionId() + ""))
        .isEqualTo(1);
  }

  @Test
  void revokeOthersKeepsCurrentSessionAndRejectsAnonymous() {
    var actors = new SqlTestActors(auth, accounts, jdbc, entities);
    var owner = actors.reader();
    var tokens =
        auth.login(
            new AuthRequests.Login(
                auth.user(owner.userId()).email(), "Test-password-42!", "Current device"));
    var current = auth.authenticate(tokens.accessToken());
    sessions.revokeOthers(current);
    sessions.revokeOthers(current);
    assertThat(auth.authenticate(tokens.accessToken()).sessionId()).isEqualTo(current.sessionId());
    assertThat(sessions.list(current, 0, 20).items())
        .singleElement()
        .extracting(SessionManagement.SessionView::current)
        .isEqualTo(true);
    assertThatThrownBy(() -> sessions.list(null, 0, 20)).isInstanceOf(DomainException.class);
    sessions.revoke(current, current.sessionId());
    assertThatThrownBy(() -> auth.authenticate(tokens.accessToken()))
        .isInstanceOf(DomainException.class);
  }
}
