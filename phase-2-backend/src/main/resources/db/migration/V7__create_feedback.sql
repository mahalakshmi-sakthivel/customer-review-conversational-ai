-- =============================================================================
-- V7__create_feedback.sql
-- Creates the 'feedback' table — the structured review collected at the
-- end of a completed WhatsApp conversation.
--
-- Key rules enforced here:
--
--   1. One feedback per conversation (UNIQUE on conversation_id).
--      A conversation can only produce one feedback record. This matches
--      the entity map in section 2: "feedback (one per completed conversation)".
--
--   2. rating CHECK (1–5): Enforces the star-rating range at the DB level.
--      Even if the application has a bug, the DB will reject a rating of 0
--      or 6.
--
--   3. sentiment CHECK: Enforces the five allowed sentiment labels.
--      The application's AI/NLP layer classifies sentiment; the DB ensures
--      only recognised values are stored.
--
--   4. Open-ended text columns (problem, suggestion, expectation):
--      TEXT with no length limit — review text is variable-length.
--      All are nullable because not every conversation yields all three.
--
--   5. No updated_at: Feedback is effectively immutable once submitted.
--      The schema defines only created_at for this table.
--
-- Depends on: tenant (V1), conversation (V5), customer (V3)
-- =============================================================================

CREATE TABLE feedback (
    id               UUID          PRIMARY KEY,

    -- Tenant isolation.
    tenant_id        UUID          NOT NULL REFERENCES tenant(id),

    -- Each conversation produces at most one feedback record.
    conversation_id  UUID          NOT NULL UNIQUE REFERENCES conversation(id),

    -- Denormalised for direct customer-level reporting without joining
    -- through conversation.
    customer_id      UUID          NOT NULL REFERENCES customer(id),

    -- Star rating 1–5. Enforced at DB level.
    rating           SMALLINT      NOT NULL
                         CHECK (rating BETWEEN 1 AND 5),

    -- Free-text fields from the conversation. All nullable.
    problem          TEXT,
    suggestion       TEXT,
    expectation      TEXT,

    -- AI-classified sentiment. Enforced at DB level.
    sentiment        VARCHAR(10)   NOT NULL
                         CHECK (sentiment IN ('positive', 'neutral', 'negative', 'mixed', 'unknown')),

    -- Immutable timestamp — feedback is not edited after submission.
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Indexes (section 3.7 of schema)
--
-- All three indexes are prefixed by tenant_id because every production
-- query on this table will filter by tenant first (schema rule #2).
--
-- 1. Time-ordered list of feedback for a tenant — supports the paginated
--    feedback list (approved sort field: createdAt, DESC).
-- ---------------------------------------------------------------------------
CREATE INDEX idx_feedback_tenant_created
    ON feedback (tenant_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 2. Filter or group by sentiment within a tenant.
--    e.g. "how many negative reviews this month for tenant X?"
-- ---------------------------------------------------------------------------
CREATE INDEX idx_feedback_tenant_sentiment
    ON feedback (tenant_id, sentiment);

-- ---------------------------------------------------------------------------
-- 3. Filter or sort by rating within a tenant.
--    Supports the pagination sort field 'rating' (section 5).
-- ---------------------------------------------------------------------------
CREATE INDEX idx_feedback_tenant_rating
    ON feedback (tenant_id, rating);

-- ---------------------------------------------------------------------------
COMMENT ON TABLE  feedback IS 'Structured review collected at the end of a completed conversation. One per conversation.';
COMMENT ON COLUMN feedback.rating     IS 'Star rating 1–5. CHECK constraint rejects values outside this range.';
COMMENT ON COLUMN feedback.sentiment  IS 'AI-classified sentiment. Allowed: positive, neutral, negative, mixed, unknown.';
COMMENT ON COLUMN feedback.problem    IS 'Customer-reported problem. Nullable — may not always be provided.';
COMMENT ON COLUMN feedback.suggestion IS 'Customer suggestion. Nullable.';
COMMENT ON COLUMN feedback.expectation IS 'Customer expectation. Nullable.';
