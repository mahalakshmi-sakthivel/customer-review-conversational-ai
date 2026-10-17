# PHASE 2 — BACKEND IMPLEMENTATION PLAN
Customer Review Conversational AI
Source: Master Spec sections 23 to 29, TRD section 24, PRD section 9.
Rule: BUILD -> TEST -> INTEGRATE -> TEST AGAIN -> MERGE. Never integrate only at the end.

---

## 1. Package structure (Spring Boot, Maven)

```text
backend/src/main/java/com/<org>/reviewai/
  config/        SecurityConfig, RedisConfig, HikariConfig, WebClientConfig, OpenApiConfig
  security/      JwtService, JwtAuthFilter, TenantContext, RoleGuard
  common/        ApiError, GlobalExceptionHandler, TraceIdFilter, PageRequestValidator
  tenant/        Tenant entity, repository, service
  user/          AppUser, AuthController, UserService
  customer/      Customer, CustomerController, CryptoConverter
  lead/          Lead, LeadService
  conversation/  Conversation, Message, ConversationService, ChatController
  feedback/      Feedback, FeedbackController, FeedbackService
  agent/         AgentDecisionEngine, NextAction, ConversationState
  llm/           LlmService (interface), GeminiLlmService, LlmExtractionResponse, PromptBuilder
  state/         RedisConversationStateStore
  cpp/           CppGrpcClient, CppFallbackService
  whatsapp/      EvolutionClient, WebhookController, WebhookValidator, OutreachScheduler
  dashboard/     TenantDashboardService, SuperAdminDashboardService
  audit/         AuditLogService
```

Layer rule: Controller -> Service -> Repository. Controllers never touch repositories. No tenantId parameter is accepted from request bodies or query strings.

---

## 2. Step order

### STEP 0 — Project setup (Sprint 1)
- Create monorepo, branches main and develop, branch naming feature/<name>.
- Spring Boot project with: Web, Security, Data JPA, Validation, Actuator, Redis, Flyway, PostgreSQL driver, springdoc-openapi, Micrometer.
- Create docs/ files: architecture, api-contract, llm-contract, db-schema, security, deployment.
- Freeze contracts: API, error format, LLM JSON, role enum, Redis key, statuses, actions, .proto.
- Exit: contracts approved.

### STEP 1 — Foundation (Sprint 2, week 1)
1. Flyway migrations from phase-1 schema.
2. JPA entities and repositories. Every repository method takes tenantId.
3. HikariCP bounded pool and Micrometer pool metrics.
4. GlobalExceptionHandler returning the frozen error format with traceId.
5. TraceIdFilter putting traceId into MDC and the response header.
Test: app boots, migrations run clean on empty DB, error format matches contract.

### STEP 2 — Security core (Sprint 2, week 1-2)
Detailed in phase-3-security-and-logging.md. Order:
1. BCrypt password hashing and AppUser login.
2. POST /api/v1/auth/login issuing JWT (userId, role, tenantId).
3. JwtAuthFilter and TenantContext.
4. Role-based endpoint rules.
5. Tenant-filtered repositories everywhere.
Test: login 200, bad login 401, wrong role 403, Tenant A cannot read Tenant B.

### STEP 3 — Core APIs with pagination (Sprint 2, week 2)
Endpoints (all under /api/v1):
- GET /customers, GET /customers/{id}
- GET /feedback
- GET /conversations, GET /conversations/{id}
- GET /conversations/{id}/messages (keyset)
- GET /leads
- GET /users (TENANT_ADMIN, own tenant)
Rules enforced in one shared validator: page >= 0, size <= max, sort in whitelist, otherwise 400 or capped.
Test: oversized page rejected or capped, unknown sort field rejected, pagination never crosses tenants.
Postman collection and Swagger up to date.

### STEP 4 — Frontend link (Sprint 2, week 2)
React login, dashboard shell, API service layer, route guards. Integrate with the live backend and run Postman and browser flow together.
Exit Sprint 2: a user authenticates and reaches only authorized tenant resources.

### STEP 5 — Redis state store (Sprint 3, part A)
RedisConversationStateStore with load, update, expire, finalise/delete.
- TTL on every write.
- Use the tenant-scoped key if approved.
- If Redis is down: fail safely, return a fallback reply, never reuse another conversation's state.
Test: round trip, expiry, two conversations never mix.

### STEP 6 — LLM integration (Sprint 3, part B)
- LlmService interface: extract(message, state), generateReply(state, action).
- GeminiLlmService using pooled WebClient, timeout, structured JSON output.
- PromptBuilder with four separated parts: system instructions, state, customer message, output schema.
- Validate response with Jakarta Bean Validation. Invalid output -> controlled retry -> fallback reply. State is not changed on failure.
Test: normal, incomplete, malformed JSON, irrelevant, multi-field, prompt injection.

### STEP 7 — Agent decision engine (Sprint 3, part C)
Deterministic Java. Priority order:
1. opt-out detected -> status OPTED_OUT, stop
2. escalation condition [OPEN: define] -> ESCALATE
3. unclear answer and clarify attempts < limit -> CLARIFY
4. name missing -> ASK_NAME
5. rating missing -> ASK_RATING
6. problem missing -> ASK_PROBLEM
7. suggestion missing -> ASK_SUGGESTION
8. expectation missing -> ASK_EXPECTATION
9. else -> THANK_AND_COMPLETE
Also: negative sentiment -> empathetic reply instruction passed to generateReply. Track clarify attempts per field. Never re-ask a known field.
The LLM nextAction is a suggestion only. The agent overrides it.
Test: table-driven unit tests for every branch.

### STEP 8 — Completion and persistence (Sprint 3)
On THANK_AND_COMPLETE: validate final record -> one DB transaction (feedback + conversation COMPLETED + lead status) -> only then finalise Redis and report success. If the transaction fails, do not report success.

### STEP 9 — ChatController and orchestration (Sprint 3)
POST /api/v1/chat. Flow:
load state -> save inbound message -> (C++ if needed) -> LLM extract -> validate -> agent decide -> LLM reply -> update state -> save outbound message -> respond.
Test: complete multi-turn review with no repeated questions. This is the Sprint 3 gate.

### STEP 10 — C++ gRPC client (Sprint 3, part D)
- Select the operation in Sprint 1 [OPEN]. Suggested: duplicate-message detection or fuzzy similarity.
- .proto in cpp-service/proto. Java gRPC stub, deadline per call, CppFallbackService in pure Java.
- Call C++ only when required. Fall back safely if it fails.
- Benchmark Java vs C++: average, P95, throughput, CPU. Keep C++ only if the numbers or a clear capability justify it.

### STEP 11 — WhatsApp integration (Sprint 4)
- EvolutionClient with pooled HTTP and safe retry.
- WebhookController: validate source (shared secret or API key header), map instance -> tenant, phone_hash -> customer, find or create conversation.
- Idempotency: unique (tenant_id, external_message_id). Duplicate event is acknowledged and ignored.
- Outgoing retry must not send duplicates (store outbound message before send, mark sent after).
- Instance management endpoints.
- OutreachScheduler for proactive first message after eligibility, tenant and opt-out checks [OPEN: eligibility rules].

### STEP 12 — Dashboards (Sprint 4)
- Tenant dashboard: total reviews, completed reviews, average rating, positive and negative counts, common problems, suggestions, conversations, lead status. All tenant-filtered aggregate queries.
- Super admin dashboard: platform-level aggregates only within policy.
- Cache only with defined key, TTL, source of truth, invalidation, tenant scope.
Exit Sprint 4: whole app works end to end locally.

### STEP 13 — Hardening, Docker, deploy (Sprint 5)
- Unit, API, LLM, security, performance tests. Record P50/P95/P99.
- Pool near-exhaustion test, large pagination test. Document final pool and page-size values.
- Dockerfiles, docker-compose, .env.example, Ubuntu deploy, Nginx, HTTPS.

---

## 3. Sprint map

| Sprint | Steps | Gate |
|---|---|---|
| 1 | 0 | Contracts and docs approved |
| 2 | 1 to 4 | Authenticated user sees only own tenant data |
| 3 | 5 to 10 | Multi-turn review with no repeated questions |
| 4 | 11 to 12 | Full flow works locally |
| 5 | 13 | Tests, isolation, Docker, Ubuntu, WhatsApp all pass |

---

## 4. Definition of done (per feature)

- [ ] Code and unit tests
- [ ] API contract and Swagger updated
- [ ] Security and tenant isolation checked
- [ ] Error handling in place
- [ ] Local integration tested
- [ ] PR reviewed, develop integration passes
- [ ] No secrets and no sensitive logs committed

---

## 5. Open items blocking this plan

| Item | Needed by |
|---|---|
| Final role list and matrix | Before Sprint 2 |
| C++ operation | Sprint 1 |
| ESCALATE conditions | Sprint 1 freeze |
| Redis key format | Sprint 1 freeze |
| Eligibility rules | Sprint 4 design |
| State expiry, max page size, pool size | Sprint 5 load test |
| Nginx host vs container | Sprint 5 |
