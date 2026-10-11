-- =============================================================================
-- V4__create_lead.sql
-- Creates the 'lead' table — tracks which customers are eligible for
-- outreach and what stage that outreach is at.
--
-- A "lead" represents the business decision to contact a customer for a
-- review conversation. One customer can have multiple lead records over
-- time (e.g. one per purchase cycle), but business logic rules (outside
-- the DB) control when new leads are created.
--
-- [OPEN — team-lead confirmation required before Sprint 2]:
--   1. status values: The schema gives examples (NEW, CONTACTED, REVIEWED,
--      OPTED_OUT) but marks the final list as "[OPEN]". The CHECK constraint
--      below uses these proposed values. The team lead must approve the
--      complete list before this migration runs in a shared environment.
--
--   2. eligible_at rules: described as "outreach eligibility [OPEN: rules]".
--      The column is included as a nullable TIMESTAMPTZ. The team lead must
--      define what sets this timestamp (e.g. X days after purchase) and
--      whether it should be NOT NULL.
--
-- Depends on: tenant (V1), customer (V3)
-- =============================================================================

CREATE TABLE lead (
    id           UUID          PRIMARY KEY,

    -- Tenant isolation — must match the customer's tenant_id.
    tenant_id    UUID          NOT NULL REFERENCES tenant(id),

    -- The customer targeted for outreach.
    customer_id  UUID          NOT NULL REFERENCES customer(id),

    -- Outreach lifecycle state.
    -- [OPEN] final value list must be approved before Sprint 2.
    -- Proposed: NEW, CONTACTED, REVIEWED, OPTED_OUT.
    status       VARCHAR(30)   NOT NULL
                     CHECK (status IN ('NEW', 'CONTACTED', 'REVIEWED', 'OPTED_OUT')),

    -- When the customer becomes eligible for outreach.
    -- [OPEN] the rules that set this timestamp are not yet defined.
    eligible_at  TIMESTAMPTZ,

    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Indexes (section 3.4 of schema)
--
-- 1. Filter and group leads by status within a tenant.
--    e.g. "show all NEW leads for tenant X" (used by the operations dashboard).
-- ---------------------------------------------------------------------------
CREATE INDEX idx_lead_tenant_status
    ON lead (tenant_id, status);

-- ---------------------------------------------------------------------------
-- 2. Find all leads for a specific customer within a tenant.
--    e.g. "has this customer been contacted before?" (de-dupe check).
-- ---------------------------------------------------------------------------
CREATE INDEX idx_lead_tenant_customer
    ON lead (tenant_id, customer_id);

-- ---------------------------------------------------------------------------
COMMENT ON TABLE  lead IS 'Tracks customers selected for review outreach and the stage of that outreach.';
COMMENT ON COLUMN lead.status      IS '[OPEN] final status list not yet approved. Proposed: NEW, CONTACTED, REVIEWED, OPTED_OUT.';
COMMENT ON COLUMN lead.eligible_at IS '[OPEN] rules for setting this timestamp not yet defined by team lead.';
