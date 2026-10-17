package com.reviewai.user;

/**
 * PROPOSED role list (Phase 1 section 3.2). Pending approval before Sprint 2.
 * Customers have no login (WhatsApp only) and tenants are entities, not roles.
 */
public enum Role {
    SUPER_ADMIN,
    TENANT_ADMIN,
    OPERATIONS_USER
}
