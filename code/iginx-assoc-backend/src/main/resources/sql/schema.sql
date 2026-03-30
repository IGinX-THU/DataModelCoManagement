-- Users Table
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Data Resources
CREATE TABLE IF NOT EXISTS sys_data_resource (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    conn_config TEXT NOT NULL,
    time_range JSONB,
    description VARCHAR(255),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Meta Model Profiles
CREATE TABLE IF NOT EXISTS meta_model_profile (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    developer VARCHAR(50),
    usage_scope VARCHAR(200),
    io_schema JSONB NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Model Assets
CREATE TABLE IF NOT EXISTS model_asset (
    id BIGSERIAL PRIMARY KEY,
    profile_id BIGINT REFERENCES meta_model_profile(id),
    file_name VARCHAR(100) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    storage_path VARCHAR(255) NOT NULL,
    version VARCHAR(20) NOT NULL,
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    file_md5 CHAR(32) NOT NULL,
    file_size BIGINT DEFAULT 0,
    io_schema JSONB,
    function_list JSONB,
    is_latest BOOLEAN DEFAULT FALSE
);

-- Association Rules
CREATE TABLE IF NOT EXISTS association_rule (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    model_id BIGINT REFERENCES model_asset(id),
    function_name VARCHAR(120),
    output_target JSONB NOT NULL,
    mapping_json JSONB NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tasks
CREATE TABLE IF NOT EXISTS task (
    id VARCHAR(32) PRIMARY KEY,
    rule_id BIGINT REFERENCES association_rule(id),
    status VARCHAR(20) NOT NULL,
    range_start TIMESTAMP,
    range_end TIMESTAMP,
    scheduled_start_time TIMESTAMP,
    scheduled_end_time TIMESTAMP,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    result_link VARCHAR(255),
    execution_snapshot JSONB,
    exec_log TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Data Export Tasks
CREATE TABLE IF NOT EXISTS data_export_task (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT,
    export_type VARCHAR(20),
    format VARCHAR(20),
    status VARCHAR(20),
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    request_json TEXT,
    error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- External Jobs
CREATE TABLE IF NOT EXISTS external_job (
    id VARCHAR(32) PRIMARY KEY,
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_json TEXT,
    result_json TEXT,
    error_code VARCHAR(64),
    error_message TEXT,
    trace_id VARCHAR(128),
    submit_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    start_time TIMESTAMP,
    finish_time TIMESTAMP
);

-- 兼容旧版本表结构：若表已存在则补充新增列
ALTER TABLE IF EXISTS model_asset
    ADD COLUMN IF NOT EXISTS function_list JSONB;

ALTER TABLE IF EXISTS association_rule
    ADD COLUMN IF NOT EXISTS function_name VARCHAR(120);

ALTER TABLE IF EXISTS association_rule
    DROP COLUMN IF EXISTS trigger_type;

ALTER TABLE IF EXISTS task
    ADD COLUMN IF NOT EXISTS execution_snapshot JSONB;

ALTER TABLE IF EXISTS task
    ADD COLUMN IF NOT EXISTS scheduled_start_time TIMESTAMP;

ALTER TABLE IF EXISTS task
    ADD COLUMN IF NOT EXISTS scheduled_end_time TIMESTAMP;
