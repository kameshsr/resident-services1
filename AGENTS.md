# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

MOSIP Resident Services is a Spring Boot application (v1.4.0-SNAPSHOT) that provides self-service APIs for residents to manage their identity (UIN/VID) through the [Resident UI](https://github.com/mosip/resident-ui/). It runs on port `8099` at context path `/resident/v1`.

- Java 21, Maven 3.9.6
- PostgreSQL 16 (runtime), H2 (tests)
- Spring Boot 3.x, Spring Data JPA, SpringDoc OpenAPI (Swagger at `/resident/v1/swagger-ui/index.html`)
- External config via Spring Cloud Config Server

## Build & Run Commands

```bash
# Build (skip GPG signing for local dev)
cd resident
mvn clean install -Dmaven.javadoc.skip=true -Dgpg.skip=true

# Build skipping tests
mvn clean install -Dmaven.javadoc.skip=true -Dgpg.skip=true -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -pl resident-service -Dtest=ResidentServiceImplTest

# Run a single test method
mvn test -pl resident-service -Dtest=ResidentServiceImplTest#methodName

# Run the application (after build)
java -jar resident/resident-service/target/resident-service-*.jar

# Build Docker image (from resident/resident-service/)
docker build -t resident-service .
```

## Module Structure

```
resident-services-1/
├── resident/                        # Maven parent (resident-parent, pom.xml)
│   └── resident-service/            # The single deployable Spring Boot service
├── api-test/                        # Functional/API tests (TestNG-based)
├── db_scripts/mosip_resident/       # DDL scripts for initial DB setup
├── db_upgrade_scripts/              # Migration scripts for upgrades
├── deploy/                          # Kubernetes / Helm deployment artifacts
└── helm/resident/                   # Helm chart
```

All application code lives in `resident/resident-service/src/main/java/io/mosip/resident/`.

## Application Package Layout

| Package | Purpose |
|---|---|
| `controller/` | REST controllers — one per feature domain |
| `service/` + `service/impl/` | Service interfaces and their implementations |
| `repository/` | Spring Data JPA repositories (`ResidentTransactionRepository` is the most important) |
| `entity/` | JPA entities; `ResidentTransactionEntity` is the central audit/event record |
| `constant/` | Enums: `RequestType`, `ApiName`, `ResidentErrorCode`, `EventStatus*`, `TemplateType` |
| `dto/` | Request/response DTOs exchanged with callers and downstream services |
| `util/` | Stateless helpers; `ResidentServiceRestClient` wraps all outbound REST calls |
| `helper/` | `CredentialStatusUpdateHelper` — shared logic for status updates and notifications |
| `batch/` | `CredentialStatusUpdateBatchJob` — scheduled polling of credential status |
| `validator/` | Input validators invoked from controllers |
| `filter/` | Servlet filters: `LoggingFilter`, `WebsubCallbackRequestDecoratorFilter` |
| `aspect/` | AOP aspects for DB logging |
| `config/` | Spring `@Configuration` classes (filters, Swagger, logger) |

## Key Architectural Concepts

### Request Lifecycle & `ResidentTransactionEntity`
Every resident self-service operation creates a row in `resident_transaction` (via `ResidentTransactionEntity`). The `event_id` is the primary key returned to the caller; `request_type_code` matches a `RequestType` enum value; `status_code` progresses through `EventStatusInProgress` → `EventStatusSuccess` / `EventStatusFailure` values.

### `RequestType` Enum
`RequestType` (in `constant/RequestType.java`) is the registry of all supported service types (e.g. `GENERATE_VID`, `UPDATE_MY_UIN`, `ORDER_PHYSICAL_CARD`). Each entry carries:
- Lambda references into `TemplateUtil` for generating acknowledgement and notification template variables.
- An optional `preUpdateInBatchJob` hook for side-effects on status transitions (e.g. `ORDER_PHYSICAL_CARD` fetches a tracking ID on success).

### Outbound Calls via `ResidentServiceRestClient`
All HTTP calls to other MOSIP kernel/platform services go through `ResidentServiceRestClient`. Target URLs are resolved via the `ApiName` enum, which is mapped to property keys in `application-default.properties` / `resident-default.properties` fetched from the config server.

### Credential Status Updates (Two Paths)
Async credential requests (SHARE_CRED_WITH_PARTNER, ORDER_PHYSICAL_CARD, etc.) are updated by two parallel mechanisms:
1. **WebSub push** — `WebSubCredentialStatusUpdateController` → `WebSubCredentialStatusUpdateServiceImpl` receives real-time events.
2. **Batch polling** — `CredentialStatusUpdateBatchJob` (`@Scheduled`) polls `CREDENTIAL_STATUS_URL` for transactions still in in-progress states.

Both paths delegate final DB write + notification dispatch to `CredentialStatusUpdateHelper`.

### Configuration
The app uses Spring Cloud Config Server. Local development overrides live in `src/main/resources/application-local.properties`. The `bootstrap.properties` sets `spring.cloud.config.uri` (defaults to `localhost`) and `spring.cloud.config.name=application,resident`.

### WebSub Callbacks
Callbacks from the MOSIP WebSub hub arrive at `/callback/*` and are pre-processed by `WebsubCallbackRequestDecoratorFilter` (enabled by default) to allow the request body to be read multiple times.

## Database

Tables are in the `resident` schema. Initialize from `db_scripts/mosip_resident/ddl/`:
- `resident_transaction.sql` — main event/audit table
- `otp_transaction.sql`
- `resident_session.sql`
- `resident_user_actions.sql`
- `resident_grievance_ticket.sql`

## Testing

Tests use JUnit 4 + Mockito + PowerMock with Spring Boot Test. The test bootstrap class is `ResidentTestBootApplication`. H2 is used in-memory for tests.

Sonar coverage excludes `constant/`, `config/`, `dto/`, `entity/`, `exception/`, `repository/` — focus unit test coverage on `service/impl/` and `util/`.
