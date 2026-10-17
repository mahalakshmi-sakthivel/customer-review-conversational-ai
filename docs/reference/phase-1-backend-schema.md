# PHASE 1 — BACKEND SCHEMA
Customer Review Conversational AI
Source: Master Spec sections 10, 15, 16, 18, 19, 30 and PRD sections 4.4, 5.1, 5.3, 7.
Sprint: freeze in Sprint 1 (db-schema.md), build in Sprint 2.
Status: DRAFT for mentor approval. Items marked [OPEN] depend on PRD section 9.6.

---

## 1. Rules that shape the schema

1. Every tenant-owned table has a NOT NULL tenant_id. No exceptions.
2. Every query on a tenant-owned table filters by tenant_id taken from the JWT, never from React.
3. "user" is a reserved word in PostgreSQL, so the table is named app_user.
4. Passwords: BCrypt or Argon2 hash only. Never reversible.
5. Phone and email: AES-GCM encrypted (recoverable). Add a separate HMAC hash column so lookups work without decrypting.
6. Timestamps: TIMESTAMPTZ, stored in UTC, returned as ISO-8601 (2026-10-06T12:30:00Z).
7. IDs: use UUID primary keys. API names stay tenantId, userId, customerId, leadId, conversationId, messageId, feedbackId.
8. Redis is temporary. PostgreSQL is the permanent source of truth.
9. Schema changes go through Flyway migrations only (V1__..., V2__...). No ddl-auto=update in any shared environment.

---

## 2. Entity map

```text
Tenant
  |-- app_user
  |-- customer
  |-- lead
  |-- conversation  (also belongs to customer)
  |      |-- message
  |      |-- feedback (one per completed conversation)
```

---

## 3. Tables

### 3.1 tenant
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| name | VARCHAR(150) NOT NULL | |
| status | VARCHAR(20) NOT NULL | ACTIVE, SUSPENDED |
| evolution_instance_name | VARCHAR(100) | maps webhook to tenant |
| created_at / updated_at | TIMESTAMPTZ NOT NULL | |

Unique: name, evolution_instance_name.

### 3.2 app_user
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK -> tenant | NULL only for platform super user [OPEN: confirm] |
| email | VARCHAR(255) NOT NULL | login id |
| password_hash | VARCHAR(255) NOT NULL | BCrypt/Argon2 |
| role | VARCHAR(30) NOT NULL | see role enum below [OPEN] |
| status | VARCHAR(20) NOT NULL | ACTIVE, DISABLED |
| failed_login_count | INT DEFAULT 0 | for lockout |
| locked_until | TIMESTAMPTZ | |
| last_login_at | TIMESTAMPTZ | |
| created_at / updated_at | TIMESTAMPTZ | |

Unique: LOWER(email). Index: (tenant_id, role).

Role enum (proposal, must be approved before Sprint 2):
SUPER_ADMIN, TENANT_ADMIN, OPERATIONS_USER.
Customer has no login (WhatsApp only). Tenant is an entity, not a login role.

### 3.3 customer
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK NOT NULL | |
| name | VARCHAR(150) | |
| phone_encrypted | BYTEA NOT NULL | AES-GCM |
| phone_hash | CHAR(64) NOT NULL | HMAC-SHA256 for lookup |
| email_encrypted | BYTEA | AES-GCM |
| opted_out | BOOLEAN DEFAULT FALSE | respects FR-CV-10 |
| purchase_info | JSONB | only if approved as sensitive data |
| created_at / updated_at | TIMESTAMPTZ | |

Unique: (tenant_id, phone_hash). Index: (tenant_id, created_at DESC).

### 3.4 lead
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK NOT NULL | |
| customer_id | UUID FK NOT NULL | |
| status | VARCHAR(30) NOT NULL | e.g. NEW, CONTACTED, REVIEWED, OPTED_OUT [OPEN: final list] |
| eligible_at | TIMESTAMPTZ | outreach eligibility [OPEN: rules] |
| created_at / updated_at | TIMESTAMPTZ | |

Index: (tenant_id, status), (tenant_id, customer_id).

### 3.5 conversation
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK NOT NULL | |
| customer_id | UUID FK NOT NULL | |
| status | VARCHAR(20) NOT NULL | ACTIVE, COMPLETED, ABANDONED, ESCALATED, OPTED_OUT |
| turn_count | INT DEFAULT 0 | |
| started_at | TIMESTAMPTZ NOT NULL | |
| ended_at | TIMESTAMPTZ | |
| created_at / updated_at | TIMESTAMPTZ | |

Index: (tenant_id, status, created_at DESC), (tenant_id, customer_id).
Partial unique index: one ACTIVE conversation per (tenant_id, customer_id).

### 3.6 message
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK NOT NULL | denormalised for fast tenant filtering |
| conversation_id | UUID FK NOT NULL | |
| direction | VARCHAR(10) NOT NULL | INBOUND, OUTBOUND |
| content | TEXT NOT NULL | |
| external_message_id | VARCHAR(100) | Evolution API id, used for dedupe |
| created_at | TIMESTAMPTZ NOT NULL | |

Unique: (tenant_id, external_message_id) WHERE external_message_id IS NOT NULL. This enforces FR-WA-06 (no duplicate processing).
Index for keyset pagination: (conversation_id, created_at DESC, id DESC).

### 3.7 feedback
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK NOT NULL | |
| conversation_id | UUID FK NOT NULL UNIQUE | one feedback per conversation |
| customer_id | UUID FK NOT NULL | |
| rating | SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5) | |
| problem | TEXT | |
| suggestion | TEXT | |
| expectation | TEXT | |
| sentiment | VARCHAR(10) NOT NULL | positive, neutral, negative, mixed, unknown |
| created_at | TIMESTAMPTZ NOT NULL | |

Index: (tenant_id, created_at DESC), (tenant_id, sentiment), (tenant_id, rating).

### 3.8 audit_log (supports the security phase)
| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID | nullable for platform events |
| user_id | UUID | nullable |
| event_type | VARCHAR(50) NOT NULL | see phase-3 file |
| outcome | VARCHAR(10) NOT NULL | SUCCESS, DENIED, FAILURE |
| trace_id | VARCHAR(64) | |
| ip_address | VARCHAR(45) | |
| detail | JSONB | no secrets, no raw customer data |
| created_at | TIMESTAMPTZ NOT NULL | |

Index: (tenant_id, created_at DESC), (event_type, created_at DESC).
Append-only: the app DB user gets INSERT and SELECT only on this table.

---

## 4. Key SQL (condensed)

```sql
CREATE TABLE tenant (
  id UUID PRIMARY KEY,
  name VARCHAR(150) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  evolution_instance_name VARCHAR(100) UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE customer (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  name VARCHAR(150),
  phone_encrypted BYTEA NOT NULL,
  phone_hash CHAR(64) NOT NULL,
  email_encrypted BYTEA,
  opted_out BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, phone_hash)
);

CREATE TABLE feedback (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  conversation_id UUID NOT NULL UNIQUE REFERENCES conversation(id),
  customer_id UUID NOT NULL REFERENCES customer(id),
  rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  problem TEXT, suggestion TEXT, expectation TEXT,
  sentiment VARCHAR(10) NOT NULL
    CHECK (sentiment IN ('positive','neutral','negative','mixed','unknown')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_one_active_conversation
  ON conversation (tenant_id, customer_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_message_history
  ON message (conversation_id, created_at DESC, id DESC);
```

Remaining tables follow section 3 the same way.

---

## 5. Pagination contract (applies to schema indexes)

Offset pagination (customers, feedback, conversations):
GET /api/v1/feedback?page=0&size=20&sort=createdAt,desc

- page >= 0
- size <= configured max (suggested start: 100, final value set after load test) [OPEN]
- sort only on approved fields:

| Endpoint | Approved sort fields |
|---|---|
| feedback | createdAt, rating, sentiment |
| customers | createdAt, name |
| conversations | createdAt, status |

- Response: content, page, size, totalElements, totalPages, first, last.

Keyset pagination (messages):
GET /api/v1/conversations/{conversationId}/messages?limit=50&before=<cursor>
Cursor = (created_at, id) of the last message seen.

---

## 6. Redis (not in PostgreSQL, but part of the data contract)

Key: conversation:{conversationId}
Or tenant-scoped: tenant:{tenantId}:conversation:{conversationId} [OPEN: decide in Sprint 1]
Recommendation: use the tenant-scoped key. It costs nothing and adds a second isolation layer.

Value: ConversationState JSON (conversationId, tenantId, customerId, name, rating, problem, suggestion, expectation, sentiment, lastAction, turnCount, status).
TTL: set on every write [OPEN: value after testing].
On completion: validate, write feedback to PostgreSQL in one transaction, then finalise or delete the Redis key. Never delete the key if the transaction failed.

Cache entry definition template (required for every cache):
key | TTL | source of truth | invalidation rule | tenant scope

---

## 7. HikariCP settings to start with (tune in Sprint 5)

```text
maximumPoolSize     = 10   (bounded, tune after load test)
minimumIdle         = 2
connectionTimeout   = 3000 ms  (exhaustion returns a controlled error)
idleTimeout         = 600000 ms
maxLifetime         = 1800000 ms
leakDetectionThreshold = 20000 ms
```
These are starting points only. The spec requires final values to come from load testing.

---

## 8. Phase 1 checklist

- [ ] Role enum and matrix approved
- [ ] Redis key format decided
- [ ] Flyway migrations V1 to V8 written
- [ ] All tenant-owned tables have tenant_id NOT NULL and tenant-aware indexes
- [ ] phone/email encrypted, phone_hash lookup works
- [ ] Unique constraint blocks duplicate external_message_id per tenant
- [ ] One ACTIVE conversation per customer enforced
- [ ] Seed data for two tenants (A and B) for isolation tests
- [ ] EXPLAIN checked on tenant list queries, no N+1 in repositories
- [ ] db-schema.md written and approved
