-- Core vs elective, and what each student chose to put in their Google Calendar.

ALTER TABLE course
    ADD COLUMN IF NOT EXISTS kind text NOT NULL DEFAULT 'CORE'
    CHECK (kind IN ('CORE', 'ELECTIVE'));

CREATE TABLE IF NOT EXISTS student_pick (
    user_id    bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    item_type  text   NOT NULL CHECK (item_type IN ('COURSE', 'EVENT', 'EXAM')),
    item_ref   text   NOT NULL,
    picked     boolean NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, item_type, item_ref)
);
