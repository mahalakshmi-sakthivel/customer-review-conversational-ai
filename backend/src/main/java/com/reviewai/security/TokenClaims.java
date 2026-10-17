package com.reviewai.security;

import com.reviewai.user.Role;

import java.time.Instant;
import java.util.UUID;

/** Validated JWT content. tenantId is null only for SUPER_ADMIN. */
public record TokenClaims(UUID userId, Role role, UUID tenantId, String jti, Instant expiresAt) {}
