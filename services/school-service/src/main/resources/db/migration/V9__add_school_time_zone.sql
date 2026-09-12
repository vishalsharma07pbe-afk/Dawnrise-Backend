ALTER TABLE schools
    ADD COLUMN time_zone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata';

ALTER TABLE schools
    ADD CONSTRAINT chk_school_time_zone_not_blank
        CHECK (BTRIM(time_zone_id) <> '');
