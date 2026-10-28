package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.identity.api.IdentityService;
import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.identity.service.TokenSecrets;
import com.comicnet.common.web.DomainException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"comicnet.mail.worker-enabled=false", "SETUP_ENABLED=true"})
@Transactional
class IdentityFlowIT {
  @Autowired AuthenticationService auth;
  @Autowired IdentityService identities;
  @Autowired JdbcTemplate jdbc;
  @Autowired com.comicnet.identity.service.AccountMail mail;

  @Test
  void emailOtpIsSingleUseAndLimitsFiveWrongAttempts() {
    String email = "otp-" + UUID.randomUUID() + "@comicnet.test";
    auth.register(new AuthRequests.Register(email, "abc123", "Otp reader"));
    long id = jdbc.queryForObject("SELECT Id FROM dbo.[User] WHERE NormalizedEmail=?", Long.class, email);
    jdbc.update("UPDATE dbo.AccountEmailOtp SET CodeHash=? WHERE UserId=? AND Purpose='VERIFY_EMAIL'",
        TokenSecrets.hash("123456"), id);
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> auth.verifyOtp(email, "000000")).isInstanceOf(DomainException.class);
    }
    assertThat(jdbc.queryForObject("SELECT Attempts FROM dbo.AccountEmailOtp WHERE UserId=?"
        + " AND Purpose='VERIFY_EMAIL'", Integer.class, id)).isEqualTo(5);
    assertThatThrownBy(() -> auth.verifyOtp(email, "123456")).isInstanceOf(DomainException.class);
    jdbc.update("UPDATE dbo.AccountEmailOtp SET Attempts=0 WHERE UserId=?", id);
    auth.verifyOtp(email, "123456");
    assertThatThrownBy(() -> auth.verifyOtp(email, "123456")).isInstanceOf(DomainException.class);
    entityManager.clear();
    assertThat(auth.login(new AuthRequests.Login(email, "abc123", "OTP test")).accessToken())
        .isNotBlank();
  }

  @Test
  void accountSessionsRotationReplayAndRevocationUseSql() {
    String email = "test-" + UUID.randomUUID() + "@comicnet.test";
    String password = "Test-only-long-password-42!";
    auth.register(new AuthRequests.Register(email, password, "Độc giả kiểm thử"));
    Long userId =
        jdbc.queryForObject("SELECT Id FROM dbo.[User] WHERE NormalizedEmail=?", Long.class, email);
    assertThat(
            jdbc.queryForObject(
                "SELECT DATEDIFF(second,SYSUTCDATETIME(),ExpiresAt) FROM dbo.AccountActionToken"
                    + " WHERE UserId=? AND Purpose='VERIFY_EMAIL'",
                Integer.class,
                userId))
        .isBetween(86390, 86401);
    assertThatThrownBy(() -> auth.login(new AuthRequests.Login(email, password, "JUnit")))
        .isInstanceOf(DomainException.class);
    jdbc.update("UPDATE dbo.[User] SET EmailVerifiedAt=SYSUTCDATETIME() WHERE Id=?", userId);
    // Clear stale JPA snapshots after the test's explicit SQL verification fixture.
    entityManager.clear();
    var session = auth.login(new AuthRequests.Login(email, password, "JUnit SQL"));
    assertThat(
            jdbc.queryForObject(
                "SELECT DATEDIFF(second,SYSUTCDATETIME(),ExpiresAt) FROM dbo.[Session] WHERE"
                    + " UserId=?",
                Integer.class,
                userId))
        .isBetween(2591990, 2592001);
    assertThat(identities.authenticate(session.accessToken()).userId()).isEqualTo(userId);
    var rotated = auth.refresh(session.refreshToken());
    assertThat(rotated.refreshToken()).isNotEqualTo(session.refreshToken());
    assertThatThrownBy(() -> auth.refresh(session.refreshToken()))
        .isInstanceOf(AuthenticationService.RefreshReplay.class);
    assertThatThrownBy(() -> identities.authenticate(rotated.accessToken()))
        .isInstanceOf(DomainException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.AccountMailJob WHERE Recipient=?", Integer.class, email))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT EncryptedBody FROM dbo.AccountMailJob WHERE Recipient=?",
                String.class,
                email))
        .doesNotContain("Mã thao tác", password);
  }

  @Autowired jakarta.persistence.EntityManager entityManager;

  @Test
  void duplicateRegistrationStaysGenericAndDoesNotCreateAnotherAccount() {
    String email = "test-" + UUID.randomUUID() + "@comicnet.test";
    auth.register(new AuthRequests.Register(email, "Test-password-42!", "Reader"));
    auth.register(
        new AuthRequests.Register(email.toUpperCase(), "Different-password-42!", "Other"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.[User] WHERE NormalizedEmail=?", Integer.class, email))
        .isEqualTo(1);
  }

  @Test
  void encryptedMailIsDeliveredThroughTheRealLocalSmtpService() {
    String email = "smtp-" + UUID.randomUUID() + "@comicnet.test";
    mail.queue(email, "VERIFY_EMAIL", UUID.randomUUID().toString());
    long id =
        jdbc.queryForObject(
            "SELECT Id FROM dbo.AccountMailJob WHERE Recipient=?", Long.class, email);
    assertThat(mail.deliverJob(id)).isTrue();
    var sent =
        jdbc.queryForMap(
            "SELECT State,EncryptedBody,SentAt FROM dbo.AccountMailJob WHERE Id=?", id);
    assertThat(sent.get("State")).isEqualTo("SUCCEEDED");
    assertThat(sent.get("EncryptedBody")).isEqualTo("");
    assertThat(sent.get("SentAt")).isNotNull();
  }
}
