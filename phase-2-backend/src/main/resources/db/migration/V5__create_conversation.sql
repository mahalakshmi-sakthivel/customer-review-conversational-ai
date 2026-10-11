-- =============================================================================
-- V5__create_conversation.sql
-- Creates the 'conversation' table — a single WhatsApp dialogue session
-- between the AI bot and one customer.
--
-- Key business rules enforced at the database level:
--
--   1. One ACTIVE conversation per customer per tenant (partial unique index).
--      If a customer already has an ACTIVE conversation, a new one cannot be
--      inserted. This prevents the bot from starting a second session before
--      the first is resolved. COMPLETED, ABANDONED, etc. are not restricted
--      — historical conversations are allowed.
--
--   2. 'turn_count' tracks the number of back-and-forth exchanges. The
--      application increments this; the DB just stores it.
--
--   3. 'started_at' is NOT NULL because a conversation always starts at a
--      known moment. 'ended_at' is nullable — it is NULL while ACTIVE and
--      set when the conversation reaches a terminal state.
--
-- Depends on: tenant (V1), customer (V3)
-- =============================================================================

CREATE TABLE conversation (
    id           UUID          PRIMARY KEY,

    -- Tenant isolation — denormalised here for fast tenant-scoped queries
    -- without joining through customer.
    tenant_id    UUID          NOT NULL REFERENCES tenant(id),

    customer_id  UUID          NOT NULL REFERENCES customer(id),

    -- Lifecycle state.
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                     CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED', 'ESCALATED', 'OPTED_OUT')),

    -- Number of message exchanges in this conversation.
    turn_count   INT           NOT NULL DEFAULT 0,

    -- started_at is always known; ended_at is set when conversation closes.
    started_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    ended_at     TIMESTAMPTZ,

    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Indexes (section 3.5 of schema)
--
-- 1. Supports dashboard queries: "list conversations by status for tenant X,
--    newest first" — matches pagination sort fields in section 5.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_conversation_tenant_status_created
    ON conversation (tenant_id, status, created_at DESC);

-- ---------------------------------------------------------------------------
-- 2. Supports customer-timeline queries: "all conversations for customer Y
--    within tenant X".
-- ---------------------------------------------------------------------------
CREATE INDEX idx_conversation_tenant_customer
    ON conversation (tenant_id, customer_id);

-- ---------------------------------------------------------------------------
-- 3. PARTIAL UNIQUE INDEX — the most important constraint on this table.
--    Only one row can exist with status = 'ACTIVE' for each
--    (tenant_id, customer_id) pair. Rows with other statuses are not
--    affected, so historical conversations coexist freely.
--
--    Example: customer 42 in tenant A can have:
--      - 1 ACTIVE conversation (enforced to be unique)
--      - many COMPLETED conversations (not restricted)
-- ---------------------------------------------------------------------------
CREATE UNIQUE INDEX uq_one_active_conversation
    ON conversation (tenant_id, customer_id)
    WHERE status = 'ACTIVE';

-- ---------------------------------------------------------------------------
COMMENT ON TABLE  conversation IS 'A single WhatsApp dialogue session between the AI bot and a customer.';
COMMENT ON COLUMN conversation.status      IS 'Allowed: ACTIVE, COMPLETED, ABANDONED, ESCALATED, OPTED_OUT.';
COMMENT ON COLUMN conversation.turn_count  IS 'Number of exchange turns. Incremented by the application.';
COMMENT ON COLUMN conversation.started_at  IS 'NOT NULL — always known at creation time.';
COMMENT ON COLUMN conversation.ended_at    IS 'NULL while ACTIVE. Set when conversation reaches a terminal status.';
COMMENT ON INDEX  uq_one_active_conversation IS 'Enforces: at most one ACTIVE conversation per customer per tenant.';
