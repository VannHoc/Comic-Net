package com.comicnet.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseConfigurationGuardTest {
  private MockEnvironment valid() {
    return new MockEnvironment()
        .withProperty("DB_USER", "comicnet_app")
        .withProperty("DB_PASSWORD", "test-only-runtime")
        .withProperty("DB_MIGRATION_USER", "comicnet_migrator")
        .withProperty("DB_MIGRATION_PASSWORD", "test-only-migration");
  }

  @Test
  void missingAndExampleCredentialsAreRejectedWithoutPrintingValues() {
    assertThatThrownBy(() -> DatabaseConfigurationGuard.validate(new MockEnvironment()))
        .hasMessageContaining("DB_USER");
    assertThatThrownBy(
            () ->
                DatabaseConfigurationGuard.validate(
                    valid().withProperty("DB_PASSWORD", "${DB_PASSWORD}")))
        .hasMessageContaining("DB_PASSWORD")
        .hasMessageNotContaining("${DB_PASSWORD}");
  }

  @Test
  void rejectsSaSharedLoginAndWrongDatabase() {
    assertThatThrownBy(
            () -> DatabaseConfigurationGuard.validate(valid().withProperty("DB_USER", "SA")))
        .hasMessageContaining("not sa");
    assertThatThrownBy(
            () ->
                DatabaseConfigurationGuard.validate(
                    valid().withProperty("DB_USER", "comicnet_migrator")))
        .hasMessageContaining("separate");
    assertThatThrownBy(
            () -> DatabaseConfigurationGuard.validate(valid().withProperty("DB_NAME", "master")))
        .hasMessageContaining("ComicNetDev");
    assertThatCode(() -> DatabaseConfigurationGuard.validate(valid())).doesNotThrowAnyException();
  }

  @Test
  void restoreVerificationRequiresLocalExplicitTargetAndAllBackgroundWritersDisabled() {
    var restore =
        valid()
            .withProperty("DB_NAME", "ComicNetRestoreQA_abcdef012345")
            .withProperty("RESTORE_VERIFICATION", "true")
            .withProperty("S3_ENDPOINT", "http://127.0.0.1:9100")
            .withProperty("spring.flyway.enabled", "false")
            .withProperty("comicnet.mail.worker-enabled", "false")
            .withProperty("comicnet.upload.worker-enabled", "false");
    assertThatThrownBy(() -> DatabaseConfigurationGuard.validate(restore))
        .isInstanceOf(IllegalStateException.class);
    restore.setActiveProfiles("local");
    assertThatCode(() -> DatabaseConfigurationGuard.validate(restore)).doesNotThrowAnyException();
    for (String writer :
        java.util.List.of(
            "spring.flyway.enabled",
            "comicnet.mail.worker-enabled",
            "comicnet.upload.worker-enabled")) {
      restore.withProperty(writer, "true");
      assertThatThrownBy(() -> DatabaseConfigurationGuard.validate(restore))
          .isInstanceOf(IllegalStateException.class);
      restore.withProperty(writer, "false");
    }
    restore.withProperty("S3_ENDPOINT", "http://127.0.0.1:9000");
    assertThatThrownBy(() -> DatabaseConfigurationGuard.validate(restore))
        .isInstanceOf(IllegalStateException.class);
    restore.withProperty("S3_ENDPOINT", "http://127.0.0.1:9100");
    for (String invalidEndpoint : java.util.List.of("http:invalid", "http://localhost:99999")) {
      restore.withProperty("S3_ENDPOINT", invalidEndpoint);
      assertThatThrownBy(() -> DatabaseConfigurationGuard.validate(restore))
          .isInstanceOf(IllegalStateException.class);
    }
    restore.withProperty("S3_ENDPOINT", "http://127.0.0.1:9100");
    restore.withProperty("DB_NAME", "master");
    assertThatThrownBy(() -> DatabaseConfigurationGuard.validate(restore))
        .isInstanceOf(IllegalStateException.class);
  }
}
