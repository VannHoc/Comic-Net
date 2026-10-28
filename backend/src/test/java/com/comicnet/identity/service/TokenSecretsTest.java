package com.comicnet.identity.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TokenSecretsTest {
  @Test
  void secretHasEntropyAndOnlyHashesAreStable() {
    String one = TokenSecrets.random(), two = TokenSecrets.random();
    assertEquals(43, one.length());
    assertNotEquals(one, two);
    assertEquals(64, TokenSecrets.hash(one).length());
    assertNotEquals(one, TokenSecrets.hash(one));
    assertTrue(TokenSecrets.equal(one, one));
    assertFalse(TokenSecrets.equal(one, two));
    assertFalse(TokenSecrets.equal(one, null));
  }

  @Test
  void emailNormalizationIsStableAndCaseInsensitive() {
    assertEquals(
        "reader@example.com", AuthenticationService.normalizeEmail(" Reader@Example.COM "));
    assertEquals(
        AuthenticationService.normalizeEmail("Đọc@Example.com"),
        AuthenticationService.normalizeEmail("ĐỌC@example.COM"));
  }
}
