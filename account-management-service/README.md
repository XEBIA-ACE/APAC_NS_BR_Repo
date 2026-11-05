# Account Management Service (scaffold)

Backend service scaffold for story **SCAFFOLD-SVC-001** (spec: `specs/SCAFFOLD-SVC-001/` on the ACE spec branch).
Only operational readiness is implemented: project skeleton, environment-based configuration, a health check and a
PostgreSQL connection helper. No Account Management business logic, authentication or OAuth flows are included.
See [`SCAFFOLD_NOTES.md`](SCAFFOLD_NOTES.md) for assumptions and open gaps.

## Stack

Java 21, Spring Boot 3.3 (Spring MVC), Spring Data JPA + PostgreSQL, Flyway, Spring Security 6,
Spring Cache (Redis), springdoc-openapi (Swagger UI), Resilience4j, Spring Boot Actuator.

## Layout

| Path | Purpose | Task |
|---|---|---|
| `pom.xml` | Dependency manifest, Java 21 enforcer, Spotless (google-java-format) | T001 |
| `src/main/java/.../AccountManagementServiceApplication.java` | Entrypoint | T001 |
| `src/main/resources/application.yml` | Environment-variable driven configuration | T002 |
| `src/main/java/.../config/SecurityConfig.java` | Public health/docs, everything else 401 | T003 |
| `src/main/java/.../database/DatabaseConnectionHelper.java` | Connectivity check + log-safe connection info | T004 |
| `src/test/...` | AC-1 verification (startup + health check) | T005 |

## Configuration

All settings come from environment variables (see [`.env.example`](.env.example)). Defaults are non-secret
local placeholders; `DB_PASSWORD` and `REDIS_PASSWORD` have no default.

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | built from `DB_HOST`/`DB_PORT`/`DB_NAME` | Full JDBC URL override |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `account_management` | PostgreSQL location |
| `DB_USERNAME` / `DB_PASSWORD` | `account_management` / _(empty)_ | PostgreSQL credentials |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` / `DB_CONNECTION_TIMEOUT_MS` | `10` / `2` / `10000` | Hikari pool |
| `FLYWAY_ENABLED` | `true` | Run Flyway on startup (no migrations yet) |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_SSL_ENABLED` | `localhost` / `6379` / _(empty)_ / `false` | Redis |
| `CACHE_TYPE` / `CACHE_TTL` | `redis` / `10m` | Spring Cache provider and default TTL |
| `HEALTH_REDIS_ENABLED` | `true` | Include Redis in the health check |
| `MANAGEMENT_ENDPOINTS_INCLUDE` | `health,info` | Exposed actuator endpoints |
| `OPENAPI_ENABLED` | `true` | Serve `/v3/api-docs` and `/swagger-ui.html` |
| `LOG_LEVEL` | `INFO` | Root log level |

## Run locally

```bash
cd account-management-service
cp .env.example .env              # set DB_PASSWORD
set -a && . ./.env && set +a
docker compose up -d --wait       # PostgreSQL 16 + Redis 7
mvn spring-boot:run
curl -i http://localhost:8080/actuator/health   # 200 {"status":"UP",...}
```

## Health check

`GET /actuator/health` returns `200 {"status":"UP"}` when the service and its datastores are up
(`503` otherwise). Component details are never exposed. Kubernetes-style probes are available at
`/actuator/health/liveness` and `/actuator/health/readiness`.

## Build, test, format

```bash
mvn verify            # compiles, runs tests (test profile: in-memory H2, no Redis), spotless:check
mvn spotless:apply    # auto-format Java sources and pom.xml
```
