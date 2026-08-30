ALTER TABLE schools
    ADD COLUMN motto VARCHAR(180),
    ADD COLUMN tagline VARCHAR(240),
    ADD COLUMN logo_data BYTEA,
    ADD COLUMN logo_content_type VARCHAR(50);

ALTER TABLE schools
    ADD CONSTRAINT chk_school_logo_pair CHECK (
        (logo_data IS NULL AND logo_content_type IS NULL)
        OR (logo_data IS NOT NULL AND logo_content_type IS NOT NULL)
    );
