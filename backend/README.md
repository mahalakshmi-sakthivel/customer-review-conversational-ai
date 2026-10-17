# Customer Review Conversational AI — Backend

Backend foundation service for the Customer Review Conversational AI platform. Built with Java 21 and Spring Boot.

---

## 1. Tech Stack & Versions

- **Java:** 21 (LTS)
- **Spring Boot:** 4.1.1 (GA release supporting Java 21)
- **Build Tool:** Apache Maven with Maven Wrapper (`.\mvnw.cmd`)
- **Database:** PostgreSQL 16
- **Database Migrations:** Flyway 12 (`flyway-core`, `flyway-database-postgresql`)
- **Connection Pool:** HikariCP
- **ORM:** Spring Data JPA / Hibernate 7
- **Security:** Spring Security (Stateless, CSRF disabled for JWT API)
- **JWT:** JJWT 0.12.6 (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- **API Documentation:** SpringDoc OpenAPI 3.1.1 (`springdoc-openapi-starter-webmvc-ui`)

---

## 2. Environment Variables

All sensitive credentials and connection URLs are injected via environment variables. Create a `.env` file at the repository root based on `.env.example`:

| Variable | Description | Example (Local Dev) |
|---|---|---|
| `APP_ENV` | Application environment / active profile | `dev` |
| `DB_URL` | PostgreSQL JDBC Connection URL | `jdbc:postgresql://localhost:5432/reviewai` |
| `DB_USERNAME` | PostgreSQL database username | `postgres` |
| `DB_PASSWORD` | PostgreSQL database password | `postgres` (or blank if trust auth) |
| `JWT_SECRET` | Secret key for JWT signature (256-bit base64) | `dGhpcy1pcy1hLXNlY3JldC1qd3Qta2V5LWZvci10ZXN0aW5nLTI1NmJpdHM=` |
| `ENCRYPTION_KEY` | AES-GCM encryption key (Base64) | `dGhpcy1pcy1hbi1lbmNyeXB0aW9uLWtleS1mb3ItdGVzdA==` |

---

## 3. Quickstart Guide (Windows PowerShell)

### Step 3.1: Start PostgreSQL

#### Option A: Using Docker Compose (from repo root)
```powershell
docker compose up -d postgres
```

#### Option B: Using Local Native PostgreSQL
Ensure PostgreSQL is running on port 5432 and the `reviewai` database exists:
```powershell
# Verify connection
& "pg_isready" -h localhost -p 5432
```

### Step 3.2: Export Environment Variables in PowerShell

```powershell
$env:APP_ENV="dev"
$env:DB_URL="jdbc:postgresql://localhost:5432/reviewai"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD=""  # Or your PostgreSQL password
$env:JWT_SECRET="dGhpcy1pcy1hLXNlY3JldC1qd3Qta2V5LWZvci10ZXN0aW5nLTI1NmJpdHM="
$env:ENCRYPTION_KEY="dGhpcy1pcy1hbi1lbmNyeXB0aW9uLWtleS1mb3ItdGVzdA=="
```

### Step 3.3: Build & Verify Project

From the `backend/` directory:
```powershell
.\mvnw.cmd clean verify
```

This runs the compilation, applies the Flyway `V1__create_tenant.sql` migration, and executes the suite of verification tests.

### Step 3.4: Run the Application

```powershell
.\mvnw.cmd spring-boot:run
```

The application starts on `http://localhost:8080`.

---

## 4. Endpoint Verification

| Path | Method | Expected Status | Description |
|---|---|---|---|
| `/actuator/health` | GET | `200 OK` | Returns `status: "UP"` with PostgreSQL database component `status: "UP"`. |
| `/actuator/info` | GET | `200 OK` | Actuator info endpoint. |
| `/swagger-ui/index.html` | GET | `200 OK` | Interactive Swagger UI documentation. |
| `/v3/api-docs` | GET | `200 OK` | OpenAPI 3.1 specification JSON. |
| `/api/v1/customers` | GET | `401 Unauthorized` | Protected API endpoint (stateless JWT authentication required). |
| Any other path | GET | `401 Unauthorized` | Secured against unauthorized access. |

### Verification Commands

```powershell
# 1. Health check (includes database status)
curl.exe -i http://localhost:8080/actuator/health

# 2. OpenAPI specification
curl.exe -i http://localhost:8080/v3/api-docs

# 3. Swagger UI page
curl.exe -i http://localhost:8080/swagger-ui/index.html

# 4. Protected endpoint test (returns 401 Unauthorized)
curl.exe -i http://localhost:8080/api/v1/customers
```

---

## 5. Database Schema & Flyway

- Flyway migrations are located in `src/main/resources/db/migration/`.
- `V1__create_tenant.sql` creates the `tenant` table with UUID primary key, unique constraints on `name` and `evolution_instance_name`, status, and UTC timestamps.
- Flyway tracks applied migrations in `flyway_schema_history`.
- Hibernate DDL validation is set to `spring.jpa.hibernate.ddl-auto=validate`.
