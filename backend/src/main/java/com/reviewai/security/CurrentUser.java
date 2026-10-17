package com.reviewai.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * The only supported way for services to learn who is calling and which tenant they belong to.
 * The values come from the validated JWT, never from the request.
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static Optional<AuthenticatedUser> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> new AccessDeniedException("Authentication required"));
    }

    /** Tenant of the caller. Fails for callers without a tenant (platform SUPER_ADMIN). */
    public static UUID requireTenantId() {
        UUID tenantId = require().tenantId();
        if (tenantId == null) {
            throw new AccessDeniedException("A tenant context is required for this operation");
        }
        return tenantId;
    }
}
