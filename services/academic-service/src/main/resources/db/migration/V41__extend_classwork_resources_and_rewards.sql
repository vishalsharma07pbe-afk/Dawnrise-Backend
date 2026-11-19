ALTER TABLE learning_links
    ADD COLUMN task_id UUID,
    ADD CONSTRAINT fk_learning_link_task
        FOREIGN KEY (post_id, task_id)
        REFERENCES learning_tasks(post_id, id)
        ON DELETE CASCADE;

ALTER TABLE learning_attachments
    ADD COLUMN removed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE learning_reward_settings (
    organization_id BIGINT NOT NULL,
    academic_year_id BIGINT NOT NULL REFERENCES academic_years(id),
    verified_homework_points INTEGER NOT NULL DEFAULT 0
        CHECK (verified_homework_points BETWEEN 0 AND 10000),
    version BIGINT NOT NULL DEFAULT 0,
    updated_by_user_id BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (organization_id, academic_year_id)
);
