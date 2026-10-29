import { useState } from "react";
import { CircleCheck, AlertTriangle, Loader2, RotateCcw } from "lucide-react";
import { adminApi } from "../api/adminApi";

const CATEGORIES = [
  ["EVENT", "Event"],
  ["QUIZ", "Quiz"],
  ["EXAM", "Exam"],
];

const AUDIENCES = [
  ["ALL", "Everyone", "All students"],
  ["COURSES", "Specific courses", "Only students enrolled in these courses"],
  ["GROUP", "Specific group", "By department and/or year of entry"],
];

const EMPTY = {
  title: "", category: "EVENT", date: "", start: "", end: "", venue: "", description: "",
  audience: "ALL", courses: "", departments: "", years: "", notify: true,
};

const toList = (s) => s.split(/[,\s]+/).map((x) => x.trim()).filter(Boolean);
const inputCls =
  "w-full rounded-lg border bg-white px-3 py-2 text-sm text-gray-900 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100";

function Field({ label, hint, children }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-gray-700">{label}</span>
      {children}
      {hint && <span className="mt-1 block text-xs text-gray-400">{hint}</span>}
    </label>
  );
}

function validate(f) {
  const errs = [];
  if (!f.title.trim()) errs.push("Please enter a title.");
  if (!f.date) errs.push("Please choose a date.");
  if (f.start && f.end && f.end <= f.start) errs.push("The end time must be after the start time.");
  if (f.end && !f.start) errs.push("Add a start time, or clear the end time.");
  if (f.audience === "COURSES" && toList(f.courses).length === 0) errs.push("Enter at least one course code.");
  if (f.audience === "GROUP") {
    if (toList(f.departments).length === 0 && toList(f.years).length === 0) errs.push("Enter a department, a year of entry, or both.");
    if (toList(f.years).some((y) => !/^\d{4}$/.test(y))) errs.push("Years must have 4 digits, for example 2025.");
  }
  return errs;
}

export default function CreateEvent() {
  const [f, setF] = useState(EMPTY);
  const [errors, setErrors] = useState([]);
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(null);
  const [serverError, setServerError] = useState("");

  const set = (key) => (e) => setF({ ...f, [key]: e.target.type === "checkbox" ? e.target.checked : e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setServerError("");
    const errs = validate(f);
    setErrors(errs);
    if (errs.length) return;

    const payload = {
      title: f.title.trim(),
      category: f.category,
      date: f.date,
      start: f.start || null,
      end: f.end || null,
      venue: f.venue.trim(),
      description: f.description.trim(),
      notify: f.notify,
      audience: {
        type: f.audience,
        courses: f.audience === "COURSES" ? toList(f.courses).map((c) => c.toUpperCase()) : [],
        departments: f.audience === "GROUP" ? toList(f.departments).map((d) => d.toUpperCase()) : [],
        entryYears: f.audience === "GROUP" ? toList(f.years).map(Number) : [],
      },
    };

    setSaving(true);
    try {
      const res = await adminApi.createEvent(payload);
      setDone({ title: payload.title, reached: res.reached });
    } catch (err) {
      setServerError(err.response?.data?.message || err.message || "Could not save the event. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  const again = () => {
    setF(EMPTY); setErrors([]); setDone(null); setServerError("");
  };

  if (done) {
    return (
      <div className="max-w-2xl rounded-2xl border border-emerald-200 bg-emerald-50 p-8 text-center">
        <CircleCheck className="mx-auto h-10 w-10 text-emerald-600" />
        <h2 className="mt-3 text-lg font-semibold text-emerald-900">Event posted</h2>
        <p className="mt-1 text-sm text-emerald-800">
          "{done.title}" was saved.{" "}
          {typeof done.reached === "number"
            ? `It reaches ${done.reached} students.`
            : "The students it applies to will get it on their calendar."}
        </p>
        <button onClick={again} className="mt-5 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700">
          <RotateCcw className="h-4 w-4" /> Create another event
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Create event</h1>
        <p className="mt-1 text-sm text-gray-500">
          Post a fest, quiz or exam. Choose who it is for, and the system adds it to those students' calendars.
        </p>
      </div>

      {(errors.length > 0 || serverError) && (
        <div className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <div>
            {errors.map((m) => <p key={m}>{m}</p>)}
            {serverError && <p>{serverError}</p>}
          </div>
        </div>
      )}

      <form onSubmit={submit} className="space-y-5 rounded-2xl border bg-white p-5 shadow-sm sm:p-6">
        <Field label="Title">
          <input value={f.title} onChange={set("title")} placeholder="e.g. Cultural Fest or CS Quiz 1" className={inputCls} />
        </Field>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Type">
            <select value={f.category} onChange={set("category")} className={inputCls}>
              {CATEGORIES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </Field>
          <Field label="Date">
            <input type="date" value={f.date} onChange={set("date")} className={inputCls} />
          </Field>
          <Field label="Start time" hint="Leave empty for an all-day event.">
            <input type="time" value={f.start} onChange={set("start")} className={inputCls} />
          </Field>
          <Field label="End time">
            <input type="time" value={f.end} onChange={set("end")} className={inputCls} />
          </Field>
        </div>

        <Field label="Venue">
          <input value={f.venue} onChange={set("venue")} placeholder="e.g. Main Ground" className={inputCls} />
        </Field>

        <Field label="Details" hint="Optional. Programme, performers, instructions.">
          <textarea value={f.description} onChange={set("description")} rows={3} className={inputCls} />
        </Field>

        <div>
          <p className="mb-2 text-sm font-medium text-gray-700">Who is this for?</p>
          <div className="space-y-2">
            {AUDIENCES.map(([v, label, text]) => (
              <label
                key={v}
                className={`flex cursor-pointer items-start gap-3 rounded-xl border-2 p-3 transition ${
                  f.audience === v ? "border-brand-500 bg-brand-50" : "border-gray-200 hover:border-brand-200"
                }`}
              >
                <input type="radio" name="audience" value={v} checked={f.audience === v} onChange={set("audience")} className="mt-1 accent-indigo-600" />
                <div>
                  <p className="text-sm font-medium text-gray-900">{label}</p>
                  <p className="text-xs text-gray-500">{text}</p>
                </div>
              </label>
            ))}
          </div>

          {f.audience === "COURSES" && (
            <div className="mt-3">
              <Field label="Course codes" hint="Separate with commas, for example CS301, MA201.">
                <input value={f.courses} onChange={set("courses")} placeholder="CS301, MA201" className={inputCls} />
              </Field>
            </div>
          )}

          {f.audience === "GROUP" && (
            <div className="mt-3 grid gap-4 sm:grid-cols-2">
              <Field label="Department codes" hint="For example CSE, ELE. Leave empty for all.">
                <input value={f.departments} onChange={set("departments")} placeholder="CSE, ELE" className={inputCls} />
              </Field>
              <Field label="Year of entry" hint="For example 2025. Leave empty for all.">
                <input value={f.years} onChange={set("years")} placeholder="2025" className={inputCls} />
              </Field>
            </div>
          )}
        </div>

        <label className="flex cursor-pointer items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={f.notify} onChange={set("notify")} className="h-4 w-4 accent-indigo-600" />
          Notify students that this was added
        </label>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={saving}
            className="flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-700 disabled:opacity-50"
          >
            {saving && <Loader2 className="h-4 w-4 animate-spin" />}
            Post event
          </button>
        </div>
      </form>
    </div>
  );
}