import { http } from "./http";
import { readWorkbook, findHeaderRow, autoMap, buildRows } from "../lib/smartImport";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// Files the admin has opened in this browser tab (kept in memory only).
const opened = new Map();
let counter = 0;

// Reads one sheet with the smart reader and describes what it found.
function analyse(sheetName, sheetRows, kindChoice) {
  const hdr = findHeaderRow(sheetRows);
  const { mapping } = autoMap(sheetRows, hdr);
  const { kind, rows } = buildRows(sheetRows, hdr, mapping, kindChoice);

  const withProblems = rows.filter((r) => r.errors.length > 0).length;
  const withNotes = rows.filter((r) => !r.errors.length && r.warnings.length > 0).length;
  const ready = rows.length - withProblems;

  let blockedReason = null;
  let blockedMessage = "";
  if (rows.length === 0) {
    blockedReason = "EMPTY";
    blockedMessage = "No rows were found in this sheet.";
  } else if (kind === "enrolment") {
    blockedReason = "NOT_SUPPORTED";
    blockedMessage = "Student enrolment files cannot be imported yet.";
  }

  return {
    sheet: sheetName,
    kind,
    blockedReason,
    blockedMessage,
    issues: [],
    summary: { total: rows.length, ready, withNotes, withProblems },
    rows,
  };
}

export const adminApi = {
  // Reads the file in the browser and shows what it found. Nothing is saved yet.
  uploadImport: async (file, kind = "auto") => {
    const sheets = await readWorkbook(file);
    const names = Object.keys(sheets);
    if (names.length === 0) {
      throw new Error("No data was found in this file.");
    }
    const importId = `local-${++counter}`;
    opened.set(importId, { fileName: file.name, sheets });
    const first = names[0];
    return {
      importId,
      fileName: file.name,
      sheets: names.map((n) => ({ name: n, rows: Math.max(sheets[n].length - 1, 0) })),
      analysis: analyse(first, sheets[first], kind),
    };
  },

  // Reads again, for example after the admin picks another sheet or another kind.
  analyzeImport: async (importId, options) => {
    const f = opened.get(importId);
    if (!f) throw new Error("This file is no longer open. Please upload it again.");
    const name = options.sheet && f.sheets[options.sheet] ? options.sheet : Object.keys(f.sheets)[0];
    return analyse(name, f.sheets[name], options.kind || "auto");
  },

  // Sends the checked rows to the backend. The backend validates them again and saves.
  confirmImport: async (importId, options) => {
    const f = opened.get(importId);
    if (!f) throw new Error("This file is no longer open. Please upload it again.");
    const name = options.sheet && f.sheets[options.sheet] ? options.sheet : Object.keys(f.sheets)[0];
    const result = analyse(name, f.sheets[name], options.kind || "auto");
    const ready = result.rows.filter((r) => r.errors.length === 0);
    const res = await http.post("/admin/imports/commit", { kind: result.kind, rows: ready });
    opened.delete(importId);
    return { saved: res.saved };
  },

  // Creates one event. The backend works out which students it applies to.
  createEvent: async (payload) => {
    if (USE_MOCK) {
      await wait(700);
      return { id: "demo", reached: null };
    }
    return http.post("/admin/events", payload);
  },
};
