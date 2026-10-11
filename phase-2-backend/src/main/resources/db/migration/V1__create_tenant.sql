-- =============================================================================
-- V1__create_tenant.sql
-- Creates the 'tenant' table — the top-level owner of all data in the system.
--
-- Every business that uses this platform is a "tenant". All other tables
-- reference back to this one via tenant_id. This table has no foreign keys,
-- so it is created first (V1).
--
-- Rules enforced here (from schema rule #1, #6, #7):
--   - UUID primary key (no auto-increment integers).
--   - TIMESTAMPTZ (timezone-aware) columns stored in UTC.
--   - 'name' is globally unique — two tenants cannot share the same name.
--   - 'evolution_instance_name' maps an incoming WhatsApp webhook to the
--     correct tenant; it must also be unique across the platform.
--   - 'status' uses a CHECK constraint to allow only known values so bad
--     data can never enter this column.
-- =============================================================================

CREATE TABLE tenant (
    id                       UUID          PRIMARY KEY,

    -- Human-readable business name. Must be unique across the platform.
    name                     VARCHAR(150)  NOT NULL UNIQUE,

    -- Lifecycle state. Only ACTIVE tenants receive WhatsApp traffic.
    status                   VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                                 CHECK (status IN ('ACTIVE', 'SUSPENDED')),

    -- The Evolution API instance name that routes webhooks to this tenant.
    -- Nullable because a tenant may be created before an instance is assigned.
    -- Must be unique once set.
    evolution_instance_name  VARCHAR(100)  UNIQUE,

    -- Audit timestamps. NOT NULL — we always know when a row was created.
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Index strategy (section 3.1 of schema)
-- The UNIQUE constraint on 'name' and 'evolution_instance_name' implicitly
-- creates B-tree indexes for those columns — no additional indexes needed
-- for tenant lookups on this table.
-- ---------------------------------------------------------------------------

COMMENT ON TABLE  tenant IS 'Top-level business entity. Every tenant-scoped table references this.';
COMMENT ON COLUMN tenant.evolution_instance_name IS 'Maps an Evolution API webhook to this tenant. Must be unique when not null.';
COMMENT ON COLUMN tenant.status IS 'Allowed values: ACTIVE, SUSPENDED.';
