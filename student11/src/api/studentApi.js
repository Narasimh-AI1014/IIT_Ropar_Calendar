import { http } from "./http";
import { mockCourses, mockSlots, mockEvents } from "../mock/student";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

// --- mock helpers (only used when VITE_USE_MOCK=true) ---
let mockElectiveIds = [5]; // pretend database
const fake = (fn) => new Promise((resolve) => setTimeout(() => resolve(fn()), 400));

export const studentApi = {
  // All courses (core + electives) the student can see
  getCourses: () =>
    USE_MOCK ? fake(() => mockCourses) : http.get("/student/courses"),

  // Ids of electives this student chose
  getElectives: () =>
    USE_MOCK ? fake(() => [...mockElectiveIds]) : http.get("/student/me/electives"),

  // Save the chosen electives
  saveElectives: (ids) =>
    USE_MOCK
      ? fake(() => {
          mockElectiveIds = ids;
          return { ok: true };
        })
      : http.put("/student/me/electives", { courseIds: ids }),

  // The student's own timetable + events, already filtered
  getSchedule: () =>
    USE_MOCK
      ? fake(() => {
          const myCourseIds = mockCourses
            .filter((c) => c.type === "CORE" || mockElectiveIds.includes(c.id))
            .map((c) => c.id);
          return {
            slots: mockSlots.filter((s) => myCourseIds.includes(s.courseId)),
            events: mockEvents,
          };
        })
      : http.get("/student/me/schedule"),

  // Campus calendar: holidays, exam weeks, no-class days, timetable swaps.
  // Each item: { id, title, category, date, endDate, appliesTo, venue, description, followsDay }
  getCampus: () =>
    USE_MOCK ? fake(() => []) : http.get("/student/campus"),
};
