# KT Day 4 — Core Resident Services: ResidentController (Part 1)

**Duration:** 2 hours | **Audience:** All
**KT Giver:** Kamesh | **KT Receiver:** Chetan
**Scope:** Secure My ID (auth lock/unlock), e-UIN & print UIN, RID/AID status — the "protect and retrieve my ID" half of `ResidentController` (20 endpoints total; remaining half on Day 5).

---

## Agenda

| # | Topic | Time |
|---|-------|------|
| 1 | ResidentController anatomy (validators, event creation) | 15 min |
| 2 | Auth lock/unlock — Secure My ID | 50 min |
| 3 | e-UIN, print UIN | 25 min |
| 4 | RID/AID status check | 20 min |
| 5 | Q&A + homework | 10 min |

---

## 1. ResidentController Anatomy

`ResidentController` is the biggest controller — every method follows: validate request (`RequestValidator`) → delegate to `ResidentServiceImpl` → create `resident_transaction` event → notify. Understanding one flow here means understanding all of them.

## 2. Secure My ID — Auth Type Lock/Unlock

**Business purpose:** a resident can switch off authentication modalities (email OTP, phone OTP, demographic, fingerprint, iris, face) so nobody — including themselves — can authenticate with them until unlocked. Protection against identity misuse.

### APIs

| API | Use |
|---|---|
| `GET /auth-lock-status` | Fetch current lock status of every auth type for the logged-in individual |
| `POST /auth-lock-unlock` | v2 API — lock and/or unlock a list of auth types in one call (with optional `unlockForSeconds` for temporary unlock) |
| `POST /req/auth-lock`, `POST /req/auth-unlock` | Legacy (pre-login era, OTP-authenticated) variants |
| `POST /req/auth-history` | Paginated authentication history for the individual |

### Flow (v2)

```
UI → POST /auth-lock-unlock {authTypes[], lock/unlock}
  → RequestValidator (allowed auth types come from config!)
  → ResidentServiceImpl.reqAauthTypeStatusUpdateV2()
  → IDA internal API: update auth-type status
  → resident_transaction row: request_type = AUTH_TYPE_LOCK_UNLOCK, status = NEW
  → async: IDA confirms via WebSub → /callback/authTypeCallback
      (WebSubUpdateAuthTypeController) → status updated to SUCCESS
  → NotificationService → email/SMS + bell notification
```

**Key points to demo:**

- Allowed auth types are a **config property** (`resident-default.properties`) — a v0.9.1 theme.
- The status is **eventually consistent**: `/auth-lock-status` reads what IDA reports; the transaction row tracks the request.
- Temporary unlock (`unlockForSeconds`) — auto relock, special handling worth showing in code.

## 3. e-UIN & Print UIN

| API | Use |
|---|---|
| `POST /req/euin` | Download e-UIN card (PDF) — OTP-authenticated, returns signed PDF bytes |
| `POST /req/print-uin` | Request a UIN card re-print (goes through credential/print pipeline) |

Flow: OTP validation → credential request to Credential Request Generator → Digital Card Service / Print service produce the card → notification when ready. These are older-generation APIs kept for compatibility; the newer card downloads are in `DownloadCardController` (Day 7).

## 4. RID/AID Status Check

| API | Use |
|---|---|
| `POST /rid/check-status` | Check registration status by RID (calls RegProc status service) |
| `GET /aid/status` *(covered again Day 5 with Get My UIN)* | AID status for card readiness |

Demo: enter an in-progress AID vs a processed one; show how RegProc stages map to the user-friendly status strings (masterdata templates).

## Special handling & learnings (fill from experience)

- Auth-lock request while a previous one is still IN_PROGRESS → validation error (walk through the check).
- WebSub callback missed → status stuck NEW; the batch job / manual re-subscription story (full detail Day 9).

## Hands-on

1. Lock phone-OTP auth on a test UIN; verify `/auth-lock-status`, the `resident_transaction` row, the bell notification, and then unlock.
2. Try locking a non-allowed auth type → observe the validation error envelope.
3. Call `/req/auth-history` and match it against IDA's records.

## Homework (before Day 5)

Skim `requpdate-uin.yaml`, `Update-UIN.yaml`, and `Resident Service Get service History and Get profile.yaml` in `api-docs/`.

## Reference

Day 3 README: [KT-Day3-Authentication-Sessions-OTP.md](./KT-Day3-Authentication-Sessions-OTP.md)
