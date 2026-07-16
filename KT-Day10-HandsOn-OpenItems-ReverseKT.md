# KT Day 10 — Hands-on, Open Items & Reverse KT

**Duration:** 2.5 hours | **Audience:** All
**KT Giver:** Kamesh | **KT Receiver:** Chetan
**Covers architect focus areas:** open bugs, known limitations, upcoming feature designs (with Confluence/JIRA links) · troubleshooting practice · sign-off

---

## Agenda

| # | Topic | Time |
|---|-------|------|
| 1 | Hands-on: API test rig + Postman run-through | 40 min |
| 2 | Bug walkthroughs — 2–3 resolved bugs end-to-end | 30 min |
| 3 | Open bugs, known limitations, upcoming feature designs | 30 min |
| 4 | Reverse KT (receiver presents) | 30 min |
| 5 | Sign-off & handover | 20 min |

---

## 1. Hands-on — Running the APIs

### api-test rig (in this repo)

`api-test/` — TestNG-based automation (`MosipTestRunner`), test scripts per operation (`AddIdentity`, `GetWithParam`, `PostWithAutogenId`…), YAML-driven test cases. Show: how to configure env/kernel properties, run a suite against the test env, and read the report (see `docs/apitestrig-*.png`). Deployed variant: `deploy/resident-apitestrig`.

### Manual

Postman collection / swagger UI: exercise at least one API from every functional group (login, auth-lock, VID, share-credential, download-card, masterdata proxy, grievance). Receiver drives, giver observes.

## 2. Resolved-Bug Walkthroughs (pick 2–3 real ones)

For each: symptom → how it was reproduced → root cause → fix commit/PR → lesson. Suggested categories from this module's history:

1. A **role-mapping/403** bug (config, not code).
2. A **stuck event / missed WebSub callback** bug (async design).
3. An **update-UIN schema/config mismatch** bug (UI schema vs ID schema vs properties).

*(Fill in actual JIRA IDs + PR links before the session.)*

## 3. Open Bugs, Known Limitations & Upcoming Designs

### Known open bugs (from release notes — verify current status in JIRA)

| JIRA | Description | Notes for receiver |
|---|---|---|
| [MOSIP-38771](https://mosip.atlassian.net/browse/MOSIP-38771) | Update my data intermittently failing for a few UINs | Current analysis, suspected area, workaround |
| [MOSIP-35282](https://mosip.atlassian.net/browse/MOSIP-35282) | VID policy transactions-allowed not enforced per policy | |
| [MOSIP-31136](https://mosip.atlassian.net/browse/MOSIP-31136) | VC verification failing for name/photo/full address | |
| [MOSIP-33065](https://mosip.atlassian.net/browse/MOSIP-33065) | UI: invalid document upload clears entered data | UI repo, but triaged here first |
| [MOSIP-33058](https://mosip.atlassian.net/browse/MOSIP-33058) | UI: cancel-update popup then "no record found" | pending-draft area |
| [MOSIP-32822](https://mosip.atlassian.net/browse/MOSIP-32822) | Long names truncated on downloaded card | template/layout |
| [MOSIP-30684](https://mosip.atlassian.net/browse/MOSIP-30684) / [30682](https://mosip.atlassian.net/browse/MOSIP-30682) | Virtual-keyboard input issues (share card, grievance) | |
| [MOSIP-30678](https://mosip.atlassian.net/browse/MOSIP-30678) | Notifications not in preferred language for personalized card | NotificationService lang selection |

Full list: JIRA filter linked from the [v0.9.1 release notes](https://docs.mosip.io/1.2.0/roadmap-and-releases/releases/resident-services-v0.9.1).

### Known limitations (discuss honestly)

- Async flows depend on WebSub availability; batch job mitigates but adds delay.
- Update-count / policy enforcement gaps (e.g., MOSIP-35282).
- Areas where behavior is config-coupled across three repos (resident-services, resident-ui, mosip-config) — upgrades must be coordinated per the compatibility table in release notes.

### Upcoming feature designs

For each in-flight design, cover: implementation approach, design decisions, and links. Fill in before the session:

| Feature / design | Status | Links (Confluence / JIRA / design doc) |
|---|---|---|
| *(e.g., resident OIDC changes — see `community-reply-resident-oidc-2821.md` in repo root)* | | |
| | | |

## 4. Reverse KT

Receiver presents one flow end-to-end (code + config + DB + async path) — giver and audience question. Target: receiver can (a) trace any API in code, (b) diagnose a stuck event, (c) explain where any given behavior is configured.

## 5. Sign-off & Handover

- Walk the **sign-off checklist** (section 7 of the [KT Plan](./Resident-Services-KT-Plan.docx)) and the **architect focus-areas table** (section 4) — mark every row.
- Access handover: GitHub (resident-services, resident-ui, mosip-config), JIRA project, Confluence space, test environments, monitoring dashboards, release/deployment jobs.
- Update the [Confluence tracking page](https://mosip.atlassian.net/wiki/spaces/KnowledgeBase/pages/2652962893) with completion status and links to these Day 1–10 READMEs.

## Reference

- All day READMEs: Day 1–9 files in this folder (`KT-Day*.md`)
- Release notes: <https://docs.mosip.io/1.2.0/roadmap-and-releases/releases/resident-services-v0.9.1>
