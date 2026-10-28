-- =========================================================================
-- 1. STRENGTHEN DATA LAYER VALUE INTEGRITY FOR EVENT CATEGORIES
-- =========================================================================
-- Enforces the strict lowercase token values settled in our contract agreements
ALTER TABLE event 
ADD CONSTRAINT chk_event_category_tokens 
CHECK (category IN ('event', 'quiz', 'lecture_alteration', 'exam'));

-- =========================================================================
-- 2. ADD AN ORG_ID CHECK CONSTRAINT TO PREVENT CASING ANOMALIES
-- =========================================================================
-- Ensures student roll numbers and faculty employee IDs maintain consistent formatting
ALTER TABLE app_user 
ADD CONSTRAINT chk_org_id_uppercase 
CHECK (org_id = UPPER(org_id));

-- =========================================================================
-- 3. AUDIT TRAIL LOGGING ENHANCEMENT FOR LOGINS & IMPORTS
-- =========================================================================
-- Adds a calling_site column to the audit log to trace cross-portal origin details
ALTER TABLE audit_log 
ADD COLUMN calling_site TEXT;

-- =========================================================================
-- 4. PERFORMANCE TUNING INDEXES FOR WORKER CLAIMS & TARGET TIMETABLES
-- =========================================================================
-- Optimizes student schedule lookups on actively enrolled courses
CREATE INDEX IF NOT EXISTS idx_enrolment_active_user 
ON enrolment (user_id) 
WHERE status = 'ENRO';

-- Optimizes the background sync engine worker queue state selections
CREATE INDEX IF NOT EXISTS idx_sync_task_worker_claim 
ON sync_task (status, next_run_at) 
WHERE status = 'PENDING';
