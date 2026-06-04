# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Build & Run

**Requirements:** JDK 21.0.3, Maven 3.9.6

```bash
# Build and install (skip GPG signing for local dev)
cd resident
mvn install -Dgpg.skip=true

# Build skipping tests
cd resident
mvn install -Dgpg.skip=true -DskipTests

# Run a single test class
cd resident/resident-service
mvn test -Dtest=ResidentServiceImplTest -Dgpg.skip=true

# Run a single test method
cd resident/resident-service
mvn test -Dtest=ResidentServiceImplTest#testMethodName -Dgpg.skip=true
```

The service starts on port `8099` with context path `/resident/v1`. Swagger UI is available at `/resident/v1/swagger-ui.html`.

## Module Structure

```
resident/                   # Parent Maven project (resident-parent)
  resident-service/         # Single deployable Spring Boot module
api-test/                   # Functional API tests (separate Maven project)
db_scripts/mosip_resident/  # PostgreSQL DDL scripts
deploy/                     # Kubernetes/Helm deployment configs
```

The entire application lives in a single Maven module: `resident/resident-service`.

## Architecture

### Spring Cloud Config
The service is config-server-dependent. On startup it fetches properties from a Spring Cloud Config Server. Local overrides live in `application-local.properties`. The key bootstrap properties (`bootstrap.properties`) point to `localhost` config server by default — you must run a Config Server or override `spring.cloud.config.uri` to work locally.

### Controller → Service → Util layering
- **Controllers** (`controller/`) validate requests and delegate to service interfaces
- **Service interfaces + impls** (`service/` + `service/impl/`) contain business logic
- **Util classes** (`util/`) are fine-grained single-responsibility helpers (e.g., `IdentityDataUtil`, `AuditUtil`, `EncryptorUtil`, `ClaimValueUtility`) — there are many of them; prefer finding an existing utility before writing new logic
- **Handler services** (`handler/service/`) deal with packet/registration-processor interactions (UIN card reprint, resident update sync/upload)

### Proxy Controllers pattern
Controllers prefixed `Proxy*` (e.g., `ProxyMasterdataController`, `ProxyPartnerManagementController`) act as pass-through facades that forward requests to downstream MOSIP kernel/infrastructure services. They exist to let the Resident UI make calls through a single authenticated origin rather than directly hitting other services.

### WebSub event handling
Three WebSub subscriber controllers/services handle asynchronous callbacks from the MOSIP platform:
- `WebSubCredentialStatusUpdateController/Service` — credential issuance status
- `WebSubRegprocWorkFlowController/Service` — registration-processor workflow events
- `WebSubUpdateAuthTypeController/Service` — authentication type lock/unlock events

These update the `resident_transaction` table based on platform events.

### Database entities
Five JPA entities map to PostgreSQL tables in the `mosip_resident` schema:
- `ResidentTransactionEntity` — all resident service requests/events (central audit/status table)
- `ResidentSessionEntity` — authenticated session tracking
- `ResidentUserEntity` — per-resident user actions/preferences
- `ResidentGrievanceEntity` — grievance ticket records
- `OtpTransactionEntity` — OTP generation/validation tracking

### Authentication & Security
- Uses MOSIP kernel `auth-adapter` for token-based authentication (OpenID Connect / Keycloak)
- `ResidentFilterConfig` sets up the security filter chain
- `spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER` is required for Spring Boot 3.x compatibility with the existing URL exclusion list in `mosip.service.end-points`

### Batch Job
`CredentialStatusUpdateBatchJob` (`batch/`) periodically polls for pending credential requests and updates their status via the credential service.

### Mock controllers
The `mock/` sub-package contains stub implementations of downstream services used for local development/testing without a full MOSIP stack.

## Configuration
Runtime configuration is externalized to the MOSIP Config Server (see [resident-default.properties](https://github.com/mosip/mosip-config/blob/master/resident-default.properties)). Key properties for local development are in `resident-service/src/main/resources/application-local.properties`.

Required config additions for Spring Boot 3.x:
```properties
hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER
```

## SonarCloud / Code Coverage
Coverage exclusions are defined in the parent `pom.xml` under `sonar.coverage.exclusions` and include `dto`, `entity`, `config`, `exception`, `repository`, and `constant` packages. Run the sonar profile with `mvn verify -Psonar`.