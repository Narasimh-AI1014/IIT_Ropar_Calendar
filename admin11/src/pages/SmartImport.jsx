import { useRef, useState } from "react";
import { Upload, FileSpreadsheet, CircleCheck, AlertTriangle, Info, Loader2, RotateCcw } from "lucide-react";
import { adminApi } from "../api/adminApi";

const KINDS = [
  ["auto", "Let the server detect"],
  ["classes", "Weekly class timetable"],
  ["exam", "Exam schedule"],
  ["event", "Event or programme"],
  ["quiz", "Quiz"],
  ["enrolment", "Student enrolments"],
];
const KIND_LABEL = Object.fromEntries(KINDS);
const DAY_NAME = ["", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const errMsg = (e) => e.response?.data?.message || e.message || "Something went wrong.";

function Banner({ tone = "amber", icon: Icon = AlertTriangle, children }) {
  const cls =
    tone === "red" ? "border-red-200 bg-red-50 text-red-700"
    : tone === "blue" ? "border-brand-200 bg-brand-50 text-brand-700"
    : "border-amber-200 bg-amber-50 text-amber-800";
  return (
    <div className={`flex items-start gap-2 rounded-xl border p-3 text-sm ${cls}`}>
      <Icon className="mt-0.5 h-4 w-4 shrink-0" /> <div>{children}</div>
    </div>
  );
}

export default function SmartImport() {
  const inputRef = useRef(null);
  const [kind, setKind] = useState("auto");
  const [upload, setUpload] = useState(null); // { importId, fileName, sheets, demo }
  const [analysis, setAnalysis] = useState(null); // what the server found
  const [sheet, setSheet] = useState("");
  const [busy, setBusy] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(null);
  const [error, setError] = useState("");
  const [dragging, setDragging] = useState(false);
  const [onlyProblems, setOnlyProblems] = useState(false);

  const reset = () => {
    setUpload(null); setAnalysis(null); setSaved(null); setError(""); setOnlyProblems(false); setSheet("");
  };

  const handleFile = async (file) => {
    setError(""); setSaved(null);
    if (!file) return;
    if (!/\.(csv|tsv|xlsx|xls|xlsm|ods)$/i.test(file.name)) {
      setError("Please upload a CSV or Excel file.");
      return;
    }
    setBusy(true);
    try {
      const res = await adminApi.uploadImport(file, kind);
      setUpload({ importId: res.importId, fileName: res.fileName, sheets: res.sheets || [], demo: !!res.demo });
      setAnalysis(res.analysis);
      setSheet(res.analysis.sheet);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setBusy(false);
    }
  };

  const reanalyze = async (next) => {
    setBusy(true); setError("");
    try {
      setAnalysis(await adminApi.analyzeImport(upload.importId, next));
      setSheet(next.sheet);
      setKind(next.kind);
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setBusy(false);
    }
  };

  const confirm = async () => {
    setSaving(true); setError("");
    try {
      const res = await adminApi.confirmImport(upload.importId, { sheet, kind });
      setSaved(res.saved);
      setUpload(null); setAnalysis(null);
    } catch (e) {
      setError("Saving failed: " + errMsg(e));
    } finally {
      setSaving(false);
    }
  };

  const sum = analysis?.summary;
  const blocked = !!analysis?.blockedReason;
  const rows = analysis?.rows || [];
  const shown = onlyProblems ? rows.filter((r) => r.errors?.length || r.warnings?.length) : rows;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Import data</h1>
        <p className="mt-1 text-sm text-gray-500">
          Upload a timetable, exam schedule, event list, quiz or enrolment file. The server reads it and shows what it found. You check it, then confirm.
        </p>
      </div>

      {error && <Banner tone="red">{error}</Banner>}

      {saved !== null && (
        <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-8 text-center">
          <CircleCheck className="mx-auto h-10 w-10 text-emerald-600" />
          <h2 className="mt-3 text-lg font-semibold text-emerald-900">Import saved</h2>
          <p className="mt-1 text-sm text-emerald-800">{saved} items were saved. Students will see them at their next sync.</p>
          <button onClick={reset} className="mt-5 inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700">
            <RotateCcw className="h-4 w-4" /> Import another file
          </button>
        </div>
      )}

      {!upload && saved === null && (
        <div className="space-y-4">
          <label className="flex flex-wrap items-center gap-3 text-sm text-gray-700">
            What is in this file?
            <select value={kind} onChange={(e) => setKind(e.target.value)} className="rounded-lg border bg-white px-3 py-2 text-sm">
              {KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
            <span className="text-xs text-gray-400">If unsure, leave it on "Let the server detect".</span>
          </label>

          <div
            onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => { e.preventDefault(); setDragging(false); handleFile(e.dataTransfer.files[0]); }}
            onClick={() => inputRef.current?.click()}
            className={`flex cursor-pointer flex-col items-center rounded-2xl border-2 border-dashed p-12 text-center transition ${
              dragging ? "border-brand-500 bg-brand-50" : "border-gray-300 bg-white hover:border-brand-500 hover:bg-gray-50"
            }`}
          >
            <div className="grid h-14 w-14 place-items-center rounded-2xl bg-brand-50 text-brand-600">
              {busy ? <Loader2 className="h-7 w-7 animate-spin" /> : <Upload className="h-7 w-7" />}
            </div>
            <p className="mt-4 font-semibold text-gray-900">{busy ? "Uploading and reading..." : "Drop a file here, or click to browse"}</p>
            <p className="mt-1 text-sm text-gray-500">CSV or Excel</p>
            <input
              ref={inputRef}
              type="file"
              accept=".csv,.tsv,.xlsx,.xls,.xlsm,.ods"
              className="hidden"
              onChange={(e) => { handleFile(e.target.files[0]); e.target.value = ""; }}
            />
          </div>
        </div>
      )}

      {upload && analysis && (
        <>
          {upload.demo && (
            <Banner tone="blue" icon={Info}>
              Demo mode: this is sample data and your file was not read. When the backend is connected, this screen shows what the server really found in your file.
            </Banner>
          )}

          <div className="flex flex-wrap items-center gap-3 rounded-2xl border bg-white p-4 shadow-sm">
            <div className="flex items-center gap-2 text-sm font-medium text-gray-800">
              <FileSpreadsheet className="h-4 w-4 text-brand-600" /> {upload.fileName}
            </div>

            {upload.sheets.length > 1 && (
              <label className="flex items-center gap-2 text-sm text-gray-600">
                Sheet
                <select
                  value={sheet}
                  disabled={busy}
                  onChange={(e) => reanalyze({ sheet: e.target.value, kind })}
                  className="max-w-[240px] rounded-lg border bg-white px-2 py-1.5 text-sm"
                >
                  {upload.sheets.map((s) => (
                    <option key={s.name} value={s.name}>{s.name} ({s.rows} rows)</option>
                  ))}
                </select>
              </label>
            )}

            <label className="flex items-center gap-2 text-sm text-gray-600">
              This file is
              <select
                value={kind}
                disabled={busy}
                onChange={(e) => reanalyze({ sheet, kind: e.target.value })}
                className="rounded-lg border bg-white px-2 py-1.5 text-sm"
              >
                {KINDS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            </label>

            {busy && <Loader2 className="h-4 w-4 animate-spin text-brand-600" />}

            <span className="ml-auto rounded-full bg-brand-50 px-3 py-1 text-xs font-medium text-brand-700">
              Server detected: {KIND_LABEL[analysis.kind] || analysis.kind}
            </span>
          </div>

          {blocked && (
            <Banner tone="red">
              {analysis.blockedMessage || "The server cannot import this sheet as it is. Pick another sheet or file."}
            </Banner>
          )}
          {(analysis.issues || []).map((i) => <Banner key={i}>{i}</Banner>)}

          <section>
            <div className="mb-2 flex flex-wrap items-center gap-3">
              <h2 className="font-semibold text-gray-900">What the server found</h2>
              <span className="rounded-full bg-gray-100 px-3 py-1 text-xs font-medium text-gray-700">{sum.total} rows</span>
              <span className="rounded-full bg-emerald-100 px-3 py-1 text-xs font-medium text-emerald-700">{sum.ready} ready</span>
              {sum.withNotes > 0 && <span className="rounded-full bg-amber-100 px-3 py-1 text-xs font-medium text-amber-700">{sum.withNotes} with notes</span>}
              {sum.withProblems > 0 && <span className="rounded-full bg-red-100 px-3 py-1 text-xs font-medium text-red-700">{sum.withProblems} with problems</span>}
              <label className="ml-auto flex cursor-pointer items-center gap-2 text-sm text-gray-600">
                <input type="checkbox" checked={onlyProblems} onChange={(e) => setOnlyProblems(e.target.checked)} className="accent-indigo-600" />
                Show only rows with notes or problems
              </label>
            </div>

            <div className="overflow-x-auto rounded-2xl border bg-white shadow-sm">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                  <tr>
                    {["Line", "Status", "Title", "When", "Venue", "Type", "People", "Notes"].map((h) => (
                      <th key={h} className="whitespace-nowrap px-3 py-3 font-medium">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {shown.map((r) => {
                    const hasErr = r.errors?.length > 0;
                    const hasWarn = r.warnings?.length > 0;
                    return (
                      <tr key={r.line} className={hasErr ? "bg-red-50/60" : ""}>
                        <td className="px-3 py-2 text-gray-400">{r.line}</td>
                        <td className="px-3 py-2">
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${hasErr ? "bg-red-100 text-red-700" : hasWarn ? "bg-amber-100 text-amber-700" : "bg-emerald-100 text-emerald-700"}`}>
                            {hasErr ? "Error" : hasWarn ? "Check" : "OK"}
                          </span>
                        </td>
                        <td className="min-w-[180px] px-3 py-2 font-medium text-gray-900">{r.title || "-"}</td>
                        <td className="whitespace-nowrap px-3 py-2 text-gray-700">
                          {analysis.kind === "classes" ? DAY_NAME[r.day] || "-" : r.date || "-"}
                          <span className="block text-xs text-gray-500">{r.start ? `${r.start} - ${r.end || "?"}` : "all day"}</span>
                        </td>
                        <td className="px-3 py-2 text-gray-700">{r.venue}</td>
                        <td className="px-3 py-2">
                          {r.category && <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-700">{r.category}</span>}
                        </td>
                        <td className="min-w-[140px] px-3 py-2 text-xs text-gray-600">{r.people}</td>
                        <td className="min-w-[200px] px-3 py-2 text-xs">
                          {(r.errors || []).map((e) => <p key={e} className="text-red-700">{e}</p>)}
                          {(r.warnings || []).map((w) => <p key={w} className="text-amber-700">{w}</p>)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {sum.total > rows.length && (
              <p className="mt-2 text-xs text-gray-400">Showing the first {rows.length} rows. All {sum.total} rows will be imported.</p>
            )}
          </section>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <button onClick={reset} className="rounded-lg border bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
              Choose a different file
            </button>
            <div className="flex items-center gap-3">
              {sum.withProblems > 0 && !blocked && <p className="text-sm text-gray-500">{sum.withProblems} rows with problems will be skipped.</p>}
              <button
                onClick={confirm}
                disabled={sum.ready === 0 || saving || blocked || busy}
                className="flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {saving && <Loader2 className="h-4 w-4 animate-spin" />}
                Confirm and import {sum.ready} items
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}