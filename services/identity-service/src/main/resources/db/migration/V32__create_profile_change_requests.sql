CREATE TABLE profile_change_requests (
     id BIGSERIAL PRIMARY KEY,
     public_id UUID NOT NULL UNIQUE,

     version BIGINT NOT NULL DEFAULT 0,

     organization_id BIGINT NOT NULL,
     target_user_id BIGINT NOT NULL,
     requested_by_user_id BIGINT NOT NULL,

     original_first_name VARCHAR(100) NOT NULL,
     original_middle_name VARCHAR(100),
     original_last_name VARCHAR(100),
     original_email VARCHAR(150),
     original_phone VARCHAR(20),

     proposed_first_name VARCHAR(100) NOT NULL,
     proposed_middle_name VARCHAR(100),
     proposed_last_name VARCHAR(100),
     proposed_email VARCHAR(150),
     proposed_phone VARCHAR(20),

     reason VARCHAR(500) NOT NULL,

     status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

     decided_by_user_id BIGINT,
     decision_reason VARCHAR(500),
     decided_at TIMESTAMP WITH TIME ZONE,
     expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

     created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
     updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

     CONSTRAINT fk_profile_change_request_organization
         FOREIGN KEY (organization_id)
             REFERENCES organizations(id),

     CONSTRAINT fk_profile_change_request_target_user
         FOREIGN KEY (target_user_id)
             REFERENCES users(id),

     CONSTRAINT fk_profile_change_request_requester
         FOREIGN KEY (requested_by_user_id)
             REFERENCES users(id),

     CONSTRAINT fk_profile_change_request_decider
         FOREIGN KEY (decided_by_user_id)
             REFERENCES users(id),

     CONSTRAINT chk_profile_change_request_status
         CHECK (
             status IN (
                        'PENDING',
                        'APPROVED',
                        'REJECTED',
                        'CANCELLED',
                        'EXPIRED'
                 )
             ),

     CONSTRAINT chk_profile_change_request_different_users
         CHECK (target_user_id <> requested_by_user_id)
);

CREATE INDEX idx_profile_change_requests_target_status
    ON profile_change_requests (
                                organization_id,
                                target_user_id,
                                status
        );

CREATE INDEX idx_profile_change_requests_requester
    ON profile_change_requests (
                                organization_id,
                                requested_by_user_id
        );

CREATE UNIQUE INDEX uk_profile_change_requests_pending_target
    ON profile_change_requests (
                                organization_id,
                                target_user_id
        )
    WHERE status = 'PENDING';