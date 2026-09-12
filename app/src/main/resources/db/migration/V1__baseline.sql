CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

CREATE TABLE property (
    id              UUID PRIMARY KEY,
    legal_name      VARCHAR(255) NOT NULL,
    trade_name      VARCHAR(255),
    cnpj            VARCHAR(14),
    street          VARCHAR(255),
    city            VARCHAR(120),
    state           VARCHAR(2),
    postal_code     VARCHAR(8),
    time_zone       VARCHAR(50)  NOT NULL DEFAULT 'America/Fortaleza',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID
);

CREATE TABLE setting (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES property(id),
    setting_key     VARCHAR(100) NOT NULL,
    setting_value   TEXT         NOT NULL,
    value_type      VARCHAR(20)  NOT NULL
                    CHECK (value_type IN ('STRING','INTEGER','DECIMAL','BOOLEAN','TIME')),
    description     TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_setting_key UNIQUE (property_id, setting_key)
);

CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    property_id     UUID         NOT NULL REFERENCES property(id),
    full_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID,
    CONSTRAINT uk_app_user_email UNIQUE (email)
);

CREATE TABLE user_role (
    app_user_id     UUID        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role            VARCHAR(30) NOT NULL
                    CHECK (role IN ('ADMIN','FRONT_DESK','WAITER','KITCHEN')),
    PRIMARY KEY (app_user_id, role)
);
