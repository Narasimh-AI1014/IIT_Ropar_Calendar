-- Campus calendar: dates that apply to the whole campus (holidays, exam weeks,
-- no-class days, timetable swaps, fests). Shown on the student Campus tab.
CREATE TABLE IF NOT EXISTS campus_item (
    id           bigserial PRIMARY KEY,
    title        text NOT NULL,
    category     text NOT NULL CHECK (category IN ('ACADEMIC', 'EXAM', 'HOLIDAY', 'NOCLASS', 'SWAP', 'FEST', 'TALK')),
    start_date   date NOT NULL,
    end_date     date,
    applies_to   text NOT NULL DEFAULT 'All students',
    venue        text,
    description  text,
    follows_day  text,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (title, start_date)
);
CREATE INDEX IF NOT EXISTS idx_campus_item_dates ON campus_item (start_date, end_date);
