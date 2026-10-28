-- Remember each item's date so past events stay in the calendar after they are over.
ALTER TABLE student_gcal_item ADD COLUMN IF NOT EXISTS item_date date;
