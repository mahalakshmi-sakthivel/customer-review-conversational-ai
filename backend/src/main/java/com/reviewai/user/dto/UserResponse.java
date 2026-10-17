package com.reviewai.user.dto;

import com.reviewai.user.AppUser;
import com.reviewai.user.Role;
import com.reviewai.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    UUID tenantId,
    String email,
    Role role,
    UserStatus status,
    Instant lastLoginAt,
    Instant createdAt
) {
    public static UserResponse from(AppUser u) {
        return new UserResponse(u.getId(), u.getTenantId(), u.getEmail(), u.getRole(),
            u.getStatus(), u.getLastLoginAt(), u.getCreatedAt());
    }
}
