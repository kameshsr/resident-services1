# Jira Draft

**Project:** MOSIP
**Type:** Improvement
**Component:** resident-services
**Summary:** Derive IDSchemaVersion from UIN identity data instead of config property `IDSchema.Version`
**Labels:** resident-services, tech-debt, config-cleanup
**Status:** DRAFT — pending Architect and PO approval before code change

## Description

Resident services currently reads the ID schema version from the config property
`IDSchema.Version` (mosip-config → `resident-default.properties`, currently `0.1`):
https://github.com/mosip/mosip-config/blob/c91d5aee9893170e37f507797df245e6c1459364/resident-default.properties#L323

The schema version is already stored per record in ID Repo as the `IDSchemaVersion`
attribute of the identity JSON, so it can be derived from the UIN itself. Reading it
from config is redundant and risky: if the deployed schema version diverges from the
config value, packets are created with the wrong schema.

The codebase already uses the correct pattern in the secure-session path
(`ResidentServiceImpl` reads `idRepoJson.get(ID_SCHEMA_VERSION)`) and in
`IdentityDataUtil`. This change extends that pattern to the remaining config-based usages.

## Current usages of `IDSchema.Version`

1. `ResidentUpdateService.java` (line 71): `@Value("${IDSchema.Version}")` → `defaultIdSchemaVersion`, used as fallback in `createPacket()` when the caller passes null (non-secure-session path, `ResidentServiceImpl` line 884).
2. `UinCardRePrintService.java` (line 69): `@Value("${IDSchema.Version}")` → `idschemaVersion`, used directly for `packetDto.setSchemaVersion()`, `setSchemaJson()` (lines 183–184) and in the demographic map (line 291).

## Proposed change

- In both services, fetch the identity JSON from ID Repo using the UIN and read the `IDSchemaVersion` attribute (same as the existing secure-session path), instead of injecting `IDSchema.Version` from config.
- Remove the null-fallback to `defaultIdSchemaVersion` in `ResidentUpdateService.createPacket()`; resolve the version from the UIN in the non-secure-session path too.
- After code change is released: deprecate and remove `IDSchema.Version` from `resident-default.properties` in mosip-config (also update `application-local.properties` and test resources).

## Impact / Risk

- Extra ID Repo call in the reprint/non-secure paths if identity is not already fetched (mitigable — identity is typically already retrieved for validation).
- Old records carry their own `IDSchemaVersion`; packets will now be built with the record's actual schema version instead of a global config value — this is the intended correctness improvement, but regression testing of Update UIN and UIN card reprint flows is required.
- Config removal must be sequenced after code deployment (backward compatible: keep property one release, then remove).

## Acceptance criteria

1. No `@Value("${IDSchema.Version}")` injection remains in resident-services main code.
2. Schema version used for packet creation always matches the `IDSchemaVersion` of the resident's identity record in ID Repo.
3. Update-UIN (secure and non-secure session) and UIN card reprint flows pass regression.
4. Unit tests updated; mosip-config cleanup ticket linked.

## Approvals required before implementation

- [ ] Architect sign-off
- [ ] Product Owner sign-off
