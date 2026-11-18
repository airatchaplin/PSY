-- Local app users (replaces Keycloak). Passwords are BCrypt hashes.
CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    username        VARCHAR(64) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(128),
    email           VARCHAR(256),
    roles           TEXT[] NOT NULL DEFAULT ARRAY['THERAPIST']::TEXT[],
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_user_username_lower ON app_user (LOWER(username));

-- External subject id for JWT `sub` (was Keycloak sub).
ALTER TABLE therapist_profile RENAME COLUMN keycloak_sub TO auth_sub;
