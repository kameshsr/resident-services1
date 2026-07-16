# KT Day 3 — Authentication, Sessions & OTP Flows

**Duration:** 2 hours | **Audience:** All
**KT Giver:** Kamesh | **KT Receiver:** Chetan
**Covers architect focus areas:** Auth Manager proxy & end-to-end auth flow · session creation/maintenance/management · API role mappings

---

## Agenda

| # | Topic | Time |
|---|-------|------|
| 1 | End-to-end login flow (eSignet/OIDC → token → session) | 40 min |
| 2 | How sessions are created, maintained, managed | 20 min |
| 3 | API security: role mappings & the Auth Manager proxy | 25 min |
| 4 | OTP flows (login OTP vs channel-verification OTP) | 25 min |
| 5 | Q&A + homework | 10 min |

---

## 1. End-to-End Authentication Flow

```
Resident UI → eSignet (OIDC) → UIN/VID + OTP → tokens (access + ID token)
           → Resident Service validates token (Keycloak/eSignet issuer)
           → extracts ida_token / subject → resolves identity via ID Repo
           → creates resident_session row → serves authenticated APIs
```

Step by step:

1. **Login initiation:** UI redirects to eSignet (OIDC client configured per [Configuring Resident OIDC Client](https://docs.mosip.io/1.2.0/modules/resident-services)). Resident enters UIN/VID, receives OTP (sent via IDA), completes login.
2. **Token validation:** the service validates the **access token and ID token** against the issuer (`mosip.keycloak.issuerUrl`); see `IdAuthController#validateOtp` (`POST /validate-otp`) and the OIDC token-validation path (`OpenID-GetTokens` / `loginv2` in `api-docs/`).
3. **Identity context:** `IdentityServiceImpl` extracts the token's subject (ida_token / partner-specific token) and fetches ID attributes from ID Repository — `GET /identity/info/type/{schemaType}` returns attributes per UI schema (used for profile, personalized card, update-my-data prefill).
4. **Every subsequent API call** carries the bearer token; a filter/interceptor resolves the logged-in individual before the controller runs.

### Key classes

`IdentityServiceImpl` (token → identity), `IdAuthController`, `IdAuthServiceImpl` (OTP auth against IDA), `ResidentSessionRepository`, `validator/RequestValidator`.

## 2. Session Creation, Maintenance, Management

- **Created:** on first authenticated call after login, a row is written to **`resident_session`** (session id, ida_token, login time, host/machine details). Used for "last login" display on the profile and for audit.
- **Maintained:** the service itself is stateless — the *token* is the session. Expiry/refresh is governed by Keycloak/eSignet token lifetimes (configured in Keycloak realm + mosip-config).
- **`resident_user_actions`** keeps per-user UI state that must survive sessions: last bell-notification click time (drives the unread count).
- **Logout:** UI drops tokens / calls OIDC logout; no server-side session invalidation needed beyond token expiry.

> Design consideration: storing only hashed/tokenized identifiers (ida_token, hashed individual id) — no raw UIN at rest.

## 3. API Security — Roles & the Auth Manager Proxy

- Endpoint access is **role-mapped via properties** in mosip-config (`resident-default.properties`), not hardcoded. Pattern: `mosip.role.resident.<api>=RESIDENT` etc. When adding a new endpoint you MUST add its role mapping, or you'll get 403s in deployed envs (classic bug source).
- Two URL families:
  - `/auth-proxy/**` — endpoints callable **before login** (UI bootstrap: ui-schema, identity-mapping, templates, audit-log). Authenticated with the service's own token via **Kernel Auth Manager** (`mosip.kernel.authmanager.url`), not the resident's.
  - `/proxy/**` and resident APIs — require the **resident's** bearer token.
- The service obtains its self-token (client-credentials via Keycloak `mosip.resident.client.secret`) for internal service-to-service calls — this is the "Auth Manager proxy" role: the resident never talks to downstream services; this service does, with its own identity, after authorizing the resident.

### Special handling / learnings (fill in from experience)

- 403 on new API in env but works locally → missing role mapping property.
- Token subject differences between eSignet and Keycloak logins → handled in `IdentityServiceImpl` (walk through the code).
- WebSub callback endpoints are **not** user-authenticated — they're protected by hub signature verification (`WebsubCallbackRequestDecoratorFilter`) + intranet exposure (Day 9).

## 4. OTP Flows — two different purposes

| Flow | Purpose | APIs | Storage |
|---|---|---|---|
| **Login/auth OTP** | Authenticate an individual (via IDA) | `POST /req/otp` (send, `ResidentOtpController`), `POST /individualId/otp`, `POST /validate-otp` (`IdAuthController`) | IDA side |
| **Channel verification OTP** | Verify a *new* email/phone during Update My Data | `POST /contact-details/send-otp`, `POST /contact-details/update-data` (`ProxyOtpController`), status via `GET /channel/verification-status/` (`VerificationController`) | `otp_transaction` table (hashed OTP, expiry, attempts) |

Walk through: `OtpManager` (generation/validation rules — attempts, expiry, block) and notification of OTP via `NotificationService`.

## Hands-on

1. Log in on test env with browser dev-tools open — capture the OIDC redirects and the bearer token; decode the JWT and inspect claims.
2. Call `/identity/info/type/personalized-card` with the token via Postman.
3. Check the new row in `resident_session`; click the bell and watch `resident_user_actions` update.
4. Grep `mosip.role.resident.` in resident-default.properties — map 5 endpoints to their roles.

## Homework (before Day 4)

Read `ResidentServiceImpl` methods for auth-lock/unlock; skim `reqauth-lock.yaml` / `reqauth-unlock.yaml` in `api-docs/`.

## Reference

- OIDC client setup: <https://docs.mosip.io/1.2.0/modules/resident-services/resident-services-configure-resident-oidc-client>
- Day 2 README: [KT-Day2-Architecture-Code-Database.md](./KT-Day2-Architecture-Code-Database.md)
