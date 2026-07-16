# KT Day 5 — Core Resident Services: ResidentController (Part 2)

**Duration:** 2 hours | **Audience:** All
**KT Giver:** Kamesh | **KT Receiver:** Chetan
**Scope:** Update My Data (update UIN) end-to-end, service history, events, notifications/bell, profile — the "change and track my data" half of `ResidentController`.

---

## Agenda

| # | Topic | Time |
|---|-------|------|
| 1 | Update My Data (update UIN) — the most complex flow | 55 min |
| 2 | Service history & events | 30 min |
| 3 | Bell notifications & profile | 20 min |
| 4 | Q&A + homework | 15 min |

---

## 1. Update My Data (Update UIN) — End-to-End

**Business purpose:** resident updates demographic attributes (name, address, email, phone, DOB…) online instead of visiting a center.

### APIs

| API | Use |
|---|---|
| `POST /req/update-uin` | Legacy OTP-authenticated update request |
| `PATCH/POST /update-uin` | Logged-in (v2) update request |
| `DocumentController /documents/{transaction-id}` | Upload supporting documents before submit (Day 7 detail) |
| `ProxyIdRepoController GET /update-count` | Remaining update count per attribute (policy-limited!) |
| `ProxyIdRepoController GET /get-pending-drafts/{langCode}`, `POST /discardPendingDraft/{eid}` | Pending draft management |
| `ProxyOtpController /contact-details/send-otp`, `/contact-details/update-data` | Verify new email/phone via OTP |

### Flow

```
UI: resident edits attributes + uploads documents (each gets a document id
    against a transaction id)
→ POST /update-uin
→ RequestValidator: updatable attributes come from CONFIG
    (mosip.resident.update-uin.* properties); email/phone require OTP verification
→ ResidentServiceImpl.reqUinUpdate()
    → builds a registration PACKET (commons-packet-service / packet manager)
    → posts packet to RegProc packet receiver (mosip.packet.receiver.url)
    → resident_transaction row: UPDATE_MY_UIN, status NEW, event_id returned to UI
→ RegProc processes the packet through its stages (async, minutes+)
    → workflow status callbacks via WebSub → /callback/regprocworkflow
      (WebSubRegprocWorkFlowController) → transaction status updated (IN_PROGRESS…)
    → on completion, IDENTITY_UPDATED event → notification + new card availability
→ Resident tracks via Track My Requests (event id) and gets email/SMS + bell updates
```

### Key design points & learnings

- **Updatable attributes, per-attribute update-count limits, and document requirements are all config/policy driven** — most "bugs" here are config mismatches between UI schema, resident properties, and ID schema.
- **Draft handling:** an unsubmitted/failed update can leave a pending draft in ID Repo — that's why `get-pending-drafts` / `discardPendingDraft` exist (known UI bug MOSIP-33058 relates to this area).
- **Known open issue:** MOSIP-38771 — Update-My-Data intermittently failing for some UINs (discuss symptoms, current analysis, workaround).
- Packet creation needs machine/center defaults for "resident" channel — special handling in packet construction (show the constants).

## 2. Service History & Events

| API | Use |
|---|---|
| `GET /service-history/{langCode}` | Paginated, filterable (date range, status, service type) history from `resident_transaction` |
| `GET /download/service-history` | Same, as a localized signed PDF |
| `GET /events/{event-id}` | Full detail of one event — status timeline, purpose, attributes involved |
| `GET /aid/status` | AID processing status (also used by Get My UIN) |

Demo: show how `request_type_code` + `status_code` in `resident_transaction` render as history rows in each language (templates from masterdata), and how `/events/{event-id}` is the same row expanded.

## 3. Bell Notifications & Profile

| API | Use |
|---|---|
| `GET /unread/notification-count` | Badge count = transactions with `read_status = false` newer than last bell click |
| `POST /bell/notification-click` (+ `GET /bell/updatedttime`) | Persist bell-click timestamp in `resident_user_actions` |
| `GET /notifications/{langCode}` | Paginated notification list (localized) |
| `GET /profile` | Name, photo, last-login (from `resident_session`), masked contact details |

## Hands-on

1. Full update-my-data run on test env: verify email OTP → upload document → submit → capture event id → watch status change via `/events/{event-id}` → see final notification. (Timebox; if RegProc is slow, prepared example.)
2. Query `resident_transaction` before/after and identify every column that changed.
3. Download service history PDF in two languages.

## Homework (before Day 6)

Skim `vid.yaml`, `API-to-generate-VID-Generate.yaml`, `Credential-Management.yaml` in `api-docs/`; read the VID policy JSON from config.

## Reference

Day 4 README: [KT-Day4-ResidentController-Part1.md](./KT-Day4-ResidentController-Part1.md)
