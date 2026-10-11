-- =============================================================================
-- V3__create_customer.sql
-- Creates the 'customer' table — end-users who interact via WhatsApp.
--
-- Customers are NOT login users. They are identified by their phone number.
-- Because phone numbers are personal data (PII), the schema applies
-- field-level encryption (schema rule #5):
--
--   phone_encrypted  — the actual phone number, encrypted with AES-GCM.
--                      Only the application (with the encryption key) can
--                      read or recover it.
--   phone_hash       — an HMAC-SHA256 of the phone number. The application
--                      hashes the input before querying, so lookups work
--                      without decrypting every row. This is the lookup key.
--
-- The same encrypted/hash pattern applies to email (optional field).
--
-- 'opted_out' (FR-CV-10): when TRUE, no outreach or AI messages should
-- be sent to this customer. The application must check this flag before
-- initiating any conversation.
--
-- 'purchase_info' (JSONB): schema marks this as "only if approved as
-- sensitive data". It is included as a nullable column per the schema
-- definition. No sensitive plaintext data should be stored here without
-- further approval.
--
-- Depends on: tenant (V1)
-- =============================================================================

CREATE TABLE customer (
    id               UUID          PRIMARY KEY,

    -- Tenant isolation — every query filters by this column.
    tenant_id        UUID          NOT NULL REFERENCES tenant(id),

    -- Display name (may be obtained from WhatsApp profile; not always present).
    name             VARCHAR(150),

    -- Encrypted phone number (AES-GCM). Required — it is the customer identity.
    phone_encrypted  BYTEA         NOT NULL,

    -- HMAC-SHA256 of the phone number. Used for lookups (unique per tenant).
    -- CHAR(64) = 32 bytes of HMAC output expressed as 64 hex characters.
    phone_hash       CHAR(64)      NOT NULL,

    -- Encrypted email (AES-GCM). Optional — not all customers provide email.
    email_encrypted  BYTEA,

    -- Opt-out flag. Must be checked before any outreach. Defaults to false.
    opted_out        BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Flexible store for purchase data. Only populate if approved for
    -- sensitive data handling.
    purchase_info    JSONB,

    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Guarantees one customer record per phone number per tenant.
    -- Two different tenants can have the same phone number (they are separate
    -- business accounts). Within one tenant, the phone must be unique.
    CONSTRAINT uq_customer_tenant_phone UNIQUE (tenant_id, phone_hash)
);

-- ---------------------------------------------------------------------------
-- Indexes (section 3.3 of schema)
--
-- Supports paginated customer lists sorted by newest first, filtered by
-- tenant. The DESC on created_at matches the approved sort order in
-- section 5 (pagination contract).
-- ---------------------------------------------------------------------------
CREATE INDEX idx_customer_tenant_created
    ON customer (tenant_id, created_at DESC);

-- ---------------------------------------------------------------------------
COMMENT ON TABLE  customer IS 'WhatsApp end-users. Not login users. Phone is encrypted; phone_hash is used for lookups.';
COMMENT ON COLUMN customer.phone_encrypted IS 'AES-GCM encrypted phone number. Decryptable with app key.';
COMMENT ON COLUMN customer.phone_hash      IS 'HMAC-SHA256 of phone. Used for all lookups to avoid decryption.';
COMMENT ON COLUMN customer.email_encrypted IS 'AES-GCM encrypted email. Nullable — not always provided.';
COMMENT ON COLUMN customer.opted_out       IS 'TRUE = customer requested no contact. Check before every outreach (FR-CV-10).';
COMMENT ON COLUMN customer.purchase_info   IS 'JSONB purchase data. Only populate after sensitive-data approval.';
