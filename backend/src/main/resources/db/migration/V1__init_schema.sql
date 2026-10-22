-- Academic Calendar: PostgreSQL schema (Flyway V1)
-- Conventions: emails lowercase, course codes uppercase, all timestamps timestamptz.

-- ============ Reference: terms, holidays, slot times ============

CREATE TABLE term (
    id          bigserial PRIMARY KEY,
    name        text NOT NULL UNIQUE,            -- e.g. 2026-27 Sem 1
    start_date  date NOT NULL,
    end_date    date NOT NULL,
    CHECK (end_date > start_date)
);

CREATE TABLE holiday (
    term_id  bigint NOT NULL REFERENCES term(id) ON DELETE CASCADE,
    day      date   NOT NULL,
    label    text,
    PRIMARY KEY (term_id, day)
);

-- Which hours each slot meets on each weekday (1 = Monday ... 7 = Sunday)
CREATE TABLE slot_time (
    slot_code   text     NOT NULL,
    weekday     smallint NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_time  time     NOT NULL,
    end_time    time     NOT NULL,
    PRIMARY KEY (slot_code, weekday),
    CHECK (end_time > start_time)
);

-- ============ People ============

CREATE TABLE app_user (
    id          bigserial PRIMARY KEY,
    email       text    NOT NULL UNIQUE CHECK (email = lower(email)),
    name        text    NOT NULL,
    role        text    NOT NULL CHECK (role IN ('STUDENT', 'FACULTY', 'ADMIN')),
    org_id      text,                              -- roll number / employee id
    dept_code   text,
    entry_year  integer CHECK (entry_year BETWEEN 1990 AND 2100),
    active      boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_app_user_group ON app_user (dept_code, entry_year) WHERE role = 'STUDENT';

-- ============ Courses ============

CREATE TABLE course (
    code               text PRIMARY KEY CHECK (code = upper(code)),
    title              text NOT NULL,
    instructor_names   text,                       -- display only
    term_id            bigint REFERENCES term(id)
);

-- Enforces "faculty can only target courses they teach"
CREATE TABLE course_instructor (
    user_id      bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    course_code  text   NOT NULL REFERENCES course(code) ON DELETE CASCADE,
    PRIMARY KEY (user_id, course_code)
);
CREATE INDEX idx_course_instructor_course ON course_instructor (course_code);

CREATE TABLE enrolment (
    id           bigserial PRIMARY KEY,
    user_id      bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    course_code  text   NOT NULL REFERENCES course(code),
    grp          text,                             -- e.g. D1, G7 (source TBD)
    status       text   NOT NULL DEFAULT 'ENRO',   -- ENRO, IPEN, APEN (meaning TBD)
    UNIQUE (user_id, course_code)
);
CREATE INDEX idx_enrolment_course ON enrolment (course_code);

CREATE TABLE course_slot (
    id           bigserial PRIMARY KEY,
    course_code  text NOT NULL REFERENCES course(code) ON DELETE CASCADE,
    grp          text NOT NULL DEFAULT '',         -- '' = whole course
    slot_code    text NOT NULL,
    room         text,
    UNIQUE (course_code, grp, slot_code)           -- upsert key for re-imports
);

CREATE TABLE exam (
    id           bigserial PRIMARY KEY,
    course_code  text NOT NULL REFERENCES course(code),
    exam_date    date NOT NULL,
    session      text NOT NULL DEFAULT '',         -- FN / AN / ...
    start_time   time,
    end_time     time,
    notes        text,
    UNIQUE (course_code, exam_date, session)       -- upsert key
);
CREATE INDEX idx_exam_course ON exam (course_code);

-- ============ Events ============

CREATE TABLE event (
    id           bigserial PRIMARY KEY,
    title        text NOT NULL,
    category     text,                             -- event, quiz, ...
    event_date   date NOT NULL,
    start_time   time,
    end_time     time,
    venue        text,
    description  text,
    created_by   bigint NOT NULL REFERENCES app_user(id),
    cancelled    boolean NOT NULL DEFAULT false,
    notify       boolean NOT NULL DEFAULT false,
    source_hash  text UNIQUE,                      -- imports only: hash of title+date+start+venue
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_event_date ON event (event_date);
CREATE INDEX idx_event_creator ON event (created_by);

CREATE TABLE event_audience (
    id           bigserial PRIMARY KEY,
    event_id     bigint NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    type         text   NOT NULL CHECK (type IN ('ALL', 'COURSES', 'GROUP')),
    course_code  text   REFERENCES course(code),
    dept_code    text,                             -- null = any department
    entry_year   integer,                          -- null = any year
    CHECK (
        (type = 'ALL'     AND course_code IS NULL AND dept_code IS NULL AND entry_year IS NULL) OR
        (type = 'COURSES' AND course_code IS NOT NULL AND dept_code IS NULL AND entry_year IS NULL) OR
        (type = 'GROUP'   AND course_code IS NULL AND (dept_code IS NOT NULL OR entry_year IS NOT NULL))
    )
);
CREATE INDEX idx_audience_event  ON event_audience (event_id);
CREATE INDEX idx_audience_course ON event_audience (course_code) WHERE type = 'COURSES';
CREATE INDEX idx_audience_group  ON event_audience (dept_code, entry_year) WHERE type = 'GROUP';

-- ============ Google Calendar sync ============

CREATE TABLE google_token (
    user_id                  bigint PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    refresh_token_encrypted  bytea NOT NULL,       -- key lives outside the DB (env secret / KMS)
    key_version              smallint NOT NULL DEFAULT 1,
    calendar_id              text,                 -- "University Timetable" calendar
    status                   text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    connected_at             timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now()
);

-- One row per synced item per student. Covers classes, exams and events.
CREATE TABLE calendar_link (
    id               bigserial PRIMARY KEY,
    user_id          bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    item_type        text   NOT NULL CHECK (item_type IN ('CLASS', 'EXAM', 'EVENT')),
    item_key         text   NOT NULL,              -- CLASS:PH101:D1:MON:PC-1 | EXAM:HS513:2026-11-20:FN | EVENT:42
    google_event_id  text   NOT NULL,
    fingerprint      text   NOT NULL,              -- hash of title/date/start/end/venue/cancelled
    last_synced_at   timestamptz,
    UNIQUE (user_id, item_key)
);

CREATE TABLE sync_task (
    id           bigserial PRIMARY KEY,
    user_id      bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    reason       text   CHECK (reason IN ('IMPORT', 'EVENT', 'ENROLMENT', 'MANUAL', 'BACKFILL')),
    status       text   NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    attempts     integer NOT NULL DEFAULT 0,
    next_run_at  timestamptz NOT NULL DEFAULT now(),
    last_error   text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    finished_at  timestamptz
);
-- Many changes for one student collapse into a single pending task
CREATE UNIQUE INDEX uq_sync_pending ON sync_task (user_id) WHERE status = 'PENDING';
-- Worker claim query: ... WHERE status = 'PENDING' AND next_run_at <= now() FOR UPDATE SKIP LOCKED
CREATE INDEX idx_sync_due ON sync_task (next_run_at) WHERE status = 'PENDING';

-- ============ Imports ============

CREATE TABLE import_job (
    id              bigserial PRIMARY KEY,
    user_id         bigint NOT NULL REFERENCES app_user(id),
    kind            text   CHECK (kind IN ('classes', 'exam', 'event', 'quiz', 'enrolment')),
    sheet           text,
    status          text   NOT NULL DEFAULT 'PREVIEW'
                    CHECK (status IN ('PREVIEW', 'CONFIRMED', 'FAILED', 'ABANDONED', 'EXPIRED')),
    blocked_reason  text   CHECK (blocked_reason IN ('GRID', 'ENROLMENT')),
    file_name       text,
    saved_count     integer,
    error           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL DEFAULT now() + interval '1 hour',
    confirmed_at    timestamptz
);
CREATE INDEX idx_import_job_expiry ON import_job (expires_at) WHERE status = 'PREVIEW';

-- Temporary parsed rows behind a preview; purged on confirm or expiry
CREATE TABLE import_row (
    id            bigserial PRIMARY KEY,
    import_id     bigint  NOT NULL REFERENCES import_job(id) ON DELETE CASCADE,
    line          integer NOT NULL,
    data          jsonb   NOT NULL,
    errors        jsonb   NOT NULL DEFAULT '[]',
    warnings      jsonb   NOT NULL DEFAULT '[]'
);
CREATE INDEX idx_import_row_import ON import_row (import_id, line);

-- Remembers the admin's column choices for the same headers
CREATE TABLE column_mapping (
    id              bigserial PRIMARY KEY,
    kind            text NOT NULL,
    header_hash     text NOT NULL,                 -- hash of the normalised header row
    mapping         jsonb NOT NULL,                -- {"Room": "venue", ...}
    created_by      bigint REFERENCES app_user(id),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (kind, header_hash)
);

-- ============ Audit ============

CREATE TABLE audit_log (
    id        bigserial PRIMARY KEY,
    user_id   bigint REFERENCES app_user(id),
    action    text NOT NULL,
    detail    text,
    at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_user_at ON audit_log (user_id, at DESC);
