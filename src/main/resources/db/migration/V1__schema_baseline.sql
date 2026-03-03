CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id VARCHAR(6) NOT NULL,
    name VARCHAR(16) NOT NULL,
    kakao_bot_user_key VARCHAR(70) NOT NULL,
    kakao_plusfriend_user_key VARCHAR(70) NULL,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    warning_count INT NOT NULL DEFAULT 0,
    is_resting BOOLEAN NOT NULL DEFAULT FALSE,
    total_char_count BIGINT NOT NULL DEFAULT 0,
    total_adjustment_count BIGINT NOT NULL DEFAULT 0,
    email_reception BOOLEAN NOT NULL DEFAULT TRUE,
    email_auth_secret_hash CHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_student_id_deleted UNIQUE (student_id, deleted),
    CONSTRAINT uk_users_kakao_bot_user_key_deleted UNIQUE (kakao_bot_user_key, deleted),
    CONSTRAINT uk_users_kakao_plusfriend_user_key_deleted UNIQUE (kakao_plusfriend_user_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE participation_applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id VARCHAR(6) NOT NULL,
    name VARCHAR(16) NOT NULL,
    kakao_bot_user_key VARCHAR(70) NOT NULL,
    status VARCHAR(16) NOT NULL,
    rejection_reason TEXT NULL,
    processed_at TIMESTAMP(6) NULL,
    processed_by_user_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE works (
    id BIGINT NOT NULL AUTO_INCREMENT,
    work_type VARCHAR(16) NOT NULL,
    work_key VARCHAR(50) NOT NULL,
    assigned_user_id BIGINT NOT NULL,
    assigned_at TIMESTAMP(6) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP(6) NULL,
    char_count INT NULL,
    reminded_48h_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_works_work_type_work_key_deleted UNIQUE (work_type, work_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dictionary_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    korean VARCHAR(200) NOT NULL,
    english VARCHAR(200) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_dictionary_entries_korean_english_deleted UNIQUE (korean, english, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE adjustments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    delta_chars INT NOT NULL,
    reason TEXT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE warnings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL,
    reason TEXT NOT NULL,
    issued_by_user_id BIGINT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    canceled_at TIMESTAMP(6) NULL,
    canceled_by_user_id BIGINT NULL,
    cancel_reason TEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE system_phase (
    id BIGINT NOT NULL,
    phase VARCHAR(16) NOT NULL,
    phase_started_at DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    issue_type VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_tokens_token_hash_deleted UNIQUE (token_hash, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE email_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_student_id VARCHAR(6) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    related_user_id BIGINT NULL,
    related_work_id BIGINT NULL,
    created_by_user_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE assignment_cursor (
    id BIGINT NOT NULL,
    last_assigned_user_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE kakao_request_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    endpoint VARCHAR(128) NOT NULL,
    idempotency_key CHAR(64) NOT NULL,
    response_json TEXT NULL,
    bot_user_key VARCHAR(70) NOT NULL,
    action_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_kakao_request_logs_endpoint_idempotency_key UNIQUE (endpoint, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_works_assigned_user_completed_deleted ON works (assigned_user_id, completed, deleted);
CREATE INDEX idx_works_assigned_at_completed_deleted ON works (assigned_at, completed, deleted);
CREATE INDEX idx_works_reminded_48h_at ON works (reminded_48h_at);

CREATE INDEX idx_participation_applications_status_created_at ON participation_applications (status, created_at);
CREATE INDEX idx_participation_applications_student_id ON participation_applications (student_id);

CREATE INDEX idx_dictionary_entries_korean_deleted ON dictionary_entries (korean, deleted);
CREATE INDEX idx_dictionary_entries_english_deleted ON dictionary_entries (english, deleted);

CREATE INDEX idx_warnings_user_id_canceled_at ON warnings (user_id, canceled_at);

INSERT INTO system_phase (id, phase, phase_started_at, created_at, updated_at)
VALUES (1, 'PHASE_0', CURRENT_DATE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO assignment_cursor (id, last_assigned_user_id, created_at, updated_at)
VALUES (1, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
