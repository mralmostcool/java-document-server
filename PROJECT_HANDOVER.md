# PROJECT HANDOVER

## Project Identity & High-Level Decisions
- **Project Name:** `java-document-server`
- **Application ID:** `java-document-server`
- **Package Namespace:** `pdfserver` (legacy namespace retained; to be refactored in future tasks)
- **JWT Architecture Decision:** JWT authentication will use Spring Security's OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server` backed by Nimbus), **not** JJWT or custom token parsing. Deferred to subsequent authentication task.
- **Branch Policy:** Strictly work and push on branch `dev`. Direct pushes to `main`/`master` are disallowed.

---

## Baseline Architecture & Components

### 1. Technology Stack
- **Runtime:** Java 21 LTS (Eclipse Temurin 21)
- **Framework:** Spring Boot 4.1.2 (Spring Framework 7.0.10, Spring Security 7.1.2)
- **Persistence & Migrations:** Spring Data JPA with Hibernate ORM 7.4.13.Final, Flyway 11 / PostgreSQL connector (`flyway-database-postgresql`), HikariCP
- **Cache & Key-Value Store:** Redis (Lettuce client 7.1.2, Redis 7.4.12)
- **JSON Serialization:** Jackson 3 (`tools.jackson.core:jackson-databind:3.1.7`) for Redis serialization; Jackson 2 (`com.fasterxml.jackson.core:jackson-databind:2.21.7`) for web/actuator serialization
- **API Documentation:** Springdoc OpenAPI 3.2.0 (Swagger UI + Scalar), restricted strictly to the `dev` profile
- **Frontend Container:** Next.js (Node.js 22 Alpine)

### 2. Security & Error Handling
- **Stateless Security Filter Chain:** Disables CSRF, enforces stateless sessions, wires `JsonAuthenticationEntryPoint` and `JsonAccessDeniedHandler`.
- **401 Unauthorized:** Unauthenticated requests to protected endpoints return standard `ErrorResponse` with HTTP 401 and tracking correlation ID.
- **403 Forbidden:** Access-denied events return standard `ErrorResponse` with HTTP 403.
- **Public Routes:**
  - `/api/v1/auth/**`
  - `/actuator/health` and `/actuator/health/**`
  - `/actuator/info`
  - Under `dev` profile only: `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/scalar/**`, `/scalar`
- **Restricted Actuator:** `/actuator/env`, `/actuator/mappings`, `/actuator/metrics` require authentication. Actuator web endpoints exposed by default: `health,info` (`mappings` exposed only in `dev`). Health details (`components`, `details`) hidden from anonymous requests (`show-details: when_authorized`).
- **Web Exception Mapping:**
  - `NoResourceFoundException` -> 404 `ErrorResponse`
  - `HttpRequestMethodNotSupportedException` -> 405 `ErrorResponse`
  - `HttpMessageNotReadableException`, `TypeMismatchException`, `MethodArgumentNotValidException` -> 400 `ErrorResponse`
  - General `Exception` -> 500 `ErrorResponse` without exposing internal stack traces.

### 3. Container & Deployment Architecture
- **Docker Compose Services:**
  - `java-document-server-postgres`: PostGIS/Postgres 17-3.5-alpine, bound to `127.0.0.1:5432`. Healthcheck via `pg_isready`.
  - `java-document-server-redis`: Redis 7-alpine, bound to `127.0.0.1:6379`. Protected via `--requirepass ${REDIS_PASSWORD}`. Healthcheck via authenticated `redis-cli -a ping`.
  - `java-document-server-backend`: Spring Boot multi-stage Alpine build, bound to `127.0.0.1:8080`. Non-root `appuser`. Entrypoint uses `exec java`. Healthcheck validates actuator health.
  - `java-document-server-frontend`: Next.js standalone build on Node 22 Alpine, bound to port 3000.
- **Fail-Fast Environment:** Compose requires mandatory environment variables `${POSTGRES_DB:?...}`, `${POSTGRES_USER:?...}`, `${POSTGRES_PASSWORD:?...}`, `${REDIS_PASSWORD:?...}`, `${SPRING_PROFILES_ACTIVE:?...}` with no hardcoded passwords.
- **Storage Directory:** `./storage/` is git-ignored, persistent storage for Postgres and Redis bind mounts.

### 4. Code Inventory
- `pdfserver.PdfserverApplication`: Boot entrypoint
- `pdfserver.common.error.ErrorResponse`: Canonical JSON error record
- `pdfserver.common.error.GlobalExceptionHandler`: Global exception advice for 400, 404, 405, and 500
- `pdfserver.common.logger.CorrelationIdFilter`: MDC and header correlation ID tracking
- `pdfserver.common.logger.ApiEndpointLogger`: Startup endpoint banner logger (`@Profile("dev")`)
- `pdfserver.config.JsonAuthenticationEntryPoint`: 401 JSON authentication entry point
- `pdfserver.config.JsonAccessDeniedHandler`: 403 JSON access denied handler
- `pdfserver.config.JwtAuthenticationFilter`: Request filter hook for incoming authentication
- `pdfserver.config.RedisConfig`: Configures Jackson 3 `GenericJacksonJsonRedisSerializer` and primary `ObjectMapper`
- `pdfserver.config.SecurityConfig`: Spring Security filter chain and CORS configuration
- `pdfserver.PdfserverApplicationTests`: Context boot test
- `pdfserver.SecurityAndActuatorTests`: Integration tests verifying 401/404 JSON, actuator access control, and health exposure
- `pdfserver.config.RedisSerializerTest`: Verification test for `GenericJacksonJsonRedisSerializer` round-tripping Java `Instant`
