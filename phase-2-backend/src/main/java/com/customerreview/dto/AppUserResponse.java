package com.customerreview.dto;

import java.time.Instant;
import java.util.UUID;

public record AppUserResponse(
    UUID id,
    UUID tenantId,
    String email,
    String role,
    String status,
    int failedLoginCount,
    Instant lockedUntil,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt
) {}
