-- =============================================================================
-- V2__create_app_user.sql
-- Creates the 'app_user' table — human logins for the platform back-office.
--
-- The table is named 'app_user' (not 'user') because "user" is a reserved
-- word in PostgreSQL (schema rule #3). Customers do NOT have logins —
-- they interact only via WhatsApp.
--
-- Security rules enforced here (schema rules #4, #5, #6, #7):
--   - 'password_hash' stores only a BCrypt or Argon2 hash. The plaintext
--     password is NEVER stored anywhere in the database.
--   - 'failed_login_count' and 'locked_until' support account lockout.
--   - All timestamps are TIMESTAMPTZ (UTC).
--   - UUID primary key.
--
-- [OPEN — team-lead confirmation required before Sprint 2]:
--   1. tenant_id nullability: The schema says tenant_id may be NULL only for
--      a "platform super user". The column is currently defined as nullable
--      (no NOT NULL) to match this note. The team lead must confirm:
--        a) Is SUPER_ADMIN always the platform super user with NULL tenant_id?
--        b) Should TENANT_ADMIN and OPERATIONS_USER always have a non-null
--           tenant_id enforced by a CHECK constraint?
--      Until confirmed, the column is nullable with no partial constraint.
--
--   2. role values: The schema proposes SUPER_ADMIN, TENANT_ADMIN,
--      OPERATIONS_USER but explicitly marks this "[OPEN] must be approved
--      before Sprint 2". The CHECK constraint below uses these proposed
--      values. The team lead must approve (or amend) before this migration
--      runs in a shared environment.
-- =============================================================================

CREATE TABLE app_user (
    id                  UUID          PRIMARY KEY,

    -- NULL only for the platform super user (SUPER_ADMIN with no tenant).
    -- [OPEN] confirm nullability rules — see header notes above.
    tenant_id           UUID          REFERENCES tenant(id),

    -- Login identifier. Stored as-is; lookups use LOWER(email) index below.
    email               VARCHAR(255)  NOT NULL,

    -- BCrypt or Argon2 hash. Never the plaintext password.
    password_hash       VARCHAR(255)  NOT NULL,

    -- [OPEN] role enum values must be approved before Sprint 2.
    -- Proposed: SUPER_ADMIN, TENANT_ADMIN, OPERATIONS_USER.
    role                VARCHAR(30)   NOT NULL
                            CHECK (role IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'OPERATIONS_USER')),

    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE', 'DISABLED')),

    -- Lockout support: track consecutive failures and unlock time.
    failed_login_count  INT           NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ,

    last_login_at       TIMESTAMPTZ,

    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Indexes (section 3.2 of schema)
--
-- 1. Case-insensitive unique email across the entire platform.
--    LOWER(email) means 'Alice@example.com' and 'alice@example.com'
--    are treated as duplicates.
-- ---------------------------------------------------------------------------
CREATE UNIQUE INDEX uq_app_user_email_lower
    ON app_user (LOWER(email));

-- ---------------------------------------------------------------------------
-- 2. Composite index for role-based queries within a tenant, e.g.:
--    "list all TENANT_ADMINs for tenant X".
-- ---------------------------------------------------------------------------
CREATE INDEX idx_app_user_tenant_role
    ON app_user (tenant_id, role);

-- ---------------------------------------------------------------------------
COMMENT ON TABLE  app_user IS 'Back-office human logins. Customers (WhatsApp users) are in the customer table.';
COMMENT ON COLUMN app_user.tenant_id       IS 'NULL only for platform SUPER_ADMIN. [OPEN] confirm with team lead.';
COMMENT ON COLUMN app_user.password_hash   IS 'BCrypt or Argon2 hash only. Never plaintext.';
COMMENT ON COLUMN app_user.role            IS '[OPEN] values must be approved before Sprint 2. Proposed: SUPER_ADMIN, TENANT_ADMIN, OPERATIONS_USER.';
COMMENT ON COLUMN app_user.failed_login_count IS 'Incremented on each failed login. Reset to 0 on successful login.';
COMMENT ON COLUMN app_user.locked_until    IS 'Account is locked until this timestamp. NULL means not locked.';
