# KT Day 8 — Proxy & Supporting APIs

**Duration:** 1.5 hours | **Audience:** Developers + QA
**KT Giver:** Kamesh | **KT Receiver:** Chetan
**Scope:** `ProxyMasterdataController` (16+), `ProxyConfigController` (3), `ProxyIdRepoController` (3), `ProxyPartnerManagementController`, `ProxyAuditController` (2), `DownLoadMasterDataController` (3), `TransliterationController` (1), `GrievanceController` (1).

> These are mostly **thin pass-throughs** — teach the *pattern* once, then just catalogue the endpoints. Session time goes to the pattern, grievance, and the PDF-generating masterdata APIs.

---

## Agenda

| # | Topic | Time |
|---|-------|------|
| 1 | The proxy pattern: why the UI never calls other services directly | 15 min |
| 2 | Masterdata & config proxies (catalogue walk) | 30 min |
| 3 | ID-Repo, partner, audit proxies + transliteration | 20 min |
| 4 | Grievance flow | 15 min |
| 5 | Q&A + homework | 10 min |

---

## 1. Why Proxy?

The browser only ever talks to resident-services. Proxies add: (a) a single CORS/auth surface, (b) the service's own auth-manager token for downstream calls, (c) auditing, (d) response caching (`CacheConfig`) for static masterdata. Two flavours:

- `/auth-proxy/**` — pre-login (UI bootstrap): ui-schema, identity-mapping, templates, audit
- `/proxy/**` — post-login

When adding a new proxy: controller + service impl that delegates via `ResidentServiceRestClient`, plus the **role-mapping property** (Day 3 lesson).

## 2. Masterdata & Config Proxies

### `ProxyMasterdataController` — data for dropdowns/screens

Groups (all `GET`, mostly cached):

- **Documents:** `/proxy/masterdata/validdocuments/{langCode}`, `/documenttypes/{documentcategorycode}/{langcode}`, `/applicanttype/{applicantId}/languages`
- **Locations:** `/locationHierarchyLevels[{langcode}]`, `/locations/immediatechildren/{locationcode}/{langcode}`, `/locations/info/{locationcode}/{langcode}`
- **Registration centers:** `/registrationcenters/{langcode}/{hierarchylevel}/names`, `/registrationcenters/page/{langcode}/{hierarchylevel}/{name}`, `/getcoordinatespecificregistrationcenters/...`, `/workingdays/{registrationCenterID}/{langCode}`
- **Schema & fields:** `/idschema/latest`, `/dynamicfields/{fieldName}[/{langCode}]`, `/gendercode/{gendertype}/{langcode}`
- **Templates (pre-login):** `/auth-proxy/masterdata/templates/{langcode}/{templatetypecode}`

### `DownLoadMasterDataController` — masterdata as PDFs

`GET /download/registration-centers-list`, `/download/nearestRegistrationcenters`, `/download/supporting-documents` — merge masterdata into localized, downloadable PDFs (Get Information tile).

### `ProxyConfigController` — UI bootstrap

`GET /proxy/config/ui-properties`, `GET /auth-proxy/config/ui-schema/{schemaType}` (drives which fields appear on personalized card / update-data screens), `GET /auth-proxy/config/identity-mapping`.

## 3. Other Proxies

| Controller | Endpoints | Notes |
|---|---|---|
| `ProxyIdRepoController` | `GET /update-count`, `GET /get-pending-drafts/{langCode}`, `POST /discardPendingDraft/{eid}` | Update-My-Data support (Day 5) |
| `ProxyPartnerManagementController` | partner list by partner-type (`@RequestMapping`-based) | Share My Data partner dropdown |
| `ProxyAuditController` | `POST /proxy/audit/log`, `POST /auth-proxy/audit/log` | UI pushes its own audit events (incl. pre-login) |
| `TransliterationController` | `POST /transliterate` | Language-to-language transliteration for multi-lang forms |

## 4. Grievance — `GrievanceController`

`POST /grievance/ticket` — raise a ticket tied to an event id; stored in `resident_grievance`, forwarded to the redressal system where configured. Fields: event id, message, contact details; ticket id returned. Known UI issue: virtual-keyboard entry blocking ticket creation (MOSIP-30682).

## Hands-on

1. Call 5 masterdata proxies in Postman; confirm second call is faster (cache).
2. Fetch `/auth-proxy/config/ui-schema/update-demographics` and match fields to the Update-My-Data screen.
3. Raise a grievance and find the row in `resident_grievance`.

## Homework (before Day 9)

Read `batch/CredentialStatusUpdateBatchJob.java` and the four `/callback/*` controllers; find the websub subscription code (search `subscribe`).

## Reference

Day 7 README: [KT-Day7-Cards-Documents.md](./KT-Day7-Cards-Documents.md)
