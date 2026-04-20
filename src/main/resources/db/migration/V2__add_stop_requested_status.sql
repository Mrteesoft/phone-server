ALTER TABLE deployments DROP CONSTRAINT chk_deployments_status;

ALTER TABLE deployments
    ADD CONSTRAINT chk_deployments_status
    CHECK (status IN ('STARTING', 'BUILDING', 'RUNNING', 'STOP_REQUESTED', 'STOPPED', 'FAILED'));
