ALTER TABLE school_provisioning
    ADD COLUMN last_error_code VARCHAR(100),
    ADD COLUMN last_error_field VARCHAR(150);