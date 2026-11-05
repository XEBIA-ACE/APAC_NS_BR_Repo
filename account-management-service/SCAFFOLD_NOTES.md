# SCAFFOLD-SVC-001 — assumptions and open gaps

These are implementation choices made where the approved spec was silent. They are **not** approved requirements.

## Assumptions

- **Service name / location**: the scaffold lives in `account-management-service/` (package
  `com.xebia.ace.accountmanagement`), following AC-1's "Account Management Service".
- **Spring MVC** was chosen over WebFlux (blocking JPA/JDBC stack).
- **Flyway** was chosen over Liquibase. No migrations are included; Flyway only validates connectivity
  and creates its schema-history table.
- **Health endpoint**: Spring Boot Actuator `GET /actuator/health` (plus liveness/readiness probes).
  Details/components are hidden (`show-details: never`). Only `health` and `info` are exposed by default.
  Redis participates in the health check (disable with `HEALTH_REDIS_ENABLED=false`).
- **Actuator** and **spring-boot-starter-validation** were added beyond the listed stack as standard
  operational/support libraries; H2 is a test-only dependency so tests run without external services.
- **Security**: a baseline Spring Security 6 filter chain permits only health/info and OpenAPI docs;
  all other requests return 401. No authentication mechanism is configured, and the default generated
  in-memory user is disabled so no password is logged.
- **Linting/formatting**: no repo conventions existed; Spotless with google-java-format (bound to `verify`),
  maven-enforcer (Java 21+, Maven 3.9+) and a root `.editorconfig` were added.
- **Resilience4j** is on the classpath for future external calls; no circuit breakers are configured yet.
- **Secrets**: credentials are read from environment variables (`DB_PASSWORD`, `REDIS_PASSWORD`) with no
  defaults. JDBC URLs are masked before logging. Integration with a secret manager is not in scope.

## Unresolved gaps

- **AC-1 technology conflict**: AC-1 text names Node.js / Express.js / OAuth 2.0 / JWT, while the
  implementation constraints mandate Java 21 / Spring Boot 3.x. Per the implementing-agent instructions the
  Java stack was implemented; the Node.js reference is treated as architectural background only.
- **AC-1 responsibilities** (authentication/authorization, account registration, OAuth 2.0 flows, token
  generation/validation) are out of scope for this scaffold and are not implemented.
- **Architecture artifacts**: AC-1 asks to ground the work in the service's C3/C4 diagrams and API spec "on file
  in Architecture"; these were not available in the repository or the spec bundle.
- **"Training Management Core Service" context** is mentioned in the instructions but not in the spec; the
  relationship between it and the Account Management Service is undefined.
- No user scenarios, API/event/data contracts, migrations or state transitions are defined by the spec.
