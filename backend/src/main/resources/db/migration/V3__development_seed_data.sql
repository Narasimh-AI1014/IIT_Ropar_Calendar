-- =========================================================================
-- 1. SEED ACADEMIC TERMS & SYSTEM HOLIDAYS
-- =========================================================================
INSERT INTO term (name, start_date, end_date) 
VALUES ('2026-27 Sem 1', '2026-08-01', '2026-12-05');

-- Seed an operational holiday block during mid-term cycles
INSERT INTO holiday (term_id, day, label) 
VALUES (1, '2026-10-02', 'Gandhi Jayanti Break');

-- =========================================================================
-- 2. SEED SYSTEM USERS (ADMIN, FACULTY, STUDENTS)
-- =========================================================================
-- Admin Account Anchor Mapping
INSERT INTO app_user (email, name, role, org_id, active) 
VALUES ('admin@iitrpr.ac.in', 'Master System Administrator', 'ADMIN', 'ADM-001', true);

-- Faculty User Profile Mapping
INSERT INTO app_user (email, name, role, org_id, dept_code, active) 
VALUES ('ramesh@iitrpr.ac.in', 'Dr. Ramesh Kumar', 'FACULTY', 'EMP-204', 'CSE', true);

-- Student Users Matrix (Cohort breakdown matching index criteria rules)
INSERT INTO app_user (email, name, role, org_id, dept_code, entry_year, active) VALUES 
('aman.2024csb1001@iitrpr.ac.in', 'Aman Sharma', 'STUDENT', '2024CSB1001', 'CSE', 2024, true),
('priya.2023eeb1045@iitrpr.ac.in', 'Priya Patel', 'STUDENT', '2023EEB1045', 'EE', 2023, true),
('rahul.2024meb1102@iitrpr.ac.in', 'Rahul Verma', 'STUDENT', '2024MEB1102', 'ME', 2024, true);

-- =========================================================================
-- 3. SEED COURSES & INSTRUCTOR ROLE MAPPINGS
-- =========================================================================
INSERT INTO course (code, title, instructor_names, term_id) VALUES 
('CS513', 'Advanced Data Structures', 'Dr. Ramesh Kumar', 1),
('EE201', 'Signals and Systems', 'Dr. Amit Jaglan', 1),
('HS513', 'Technical Communication', 'Dr. Neha Sharma', 1);

-- Map Faculty user (Dr. Ramesh) to the Advanced Data Structures course matrix
INSERT INTO course_instructor (user_id, course_code) 
VALUES ((SELECT id FROM app_user WHERE email = 'ramesh@iitrpr.ac.in'), 'CS513');

-- =========================================================================
-- 4. SEED CORE STUDENT ENROLMENTS
-- =========================================================================
-- Enroll Aman (CSE 2024) in Advanced Data Structures and Technical Comm
INSERT INTO enrolment (user_id, course_code, grp, status) VALUES 
((SELECT id FROM app_user WHERE email = 'aman.2024csb1001@iitrpr.ac.in'), 'CS513', 'G1', 'ENRO'),
((SELECT id FROM app_user WHERE email = 'aman.2024csb1001@iitrpr.ac.in'), 'HS513', '', 'ENRO');

-- Enroll Priya (EE 2023) in Signals and Systems
INSERT INTO enrolment (user_id, course_code, grp, status) VALUES 
((SELECT id FROM app_user WHERE email = 'priya.2023eeb1045@iitrpr.ac.in'), 'EE201', 'D1', 'ENRO');

-- =========================================================================
-- 5. SEED TIMETABLE SLOTS AND WEEKLY TIME CONFIGURATIONS
-- =========================================================================
-- Base Timetable Slot mappings matching specific student groups
INSERT INTO course_slot (course_code, grp, slot_code, room) VALUES 
('CS513', 'G1', 'PC-1', 'LHC-102'),
('EE201', 'D1', 'PC-2', 'LHC-204'),
('HS513', '', 'G7', 'LHC-105');

-- Base Hours Definition entries mapping to standard weekly slot codes
INSERT INTO slot_time (slot_code, weekday, start_time, end_time) VALUES 
('PC-1', 1, '09:00:00', '09:55:00'), -- Monday 9 AM
('PC-1', 3, '09:00:00', '09:55:00'), -- Wednesday 9 AM
('PC-2', 2, '10:00:00', '10:55:00'), -- Tuesday 10 AM
('G7', 4, '14:00:00', '14:55:00');   -- Thursday 2 PM

-- =========================================================================
-- 6. SEED TARGETED ACADEMIC EXAMINATIONS
-- =========================================================================
-- Creates a single Mid-Sem exam entry for the CS513 baseline
INSERT INTO exam (course_code, exam_date, session, start_time, end_time, notes) 
VALUES ('CS513', '2026-11-20', 'FN', '09:30:00', '12:30:00', 'Mid-Semester Examination. Bring ID cards.');

-- =========================================================================
-- 7. SEED ACCREDITED EVENTS AND AUDIENCE TARGETS
-- =========================================================================
-- Create a custom event (Quiz 1) authored by the faculty member
INSERT INTO event (title, category, event_date, start_time, end_time, venue, description, created_by, cancelled, notify)
VALUES (
    'Surprise Quiz 1', 
    'quiz', 
    '2026-10-28', 
    '15:00:00', 
    '16:00:00', 
    'LHC-102', 
    'Evaluative quiz covering advanced tree structural properties.',
    (SELECT id FROM app_user WHERE email = 'ramesh@iitrpr.ac.in'),
    false,
    true
);

-- Establish dynamic audience mapping rules linking the event directly to the CS513 course matrix
INSERT INTO event_audience (event_id, type, course_code, dept_code, entry_year)
VALUES (
    (SELECT id FROM event WHERE title = 'Surprise Quiz 1'),
    'COURSES',
    'CS513',
    NULL,
    NULL
);
