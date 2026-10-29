import { useEffect, useRef, useState } from "react";
import { format, parseISO } from "date-fns";
import { Upload, Plus, Pencil, Trash2, Loader2, AlertTriangle, CircleCheck, Info } from "lucide-react";
import { campusApi } from "../api/campusApi";

const CATEGORIES = {
  ACADEMIC: ["Academic", "bg-blue-100 text-blue-700"],
  EXAM: ["Exams", "bg-amber-100 text-amber-700"],
  HOLIDAY: ["Vacation or holiday", "bg-emerald-100 text-emerald-700"],
  NOCLASS: ["No class day", "bg-rose-100 text-rose-700"],
  SWAP: ["Follows another weekday timetable", "bg-cyan-100 text-cyan-700"],
  FEST: ["Fest", "bg-pink-100 text-pink-700"],
  TALK: ["Talk", "bg-violet-100 text-violet-700"],
};
const APPLIES = ["All students", "UG and M.Sc. only", "UG and PG students", "New students", "Continuing students", "Faculty"];
const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const EMPTY = { title: "", category: "ACADEMIC", date: "", endDate: "", appliesTo: "All students", venue: "", description: "", followsDay: "" };
const inputCls = "w-full rounded-lg border bg-white px-3 py-2 text-sm text-gray-900 focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100";
const errMsg = (e) => e.response?.data?.message || e.message || "Something went wrong.";

const when = (i) => {
  const s = format(parseISO(i.date), "EEE d MMM yyyy");
  return i.endDate && i.endDate !== i.date ? `${s} to ${format(parseISO(i.endDate), "EEE d MMM yyyy")}` : s;
};

function Field({ label, children }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-gray-700">{label}</span>
      {children}
    </label>
  );
}

function ItemForm({ initial, onCancel, onSaved }) {
  const [f, setF] = useState({ ...EMPTY, ...initial });
  const [errors, setErrors] = useState([]);
  const [saving, setSaving] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    const errs = [];
    if (!f.title.trim()) errs.push("Please enter a title.");
    if (!f.date) errs.push("Please choose a start date.");
    if (f.endDate && f.date && f.endDate < f.date) errs.push("The end date is before the start date.");
    if (f.category === "SWAP" && !f.followsDay) errs.push("Choose which weekday timetable is followed.");
    setErrors(errs);
    if (errs.length) return;
    setSaving(true);
    try {
      await campusApi.save({ ...f, title: f.title.trim(), followsDay: f.category === "SWAP" ? f.followsDay : "" });
      onSaved();
    } catch (err) {
      setErrors([errMsg(err)]);
      setSaving(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4 rounded-2xl border bg-white p-5 shadow-sm">
      <h2 className="font-semibold text-gray-900">{initial.id ? "Edit item" : "Add item"}</h2>
      {errors.length > 0 && (
        <div className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> <div>{errors.map((m) => <p key={m}>{m}</p>)}</div>
        </div>
      )}
      <Field label="Title"><input value={f.title} onChange={set("title")} className={inputCls} /></Field>
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Category">
          <select value={f.category} onChange={set("category")} className={inputCls}>
            {Object.entries(CATEGORIES).map(([k, [l]]) => <option key={k} value={k}>{l}</option>)}
          </select>
        </Field>
        {f.category === "SWAP" ? (
          <Field label="Follows the timetable of">
            <select value={f.followsDay} onChange={set("followsDay")} className={inputCls}>
              <option value="">Choose a weekday</option>
              {DAYS.map((d) => <option key={d}>{d}</option>)}
            </select>
          </Field>
        ) : (
          <Field label="Applies to">
            <input list="applies" value={f.appliesTo} onChange={set("appliesTo")} className={inputCls} />
            <datalist id="applies">{APPLIES.map((a) => <option key={a} value={a} />)}</datalist>
          </Field>
        )}
        <Field label="Start date"><input type="date" value={f.date} onChange={set("date")} className={inputCls} /></Field>
        <Field label="End date (optional)"><input type="date" value={f.endDate || ""} onChange={set("endDate")} className={inputCls} /></Field>
      </div>
      <Field label="Venue (optional)"><input value={f.venue} onChange={set("venue")} className={inputCls} /></Field>
      <Field label="Details (optional)"><textarea rows={2} value={f.description} onChange={set("description")} className={inputCls} /></Field>
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="rounded-lg border bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">Cancel</button>
        <button type="submit" disabled={saving} className="flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50">
          {saving && <Loader2 className="h-4 w-4 animate-spin" />} Save
        </button>
      </div>
    </form>
  );
}

export default function CampusCalendar() {
  const fileRef = useRef(null);
  const [items, setItems] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [editing, setEditing] = useState(null);
  const [showPast, setShowPast] = useState(false);
  const [query, setQuery] = useState("");
  const [imp, setImp] = useState(null); // { busy, result }

  const load = () => campusApi.list().then(setItems).catch((e) => setError(errMsg(e)));
  useEffect(() => { load(); }, []);

  const pickFile = async (file) => {
    if (!file) return;
    setError(""); setNotice("");
    if (!/\.(csv|xlsx|xls|xlsm)$/i.test(file.name)) { setError("Please upload a CSV or Excel file."); return; }
    setImp({ busy: true, result: null });
    try {
      setImp({ busy: false, result: await campusApi.uploadImport(file) });
    } catch (e) {
      setImp(null); setError(errMsg(e));
    }
  };

  const confirmImport = async () => {
    setImp({ ...imp, busy: true });
    try {
      const res = await campusApi.confirmImport(imp.result.importId);
      setImp(null); setNotice(`${res.saved} items saved to the campus calendar.`);
      load();
    } catch (e) {
      setImp({ ...imp, busy: false }); setError(errMsg(e));
    }
  };

  const remove = async (i) => {
    if (!window.confirm(`Delete "${i.title}"? Students will no longer see it.`)) return;
    try { await campusApi.remove(i.id); setNotice("Item deleted."); load(); } catch (e) { setError(errMsg(e)); }
  };

  const today = format(new Date(), "yyyy-MM-dd");
  const shown = (items || [])
    .filter((i) => showPast || (i.endDate || i.date) >= today)
    .filter((i) => !query || i.title.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => a.date.localeCompare(b.date));
  const res = imp?.result;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Campus calendar</h1>
          <p className="mt-1 text-sm text-gray-500">Academic dates, exams, vacations and timetable changes. Students see these on their Campus tab.</p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => fileRef.current?.click()} className="flex items-center gap-2 rounded-lg border bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
            <Upload className="h-4 w-4" /> Import from Excel
          </button>
          <button onClick={() => setEditing({})} className="flex items-center gap-2 rounded-lg bg-brand-600 px-3 py-2 text-sm font-semibold text-white hover:bg-brand-700">
            <Plus className="h-4 w-4" /> Add item
          </button>
          <input ref={fileRef} type="file" accept=".csv,.xlsx,.xls,.xlsm" className="hidden" onChange={(e) => { pickFile(e.target.files[0]); e.target.value = ""; }} />
        </div>
      </div>

      {error && (
        <div className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {error}
        </div>
      )}
      {notice && (
        <div className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
          <CircleCheck className="mt-0.5 h-4 w-4 shrink-0" /> {notice}
        </div>
      )}

      {imp && (
        <section className="space-y-3 rounded-2xl border bg-white p-5 shadow-sm">
          {imp.busy && !res && <p className="flex items-center gap-2 text-sm text-gray-500"><Loader2 className="h-4 w-4 animate-spin" /> Uploading and reading...</p>}
          {res && (
            <>
              {res.demo && (
                <div className="flex items-start gap-2 rounded-xl border border-brand-200 bg-brand-50 p-3 text-sm text-brand-700">
                  <Info className="mt-0.5 h-4 w-4 shrink-0" /> Demo mode: your file was not read. When the backend is connected, this shows what the server found.
                </div>
              )}
              <p className="text-sm text-gray-700"><b>{res.summary.ready}</b> of {res.summary.total} rows are ready{res.summary.withProblems > 0 && `, ${res.summary.withProblems} have problems and will be skipped`}.</p>
              <div className="overflow-x-auto rounded-xl border">
                <table className="min-w-full text-left text-sm">
                  <thead className="bg-gray-50 text-xs text-gray-500"><tr>{["Line", "Title", "When", "Category", "Applies to", "Problems"].map((h) => <th key={h} className="px-3 py-2 font-medium">{h}</th>)}</tr></thead>
                  <tbody className="divide-y">
                    {res.rows.slice(0, 50).map((r) => (
                      <tr key={r.line} className={r.errors?.length ? "bg-red-50/60" : ""}>
                        <td className="px-3 py-2 text-gray-400">{r.line}</td>
                        <td className="px-3 py-2 font-medium text-gray-900">{r.title}</td>
                        <td className="whitespace-nowrap px-3 py-2 text-gray-700">{r.date ? when(r) : "-"}</td>
                        <td className="px-3 py-2 text-gray-700">{CATEGORIES[r.category]?.[0] || r.category}</td>
                        <td className="px-3 py-2 text-gray-700">{r.appliesTo}</td>
                        <td className="px-3 py-2 text-xs text-red-700">{(r.errors || []).join(", ")}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="flex justify-end gap-2">
                <button onClick={() => setImp(null)} className="rounded-lg border bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">Cancel</button>
                <button onClick={confirmImport} disabled={imp.busy || res.summary.ready === 0} className="flex items-center gap-2 rounded-lg bg-brand-600 px-5 py-2 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-50">
                  {imp.busy && <Loader2 className="h-4 w-4 animate-spin" />} Confirm and save {res.summary.ready} items
                </button>
              </div>
            </>
          )}
        </section>
      )}

      {editing && (
        <ItemForm
          key={editing.id || "new"}
          initial={editing}
          onCancel={() => setEditing(null)}
          onSaved={() => { setEditing(null); setNotice("Item saved."); load(); }}
        />
      )}

      <div className="flex flex-wrap items-center gap-3">
        <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search items" className="w-56 rounded-lg border bg-white px-3 py-2 text-sm focus:border-brand-500 focus:outline-none" />
        <label className="flex cursor-pointer items-center gap-2 text-sm text-gray-600">
          <input type="checkbox" checked={showPast} onChange={(e) => setShowPast(e.target.checked)} className="accent-indigo-600" /> Show past items
        </label>
        {items && <span className="ml-auto text-xs text-gray-400">{shown.length} shown</span>}
      </div>

      {!items && !error && <p className="flex items-center gap-2 text-sm text-gray-500"><Loader2 className="h-4 w-4 animate-spin" /> Loading...</p>}
      {items && shown.length === 0 && (
        <div className="rounded-2xl border border-dashed border-gray-300 bg-white p-10 text-center text-sm text-gray-500">
          Nothing to show. Import the campus calendar from Excel, or add an item.
        </div>
      )}

      <div className="space-y-2">
        {shown.map((i) => {
          const [label, chip] = CATEGORIES[i.category] || [i.category, "bg-gray-100 text-gray-700"];
          return (
            <div key={i.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border bg-white p-4 shadow-sm">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-semibold text-gray-900">{i.title}</p>
                  <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${chip}`}>{label}</span>
                </div>
                <p className="mt-1 text-sm text-gray-600">{when(i)}</p>
                <p className="text-sm text-gray-500">
                  {i.category === "SWAP" ? `Follows the ${i.followsDay} timetable` : `For: ${i.appliesTo || "All students"}`}
                </p>
              </div>
              <div className="flex gap-2">
                <button onClick={() => setEditing(i)} className="flex items-center gap-1 rounded-lg border px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"><Pencil className="h-4 w-4" /> Edit</button>
                <button onClick={() => remove(i)} className="flex items-center gap-1 rounded-lg border border-red-200 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50"><Trash2 className="h-4 w-4" /> Delete</button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}