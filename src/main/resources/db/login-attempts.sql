CREATE TABLE IF NOT EXISTS auth_login_attempts (
    attempt_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    failures JSON NOT NULL,
    expires_at_ms BIGINT NOT NULL,
    INDEX ix_auth_login_attempts_expires_at (expires_at_ms)
) ENGINE=InnoDB;
