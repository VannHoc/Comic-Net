package com.comicnet.storage.service;

import com.comicnet.common.web.DomainException;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class S3Gateway implements AutoCloseable {
  private final Environment env;
  private S3Client client;
  private S3Presigner signer;
  private String bucket;

  public S3Gateway(Environment env) {
    this.env = env;
  }

  private synchronized void initialize() {
    if (client != null) return;
    try {
      URI endpoint = URI.create(env.getRequiredProperty("S3_ENDPOINT"));
      boolean local =
          Set.of("localhost", "127.0.0.1", "[::1]").contains(endpoint.getHost())
              && env.acceptsProfiles(org.springframework.core.env.Profiles.of("local"));
      if (endpoint.getUserInfo() != null
          || endpoint.getQuery() != null
          || endpoint.getFragment() != null
          || (!"https".equals(endpoint.getScheme())
              && !(local && "http".equals(endpoint.getScheme()))))
        throw new IllegalArgumentException();
      var credentials =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(
                  env.getRequiredProperty("S3_ACCESS_KEY"),
                  env.getRequiredProperty("S3_SECRET_KEY")));
      var config =
          S3Configuration.builder()
              .pathStyleAccessEnabled(true)
              .chunkedEncodingEnabled(false)
              .build();
      String configuredBucket = env.getProperty("S3_BUCKET", "comicnet-dev");
      var candidate =
          S3Client.builder()
              .endpointOverride(endpoint)
              .region(Region.of(env.getProperty("S3_REGION", "us-east-1")))
              .credentialsProvider(credentials)
              .serviceConfiguration(config)
              .httpClientBuilder(
                  UrlConnectionHttpClient.builder()
                      .connectionTimeout(Duration.ofSeconds(3))
                      .socketTimeout(Duration.ofSeconds(15)))
              .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(25)))
              .build();
      try {
        candidate.headBucket(r -> r.bucket(configuredBucket));
      } catch (S3Exception e) {
        if (e.statusCode() == 404
            && local
            && env.getProperty("S3_CREATE_BUCKET", Boolean.class, false))
          candidate.createBucket(r -> r.bucket(configuredBucket));
        else {
          candidate.close();
          throw e;
        }
      }
      signer =
          S3Presigner.builder()
              .endpointOverride(endpoint)
              .region(Region.of(env.getProperty("S3_REGION", "us-east-1")))
              .credentialsProvider(credentials)
              .serviceConfiguration(config)
              .build();
      bucket = configuredBucket;
      client = candidate;
    } catch (Exception e) {
      throw new DomainException(
          503, "STORAGE_UNAVAILABLE", "Dịch vụ lưu trữ chưa sẵn sàng. Kiểm tra cấu hình backend.");
    }
  }

  public void put(String key, byte[] bytes, String mime) {
    initialize();
    try {
      client.putObject(
          r -> r.bucket(bucket).key(key).contentType(mime), RequestBody.fromBytes(bytes));
    } catch (Exception e) {
      throw unavailable();
    }
  }

  public String bucket() {
    initialize();
    return bucket;
  }

  public void putStream(String key, java.io.InputStream stream, long size) {
    initialize();
    if (size < 1 || size > 100L * 1024 * 1024)
      throw new IllegalArgumentException("Staging content must be 1 byte to 100 MiB.");
    java.nio.file.Path source = null;
    try {
      // Signing/checksum calculation and retries can each reread the body. Multipart file
      // streams do not support reset; use a bounded disk spool rather than a heap-sized buffer.
      source = java.nio.file.Files.createTempFile("comicnet-s3-stage-", ".bin");
      try (var output = java.nio.file.Files.newOutputStream(source)) {
        byte[] buffer = new byte[16384];
        long total = 0;
        int read;
        while ((read = stream.read(buffer)) != -1) {
          total += read;
          if (total > size) throw new java.io.IOException("Staging size mismatch.");
          output.write(buffer, 0, read);
        }
        if (total != size) throw new java.io.IOException("Staging size mismatch.");
      }
      client.putObject(
          r -> r.bucket(bucket).key(key).contentType("application/octet-stream"),
          RequestBody.fromFile(source));
    } catch (Exception e) {
      // Never log SDK messages/requests: they can contain signed URLs or credentials.
      org.slf4j.LoggerFactory.getLogger(S3Gateway.class)
          .warn(
              "S3 staging transport failed: type={}, cause={}",
              e.getClass().getSimpleName(),
              e.getCause() == null ? "none" : e.getCause().getClass().getSimpleName());
      throw unavailable();
    } finally {
      if (source != null) {
        try {
          java.nio.file.Files.deleteIfExists(source);
        } catch (java.io.IOException cleanup) {
          org.slf4j.LoggerFactory.getLogger(S3Gateway.class)
              .warn(
                  "Could not remove an S3 staging spool; type={}",
                  cleanup.getClass().getSimpleName());
        }
      }
    }
  }

  public void download(String key, java.nio.file.Path target) {
    initialize();
    try (var input = client.getObject(r -> r.bucket(bucket).key(key));
        var output = java.nio.file.Files.newOutputStream(target)) {
      byte[] buffer = new byte[16384];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > 100L * 1024 * 1024)
          throw new java.io.IOException("Staging size limit exceeded.");
        output.write(buffer, 0, read);
      }
    } catch (Exception e) {
      throw unavailable();
    }
  }

  public boolean exists(String key) {
    initialize();
    try {
      client.headObject(r -> r.bucket(bucket).key(key));
      return true;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) return false;
      throw unavailable();
    } catch (Exception e) {
      throw unavailable();
    }
  }

  public byte[] get(String key) {
    initialize();
    try {
      return client.getObjectAsBytes(r -> r.bucket(bucket).key(key)).asByteArray();
    } catch (Exception e) {
      throw unavailable();
    }
  }

  public void delete(String key) {
    initialize();
    try {
      client.deleteObject(r -> r.bucket(bucket).key(key));
    } catch (Exception e) {
      throw unavailable();
    }
  }

  public String sign(String key, Duration ttl) {
    initialize();
    return signer
        .presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(r -> r.bucket(bucket).key(key))
                .build())
        .url()
        .toString();
  }

  private DomainException unavailable() {
    return new DomainException(
        503, "STORAGE_UNAVAILABLE", "Dịch vụ lưu trữ tạm thời không khả dụng.");
  }

  @jakarta.annotation.PreDestroy
  @Override
  public synchronized void close() {
    if (client != null) client.close();
    if (signer != null) signer.close();
    client = null;
    signer = null;
  }
}
