import { norm, parseDate, parseRange, readWorkbook, findHeaderRow, autoMap, buildRows } from "../lib/smartImport";

// This file pretends to be the backend while VITE_USE_MOCK=true.
// The real backend replaces it, so it is never used when VITE_USE_MOCK=false.
const store = new Map();

function looksLikeEnrolment(rows, hdr) {
  const heads = (rows[hdr] || []).map(norm);
  const any = (list) => heads.some((h) => list.includes(h));
  return (
    any(["email", "org id", "roll no", "roll number", "student name", "first name"]) &&
    any(["code", "course code", "subject code"]) &&
    !any(["date", "day", "start", "start time", "time", "end", "end time", "exam date"])
  );
}

function looksLikeGrid(rows, hdr) {
  const cells = (rows[hdr] || []).map((c) => String(c ?? "").trim()).filter(Boolean);
  const sessions = cells.filter((c) => /\b(morning|afternoon|evening)\b/i.test(c)).length;
  const whenHeads = cells.filter((c) => parseDate(c) || parseRange(c)).length;
  return sessions >= 2 || whenHeads >= 3;
}

function pickBestSheet(sheets) {
  let best = { name: Object.keys(sheets)[0], score: -Infinity };
  for (const [name, rows] of Object.entries(sheets)) {
    const hdr = findHeaderRow(rows);
    const fields = new Set(Object.values(autoMap(rows, hdr).mapping));
    let score = 0;
    if (fields.has("date") || fields.has("day")) score += 4;
    if (fields.has("start") || fields.has("time")) score += 3;
    if (fields.has("title") || fields.has("course_code")) score += 1;
    if (looksLikeEnrolment(rows, hdr)) score -= 10;
    if (looksLikeGrid(rows, hdr)) score -= 10;
    if (score > best.score) best = { name, score };
  }
  return best.name;
}

const label = (s) => (s >= 200 ? "set" : s >= 100 ? "high" : s >= 60 ? "medium" : "low");

export function mockAnalyze(importId, { sheet, headerRow, kind = "auto", mapping: override = {} } = {}) {
  const sheets = store.get(importId);
  if (!sheets) throw new Error("This upload has expired. Please upload the file again.");
  const rows = sheets[sheet];
  const hdr = headerRow ? headerRow - 1 : findHeaderRow(rows);

  const auto = autoMap(rows, hdr);
  const mapping = { ...auto.mapping };
  const conf = { ...auto.conf };
  for (const [c, f] of Object.entries(override)) {
    if (f && f !== "extra") for (const k of Object.keys(mapping)) if (mapping[k] === f) mapping[k] = "extra";
    mapping[c] = f;
    conf[c] = 200;
  }

  const built = buildRows(rows, hdr, mapping, kind);
  const clean = built.rows.filter((r) => !/^total\b/i.test(r.title) && !/^total\b/i.test(r.course_code));
  const ready = clean.filter((r) => r.errors.length === 0);

  const columns = (rows[hdr] || [])
    .map((h, c) => ({
      index: c,
      header: String(h ?? "").trim(),
      samples: rows.slice(hdr + 1, hdr + 40).map((r) => String(r[c] ?? "").trim()).filter(Boolean).slice(0, 2),
      field: mapping[c] ?? "",
      confidence: label(conf[c] || 0),
    }))
    .filter((col) => col.header);

  return {
    sheet,
    headerRow: hdr + 1,
    kind: built.kind,
    blockedReason: looksLikeGrid(rows, hdr) ? "GRID" : looksLikeEnrolment(rows, hdr) ? "ENROLMENT" : null,
    columns,
    summary: {
      total: clean.length,
      ready: ready.length,
      withNotes: ready.filter((r) => r.warnings.length).length,
      withProblems: clean.length - ready.length,
    },
    rows: clean.slice(0, 200).map((r) => ({
      line: r.line, title: r.title, courseCode: r.course_code, date: r.date, day: r.day,
      start: r.start, end: r.end, venue: r.venue, category: r.category,
      people: r.people, audience: r.audience, errors: r.errors, warnings: r.warnings,
    })),
  };
}

export async function mockUpload(file) {
  const sheets = await readWorkbook(file);
  const names = Object.keys(sheets);
  if (!names.length) throw new Error("No readable table was found in this file.");
  const importId = crypto.randomUUID();
  store.set(importId, sheets);
  return {
    importId,
    fileName: file.name,
    sheets: names.map((name) => ({ name, rows: sheets[name].length })),
    analysis: mockAnalyze(importId, { sheet: pickBestSheet(sheets) }),
  };
}

export function mockConfirm(importId, options) {
  const a = mockAnalyze(importId, options);
  if (a.blockedReason) throw new Error("This sheet cannot be imported.");
  return { saved: a.summary.ready };
}