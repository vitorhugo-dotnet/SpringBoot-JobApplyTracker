CREATE TABLE export_schedules (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    format VARCHAR(20) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    time_of_day TIME NOT NULL,
    day_of_week INT NULL,
    day_of_month INT NULL,
    timezone VARCHAR(64) NOT NULL,
    destination VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    filters_json TEXT NULL,
    columns_json TEXT NULL,
    next_run_at DATETIME NULL,
    last_run_at DATETIME NULL,
    running BOOLEAN NOT NULL DEFAULT FALSE,
    running_since DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_export_schedules PRIMARY KEY (id),
    CONSTRAINT fk_export_schedules_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_export_schedules_user ON export_schedules (user_id);
-- Drives the scheduler poll: enabled schedules ordered by when they are next due.
CREATE INDEX idx_export_schedules_next_run ON export_schedules (enabled, next_run_at);

CREATE TABLE export_executions (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    schedule_id BINARY(16) NULL,
    schedule_name VARCHAR(120) NULL,
    trigger_type VARCHAR(20) NOT NULL,
    format VARCHAR(20) NOT NULL,
    destination VARCHAR(30) NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    record_count INT NULL,
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    file_name VARCHAR(255) NULL,
    file_id VARCHAR(255) NULL,
    file_url VARCHAR(2048) NULL,
    error_message VARCHAR(500) NULL,
    CONSTRAINT pk_export_executions PRIMARY KEY (id),
    CONSTRAINT fk_export_executions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- History outlives the schedule it came from: the run keeps the schedule name it was created with.
    CONSTRAINT fk_export_executions_schedule FOREIGN KEY (schedule_id) REFERENCES export_schedules (id) ON DELETE SET NULL
);

CREATE INDEX idx_export_executions_user ON export_executions (user_id, started_at);
CREATE INDEX idx_export_executions_schedule ON export_executions (schedule_id);
