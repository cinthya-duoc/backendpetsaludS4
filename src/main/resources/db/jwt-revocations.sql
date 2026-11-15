CREATE TABLE IF NOT EXISTS jwt_revocations (
    jti VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    expires_at BIGINT NOT NULL,
    INDEX ix_jwt_revocations_expires_at (expires_at)
) ENGINE=InnoDB;
