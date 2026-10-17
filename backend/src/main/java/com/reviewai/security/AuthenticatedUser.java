package com.reviewai.security;

import com.reviewai.user.Role;

import java.util.UUID;

/** Principal placed in the SecurityContext after a JWT has been validated. */
public record AuthenticatedUser(UUID userId, Role role, UUID tenantId) {

    @Override
    public String toString() {
        return userId.toString();
    }
}
