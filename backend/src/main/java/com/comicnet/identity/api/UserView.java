package com.comicnet.identity.api;

import java.util.List;

public record UserView(
    String id,
    String email,
    String username,
    String displayName,
    String status,
    boolean emailVerified,
    List<String> roles) {}
