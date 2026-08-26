ALTER TABLE school_provisioning
    ADD COLUMN request_revision INTEGER NOT NULL DEFAULT 0;

ALTER TABLE school_provisioning
    ADD CONSTRAINT chk_school_provisioning_request_revision
        CHECK (request_revision >= 0);
