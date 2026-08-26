ALTER TABLE schools
    DROP CONSTRAINT IF EXISTS schools_email_key;

ALTER TABLE schools
    DROP CONSTRAINT IF EXISTS schools_phone_key;

CREATE INDEX idx_schools_email
    ON schools(email);

CREATE INDEX idx_schools_phone
    ON schools(phone);