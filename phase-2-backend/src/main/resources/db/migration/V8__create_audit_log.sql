-- =============================================================================
-- V8__create_audit_log.sql
-- Creates the 'audit_log' table — an append-only security event log.
--
-- Key design points:
--
--   1. Append-only: the schema explicitly states "the app DB user gets
--      INSERT and SELECT only on this table." No UPDATE or DELETE.
--      This is enforced at the PostgreSQL role/permission level (outside
--      Flyway), not by a constraint. The comment below documents this
--      requirement so the DBA can apply the correct GRANT after migration.
--
--   2. Nullable tenant_id and user_id: platform-level events (e.g. a
--      SUPER_ADMIN login) may not belong to any tenant. Failed logins
--      before authentication completes may have no user_id yet.
--      Both columns are nullable by design.
--
--   3. No secrets in 'detail' (JSONB): the column exists for structured
--      diagnostic context (e.g. which resource was accessed). The
--      application must NEVER write plaintext passwords, raw phone numbers,
--      or decrypted PII into this column.
--
--   4. ip_address VARCHAR(45): accommodates both IPv4 (max 15 chars) and
--      IPv6 with full notation (max 45 chars).
--
--   5. No updated_at: audit records are immutable. Only created_at.
--
--   6. No foreign key on user_id or tenant_id: this is intentional.
--      If a user or tenant is deleted, the audit record must remain.
--      Foreign keys with ON DELETE CASCADE would destroy evidence.
--      The columns are UUIDs; the application is responsible for
--      providing valid IDs.
--
-- Depends on: nothing (nullable tenant_id/user_id — no FK constraints)
-- Created last (V8) so it depends on no table it could break.
-- =============================================================================

CREATE TABLE audit_log (
    id          UUID          PRIMARY KEY,

    -- Nullable — platform events may not belong to a tenant.
    -- No FK constraint intentionally (see notes above).
    tenant_id   UUID,

    -- Nullable — pre-authentication events may not have a known user.
    -- No FK constraint intentionally (see notes above).
    user_id     UUID,

    -- What happened. Values defined in the phase-3 security specification.
    event_type  VARCHAR(50)   NOT NULL,

    -- Whether the event succeeded or was blocked.
    outcome     VARCHAR(10)   NOT NULL
                    CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE')),

    -- Distributed tracing ID — correlates this event with application logs.
    trace_id    VARCHAR(64),

    -- Source IP address. VARCHAR(45) covers IPv4 and IPv6.
    ip_address  VARCHAR(45),

    -- Structured context (e.g. resource accessed, error code).
    -- MUST NOT contain: passwords, raw phone numbers, decrypted PII.
    detail      JSONB,

    -- Immutable insert timestamp. Required — every audit event must be dated.
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Indexes (section 3.8 of schema)
--
-- 1. Tenant-scoped audit trail — "show all events for tenant X, newest first".
--    tenant_id can be NULL, so this index also works for platform events
--    (the NULL rows will not match a tenant_id = ? filter, which is correct).
-- ---------------------------------------------------------------------------
CREATE INDEX idx_audit_tenant_created
    ON audit_log (tenant_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 2. Event-type timeline — "show all LOGIN_FAILED events across the platform,
--    newest first." Useful for security monitoring regardless of tenant.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_audit_event_created
    ON audit_log (event_type, created_at DESC);

-- ---------------------------------------------------------------------------
-- PERMISSION NOTE (must be applied by DBA after migration runs):
-- The application database user should receive only INSERT and SELECT
-- on this table — never UPDATE or DELETE. Example:
--
--   REVOKE UPDATE, DELETE ON audit_log FROM <app_db_user>;
--   GRANT  INSERT, SELECT  ON audit_log TO  <app_db_user>;
--
-- This enforces the append-only requirement stated in section 3.8.
-- Flyway itself runs as the migration user (which needs DDL rights) and
-- does not apply these runtime grants — a separate DBA step is required.
-- ---------------------------------------------------------------------------

COMMENT ON TABLE  audit_log IS 'Append-only security event log. App DB user must have INSERT+SELECT only (no UPDATE/DELETE).';
COMMENT ON COLUMN audit_log.tenant_id   IS 'Nullable. NULL for platform-level events. No FK — records must survive tenant deletion.';
COMMENT ON COLUMN audit_log.user_id     IS 'Nullable. NULL for pre-auth events. No FK — records must survive user deletion.';
COMMENT ON COLUMN audit_log.event_type  IS 'Event codes defined in phase-3 security spec.';
COMMENT ON COLUMN audit_log.outcome     IS 'Allowed: SUCCESS, DENIED, FAILURE.';
COMMENT ON COLUMN audit_log.detail      IS 'JSONB context. MUST NOT contain passwords, raw phones, or decrypted PII.';
