# IIT Ropar Academic Calendar

A system that gives every IIT Ropar student a personal timetable (classes, exams, quizzes and campus events) and lets them add it to Google Calendar in one click. The admin office and faculty post the data once; the system works out which students each item applies to.

## The problem it solves

Timetables, exam schedules and event notices reach students as spreadsheets, emails and posters, and what applies differs per student: it depends on their courses, group, department and entry year. Students copy entries by hand and miss changes. This system keeps one source of truth and shows each student only what applies to them.

## Who uses it

| Role | What they do |
|---|---|
| **Student** | Signs in with their IIT Ropar Google account. Sees classes, exams and events, chooses electives, and syncs the schedule to Google Calendar (website and Chrome extension). |
| **Admin office** | Posts events to a chosen audience (everyone, selected courses, or a department and entry-year group). Uploads timetable, exam, event and enrolment spreadsheets; the system detects the sheet kind and shows a preview before saving. |
| **Faculty** | Posts quizzes and extra-class changes for the courses they teach; can edit or cancel them. |

## How it works

1. A person signs in with Google. The backend verifies the Google token and the `@iitrpr.ac.in` domain, then looks the email up in its own user table to get the role (student, faculty or admin).
2. The backend returns its own login token. Every later request uses it, so the system always knows who is asking and never trusts an id sent by the browser.
3. Admins and faculty post data. Each event stores its audience rules. A student's schedule is built by matching their enrolments, group, department and entry year against those rules.
4. When something changes, affected students are queued for a calendar sync. The sync compares the student's schedule with what was last pushed to Google Calendar and creates, updates or deletes only the differences.

## Components

| Part | Technology | Purpose |
|---|---|---|
| Student website | React, Vite, Tailwind | Timetable, courses, electives |
| Admin website | React, Vite, Tailwind | Create events, import spreadsheets, campus calendar |
| Faculty website | React, Vite, Tailwind | Post and manage quizzes and events for their courses |
| Chrome extension | Manifest V3 | Pushes the student's schedule to Google Calendar |
| Backend | Spring Boot 4, Java 21 | Login, schedule matching, imports, sync queue |
| Database | PostgreSQL 16, Flyway migrations | Users, courses, slots, enrolments, events, imports, sync state |
| Packaging | Docker Compose | Runs the database, backend and sites together |

Default local ports: student `5173`, admin `5175`, faculty `5176`, backend `8080` (API under `/api`, Swagger UI at `/swagger-ui.html`), database `5433`.

## Main data

Terms and holidays, users with roles, courses and instructors, enrolments with groups, weekly class slots and slot times, exams, events with audience rules, import jobs and their staged rows, and sync records (Google tokens, calendar links, a task queue).

## Configuration

| Variable | Used by | Meaning |
|---|---|---|
| `GOOGLE_CLIENT_ID` | backend | Web OAuth client ID; Google tokens must be issued for it |
| `GOOGLE_CLIENT_SECRET` | backend | Needed for server-side Calendar sync (optional if sync runs in the extension) |
| `ALLOWED_DOMAIN` | backend | Allowed email domain, default `iitrpr.ac.in` |
| `JWT_SECRET` | backend | Secret for the app's login tokens (32+ characters) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | backend | Database connection |
| `VITE_API_URL` | each site | API base URL, e.g. `/api` |
| `VITE_GOOGLE_CLIENT_ID` | each site | Same Web client ID |
| `VITE_USE_MOCK` | each site | `false` to use the real backend |

Each user must exist in the `app_user` table (with lowercase email and a role) before they can sign in.

## Current status

- **Working:** Google sign-in for students and admin; the student dashboard showing real courses, classes, exams and events.
- **Written but untested:** admin event creation, spreadsheet import, Calendar sync.
- **Not started:** faculty sign-in and posting, the campus calendar, the extension's connection to the backend (the extension currently uses sample data).

## Open decisions

- Which sync design to keep: browser extension (client-side) or server worker.
- The real meaning of enrolment statuses `ENRO`, `IPEN`, `APEN`.
- The university's real slot table (slot code to weekday and time).
- Whether faculty and admin accounts will be pre-loaded into `app_user`.
- Which real file layouts the admin office will upload.
