ALTER TABLE calendar_link
  ADD COLUMN IF NOT EXISTS course_code text,
  ADD COLUMN IF NOT EXISTS category text,
  ADD COLUMN IF NOT EXISTS event_date text,
  ADD COLUMN IF NOT EXISTS synced boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS status text DEFAULT 'ENRO',
  ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE calendar_link
  ALTER COLUMN item_type DROP NOT NULL,
  ALTER COLUMN item_key DROP NOT NULL,
  ALTER COLUMN fingerprint DROP NOT NULL;