CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name VARCHAR(100) NOT NULL,
    device_token VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    last_seen_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_devices_status CHECK (status IN ('REGISTERED', 'ONLINE', 'OFFLINE'))
);

CREATE INDEX idx_devices_user_id ON devices(user_id);
CREATE INDEX idx_devices_last_seen_at ON devices(last_seen_at);

CREATE TABLE projects (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    runtime VARCHAR(50) NOT NULL,
    framework_type VARCHAR(50) NOT NULL,
    repo_url VARCHAR(500) NOT NULL,
    branch VARCHAR(120) NOT NULL,
    install_command VARCHAR(500) NULL,
    build_command VARCHAR(500) NULL,
    start_command VARCHAR(500) NOT NULL,
    output_directory VARCHAR(255) NULL,
    local_port INTEGER NOT NULL,
    env_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_projects_device_id ON projects(device_id);

CREATE TABLE deployments (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    public_url VARCHAR(500) NULL,
    started_at TIMESTAMPTZ NOT NULL,
    stopped_at TIMESTAMPTZ NULL,
    logs_pointer VARCHAR(500) NULL,
    last_reported_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_deployments_status CHECK (status IN ('STARTING', 'BUILDING', 'RUNNING', 'STOPPED', 'FAILED'))
);

CREATE INDEX idx_deployments_project_id ON deployments(project_id);
CREATE INDEX idx_deployments_status ON deployments(status);

CREATE TABLE domain_mappings (
    id UUID PRIMARY KEY,
    deployment_id UUID NOT NULL UNIQUE REFERENCES deployments(id) ON DELETE CASCADE,
    full_domain VARCHAR(255) NOT NULL UNIQUE,
    base_domain VARCHAR(255) NOT NULL,
    subdomain VARCHAR(63) NOT NULL,
    path_prefix VARCHAR(120) NULL,
    proxy_type VARCHAR(32) NOT NULL,
    target_port INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_domain_mapping_proxy_type CHECK (proxy_type IN ('NGINX', 'CADDY', 'INTERNAL_HTTP_PROXY')),
    CONSTRAINT chk_domain_mapping_status CHECK (status IN ('PENDING', 'ACTIVE', 'RELEASED', 'FAILED'))
);

CREATE INDEX idx_domain_mappings_full_domain ON domain_mappings(full_domain);
CREATE INDEX idx_domain_mappings_status ON domain_mappings(status);
