CREATE TABLE IF NOT EXISTS webauthn_credentials (
    id             BINARY(16)   NOT NULL PRIMARY KEY,
    user_id        BINARY(16)   NOT NULL,
    credential_id  VARCHAR(512) NOT NULL,
    public_key_cose TEXT        NOT NULL,
    sign_count     BIGINT       NOT NULL DEFAULT 0,
    transports     VARCHAR(255),
    user_handle    VARCHAR(255) NOT NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    CONSTRAINT uk_webauthn_credential_id UNIQUE (credential_id),
    INDEX idx_webauthn_credentials_user_id (user_id),
    INDEX idx_webauthn_credentials_credential_id (credential_id),
    CONSTRAINT fk_webauthn_credentials_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS webauthn_challenges (
    id           BINARY(16)   NOT NULL PRIMARY KEY,
    user_id      BINARY(16),
    type         VARCHAR(32)  NOT NULL,
    request_json TEXT         NOT NULL,
    challenge    VARCHAR(512) NOT NULL,
    used         BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at   DATETIME     NOT NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    INDEX idx_webauthn_challenges_user_id (user_id),
    INDEX idx_webauthn_challenges_expires_at (expires_at),
    CONSTRAINT fk_webauthn_challenges_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
