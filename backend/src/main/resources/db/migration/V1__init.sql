-- V1__init.sql
-- Initial schema for the API Gateway. All four tables from the spec.
-- All timestamps are DATETIME(3) in UTC (app writes Instants as UTC).
-- All primary keys are BIGINT AUTO_INCREMENT.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
    created_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE apis (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id           BIGINT       NOT NULL,
    name               VARCHAR(100) NOT NULL,
    slug               VARCHAR(50)  NOT NULL,
    upstream_base_url  VARCHAR(500) NOT NULL,
    timeout_ms         INT          NOT NULL DEFAULT 10000,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_apis_slug (slug),
    CONSTRAINT fk_apis_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    KEY idx_apis_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE api_keys (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    api_id          BIGINT       NOT NULL,
    name            VARCHAR(100) NOT NULL,
    key_prefix      VARCHAR(16)  NOT NULL,
    key_hash        CHAR(64)     NOT NULL,
    capacity        INT          NOT NULL DEFAULT 10,
    refill_per_sec  DECIMAL(8,2) NOT NULL DEFAULT 5.00,
    status          ENUM('ACTIVE','REVOKED') NOT NULL DEFAULT 'ACTIVE',
    expires_at      DATETIME(3)  NULL,
    last_used_at    DATETIME(3)  NULL,
    created_at      DATETIME(3)  NOT NULL,
    revoked_at      DATETIME(3)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_keys_key_hash (key_hash),
    CONSTRAINT fk_api_keys_api FOREIGN KEY (api_id) REFERENCES apis (id) ON DELETE CASCADE,
    KEY idx_api_keys_api (api_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usage_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    api_id        BIGINT       NOT NULL,
    api_key_id    BIGINT       NULL,
    request_id    CHAR(36)     NOT NULL,
    method        VARCHAR(10)  NOT NULL,
    path          VARCHAR(500) NOT NULL,
    status_code   SMALLINT     NOT NULL,
    latency_ms    INT          NOT NULL,
    outcome       ENUM('SUCCESS','RATE_LIMITED','UNAUTHORIZED','UPSTREAM_ERROR','UPSTREAM_TIMEOUT','REJECTED') NOT NULL,
    created_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_usage_logs_api FOREIGN KEY (api_id) REFERENCES apis (id) ON DELETE CASCADE,
    KEY idx_usage_logs_api_created (api_id, created_at),
    KEY idx_usage_logs_key_created (api_key_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
