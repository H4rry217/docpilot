CREATE TABLE IF NOT EXISTS docpilot_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    create_by VARCHAR(255),
    creator_id BIGINT,
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    update_by VARCHAR(255),
    updater_id BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_docpilot_user_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS docpilot_setting (
    setting_key VARCHAR(120) NOT NULL,
    setting_value VARCHAR(2048) NOT NULL,
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (setting_key)
);

CREATE TABLE IF NOT EXISTS docpilot_user_setting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    setting_key VARCHAR(120) NOT NULL,
    setting_value VARCHAR(4096) NOT NULL,
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    create_by VARCHAR(255),
    creator_id BIGINT,
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    update_by VARCHAR(255),
    updater_id BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_docpilot_user_setting_user_key UNIQUE (user_id, setting_key)
);

CREATE TABLE IF NOT EXISTS docpilot_user_identity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_id VARCHAR(120) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    email VARCHAR(255),
    display_name VARCHAR(80),
    create_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    create_by VARCHAR(255),
    creator_id BIGINT,
    update_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    update_by VARCHAR(255),
    updater_id BIGINT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uq_docpilot_user_identity_provider_subject UNIQUE (provider_id, subject)
);
