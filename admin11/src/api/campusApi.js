import { http } from "./http";
import { readWorkbook } from "../lib/smartImport";
import { analyseCampusSheet } from "../lib/campusImport";

const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// Demo data, used only while VITE_USE_MOCK=true. The real list lives in the database.
let items = [
  { id: 1, title: "Follows the Tuesday timetable", category: "SWAP", date: "2026-10-21", endDate: "", appliesTo: "All students", venue: "", description: "", followsDay: "Tuesday" },
  { id: 2, title: "Last day of classes", category: "ACADEMIC", date: "2026-11-13", endDate: "", appliesTo: "UG and PG students", venue: "", description: "", followsDay: "" },
  { id: 3, title: "End-semester examination", category: "EXAM", date: "2026-11-16", endDate: "2026-11-26", appliesTo: "All students", venue: "", description: "", followsDay: "" },
];
let nextId = 100;

// Files opened in this browser tab (kept in memory only).
const opened = new Map();
let counter = 0;

export const campusApi = {
  list: async () => {
    if (USE_MOCK) { await wait(300); return [...items]; }
    return http.get("/admin/campus");
  },

  save: async (item) => {
    if (USE_MOCK) {
      await wait(400);
      if (item.id) items = items.map((i) => (i.id === item.id ? { ...i, ...item } : i));
      else items = [...items, { ...item, id: nextId++ }];
      return { ok: true };
    }
    return item.id ? http.put(`/admin/campus/${item.id}`, item) : http.post("/admin/campus", item);
  },

  remove: async (id) => {
    if (USE_MOCK) { await wait(300); items = items.filter((i) => i.id !== id); return { ok: true }; }
    return http.delete(`/admin/campus/${id}`);
  },

  // The file is read in the browser and shown for checking. Nothing is saved yet.
  uploadImport: async (file) => {
    const sheets = await readWorkbook(file);
    const names = Object.keys(sheets);
    if (names.length === 0) throw new Error("No data was found in this file.");
    const result = analyseCampusSheet(sheets[names[0]]);
    const importId = `campus-${++counter}`;
    opened.set(importId, result.rows);
    return { importId, summary: result.summary, rows: result.rows };
  },

  // Sends the checked rows. The backend checks them again and saves.
  // Items with the same title and start date are updated, not duplicated.
  confirmImport: async (importId) => {
    const rows = opened.get(importId);
    if (!rows) throw new Error("This file is no longer open. Please upload it again.");
    const ready = rows.filter((r) => r.errors.length === 0);
    const res = await http.post("/admin/campus/import", { rows: ready });
    opened.delete(importId);
    return { saved: res.saved };
  },
};
