-- Below script is required to rollback from 1.4.0 to 1.3.1
-- Reverts the MOSIP-41105 column widening on resident.resident_session.
-- NOTE: this narrows ip_address and host back to varchar(128). It will fail if
-- any row already holds an encrypted value longer than 128 characters; purge or
-- migrate such rows (e.g. TRUNCATE resident.resident_session, or null these
-- columns) before running this rollback.

\c mosip_resident

ALTER TABLE resident.resident_session ALTER COLUMN ip_address TYPE character varying(128);
ALTER TABLE resident.resident_session ALTER COLUMN host TYPE character varying(128);
