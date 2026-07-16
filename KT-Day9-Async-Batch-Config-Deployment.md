# KT Day 9 — Async Flows, Batch Job, mosip-config & Deployment

**Duration:** 2.5 hours | **Audience:** Developers
**KT Giver:** Kamesh | **KT Receiver:** Chetan
**Covers architect focus areas:** mosip-config review incl. role mappings · special handling, design considerations, learnings from resolved bugs · troubleshooting best practices

---

## Agenda

| # | Topic | Time |
|---|-------|------|
| 1 | WebSub: subscriptions & the 4 callback endpoints | 40 min |
| 2 | CredentialStatusUpdateBatchJob | 20 min |
| 3 | NotificationService (email/SMS/bell templates) | 20 min |
| 4 | mosip-config deep dive (incl. role mappings) | 30 min |
| 5 | Deployment (docker/helm) + troubleshooting playbook | 30 min |
| 6 | Q&A | 10 min |

---

## 1. WebSub — How Async Completion Works

The service **subscribes** at startup (re-subscription scheduler) to hub topics, and receives **callbacks**:

| Callback | Controller | Purpose |
|---|---|---|
| `POST /callback/credentialStatusUpdate` | `WebSubCredentialStatusUpdateController` | Credential/card ready or failed → update transaction, notify |
| `POST /callback/regprocworkflow` | `WebSubRegprocWorkFlowController` | RegProc workflow status for update-UIN packets |
| `POST /callback/authTypeCallback` | `WebSubUpdateAuthTypeController` | IDA confirms auth-type lock/unlock |
| `POST /callback/authTransaction` | `AuthTransactionCallbackController` | Authentication events on the individual → history entries |

Security: callbacks are hub-signature verified (`WebsubCallbackRequestDecoratorFilter`, decorated request stream) and exposed only internally — **not** user-token authenticated.

**Failure modes & learnings (discuss with real examples):**

- Hub down / subscription lapsed → missed callbacks → events stuck IN_PROGRESS/NEW. Remedies: re-subscription delay properties, restart-time re-subscribe, and the batch job below.
- Duplicate callbacks → handlers must be idempotent (check current status before update).

## 2. `CredentialStatusUpdateBatchJob`

`@Scheduled` job (initial delay + fixed delay from properties `CREDENTIAL_UPDATE_STATUS_UPDATE_INITIAL_DELAY` / interval, overridable in config). It scans non-terminal `resident_transaction` rows (NEW/ISSUED credential requests), polls the credential request generator for real status, updates rows, and triggers notifications — the **safety net for missed WebSub callbacks**. Can be disabled per env via property (important for local dev to avoid noise).

## 3. NotificationService

Single funnel for email + SMS + bell events: picks **template code** per request-type + status (templates in masterdata, per language), merges attributes (masked), calls kernel notification service, and flips `read_status` bookkeeping for the bell. Preferred-language selection is the source of known issue MOSIP-30678.

## 4. mosip-config Deep Dive

Repo: [mosip-config](https://github.com/mosip/mosip-config) (tag 1.2.3.1). Served to the app via Spring Cloud Config.

| File | What resident-services reads |
|---|---|
| `application-default.properties` | Platform-wide: keycloak URLs, websub hub, kernel service URLs |
| `resident-default.properties` | Everything module-specific — walk this file top to bottom |

Property groups to walk through in `resident-default.properties`:

- **Service URLs:** `mosip.idrepo.*`, `mosip.ida.internal.url`, `mosip.regproc.*`, `mosip.pms.*`, `mosip.kernel.*`, `mosip.packet.receiver.url`
- **API role mappings:** `mosip.role.resident.*` — required for every endpoint (403 troubleshooting, Day 3)
- **Auth/OIDC:** `mosip.keycloak.issuerUrl`, `mosip.resident.client.secret` (env-injected), allowed audiences
- **Feature config:** allowed auth types, updatable UIN attributes + update counts, VID policy, grievance settings, notification template codes, UI schema/properties
- **Batch/websub tuning:** batch delays, re-subscription delay, callback secrets
- **v0.9.1 theme:** attributes moved from hardcoded → config; check release notes diff when upgrading

> Practice: change one property in a local config override and show it taking effect (e.g., disable the batch job).

## 5. Deployment & Troubleshooting

### Deployment artefacts in this repo

- `Dockerfile` under `resident/resident-service`; images published as `mosipid/resident-service`
- `helm/` charts + `deploy/` scripts (`deploy.sh`, `copy_cm_func.sh` for configmaps); K8s deployment alongside other MOSIP modules
- DB init: `db_scripts/mosip_resident` (fresh), `db_upgrade_scripts/` (version upgrades)
- Local dev: `resident/local-dev-setup`, plus docker-based local setup in the root README

### Troubleshooting playbook (best practices)

| Symptom | First checks |
|---|---|
| 403 on an API | Role mapping property; token audience; `/auth-proxy` vs `/proxy` family |
| Event stuck IN_PROGRESS | WebSub subscription state; hub logs; batch job enabled?; downstream service status |
| Wrong/missing notification | Template code exists for lang? masterdata template content; notification service logs |
| Update-UIN failures | Packet receiver reachable? ID schema vs UI schema vs updatable-attributes config aligned? pending draft blocking? |
| Slow masterdata screens | Cache config; masterdata service health |

Logs: structured via `LoggerConfiguration`; correlate by event id / request id across services. Metrics: RestTemplate metrics interceptor + actuator.

## Hands-on

1. Trigger a share-credential and tail logs through callback receipt.
2. Stop the batch job via property, create a stuck transaction, re-enable and watch it repair.
3. Grep every `mosip.role.resident.` property and spot-check three against controllers.

## Homework (before Day 10)

Prepare reverse-KT: pick one flow (your choice) to present back end-to-end tomorrow. Collect any open questions.

## Reference

- Deployment guide: <https://docs.mosip.io/1.2.0/modules/resident-services/resident-services-deployment-guide>
- Day 8 README: [KT-Day8-Proxy-Supporting-APIs.md](./KT-Day8-Proxy-Supporting-APIs.md)
