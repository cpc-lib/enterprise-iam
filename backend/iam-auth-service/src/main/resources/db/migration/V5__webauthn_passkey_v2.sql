-- Owner: iam-auth-service / iam_auth
-- V2.0 WebAuthn / FIDO2 Passkey credentials and ceremony challenges

CREATE TABLE iam_webauthn_credential (
    id              BIGINT PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    credential_id   VARCHAR(255) NOT NULL,
    public_key_cose TEXT NOT NULL,
    sign_count      BIGINT NOT NULL DEFAULT 0,
    aaguid          VARCHAR(64) NULL,
    transports      VARCHAR(255) NULL,
    device_name     VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_used_at    TIMESTAMP(6) NULL,
    CONSTRAINT ck_iam_webauthn_cred_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT uk_iam_webauthn_cred UNIQUE (tenant_id, credential_id),
    KEY idx_iam_webauthn_cred_user (tenant_id, user_id, status)
);

CREATE TABLE iam_webauthn_challenge (
    challenge_id    VARCHAR(64) PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    user_id         BIGINT NULL,
    username        VARCHAR(128) NULL,
    ceremony_type   VARCHAR(32) NOT NULL,
    challenge_b64   VARCHAR(255) NOT NULL,
    rp_id           VARCHAR(255) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    expires_at      TIMESTAMP(6) NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_iam_webauthn_ceremony CHECK (ceremony_type IN ('REGISTRATION', 'AUTHENTICATION')),
    CONSTRAINT ck_iam_webauthn_chl_status CHECK (status IN ('PENDING', 'CONSUMED', 'EXPIRED')),
    KEY idx_iam_webauthn_chl_tenant (tenant_id, status, expires_at)
);
