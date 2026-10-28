-- Real Google Calendar connection per student.
-- The refresh token is stored encrypted (AES-GCM); the key is never in the database.

CREATE TABLE IF NOT EXISTS student_google (
    user_id            bigint PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    refresh_token_enc  text NOT NULL,
    calendar_id        text,
    status             text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    connected_at       timestamptz NOT NULL DEFAULT now(),
    last_sync_at       timestamptz,
    last_error         text
);

-- Which Google event belongs to which item, so later syncs update instead of duplicating.
CREATE TABLE IF NOT EXISTS student_gcal_item (
    user_id          bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    item_key         text   NOT NULL,
    google_event_id  text   NOT NULL,
    fingerprint      text   NOT NULL,
    PRIMARY KEY (user_id, item_key)
);
