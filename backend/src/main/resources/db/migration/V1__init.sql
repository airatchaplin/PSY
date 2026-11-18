CREATE TABLE therapist_profile (
    id              UUID PRIMARY KEY,
    keycloak_sub    VARCHAR(128) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    email           VARCHAR(255),
    disclaimer_accepted_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE client (
    id                  UUID PRIMARY KEY,
    therapist_id        UUID NOT NULL REFERENCES therapist_profile(id),
    name                VARCHAR(255) NOT NULL,
    age                 INT NOT NULL,
    presenting_request  TEXT NOT NULL,
    difficulty          VARCHAR(32) NOT NULL,
    behavior_tags       TEXT[] NOT NULL DEFAULT '{}',
    defenses            TEXT[] NOT NULL DEFAULT '{}',
    persona_json        JSONB NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    case_id             VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_client_therapist ON client(therapist_id);

CREATE TABLE therapy_session (
    id              UUID PRIMARY KEY,
    client_id       UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    therapist_id    UUID NOT NULL REFERENCES therapist_profile(id),
    status          VARCHAR(32) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_session_client ON therapy_session(client_id);
CREATE UNIQUE INDEX uq_client_active_session
    ON therapy_session(client_id)
    WHERE status = 'ACTIVE';

CREATE TABLE message (
    id              UUID PRIMARY KEY,
    session_id      UUID NOT NULL REFERENCES therapy_session(id) ON DELETE CASCADE,
    client_id       UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    role            VARCHAR(32) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_message_client_created ON message(client_id, created_at);
CREATE INDEX idx_message_session ON message(session_id, created_at);

CREATE TABLE therapist_note (
    id              UUID PRIMARY KEY,
    therapist_id    UUID NOT NULL REFERENCES therapist_profile(id),
    client_id       UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    session_id      UUID REFERENCES therapy_session(id) ON DELETE SET NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_note_client ON therapist_note(client_id);

CREATE TABLE supervisor_thread (
    id              UUID PRIMARY KEY,
    therapist_id    UUID NOT NULL REFERENCES therapist_profile(id),
    client_id       UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    session_id      UUID REFERENCES therapy_session(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_supervisor_thread_client ON supervisor_thread(therapist_id, client_id);

CREATE TABLE supervisor_message (
    id              UUID PRIMARY KEY,
    thread_id       UUID NOT NULL REFERENCES supervisor_thread(id) ON DELETE CASCADE,
    role            VARCHAR(32) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_supervisor_message_thread ON supervisor_message(thread_id, created_at);

CREATE TABLE session_analysis (
    id              UUID PRIMARY KEY,
    therapist_id    UUID NOT NULL REFERENCES therapist_profile(id),
    client_id       UUID REFERENCES client(id) ON DELETE CASCADE,
    session_id      UUID REFERENCES therapy_session(id) ON DELETE CASCADE,
    scope           VARCHAR(32) NOT NULL,
    report_md       TEXT NOT NULL,
    rubric_json     JSONB NOT NULL,
    alternatives_json JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_therapist ON session_analysis(therapist_id, created_at);
CREATE INDEX idx_analysis_session ON session_analysis(session_id);
