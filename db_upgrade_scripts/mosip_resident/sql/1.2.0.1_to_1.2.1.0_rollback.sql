\c mosip_resident

REVOKE SELECT, INSERT, REFERENCES, UPDATE, DELETE
ON resident.otp_transaction
TO residentuser;

REVOKE SELECT, INSERT, REFERENCES, UPDATE, DELETE
ON resident.resident_grievance_ticket
TO residentuser;

ALTER TABLE resident.resident_session alter column machine_type type varchar(30);

-- Restoring ip_address and host columns
ALTER TABLE resident.resident_session ADD COLUMN IF NOT EXISTS ip_address character varying(128);
ALTER TABLE resident.resident_session ADD COLUMN IF NOT EXISTS host character varying(128);
COMMENT ON COLUMN resident.resident_session.ip_address IS 'The ip_address of device from which the user logged in';
COMMENT ON COLUMN resident.resident_session.host IS 'The host of the site';

DROP INDEX IF EXISTS idx_resident_user_actions_ida_token;

REVOKE SELECT, INSERT, REFERENCES, UPDATE, DELETE
   ON resident.resident_user_actions
   TO residentuser;