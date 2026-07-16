# KT Day 6 — VID Management & Credential Sharing

**Duration:** 2 hours | **Audience:** All
**KT Giver:** Kamesh | **KT Receiver:** Chetan
**Scope:** `ResidentVidController` (6 endpoints), `ResidentCredentialController` (7 endpoints), partner interaction.

---

## Agenda

| # | Topic | Time |
|---|-------|------|
| 1 | VID concepts & policy | 20 min |
| 2 | Manage My VID — APIs & flows | 40 min |
| 3 | Credential sharing — Share My Data | 45 min |
| 4 | Q&A + homework | 15 min |

---

## 1. VID Concepts & Policy

A **VID (Virtual ID)** is a revocable alias for the UIN so the resident never exposes the UIN itself. Types come from the **VID policy JSON** (served via `GET /vid/policy`, sourced from config/ID Repo):

- **Perpetual** — no expiry; used for ordering cards, sharing with partners
- **Temporary** — short validity
- **One-time** — single authentication use

Policy controls: how many active VIDs per type, expiry, whether auto-regenerate on revoke.

## 2. Manage My VID — APIs

| API | Use |
|---|---|
| `GET /vids` | List active VIDs of the logged-in individual (with type, expiry, masked VID) |
| `POST /generate-vid` | v2: generate VID for logged-in user (also legacy `POST /vid` with OTP) |
| `PATCH /revoke-vid/{vid}` | v2: revoke (legacy `PATCH /vid/{vid}`) |
| `GET /vid/policy` | VID policy JSON for the UI |

### Flow (generate)

```
POST /generate-vid → RequestValidator (VID type vs policy)
→ ResidentVidServiceImpl → ID Repository VID service (mosip.idrepo.vid.url)
→ resident_transaction row (GENERATE_VID) + notification (masked VID in email/SMS)
```

Revoke is symmetric (`REVOKE_VID`). Both are synchronous — the VID service responds immediately; the notification is still recorded as an event.

**Learnings to discuss:** policy violations (max active VIDs) → error envelope; VID used for login being revoked — session implications; VID card download is *not* here — it goes through the credential/download-card pipeline (Day 7).

## 3. Credential Sharing — Share My Data

**Business purpose:** resident shares selected identity attributes with a **registered partner** (bank, telco…) — with policy-driven formatting/masking — so the partner can deliver a service.

| API | Use |
|---|---|
| `POST /share-credential` | v2 logged-in share request (attributes, purpose, partner, masking prefs) |
| `POST /req/credential` | Legacy OTP-authenticated credential request |
| `GET /req/credential/status/{requestId}` | Poll request status |
| `GET /req/credential/cancel/{requestId}` | Cancel a pending request |
| `GET /credential/types` | Available credential types |
| `GET /req/policy/partnerId/{partnerId}/credentialType/{credentialType}` | Partner's data-share policy (which attributes, format, masking) |
| `GET /req/card/{requestId}` | Download the produced card/credential |

Partner list for the UI comes from `ProxyPartnerManagementController` (`/auth-proxy/partners`-style, Day 8).

### Flow

```
UI: pick partner → policy fetched → resident picks attributes (+ masking/format)
→ POST /share-credential
→ ResidentCredentialServiceImpl → Credential Request Generator
   (mosip.idrepo.credrequest.generator.url) → returns credential_request_id
→ resident_transaction row: SHARE_CRED_WITH_PARTNER, stores request id + attribute list + purpose
→ Credential Service builds credential → publishes to partner via WebSub/datashare
→ status callback → /callback/credentialStatusUpdate → transaction SUCCESS
→ notification: "your data was shared with X for purpose Y"
```

**Design points:**

- `purpose` free-text is stored on the transaction (traceability — resident can later see *why* data was shared).
- Attribute formatting/masking can **override the policy format** per user input (`API-to-apply-data-format-and-masking…` in api-docs).
- Status lifecycle: NEW → ISSUED/failed; the `CredentialStatusUpdateBatchJob` (Day 9) repairs missed callbacks.
- Known issue MOSIP-31136: VC verification failing for name/photo/full-address attributes — walk through the analysis.

## Hands-on

1. Generate + revoke a perpetual VID; check `/vids`, the transaction rows, and the masked notification.
2. Fetch a partner policy JSON and map its attributes to the share-credential request body.
3. Run a share-credential request, poll status, then cancel one and observe statuses.

## Homework (before Day 7)

Skim `API-to-download-the-UIN-card-using-AIDVIDUIN.yaml`, `API-to-order-a-physical-card.yaml`, `Document.yaml` in `api-docs/`.

## Reference

Day 5 README: [KT-Day5-ResidentController-Part2.md](./KT-Day5-ResidentController-Part2.md)
