# KT Day 2 — Architecture, Code Structure & Database Schema

**Duration:** 2.5 hours | **Audience:** Developers (QA optional)
**KT Giver:** Kamesh | **KT Receiver:** Chetan
**Covers architect focus areas:** architecture & key components · DB schema per table · code structure, patterns, key classes/packages

---

## Agenda

| # | Topic | Time |
|---|-------|------|
| 1 | Architecture overview & key components | 30 min |
| 2 | Code structure & implementation approach | 60 min |
| 3 | Database schema — table-by-table | 40 min |
| 4 | Q&A + homework | 20 min |

---

## 1. Architecture Overview & Key Components

Resident Service is a **single Spring Boot application** (module `resident/resident-service`) that acts as the backend-for-frontend of the Resident UI. Key characteristics:

- **Stateless REST layer** — identity comes from the OIDC token on every request; session bookkeeping is in the DB, not in memory.
- **Orchestrator, not owner** — identity data lives in ID Repository; authentication in IDA; packets in RegProc. This service orchestrates calls and records the resulting events.
- **Every user-visible request becomes a row in `resident_transaction`** — this is the central design idea; history, event tracking, and notifications all hang off it.
- **Async via WebSub** — long-running operations complete through hub callbacks (Day 9).

Component map: 25 controllers → 25+ services (interface + `impl`) → JPA repositories → PostgreSQL (`mosip_resident`), plus external clients to ~12 MOSIP services (see Day 1 diagram).

## 2. Code Structure & Implementation Approach

### Module organization

```
resident/pom.xml                    # parent
└── resident-service/               # the only service module
    └── io.mosip.resident
        ├── controller/             # REST layer, thin — validation + delegation
        ├── service/ + service/impl # business logic; interface-impl pattern
        ├── repository/             # Spring Data JPA
        ├── entity/                 # 5 JPA entities
        ├── dto/                    # request/response wrappers (RequestWrapper/ResponseWrapper)
        ├── validator/              # RequestValidator & friends — called from controllers
        ├── config/                 # DataSource, caching, swagger, filters registration
        ├── filter/                 # LoggingFilter, WebsubCallbackRequestDecoratorFilter, RepeatableStreamHttpServletRequest
        ├── interceptor/            # ResidentEntityInterceptor (audit columns), RestTemplate logging/metrics
        ├── aspect/                 # cross-cutting concerns
        ├── batch/                  # CredentialStatusUpdateBatchJob
        ├── exception/ + handler/   # error codes, custom exceptions, global advice
        ├── constant/               # ResidentErrorCode, TemplateVariablesConstants, etc.
        └── util/ / helper/ / function/  # Utility, IdentityUtil, AuditUtil…
```

### Coding patterns & design principles

- **Controller → Validator → Service → Repository/External call.** Controllers never contain business logic. Follow this when adding new features.
- **Interface + Impl** for every service (`ResidentService` / `ResidentServiceImpl`) — mock-friendly, consistent.
- **RequestWrapper / ResponseWrapper envelope** — all APIs use MOSIP's standard `{id, version, requesttime, request/response, errors[]}` JSON envelope.
- **Error handling:** throw typed exceptions (`ResidentServiceException`, `ApisResourceAccessException`, `InvalidInputException`…) carrying a `ResidentErrorCode`; the global exception handler maps them to the envelope's `errors[]`. Never return raw 500s.
- **Auditing:** `AuditUtil` publishes audit events for each action; `ResidentEntityInterceptor` stamps `cr_by/cr_dtimes/upd_by/upd_dtimes` on entities.
- **External calls:** shared `RestTemplate`/`ResidentServiceRestClient` with logging + metrics interceptors; URLs come from properties, never hardcoded.
- **Config-driven behavior:** allowed auth types, UI schema, notification templates, VID policy — all from mosip-config (Day 9).

### Key classes to know when fixing bugs / adding features

| Where to look | Class(es) |
|---|---|
| Main resident flows | `ResidentServiceImpl` (largest class — auth lock, update UIN, service history) |
| Identity/session context | `IdentityServiceImpl`, `IdentityUtil` (who is logged in, ID attributes) |
| Credential flows | `ResidentCredentialServiceImpl` |
| Notifications (email/SMS + bell) | `NotificationService` |
| Validation rules | `RequestValidator` |
| Error codes | `constant/ResidentErrorCode` |
| Utility grab-bag (dates, masking, event IDs) | `Utility`, `helper/` |
| Request envelope filter chain | `config/ResidentFilterConfig`, `filter/` |

### Implementation approach for a new API (recipe)

1. Define DTOs (`dto/`), add controller method with `@Operation` swagger annotation.
2. Add validation in `RequestValidator`.
3. Service interface + impl; call downstream via `ResidentServiceRestClient`.
4. Insert a `resident_transaction` row with proper `request_type_code` + status; audit via `AuditUtil`.
5. Send notification if user-visible (`NotificationService` + template code in mosip-config).
6. Unit tests mirror package structure under `src/test`.

## 3. Database Schema (`mosip_resident`)

DDL in `db_scripts/mosip_resident`; upgrades in `db_upgrade_scripts/`.

### `resident_transaction` — the backbone (~36 columns)

One row per service request/event. Everything the resident sees in *View My History*, *Track My Requests*, and the bell notifications is derived from this table.

Key column groups:

- **Identity:** `event_id` (what the resident tracks), `aid`, `individual_id` (hashed), `token_id`
- **Classification:** `request_type_code` (UPDATE_MY_UIN, GENERATE_VID, SHARE_CRED_WITH_PARTNER, DOWNLOAD_PERSONALIZED_CARD…), `auth_type_code`
- **Status:** `status_code` (NEW / IN_PROGRESS / SUCCESS / FAILED…), `status_comment`, `read_status` (drives unread bell count), `pinned_status`
- **Credential linkage:** `credential_request_id`, `attribute_list`, `purpose`
- **Bookkeeping:** `requested_dtimes`, `cr_by`, `cr_dtimes`, `upd_by`, `upd_dtimes`

### Other tables

| Table | Data stored | Written by |
|---|---|---|
| `resident_grievance` | Grievance tickets: ticket id, event id, message, status, contact details | `GrievanceController` flow |
| `otp_transaction` | Hashed OTP, generation/expiry times, validation status — for email/phone verification | `ProxyOtpService` / `OtpManager` |
| `resident_user_actions` (`ResidentUserEntity`) | Per-user last bell click timestamp (`last_bell_notif_click_dtimes`) | bell APIs |
| `resident_session` (`ResidentSessionEntity`) | Login sessions: session id, ida_token, login timestamp, host/machine info | login flow (Day 3) |

> Note: identity data (name, address, photo) is **never stored** in this DB — only transaction metadata. Identity always comes fresh from ID Repository.

## 4. Hands-on (during/after session)

1. Trace one API end-to-end in the IDE: `/service-history/{langCode}` → `ResidentController#getServiceHistory` → `ResidentServiceImpl` → `ResidentTransactionRepository`.
2. Run the service locally against test env config; hit `/actuator/health` and swagger UI.
3. Query `resident_transaction` for one UIN and match rows to what the UI shows.

## Homework (before Day 3)

1. Read `ResidentSessionEntity` + find where it's written (login flow) — Day 3 preview.
2. Skim `RequestValidator` — note validation style.
3. List which `request_type_code` values exist (`constant/RequestType`).

## Reference

- Developer guide: <https://docs.mosip.io/1.2.0/modules/resident-services> (developer guide page)
- Day 1 README: [KT-Day1-Overview.md](./KT-Day1-Overview.md)
