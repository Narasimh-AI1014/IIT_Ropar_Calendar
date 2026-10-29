// Reads a campus-calendar sheet (holidays, exam weeks, no-class days...) into standard items.
import { parseDate, norm, findHeaderRow } from "./smartImport.js";

const DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

// Which field does this column header describe?
function fieldOf(header) {
  const h = norm(header);
  if (!h) return null;
  if (/\b(end|till|until)\b/.test(h) || h === "to" || h === "to date") return "endDate";
  if (/\b(start|from|begin|begins)\b/.test(h) || /\bdates?\b/.test(h) || h === "when") return "date";
  if (/\b(category|type|kind)\b/.test(h)) return "category";
  if (/\b(applies|audience|students|for|batch)\b/.test(h)) return "appliesTo";
  if (/\b(venue|location|place|where)\b/.test(h)) return "venue";
  if (/\b(follows|timetable)\b/.test(h)) return "followsDay";
  if (/\b(details|notes|remarks|comments|about)\b/.test(h)) return "description";
  if (/\b(title|event|activity|particulars?|item|name|description|programme|program)\b/.test(h)) return "title";
  return null;
}

function classify(text) {
  const t = String(text || "").toLowerCase();
  if (!t) return null;
  if (/no[\s-]?class/.test(t)) return "NOCLASS";
  if (/follow|time\s?table/.test(t)) return "SWAP";
  if (/exam/.test(t)) return "EXAM";
  if (/vacation|holiday|break|recess/.test(t)) return "HOLIDAY";
  if (/fest|cultural/.test(t)) return "FEST";
  if (/talk|lecture|seminar|colloquium/.test(t)) return "TALK";
  if (/academic/.test(t)) return "ACADEMIC";
  return null;
}

const findDay = (text) => {
  const m = String(text || "").match(/\b(mon|tues?|wed(?:nes)?|thu(?:rs)?|fri|sat)[a-z]*\b/i);
  if (!m) return "";
  const key = m[1].slice(0, 3).toLowerCase();
  return DAY_NAMES.find((d) => d.slice(0, 3).toLowerCase() === key) || "";
};

export function analyseCampusSheet(sheetRows) {
  const hdr = findHeaderRow(sheetRows);
  const headers = sheetRows[hdr] || [];
  const col = {};
  headers.forEach((h, c) => {
    let f = fieldOf(h);
    if (!f) return;
    if (f in col) {
      if (f === "title" && !("description" in col)) f = "description";
      else return;
    }
    col[f] = c;
  });

  const rows = [];
  for (let i = hdr + 1; i < sheetRows.length; i++) {
    const r = sheetRows[i];
    if (!r.some((c) => String(c ?? "").trim())) continue;
    const get = (f) => (f in col ? String(r[col[f]] ?? "").trim() : "");

    const rec = {
      line: i + 1,
      title: get("title"),
      category: "ACADEMIC",
      date: "",
      endDate: "",
      appliesTo: get("appliesTo") || "All students",
      venue: get("venue"),
      description: get("description"),
      followsDay: "",
      errors: [],
    };

    // dates (a single cell may hold a range such as "16-11-2026 to 26-11-2026")
    const rawDate = get("date");
    const rawEnd = get("endDate");
    if (!rawDate) {
      rec.errors.push("Start date missing");
    } else {
      const parts = rawDate.split(/\s+to\s+|\s*[–—]\s*|\s+-\s+/i);
      const a = parts.length === 2 ? parseDate(parts[0]) : null;
      const b = parts.length === 2 ? parseDate(parts[1]) : null;
      if (a && b) {
        rec.date = a;
        rec.endDate = b;
      } else {
        const d = parseDate(rawDate);
        if (d) rec.date = d;
        else rec.errors.push(`Date "${rawDate}" not understood`);
      }
    }
    if (rawEnd) {
      const e = parseDate(rawEnd);
      if (e) rec.endDate = e;
      else rec.errors.push(`End date "${rawEnd}" not understood`);
    }
    if (rec.date && rec.endDate && rec.endDate < rec.date) rec.errors.push("End date is before the start date");
    if (rec.endDate && rec.endDate === rec.date) rec.endDate = "";

    if (!rec.title) rec.errors.push("No title");

    const catText = get("category");
    rec.category = classify(catText) || classify(rec.title) || "ACADEMIC";
    if (rec.category === "SWAP") {
      rec.followsDay = findDay(get("followsDay")) || findDay(catText) || findDay(rec.title) || findDay(rec.description);
      if (!rec.followsDay) rec.errors.push("Which weekday timetable is followed?");
    }
    rows.push(rec);
  }

  const withProblems = rows.filter((r) => r.errors.length > 0).length;
  return { rows, summary: { total: rows.length, ready: rows.length - withProblems, withProblems } };
}
