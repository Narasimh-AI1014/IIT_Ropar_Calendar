-- Standing preferences: which kinds of items a student wants in Google Calendar.
CREATE TABLE IF NOT EXISTS student_pref (
    user_id      bigint PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    want_quiz    boolean NOT NULL DEFAULT true,
    want_exam    boolean NOT NULL DEFAULT true,
    want_change  boolean NOT NULL DEFAULT true,   -- class changes / lecture alterations
    want_event   boolean NOT NULL DEFAULT false,  -- fests, talks, general events
    updated_at   timestamptz NOT NULL DEFAULT now()
);
