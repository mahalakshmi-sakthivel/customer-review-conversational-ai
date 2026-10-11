-- =============================================================================
-- V6__create_message.sql
-- Creates the 'message' table — individual WhatsApp messages within a
-- conversation, both sent (OUTBOUND) and received (INBOUND).
--
-- Key design points:
--
--   1. Deduplication via external_message_id (FR-WA-06):
--      The Evolution API assigns its own ID to every WhatsApp message.
--      If a webhook is delivered twice (retries are normal in HTTP webhooks),
--      the conditional unique index below will reject the second insert,
--      preventing the same message from being processed twice.
--      The index is conditional (WHERE external_message_id IS NOT NULL)
--      because OUTBOUND messages we send may not have an Evolution ID yet.
--
--   2. Keyset pagination index:
--      The schema requires cursor-based (keyset) pagination for message
--      history. The cursor is (created_at, id) of the last message seen.
--      The index on (conversation_id, created_at DESC, id DESC) directly
--      supports "give me 50 messages before this cursor" without full
--      table scans.
--
--   3. tenant_id is denormalised here (it could be derived via
--      conversation → tenant_id) to allow fast tenant-scoped queries
--      without an extra join, per schema rule #1.
--
-- Depends on: tenant (V1), conversation (V5)
-- =============================================================================

CREATE TABLE message (
    id                   UUID          PRIMARY KEY,

    -- Denormalised tenant reference — allows tenant filtering without joining.
    tenant_id            UUID          NOT NULL REFERENCES tenant(id),

    conversation_id      UUID          NOT NULL REFERENCES conversation(id),

    -- INBOUND = customer sent it; OUTBOUND = bot sent it.
    direction            VARCHAR(10)   NOT NULL
                             CHECK (direction IN ('INBOUND', 'OUTBOUND')),

    -- Full message text. NOT NULL — every message must have content.
    content              TEXT          NOT NULL,

    -- The Evolution API's own message identifier. Used for deduplication.
    -- NULL for OUTBOUND messages that have not yet received an API ID.
    external_message_id  VARCHAR(100),

    -- Message timestamp — NOT NULL because we always know when it was sent.
    -- No updated_at: messages are immutable once stored.
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Indexes (section 3.6 of schema)
--
-- 1. Keyset pagination index — supports loading message history in order.
--    Query pattern: WHERE conversation_id = ? AND
--                         (created_at, id) < (cursor_ts, cursor_id)
--                   ORDER BY created_at DESC, id DESC
--                   LIMIT 50
-- ---------------------------------------------------------------------------
CREATE INDEX idx_message_history
    ON message (conversation_id, created_at DESC, id DESC);

-- ---------------------------------------------------------------------------
-- 2. Deduplication index (FR-WA-06) — partial, applies only when the
--    external ID is present.
--    Guarantees: within one tenant, the same Evolution API message ID
--    can only appear once. A second insert with the same
--    (tenant_id, external_message_id) will fail with a unique violation,
--    which the application catches and ignores (idempotent processing).
-- ---------------------------------------------------------------------------
CREATE UNIQUE INDEX uq_message_tenant_external_id
    ON message (tenant_id, external_message_id)
    WHERE external_message_id IS NOT NULL;

-- ---------------------------------------------------------------------------
COMMENT ON TABLE  message IS 'Individual WhatsApp messages within a conversation. Immutable once stored.';
COMMENT ON COLUMN message.direction           IS 'INBOUND = received from customer; OUTBOUND = sent by bot.';
COMMENT ON COLUMN message.external_message_id IS 'Evolution API message ID. Used by uq_message_tenant_external_id to block duplicate webhook processing (FR-WA-06).';
COMMENT ON INDEX  uq_message_tenant_external_id IS 'Partial unique index — enforces FR-WA-06: no duplicate message processing per tenant.';
COMMENT ON INDEX  idx_message_history           IS 'Supports keyset pagination: (conversation_id, created_at DESC, id DESC) cursor queries.';
