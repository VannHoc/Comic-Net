package com.comicnet.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenSecrets {
  private static final SecureRandom RANDOM = new SecureRandom();

  private TokenSecrets() {}

  public static String random() {
    byte[] b = new byte[32];
    RANDOM.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static String sixDigitCode() {
    return String.format(java.util.Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static boolean equal(String first, String second) {
    return first != null
        && second != null
        && MessageDigest.isEqual(
            first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
  }
}
