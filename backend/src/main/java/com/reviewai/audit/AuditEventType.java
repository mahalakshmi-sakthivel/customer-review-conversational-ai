package com.reviewai.audit;

/** Subset of the Phase 3 section 8 event list that belongs to authentication and authorization. */
public enum AuditEventType {
    AUTH_LOGIN,
    AUTH_LOCKOUT,
    AUTH_TOKEN_REJECTED,
    ACCESS_DENIED_ROLE,
    ACCESS_DENIED_TENANT
}
