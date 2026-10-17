package com.reviewai.user.dto;

import com.reviewai.user.Role;

import java.util.UUID;

/** tenantId is null for the platform SUPER_ADMIN. No password hash is ever returned. */
public record LoginResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    UUID userId,
    UUID tenantId,
    String email,
    Role role
) {}
