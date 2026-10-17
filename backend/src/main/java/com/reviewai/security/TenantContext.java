package com.reviewai.security;

import java.util.UUID;

/**
 * ThreadLocal container for the current request's tenant ID.
 * Must always be cleared after request completion to prevent thread pool leaks (PRD NFR-SEC-01).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
