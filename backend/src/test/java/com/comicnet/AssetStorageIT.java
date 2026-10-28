package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.identity.dto.AuthRequests;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.storage.api.AssetStore;
import com.comicnet.storage.api.StagingStore;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "comicnet.mail.worker-enabled=false")
@Transactional
class AssetStorageIT {
  @Autowired AssetStore assets;
  @Autowired StagingStore staging;
  @Autowired AuthenticationService auth;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;

  @Test
  void fileBackedStagingSurvivesSdkRereadsWithoutBufferingWholeUploadInMemory() throws Exception {
    var source = Files.createTempFile("comicnet-staging-test-", ".bin");
    var downloaded = Files.createTempFile("comicnet-staging-read-", ".bin");
    String key = "staging/" + UUID.randomUUID();
    byte[] bytes = new byte[256 * 1024];
    new java.util.Random(42).nextBytes(bytes);
    Files.write(source, bytes);
    try {
      try (var input = Files.newInputStream(source)) {
        assertThat(input.markSupported()).isFalse();
        staging.put(key, input, Files.size(source));
      }
      staging.download(key, downloaded);
      assertThat(Files.mismatch(source, downloaded)).isEqualTo(-1);
    } finally {
      staging.remove(key);
      Files.deleteIfExists(source);
      Files.deleteIfExists(downloaded);
    }
  }

  @Test
  void realS3UploadDerivativeSignedReadAndIdempotency() throws Exception {
    String email = "storage-" + UUID.randomUUID() + "@comicnet.test";
    auth.register(new AuthRequests.Register(email, "Test-password-42!", "Creator"));
    long user =
        jdbc.queryForObject("SELECT Id FROM dbo.[User] WHERE NormalizedEmail=?", Long.class, email);
    jdbc.update("UPDATE dbo.[User] SET EmailVerifiedAt=SYSUTCDATETIME() WHERE Id=?", user);
    jdbc.update(
        "INSERT dbo.UserRole(UserId,RoleId) SELECT ?,Id FROM dbo.[Role] WHERE Code='CREATOR'",
        user);
    entities.clear();
    var actor =
        auth.authenticate(
            auth.login(new AuthRequests.Login(email, "Test-password-42!", "JUnit S3"))
                .accessToken());
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(10, 20, BufferedImage.TYPE_INT_ARGB), "PNG", bytes);
    String key = UUID.randomUUID().toString();
    var asset = assets.uploadCatalog(actor, "COVER", bytes.toByteArray(), "image/png", key);
    assertThat(asset.status()).isEqualTo("READY");
    assertThat(assets.uploadCatalog(actor, "COVER", bytes.toByteArray(), "image/png", key).id())
        .isEqualTo(asset.id());
    assets.requireOwned(Long.parseLong(asset.id()), user, "COVER");
    try (var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
      var display = assets.display(Long.parseLong(asset.id()), "JPEG");
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create(display.url())).GET().build(),
              HttpResponse.BodyHandlers.ofByteArray());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Type")).hasValue("image/jpeg");
      assertThat(ImageIO.read(new ByteArrayInputStream(response.body())).getHeight()).isEqualTo(20);
      URI signed = URI.create(display.url());
      URI anonymous =
          new URI(signed.getScheme(), signed.getAuthority(), signed.getPath(), null, null);
      assertThat(
              http.send(
                      HttpRequest.newBuilder(anonymous).GET().build(),
                      HttpResponse.BodyHandlers.discarding())
                  .statusCode())
          .isEqualTo(403);
    }
  }
}
