package com.comicnet.identity.service;

import com.comicnet.common.web.DomainException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {
  private final Environment env;
  private volatile JwtEncoder encoder;
  private volatile JwtDecoder decoder;

  public JwtTokens(Environment env) {
    this.env = env;
  }

  private synchronized void initialize() {
    if (encoder != null) return;
    try {
      var factory = KeyFactory.getInstance("RSA");
      var privateKey =
          (RSAPrivateKey)
              factory.generatePrivate(
                  new PKCS8EncodedKeySpec(pem(env.getProperty("JWT_PRIVATE_KEY_PATH"))));
      var publicKey =
          (RSAPublicKey)
              factory.generatePublic(
                  new X509EncodedKeySpec(pem(env.getProperty("JWT_PUBLIC_KEY_PATH"))));
      if (publicKey.getModulus().bitLength() < 2048
          || !privateKey.getModulus().equals(publicKey.getModulus()))
        throw new IllegalArgumentException();
      var key =
          new RSAKey.Builder(publicKey)
              .privateKey(privateKey)
              .keyID(env.getProperty("JWT_KID", "comicnet-local-1"))
              .algorithm(JWSAlgorithm.RS256)
              .build();
      var localDecoder =
          NimbusJwtDecoder.withPublicKey(publicKey)
              .signatureAlgorithm(SignatureAlgorithm.RS256)
              .build();
      localDecoder.setJwtValidator(
          new DelegatingOAuth2TokenValidator<>(
              new JwtTimestampValidator(Duration.ofSeconds(30)),
              new JwtIssuerValidator(issuer()),
              jwt ->
                  jwt.getAudience().contains(audience())
                          && jwt.getIssuedAt() != null
                          && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))
                          && jwt.getId() != null
                          && jwt.getClaimAsString("sid") != null
                      ? OAuth2TokenValidatorResult.success()
                      : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
      decoder = localDecoder;
      encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    } catch (Exception e) {
      throw new DomainException(
          503, "AUTH_NOT_CONFIGURED", "Khóa xác thực chưa được cấu hình hợp lệ trên backend.");
    }
  }

  private static byte[] pem(String file) throws Exception {
    if (file == null) throw new IllegalArgumentException();
    String s =
        Files.readString(Path.of(file)).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    return Base64.getDecoder().decode(s);
  }

  public String issue(long userId, long sessionId, long authVersion) {
    initialize();
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer())
            .audience(java.util.List.of(audience()))
            .subject(Long.toString(userId))
            .issuedAt(now)
            .notBefore(now)
            .expiresAt(now.plusSeconds(600))
            .id(UUID.randomUUID().toString())
            .claim("sid", Long.toString(sessionId))
            .claim("av", authVersion)
            .build();
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256)
                    .keyId(env.getProperty("JWT_KID", "comicnet-local-1"))
                    .build(),
                claims))
        .getTokenValue();
  }

  public Jwt decode(String token) {
    initialize();
    try {
      return decoder.decode(token);
    } catch (JwtException | IllegalArgumentException e) {
      throw DomainException.unauthorized();
    }
  }

  private String issuer() {
    return env.getProperty("JWT_ISSUER", "urn:comic-net:local");
  }

  private String audience() {
    return env.getProperty("JWT_AUDIENCE", "comic-net-clients");
  }
}
