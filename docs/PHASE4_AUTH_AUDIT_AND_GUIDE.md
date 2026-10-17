# Phase 4: JWT Authentication and Authorization

Scope: 4.1 login API, 4.2 JWT + Spring Security, 4.3 role authorization, 4.4 tenant authorization and isolation.

## 0. Verification status (read first)

| Item | Status |
|---|---|
| Code written | Yes |
| Syntax-parsed with javac 21 | Yes. No syntax errors. (Dependency errors only, because jars were not available.) |
| `mvn compile` | **NOT RUN.** Maven Central is unreachable from the authoring environment. |
| `mvn test` (new and existing tests) | **NOT RUN.** No test result exists. Do not treat any test as passing. |
| Flyway V2/V8 against PostgreSQL | **NOT RUN.** |

First action for the backend lead: `./mvnw clean test`, then the manual flow in section 7.

## 1. Codebase audit

Two code bases were uploaded. They are different projects:

| | `Customer_feedback_AI_backend` (used as base) | `customer-review-phase2-merged` (reference only) |
|---|---|---|
| Package | `com.reviewai` | `com.customerreview` |
| Spring Boot | 4.1.1 | 3.5.6 |
| Matches brief (`com.reviewai.config.SecurityConfig`, `...security.TenantContext`) | Yes | No |
| Migrations | V1 tenant only | V1..V9 (app_user = V2, audit_log = V8) |

**Base: existing code and findings**

- `SecurityConfig`: stateless, CSRF off. `/api/v1/tenants/**` was `permitAll` (anyone could create, list or delete tenants). Placeholder `UserDetailsService` that always throws. No JWT filter, no login, no role rules.
- `TenantContext`: ThreadLocal holder, fine. Nothing ever set it.
- `TraceIdFilter`: cleared TenantContext (kept). It accepted any client `X-Trace-Id` verbatim: log injection, and would overflow `audit_log.trace_id VARCHAR(64)`. Fixed (safe-charset, max 64).
- `GlobalExceptionHandler`: handles `AccessDeniedException` (403). Its catch-all `Exception` handler turns framework 404/405 into 500 (existing, not changed; masked for authenticated callers by deny-by-default).
- `pom.xml`: JJWT 0.12.6, Security, JPA, Flyway, H2 (tests) already present. No new dependencies added.
- `application.yml`: `app.jwt.secret` already present. Tests use H2 with `ddl-auto: create-drop` and Flyway disabled, so entities must work on both H2 and PostgreSQL.
- Existing tests use `MockMvcBuilders.webAppContextSetup(...).apply(springSecurity())`; new tests follow that pattern. `TenantControllerTest` builds `new GlobalExceptionHandler()`, so the audit dependency there is optional-injected to keep it working.
- `CORS`: allows all origins (no credentials). Tighten for production (not Phase 4 scope).

**Reference zip: not merged, but relevant**: it already has auth classes, but they trust an `X-Tenant-Id` header (`TenantInterceptorFilter`), use `sendError`, and `JwtAuthenticationFilter` is a `@Component` that does not set tenant context from the token. Do not copy those. Its `V2__create_app_user.sql` and `V8__create_audit_log.sql` collide in name with the ones added here; keep one set when merging.

## 2. Files created / modified (all under `backend/` unless noted)

Created, main:
`user/{Role,UserStatus,AppUser,AppUserRepository,AuthService,AuthController,UserService,UserController,BootstrapSuperAdminRunner}.java`,
`user/dto/{LoginRequest,LoginResponse,MeResponse,UserResponse}.java`,
`security/{JwtService,JwtAuthFilter,TokenClaims,InvalidTokenException,AuthenticatedUser,CurrentUser,RestAuthenticationEntryPoint,RestAccessDeniedHandler,SecurityErrorWriter}.java`,
`audit/{AuditLog,AuditLogRepository,AuditLogService,AuditLogWriter,AuditEventType,AuditOutcome}.java`,
`common/InvalidCredentialsException.java`,
`db/migration/V2__create_app_user.sql`, `db/migration/V8__create_audit_log.sql`.

Modified: `config/SecurityConfig.java` (rewritten in place), `common/GlobalExceptionHandler.java` (+401 handler, +audit on 403), `common/TraceIdFilter.java` (trace-id validation), `tenant/TenantController.java` (+`@PreAuthorize`), `main/resources/application.yml`, `test/resources/application.yml` (bcrypt strength 4), `../.env.example`.

Created, tests: `security/JwtServiceTest.java` (12 tests), `user/AuthSecurityIntegrationTest.java` (~22 tests).

## 3. Design as implemented

- **Login** `POST /api/v1/auth/login`: case-insensitive email lookup with a row lock; BCrypt verify (a dummy hash is checked for unknown emails to equalise timing); disabled, locked, unknown and wrong-password all return the same 401 `Invalid email or password`. Reasons go to `audit_log` only. Failed count / `locked_until` / `last_login_at` updated; counter persists despite the exception (`noRollbackFor`).
- **JWT** HS256 only. Claims: `sub`, `role`, `tenantId` (omitted for SUPER_ADMIN), `iat`, `exp`, `jti`. Rejected: bad signature, other algorithm (including `none`, HS384), missing exp/sub/role/jti, unknown role, tenant role without `tenantId`. Secret must be Base64 of at least 32 bytes or the app fails to start.
- **Filter** `JwtAuthFilter` (not a bean, so it runs once, in the chain). Invalid token leaves the request anonymous with a reason attribute; the entry point answers 401 only on protected URLs, always with the same message, and audits the reason.
- **Authorization**: deny by default (`anyRequest().denyAll()`); URL rules plus `@PreAuthorize`. Method-security denials go through `GlobalExceptionHandler`; URL denials through `RestAccessDeniedHandler`. Both write `ACCESS_DENIED_ROLE`.
- **Tenant isolation**: tenant comes from the validated token (`CurrentUser`), set in `TenantContext` after authentication, cleared in `finally` (plus `TraceIdFilter`). Request params and `X-Tenant-Id` are never read. `UserService` shows the pattern: `findByIdAndTenantId`, 404 for other-tenant ids, `ACCESS_DENIED_TENANT` audited (via a boolean `existsById`, no data returned).
- **Audit events written**: AUTH_LOGIN (success/failure), AUTH_LOCKOUT, AUTH_TOKEN_REJECTED, ACCESS_DENIED_ROLE, ACCESS_DENIED_TENANT. Written in a separate transaction; failure to audit never breaks the request. Sensitive-looking keys are dropped.

## 4. Endpoint / permission table (PROPOSED, pending approval)

| Endpoint | Access |
|---|---|
| POST /api/v1/auth/login | public |
| GET /actuator/health, /v3/api-docs/**, /swagger-ui/** | public |
| GET /api/v1/auth/me | any authenticated |
| /api/v1/tenants/** | SUPER_ADMIN |
| GET /api/v1/users/{id} | TENANT_ADMIN, own tenant only |
| everything else | denied until a rule is added |

Changed from before: `/actuator/info` is no longer public (Phase 3: only health is public).

## 5. Decisions needing approval (nothing here is assumed final)

1. **Role list and matrix**: SUPER_ADMIN / TENANT_ADMIN / OPERATIONS_USER are proposals. Open: may TENANT_ADMIN read its own tenant under `/tenants`? May SUPER_ADMIN read tenant-owned data (`/users`)? Is OPERATIONS_USER read-only on feedback/dashboard?
2. **Super-admin tenant model**: implemented as `tenant_id` NULL + DB CHECK `role='SUPER_ADMIN' OR tenant_id IS NOT NULL`. Confirm, or change the migration before any shared environment.
3. **JWT / refresh policy**: 15-minute access token, no refresh token, no revocation. A disabled user or suspended tenant keeps working until the token expires. Decide on refresh tokens / revocation / per-request status check.
4. **Lockout numbers**: 5 failures, 15 minutes (configurable). Locked and disabled accounts return the generic 401 (no "locked" hint).
5. **Cross-tenant response**: 404 (matches Phase 3), with a 403 alternative if the mentor prefers.
6. **Tenant status at login**: SUSPENDED/DELETED tenants are not blocked. Recommend adding.
7. **Audit retention, alerting, who may view audit entries**: not implemented.
8. **Client IP behind Nginx**: `remoteAddr` is used. Set `server.forward-headers-strategy` and a trusted-proxy rule when deploying.
9. **Swagger/api-docs public in production**: recommend disabling in prod.
10. **Migration numbering**: V2 and V8 follow the Phase 1 plan; V3-V7 are other people's. A DB that already applied V8 will reject a later V3-V7 unless it is reset or `spring.flyway.out-of-order=true`.
11. **Bootstrap SUPER_ADMIN** via env vars is an addition so login can be used at all; approve or replace with a seed migration.
12. `audit_log` append-only is by DB grant (INSERT, SELECT only for the app user). Not set up here.

## 6. Environment variables (names only)

`JWT_SECRET` (Base64, >= 32 bytes: `openssl rand -base64 48`), `JWT_ACCESS_TOKEN_MINUTES` (1-60, default 15), `BCRYPT_STRENGTH` (default 12), `LOGIN_MAX_FAILED_ATTEMPTS` (5), `LOGIN_LOCK_MINUTES` (15), `BOOTSTRAP_SUPER_ADMIN_EMAIL`, `BOOTSTRAP_SUPER_ADMIN_PASSWORD` (>= 12 chars; remove after first start), plus existing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ENCRYPTION_KEY`, `APP_ENV`.

## 7. Integration and verification guide

1. Copy the files listed in section 2 into the main repo (watch the V2/V8 clash if the Phase 2 migrations are merged).
2. `docker compose up -d postgres`, set the env vars from section 6 (including the bootstrap pair), then `./mvnw clean test`. Fix any compile error first; **report real results**.
3. `./mvnw spring-boot:run`. Check Flyway applied V1, V2, V8.
4. Flow:
```bash
# login as super admin
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"<bootstrap email>","password":"<bootstrap password>"}'
# => {"accessToken":"...","tokenType":"Bearer","expiresInSeconds":900,"role":"SUPER_ADMIN",...}
TOKEN=<accessToken>
curl -s localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"
curl -s -X POST localhost:8080/api/v1/tenants -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Tenant A"}'
curl -i localhost:8080/api/v1/tenants                       # 401, no token
```
5. There is no user-management API yet (later phase). To create a TENANT_ADMIN for testing, generate a hash (`python3 -c "import bcrypt;print(bcrypt.hashpw(b'Correct-Horse-9',bcrypt.gensalt(12)).decode())"`) and insert:
```sql
INSERT INTO app_user (id, tenant_id, email, password_hash, role)
VALUES (gen_random_uuid(), '<tenant id>', 'admin-a@example.com', '<hash>', 'TENANT_ADMIN');
```
6. Check: wrong password twice returns identical 401 bodies; 5 wrong attempts lock the account; OPERATIONS_USER on `/api/v1/tenants` returns 403; a Tenant A admin calling `/api/v1/users/<Tenant B user id>` returns 404 and an `ACCESS_DENIED_TENANT` row appears in `audit_log`.
7. Check no logs contain tokens or passwords (`grep -i bearer`, the test password).

## 8. Known risks to verify when it first compiles

- `AuditLog.detail` uses `@JdbcTypeCode(SqlTypes.JSON)` on a `Map`. Needs a Jackson format mapper available to Hibernate on Spring Boot 4; if context startup fails on this, change the column/mapping first.
- `GlobalExceptionHandler` injects the audit service by field, so it stays constructible with `new` in the existing test.
- Spring Boot 4 / Spring Security 7 APIs were used from memory (`UsernamePasswordAuthenticationToken.authenticated`, `requestMatchers(HttpMethod, String)`, `@EnableMethodSecurity`); a compile error here is likely a small signature fix.
- Out of scope and untouched: frontend, pagination/core APIs, refresh tokens, user management, rate limiting.
