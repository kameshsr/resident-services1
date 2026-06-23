-- Below script is required to upgrade from 1.3.1 to 1.4.0
-- MOSIP-41105: ip_address and host in resident.resident_session are now stored
-- encrypted (reversible keymanager encryption via ResidentEntityInterceptor).
-- The ciphertext is longer than the original plaintext, so the columns must be
-- widened to hold it.

\c mosip_resident

ALTER TABLE resident.resident_session ALTER COLUMN ip_address TYPE character varying(1024);
ALTER TABLE resident.resident_session ALTER COLUMN host TYPE character varying(1024);
