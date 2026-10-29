import { useRef, useState } from "react";
import * as XLSX from "xlsx";
import { Upload, FileSpreadsheet, Download, CircleCheck, AlertTriangle, Loader2, RotateCcw } from "lucide-react";
import { adminApi } from "../api/adminApi";

const REQUIRED = [
  "course_code", "course_name", "type", "program", "branch", "semester",
  "batch", "day", "start", "end", "room", "faculty",
];

const DAY_MAP = {
  mon: 1, monday: 1, tue: 2, tues: 2, tuesday: 2, wed: 3, wednesday: 3,
  thu: 4, thur: 4, thurs: 4, thursday: 4, fri: 5, friday: 5,
  1: 1, 2: 2, 3: 3, 4: 4, 5: 5,
};
const DAY_NAME = ["", "Mon", "Tue", "Wed", "Thu", "Fri"];
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

const TEMPLATE = `course_code,course_name,type,program,branch,semester,batch,day,start,end,room,faculty,valid_from,valid_to
CS501,Advanced Algorithms,CORE,MTech,CS,1,A,Mon,09:00,10:00,L-101,Dr. Meera Singh,2026-08-01,2026-11-30
CS601,Artificial Intelligence,ELECTIVE,MTech,CS,1,A,Tue,14:00,15:30,L-201,Dr. Anita Rao,2026-08-01,2026-11-30
`;

const toMin = (t) => {
  const [h, m] = t.split(":").map(Number);
  return h * 60 + m;
};

function normTime(v) {
  const m = String(v).trim().toUpperCase().match(/^(\d{1,2}):(\d{2})(?::\d{2})?\s*(AM|PM)?$/);
  if (!m) return null;
  let h = Number(m[1]);
  if (m[3] === "PM" && h < 12) h += 12;
  if (m[3] === "AM" && h === 12) h = 0;
  if (h > 23 || Number(m[2]) > 59) return null;
  return `${String(h).padStart(2, "0")}:${m[2]}`;
}

// Reads CSV or Excel into a list of plain objects
async function parseFile(file) {
  const wb = XLSX.read(await file.arrayBuffer(), { cellDates: true });
  const sheet = wb.Sheets[wb.SheetNames[0]];
  const raw = XLSX.utils.sheet_to_json(sheet, { defval: "", raw: false, dateNF: "yyyy-mm-dd" });
  return raw.map((r) => {
    const o = {};
    for (const [k, v] of Object.entries(r)) {
      o[k.trim().toLowerCase().replace(/\s+/g, "_")] = String(v).trim();
    }
    return o;
  });
}

// Checks every row, then looks for clashes between rows
function validate(rawRows) {
  const rows = rawRows.map((r, i) => {
    const errors = [];
    const day = DAY_MAP[String(r.day).toLowerCase()];
    const start = normTime(r.start);
    const end = normTime(r.end);
    const type = String(r.type).toUpperCase();

    REQUIRED.forEach((c) => {
      if (!r[c]) errors.push(`${c} is empty`);
    });
    if (r.day && !day) errors.push("day must be Mon to Fri");
    if (r.start && !start) errors.push("start time is not valid (use HH:mm)");
    if (r.end && !end) errors.push("end time is not valid (use HH:mm)");
    if (start && end && toMin(start) >= toMin(end)) errors.push("end must be after start");
    if (r.type && !["CORE", "ELECTIVE"].includes(type)) errors.push("type must be CORE or ELECTIVE");
    if (r.semester && !/^\d+$/.test(r.semester)) errors.push("semester must be a number");
    if (r.valid_from && !DATE_RE.test(r.valid_from)) errors.push("valid_from must be yyyy-mm-dd");
    if (r.valid_to && !DATE_RE.test(r.valid_to)) errors.push("valid_to must be yyyy-mm-dd");
    if (DATE_RE.test(r.valid_from || "") && DATE_RE.test(r.valid_to || "") && r.valid_from > r.valid_to) {
      errors.push("valid_to is before valid_from");
    }
    return { line: i + 2, d: r, day, start, end, type, errors };
  });

  for (let i = 0; i < rows.length; i++) {
    const a = rows[i];
    if (!a.day || !a.start || !a.end) continue;
    for (let j = i + 1; j < rows.length; j++) {
      const b = rows[j];
      if (!b.day || !b.start || !b.end || a.day !== b.day) continue;
      const overlap = toMin(a.start) < toMin(b.end) && toMin(b.start) < toMin(a.end);
      if (!overlap) continue;

      const sameGroup = ["program", "branch", "semester", "batch"].every((k) => a.d[k] === b.d[k]);
      const msgs = [];
      if (a.d.room && a.d.room === b.d.room) msgs.push(`room ${a.d.room} is double-booked`);
      if (a.d.faculty && a.d.faculty === b.d.faculty) msgs.push(`${a.d.faculty} has two classes at once`);
      if (sameGroup && a.type === "CORE" && b.type === "CORE") msgs.push("two core classes overlap for the same batch");

      msgs.forEach((m) => {
        a.errors.push(`Clash with line ${b.line}: ${m}`);
        b.errors.push(`Clash with line ${a.line}: ${m}`);
      });
    }
  }
  return rows;
}

export default function UploadTimetable() {
  const inputRef = useRef(null);
  const [fileName, setFileName] = useState("");
  const [rows, setRows] = useState(null);
  const [fatal, setFatal] = useState("");
  const [onlyErrors, setOnlyErrors] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedCount, setSavedCount] = useState(null);

  const reset = () => {
    setRows(null);
    setFileName("");
    setFatal("");
    setSavedCount(null);
    setOnlyErrors(false);
  };

  const handleFile = async (file) => {
    setFatal("");
    setSavedCount(null);
    setRows(null);
    if (!file) return;
    if (!/\.(csv|xlsx|xls)$/i.test(file.name)) {
      setFatal("Please upload a .csv or .xlsx file.");
      return;
    }
    try {
      const raw = await parseFile(file);
      if (raw.length === 0) {
        setFatal("The file has no data rows.");
        return;
      }
      const missing = REQUIRED.filter((c) => !(c in raw[0]));
      if (missing.length) {
        setFatal(`Missing columns: ${missing.join(", ")}. Download the template to see the format.`);
        return;
      }
      setFileName(file.name);
      setRows(validate(raw));
    } catch {
      setFatal("Could not read this file. Make sure it is a valid CSV or Excel file.");
    }
  };

  const downloadTemplate = () => {
    const url = URL.createObjectURL(new Blob([TEMPLATE], { type: "text/csv" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = "timetable-template.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  const confirm = async () => {
    setSaving(true);
    try {
      const clean = rows.map((r) => ({ ...r.d, type: r.type, day: r.day, start: r.start, end: r.end }));
      const res = await adminApi.confirmTimetable(clean);
      setSavedCount(res.saved);
      setRows(null);
    } catch (e) {
      setFatal(e.response?.data?.message || "Saving failed. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  const errorRows = rows ? rows.filter((r) => r.errors.length > 0).length : 0;
  const okRows = rows ? rows.length - errorRows : 0;
  const shown = rows ? (onlyErrors ? rows.filter((r) => r.errors.length > 0) : rows) : [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Upload timetable</h1>
          <p className="mt-1 text-sm text-gray-500">
            Upload a CSV or Excel file. We check every row and show problems before anything is saved.
          </p>
        </div>
        <button
          onClick={downloadTemplate}
          className="flex items-center gap-2 rounded-lg border bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm hover:bg-gray-50"
        >
          <Download className="h-4 w-4" /> Download template
        </button>
      </div>

      {fatal && (
        <div className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {fatal}
        </div>
      )}

      {/* Done */}
      {savedCount !== null && (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-8 text-center">
          <CircleCheck className="mx-auto h-10 w-10 text-emerald-600" />
          <h2 className="mt-3 text-lg font-semibold text-emerald-900">Timetable saved</h2>
          <p className="mt-1 text-sm text-emerald-800">{savedCount} classes were saved. Students will see them at their next sync.</p>
          <button onClick={reset} className="mt-5 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700">
            <RotateCcw className="h-4 w-4" /> Upload another file
          </button>
        </div>
      )}

      {/* Step 1: choose file */}
      {rows === null && savedCount === null && (
        <div
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDragging(false);
            handleFile(e.dataTransfer.files[0]);
          }}
          onClick={() => inputRef.current?.click()}
          className={`flex cursor-pointer flex-col items-center rounded-2xl border-2 border-dashed p-12 text-center transition ${
            dragging ? "border-brand-500 bg-brand-50" : "border-gray-300 bg-white hover:border-brand-400 hover:bg-gray-50"
          }`}
        >
          <div className="grid h-14 w-14 place-items-center rounded-2xl bg-brand-50 text-brand-600">
            <Upload className="h-7 w-7" />
          </div>
          <p className="mt-4 font-semibold text-gray-900">Drop your file here, or click to browse</p>
          <p className="mt-1 text-sm text-gray-500">CSV or Excel (.xlsx)</p>
          <input
            ref={inputRef}
            type="file"
            accept=".csv,.xlsx,.xls"
            className="hidden"
            onChange={(e) => {
              handleFile(e.target.files[0]);
              e.target.value = "";
            }}
          />
        </div>
      )}

      {/* Step 2: review */}
      {rows !== null && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2 rounded-lg bg-white px-3 py-2 text-sm shadow-sm ring-1 ring-gray-200">
              <FileSpreadsheet className="h-4 w-4 text-brand-600" /> {fileName}
            </div>
            <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-700">{rows.length} rows</span>
            <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-medium text-emerald-700">{okRows} ok</span>
            <span className={`rounded-full px-3 py-1 text-xs font-medium ${errorRows ? "bg-red-100 text-red-700" : "bg-gray-100 text-gray-500"}`}>
              {errorRows} with problems
            </span>
            {errorRows > 0 && (
              <label className="ml-auto flex cursor-pointer items-center gap-2 text-sm text-gray-600">
                <input type="checkbox" checked={onlyErrors} onChange={(e) => setOnlyErrors(e.target.checked)} className="accent-indigo-600" />
                Show only problems
              </label>
            )}
          </div>

          <div className="overflow-x-auto rounded-2xl border bg-white shadow-sm">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <tr>
                  {["Line", "Status", "Course", "Type", "Group", "Day", "Time", "Room", "Faculty", "Problems"].map((h) => (
                    <th key={h} className="whitespace-nowrap px-3 py-3 font-medium">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y">
                {shown.map((r) => {
                  const bad = r.errors.length > 0;
                  return (
                    <tr key={r.line} className={bad ? "bg-red-50/60" : ""}>
                      <td className="px-3 py-2 text-gray-400">{r.line}</td>
                      <td className="px-3 py-2">
                        {bad ? (
                          <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">Error</span>
                        ) : (
                          <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">OK</span>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-3 py-2">
                        <span className="font-medium text-gray-900">{r.d.course_code}</span>
                        <span className="block text-xs text-gray-500">{r.d.course_name}</span>
                      </td>
                      <td className="px-3 py-2 text-gray-700">{r.type}</td>
                      <td className="whitespace-nowrap px-3 py-2 text-gray-700">
                        {r.d.program} {r.d.branch} · Sem {r.d.semester} · {r.d.batch}
                      </td>
                      <td className="px-3 py-2 text-gray-700">{DAY_NAME[r.day] || r.d.day}</td>
                      <td className="whitespace-nowrap px-3 py-2 text-gray-700">
                        {r.start || r.d.start} - {r.end || r.d.end}
                      </td>
                      <td className="px-3 py-2 text-gray-700">{r.d.room}</td>
                      <td className="whitespace-nowrap px-3 py-2 text-gray-700">{r.d.faculty}</td>
                      <td className="min-w-[220px] px-3 py-2 text-xs text-red-700">
                        {r.errors.map((e) => (
                          <p key={e}>{e}</p>
                        ))}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <button onClick={reset} className="rounded-lg border bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
              Choose a different file
            </button>
            <div className="flex items-center gap-3">
              {errorRows > 0 && <p className="text-sm text-red-600">Fix the problems in your file, then upload it again.</p>}
              <button
                onClick={confirm}
                disabled={errorRows > 0 || saving}
                className="flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {saving && <Loader2 className="h-4 w-4 animate-spin" />}
                Confirm and save {rows.length} classes
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}