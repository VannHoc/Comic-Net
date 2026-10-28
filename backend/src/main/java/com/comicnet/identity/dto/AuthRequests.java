package com.comicnet.identity.dto;

import jakarta.validation.constraints.*;

public final class AuthRequests {
  private AuthRequests() {}

  public record Register(
      @Email @Size(max = 320) String email,
      @NotNull @Size(min = 3, max = 12) String password,
      @NotBlank @Size(min = 3, max = 12) String displayName,
      @Pattern(regexp = "[A-Za-z0-9_]{3,12}") String username,
      @Pattern(regexp = "\\+?[0-9]{9,15}") String phone) {
    public Register(String email, String password, String displayName) {
      this(email, password, displayName, null, null);
    }
  }

  public record Login(
      @NotBlank @Size(min = 3, max = 320) String email,
      @NotNull @Size(min = 3, max = 128) String password,
      @Size(min = 1, max = 100) String deviceLabel) {}

  public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) {}

  public record TokenRequest(@NotBlank @Size(max = 1024) String token) {}

  public record OtpRequest(
      @NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}

  public record OtpReset(
      @NotBlank @Email @Size(max = 320) String email,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
      @NotNull @Size(min = 3, max = 12) String password) {}

  public record Reset(
      @NotBlank @Size(max = 1024) String token,
      @NotNull @Size(min = 3, max = 12) String password) {}

  public record Profile(@NotBlank @Size(min = 3, max = 12) String displayName) {}

  public record Username(@NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,12}") String username) {}
}
