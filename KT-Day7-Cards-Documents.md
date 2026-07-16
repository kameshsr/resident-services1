# KT Day 7 — Card & Document Flows

**Duration:** 1.5 hours | **Audience:** All
**KT Giver:** Kamesh | **KT Receiver:** Chetan
**Scope:** `DownloadCardController` (4), `OrderCardController` (3), `PinStatusController` (2), `DocumentController` (4), `AcknowledgementController` (1).

---

## Agenda

| # | Topic | Time |
|---|-------|------|
| 1 | Card download flows (UIN card, personalized card, VID card) | 35 min |
| 2 | Order a physical card (print partner + payment) | 25 min |
| 3 | Documents, acknowledgements, pin/unpin | 20 min |
| 4 | Q&A + homework | 10 min |

---

## 1. Card Downloads — `DownloadCardController`

| API | Use |
|---|---|
| `POST /download-card` | Download UIN card by AID/VID/UIN (OTP-authenticated) |
| `POST /download/personalized-card` | Build + sign a card with user-chosen attributes (min 3) |
| `GET /request-card/vid/{VID}` | Request a VID card (credential pipeline; returns event id) |
| `GET /aid-stage/{aid}` | Which RegProc stage an AID is at (drives "card ready?" in Get My UIN) |
| *(ResidentController)* `GET /download-card/event/{eventId}` | Fetch the produced card PDF for a completed event |

### Flow patterns

- **Direct download** (card already exists): Digital Card Service returns the signed PDF → streamed to the UI.
- **Event-based** (card must be produced): request → credential request generator → Digital Card Service → WebSub "card ready" → bell/email notification → UI downloads via `/download-card/event/{eventId}`.
- **Personalized card:** attributes fetched via `/identity/info` per UI schema → template merged (masterdata templates) → PDF generated and **signed via Keymanager** → download. Config decides which attributes are offerable.

**Known issues to mention:** long names truncated on card (MOSIP-32822); email/SMS notification language for personalized card (MOSIP-30678).

## 2. Order a Physical Card — `OrderCardController`

| API | Use |
|---|---|
| `GET /physical-card/order` | Start order: redirects resident to the print partner's page (with signed params) |
| `GET /physical-card/order-redirect` | Partner redirects back with payment/order result; service validates and records |
| `POST /sendCard` | Submit the card order to the partner after successful payment |

Flow: choose partner (PMS) → redirect to partner's payment page → redirect back (`order-redirect` validates transaction id, prevents tampering) → order submitted → event id + notifications track printing/dispatch. Uses a **perpetual VID** typically; payment handling is partner-side (mock payment provider APIs exist in `api-docs/` for testing).

## 3. Documents, Acknowledgements, Pin/Unpin

### `DocumentController` — supporting documents for Update My Data

| API | Use |
|---|---|
| `POST /documents/{transaction-id}` | Upload a document (doc type + category) against a draft transaction |
| `GET /documents/{transaction-id}` | List uploaded docs for the transaction |
| `GET /document/{document-id}` | Fetch one document |
| `DELETE /documents/{document-id}` | Remove an uploaded doc |

Documents are held (object store via packet manager utilities) until the update-UIN packet is built, then embedded into the packet. Validation: allowed types/sizes from config; virus-scan hook. Known UI bug: invalid doc upload clears entered data (MOSIP-33065).

### `AcknowledgementController`

`GET /ack/download/pdf/event/{eventId}/language/{languageCode}` — signed, localized acknowledgement PDF for any event (used from Track My Requests).

### `PinStatusController`

`POST /pinned/{eventId}` / `POST /unpinned/{eventId}` — toggles `pinned_status` on `resident_transaction` so the event sticks to the top of View My History.

## Hands-on

1. Download a personalized card with 3 attributes; verify the signature panel in the PDF.
2. Upload + list + delete a document against a dummy transaction id.
3. Pin an event and re-query service history to see ordering change.
4. Walk the physical-card order on the mock payment provider.

## Homework (before Day 8)

Open `ProxyMasterdataController` and count how many of its endpoints your UI screens actually use — brings Day 8's "thin proxy" point home.

## Reference

Day 6 README: [KT-Day6-VID-Credentials.md](./KT-Day6-VID-Credentials.md)
