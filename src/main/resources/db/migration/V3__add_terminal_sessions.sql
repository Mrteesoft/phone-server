CREATE TABLE terminal_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    display_name VARCHAR(120) NOT NULL,
    shell_type VARCHAR(50) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_directory VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_at TIMESTAMPTZ NULL,
    closed_at TIMESTAMPTZ NULL,
    last_activity_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_terminal_sessions_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'CLOSE_REQUESTED', 'CLOSED', 'FAILED'))
);

CREATE INDEX idx_terminal_sessions_user_id ON terminal_sessions(user_id);
CREATE INDEX idx_terminal_sessions_device_id ON terminal_sessions(device_id);
CREATE INDEX idx_terminal_sessions_status ON terminal_sessions(status);
CREATE INDEX idx_terminal_sessions_created_at ON terminal_sessions(created_at);

CREATE TABLE terminal_commands (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES terminal_sessions(id) ON DELETE CASCADE,
    sequence_number BIGINT NOT NULL,
    command_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    output_text TEXT NULL,
    working_directory VARCHAR(500) NULL,
    exit_code INTEGER NULL,
    timeout_seconds INTEGER NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_terminal_commands_session_sequence UNIQUE (session_id, sequence_number),
    CONSTRAINT chk_terminal_commands_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_terminal_commands_timeout
        CHECK (timeout_seconds IS NULL OR timeout_seconds BETWEEN 1 AND 3600)
);

CREATE INDEX idx_terminal_commands_session_id ON terminal_commands(session_id);
CREATE INDEX idx_terminal_commands_status ON terminal_commands(status);
CREATE INDEX idx_terminal_commands_created_at ON terminal_commands(created_at);
