package com.comicnet.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.Nationalized;

@Entity
@Table(name = "[User]", schema = "dbo")
public class UserAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Nationalized
  @Column(name = "Email", length = 320)
  private String email;

  @Nationalized
  @Column(name = "NormalizedEmail", length = 320)
  private String normalizedEmail;

  @Nationalized
  @Column(name = "Username", length = 12)
  private String username;

  @Nationalized
  @Column(name = "NormalizedUsername", length = 12)
  private String normalizedUsername;

  @Nationalized
  @Column(name = "RecoveryPhone", length = 20)
  private String recoveryPhone;

  @Nationalized
  @Column(name = "PasswordHash", length = 500)
  private String passwordHash;

  @Nationalized
  @Column(name = "DisplayName", nullable = false, length = 100)
  private String displayName;

  @Column(name = "Status", nullable = false, length = 20)
  private String status = "ACTIVE";

  @Column(name = "EmailVerifiedAt")
  private Instant emailVerifiedAt;

  @Column(name = "AuthVersion", nullable = false)
  private long authVersion;

  @Version
  @Column(name = "LockVersion", nullable = false)
  private long lockVersion;

  @Column(name = "CreatedAt", insertable = false, updatable = false)
  private Instant createdAt;

  @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.BINARY)
  @Column(
      name = "RowVersion",
      insertable = false,
      updatable = false,
      columnDefinition = "rowversion",
      length = 8)
  private byte[] rowVersion;

  protected UserAccount() {}

  public UserAccount(String email, String normalizedEmail, String hash, String displayName) {
    this.email = email;
    this.normalizedEmail = normalizedEmail;
    this.passwordHash = hash;
    this.displayName = displayName;
  }

  public UserAccount(String email, String normalizedEmail, String username,
      String normalizedUsername, String recoveryPhone, String hash, String displayName) {
    this(email, normalizedEmail, hash, displayName);
    this.username = username;
    this.normalizedUsername = normalizedUsername;
    this.recoveryPhone = recoveryPhone;
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getUsername() { return username; }

  public void changeUsername(String value, String normalized) {
    username = value;
    normalizedUsername = normalized;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getStatus() {
    return status;
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public long getAuthVersion() {
    return authVersion;
  }

  public void verify(Instant now) {
    emailVerifiedAt = now;
  }

  public void rename(String value) {
    displayName = value;
  }

  public void changePassword(String value) {
    passwordHash = value;
    authVersion++;
  }

  public void changeStatus(String value) {
    status = value;
    authVersion++;
  }

  public void invalidateAuth() {
    authVersion++;
  }
}
