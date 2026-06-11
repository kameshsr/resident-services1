# Resident Services Local Development Setup Guide

This guide walks you through setting up the Resident service locally **without** standing up the full MOSIP platform. All external MOSIP dependencies (database, config server, authmanager, keymanager, masterdata, ID repository, IDA internal, PMS, regproc, WebSub, notification, audit, object store) are replaced with lightweight Docker containers provided in the `local-dev-setup/docker-compose/` directory.

The approach mirrors the IDA local dev setup (`id-authentication/authentication/local-dev-setup`): PostgreSQL + Spring Cloud Config Server + a single WireMock container that stubs every external MOSIP API, plus MinIO for the object store.

> **Note:** This setup uses the **same host ports** as the IDA local setup (5455, 51000, 8082). Run only one of the two stacks at a time, or stop the IDA containers first (`docker compose down` in the IDA `docker-compose` folder).

---

## Prerequisites

Install the following tools before proceeding:

| Tool | Version |
|------|---------|
| Git | Latest |
| Java | 21 |
| Maven | 3.9.6 |
| Docker + Docker Compose | Latest |

---

## 1. Clone the Repositories

Clone both repositories from the MOSIP GitHub organisation. Use the `develop` branch for both.

```bash
git clone -b develop https://github.com/mosip/mosip-config
git clone -b develop https://github.com/mosip/resident-services
```

---

## 2. Build the Project

From the root of the `resident-services` repository, build all modules. The `-Dgpg.skip=true` flag skips GPG signing and `-Dmaven.javadoc.skip=true` skips javadoc generation, neither of which is required in a local environment.

```bash
cd resident-services/resident
mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true
```

> Unlike the IDA services, the resident service already declares `kernel-auth-adapter` as a dependency — **no `pom.xml` change is needed**.

---

## 3. Configure `mosip-config`

Navigate to your cloned `mosip-config` folder and edit the following property files.

### 3.1 `application-default.properties`

Update these four URLs to point to the local WireMock mock service (port `8082`). If you have already done this for the IDA local setup, skip this step — the values are the same.

| Property | Old Value | New Value |
|----------|-----------|-----------|
| `mosip.kernel.masterdata.url` | `http://masterdata.kernel` | `http://localhost:8082` |
| `mosip.kernel.notification.url` | `http://notifier.kernel` | `http://localhost:8082` |
| `mosip.kernel.otpmanager.url` | `http://otpmanager.kernel` | `http://localhost:8082` |
| `mosip.websub.url` | `http://websub.websub` | `http://localhost:8082` |

> All remaining URL overrides are made in `resident-default.properties` (next section) so that other MOSIP modules using `application-default.properties` are not affected.

---

### 3.2 `resident-default.properties`

#### Add these new properties

```properties
# --- Local dev: route all external MOSIP services to WireMock (8082) ---
mosip.kernel.authmanager.url=http://localhost:8082
mosip.kernel.keymanager.url=http://localhost:8082
mosip.kernel.auditmanager.url=http://localhost:8082
mosip.kernel.syncdata.url=http://localhost:8082
mosip.kernel.ridgenerator.url=http://localhost:8082
mosip.idrepo.identity.url=http://localhost:8082
mosip.idrepo.vid.url=http://localhost:8082
mosip.idrepo.credrequest.generator.url=http://localhost:8082
mosip.idrepo.credential.service.url=http://localhost:8082
mosip.pms.partnermanager.url=http://localhost:8082
mosip.regproc.status.service.url=http://localhost:8082
mosip.regproc.transaction.service.url=http://localhost:8082
mosip.packet.receiver.url=http://localhost:8082
mosip.ida.internal.url=http://localhost:8082
mosip.digitalcard.service.url=http://localhost:8082

# --- Local dev: self URLs (resident runs on 8099) ---
mosip.api.internal.url=http://localhost:8099
mosip.api.public.url=http://localhost:8099
mosip.resident.url=http://localhost:8099

# --- Local dev: IAM / esignet (mocked) ---
mosip.esignet.host=localhost
mosip.iam.base.url=http://localhost:8082/v1/esignet

# --- Local dev: literal values for unresolved placeholders ---
mosip.resident.client.secret=a
resident.oidc.clientid=mosip-resident-oidc-client
resident.captcha.site.key=a
resident.captcha.secret.key=a
resident.websub.authtype.status.secret=a
resident.websub.auth.transaction.status.secret=a
resident.websub.credential.status.update.secret=a
resident.websub.regproc.workflow.complete.secret=a
```

#### Change these existing properties

| Property | Old Value | New Value |
|----------|-----------|-----------|
| `mosip.resident.database.hostname` | `${mosip.database.hostname.override:postgres-postgresql.postgres}` | `localhost` |
| `mosip.resident.database.port` | `${mosip.database.port.override:5432}` | `5455` |
| `javax.persistence.jdbc.user` | `residentuser` | `postgres` |
| `javax.persistence.jdbc.password` | `${db.dbuser.password}` | `mosip123` |
| `mosip.resident.virus-scanner.enabled` | `true` | `false` |
| `mosip.kernel.tokenid.uin.salt` | `${mosip.kernel.uin.salt}` | `zHuDEAbmbxiUbUShgy6pwUhKh9DE0EZn9kQDKPPKbWscGajMwf` |
| `mosip.kernel.tokenid.partnercode.salt` | `${mosip.kernel.partnercode.salt}` | `yS8w5Wb6vhIKdf1msi4LYTJks7mqkbmITk2O63Iq8h0bkRlD0d` |
| `object.store.s3.url` | `http://minio.minio:9000` | `http://localhost:9000` |
| `object.store.s3.accesskey` | `${s3.accesskey}` | `minioadmin` |
| `object.store.s3.secretkey` | `${s3.secretkey}` | `minioadmin` |
| `object.store.s3.region` | `${s3.region}` | `us-east-1` |
| `auth.server.admin.issuer.domain.validate` | `true` | `false` |

> **Virus scanner:** disabled because no ClamAV container is included; document upload would otherwise fail when scanning.
> **Salts:** same literal values used by the IDA local setup, so token IDs stay consistent if you later run both setups against shared data.

---

## 4. Start Docker Services

The `local-dev-setup/docker-compose/` directory contains a Docker Compose file that starts the following services:

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL | `5455` | Resident database `mosip_resident` (pre-seeded via `init.sql` from `db_scripts/mosip_resident`) |
| Config Server | `51000` | Spring Cloud Config Server serving `mosip-config` |
| WireMock (mock-service) | `8082` | Mocks authmanager, keymanager, masterdata, notification, IDA internal, ID repo, PMS, regproc, syncdata, digital card, RID generator, credential services, audit, WebSub, and keycloak/esignet token endpoints |
| MinIO (object-store) | `9000` (API), `9001` (console) | S3-compatible object store used for resident document storage (credentials `minioadmin`/`minioadmin`) |

### Configure the Config Server volume

Before starting, open `docker-compose/docker-compose.yml` and update the config-server volume mount to the **absolute path** of your cloned `mosip-config` directory:

```yaml
volumes:
  - /absolute/path/to/mosip-config:/mosip-config
```

### Start the services

```bash
cd resident/local-dev-setup/docker-compose
docker compose up -d
```

### Verify all services are running

```bash
docker compose ps
```

All four services (`database`, `config-server`, `mock-service`, `object-store`) should show status `running` or `healthy`.

Quick smoke checks:

```bash
# Config server serves the resident properties
curl http://localhost:51000/config/resident/default/master

# WireMock is up and stubs are loaded
curl http://localhost:8082/__admin/mappings

# MinIO console
# open http://localhost:9001 (minioadmin / minioadmin)
```

---

## 5. Start the Resident Service

### 5.1 Update `bootstrap.properties`

Open `resident/resident-service/src/main/resources/bootstrap.properties` and apply the following changes:

| Property | Old Value | New Value |
|----------|-----------|-----------|
| `spring.profiles.active` | `mz` | `default` |
| `spring.cloud.config.label` | `develop` | `master` |
| `spring.cloud.config.uri` | `localhost` | `http://localhost:51000/config` |

Also **add** these properties (they may not exist yet):

```properties
keycloak.external.url=http://localhost:8082
keycloak.internal.url=http://localhost:8082
spring.cloud.loadbalancer.enabled=false
```

> `spring.cloud.loadbalancer.enabled=false` is required to prevent Spring Cloud LoadBalancer from intercepting outbound HTTP calls to `localhost`. Without it, the LoadBalancer treats `localhost` as a service-registry name and returns a 503 when downstream calls are made.
>
> `keycloak.internal.url` feeds `mosip.keycloak.issuerUrl` (`${keycloak.internal.url}/auth/realms/mosip`), which WireMock stubs at `POST /auth/realms/mosip/protocol/openid-connect/token`.

### 5.2 Run the Resident Service

#### Option A — Maven (recommended during development)

```bash
cd resident/resident-service
mvn spring-boot:run
```

#### Option B — JAR

Since `bootstrap.properties` was modified after the initial build, rebuild the module first to include the updated configuration in the JAR:

```bash
cd resident/resident-service
mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true
java -jar target/resident-service-*.jar
```

#### Option C — IntelliJ IDEA

1. Open the `resident-services/resident` directory as a Maven project (**File → Open**).
2. Wait for IntelliJ to finish importing and indexing all modules.
3. Navigate to `resident-service/src/main/java` and open the main application class (`ResidentBootApplication.java`).
4. Click the **Run** button (green triangle) next to the class declaration, or right-click the file and choose **Run 'ResidentBootApplication'**.
5. To persist the run configuration, open **Run → Edit Configurations**, select the generated configuration, and verify the **Working directory** is set to the `resident-service` module root.

#### Option D — VS Code

1. Install the **Extension Pack for Java** (`vscjava.vscode-java-pack`) if not already installed.
2. Open the `resident-services/resident` folder (**File → Open Folder**).
3. Wait for the Java Language Server to finish building the workspace.
4. Open `resident-service/src/main/java/io/mosip/resident/ResidentBootApplication.java`.
5. Click **Run** above the `main` method (shown by the CodeLens link), or press `F5` with the file open.
6. VS Code will auto-generate a launch configuration in `.vscode/launch.json`; you can edit it to add any `-D` JVM arguments if needed.

The service starts on port **8099** with context path `/resident/v1`.

Swagger UI is available at:
```
http://localhost:8099/resident/v1/swagger-ui/index.html
```

---

## 6. Verify the Setup

1. **Health check:**
   ```bash
   curl http://localhost:8099/resident/v1/actuator/health
   ```
2. **Swagger UI** loads at the URL above.
3. **Database:** the service connects to `mosip_resident` on `localhost:5455` (schema `resident` with tables `otp_transaction`, `resident_transaction`, `resident_grievance_ticket`, `resident_user_actions`, `resident_session`).
4. **WebSub subscriptions:** about 60 seconds after startup (`subscriptions-delay-on-startup`), the service registers and subscribes to its topics against the mock hub. Watch the WireMock log:
   ```bash
   docker compose logs -f mock-service
   ```
   You should see `POST /hub/` requests for topics such as `AUTH_TYPE_STATUS_UPDATE_ACK`, `AUTHENTICATION_TRANSACTION_STATUS`, `CREDENTIAL_STATUS_UPDATE`, and `REGISTRATION_PROCESSOR_WORKFLOW_COMPLETED_EVENT`.

---

## Known Limitations

- **Mocked responses are canned.** WireMock returns static success payloads (see `docker-compose/wiremock/mappings/resident-mocks.json`). Flows that need real cryptography (keymanager encrypt/decrypt, PDF signing) or real identity data return dummy values — good enough to exercise the resident service code paths, not for end-to-end validation.
- **No real OIDC login.** The full resident login flow requires eSignet/Keycloak. The JWKS endpoint is stubbed with an empty key set, so endpoints protected by user-token validation cannot be called with a real signed token. Use Swagger for unauthenticated endpoints, and unit/integration tests for the rest.
- **No ClamAV.** Virus scanning is disabled via `mosip.resident.virus-scanner.enabled=false`.
- **Port clash with the IDA local setup.** Both stacks use 5455/51000/8082 — run one at a time.

---

## Local Services Reference

| Service | URL |
|---------|-----|
| Config Server | `http://localhost:51000/config` |
| WireMock (mock APIs) | `http://localhost:8082` |
| MinIO (S3 API) | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |
| PostgreSQL | `localhost:5455` |
| Resident Service | `http://localhost:8099/resident/v1` |

---

## Files in `local-dev-setup/`

| File / Directory | Purpose |
|------------------|---------|
| `docker-compose/docker-compose.yml` | Starts all required backing services |
| `docker-compose/init.sql` | Initialises the `mosip_resident` database schema and tables (generated from `db_scripts/mosip_resident`) |
| `docker-compose/wiremock/mappings/resident-mocks.json` | WireMock stubs for all external MOSIP APIs the resident service calls |
| `docker-compose/wiremock/__files/` | Static files served by WireMock (currently empty) |

---

## Troubleshooting

**Config server returns 404 for properties**
Verify the volume path in `docker-compose.yml` points to the correct `mosip-config` directory and that you have edited the right property files. Test with `curl http://localhost:51000/config/resident/default/master`.

**`Could not resolve placeholder ...` at startup**
A `${...}` placeholder in `resident-default.properties` has no value. Re-check section 3.2 — every placeholder listed there (client secret, websub secrets, captcha keys, salts, S3 credentials) must be replaced with a literal value.

**503 errors when calling downstream services**
This is caused by Spring Cloud LoadBalancer treating `localhost` as a service-registry name. Verify `spring.cloud.loadbalancer.enabled=false` is present in `bootstrap.properties` (rebuild the JAR if running Option B).

**Database connection refused**
Confirm the postgres container is running and mapped to host port `5455` (`docker compose ps`), and that `mosip.resident.database.hostname=localhost` / `mosip.resident.database.port=5455` are set.

**Re-seeding the database**
The `init.sql` script only runs on first container creation. To re-seed, recreate the container: `docker compose down && docker compose up -d` (add `-v` if volumes were configured).

**WebSub subscription errors in the service log**
Harmless in local dev — the mock hub accepts every request, and the initializer retries on a schedule (`re-subscription-interval-secs`).
