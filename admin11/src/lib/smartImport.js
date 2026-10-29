import * as XLSX from "xlsx";

export const FIELDS = {
  title: { label: "Title / event name", words: ["title", "event", "event name", "name", "activity", "program item", "programme item", "item", "performance", "topic", "quiz", "quiz name", "exam name", "course", "subject", "course name", "course title", "subject name"] },
  course_code: { label: "Course code", words: ["course code", "code", "subject code", "paper code", "course no", "course"] },
  date: { label: "Date", words: ["date", "exam date", "event date", "dated"] },
  day: { label: "Weekday", words: ["day", "weekday", "week day"] },
  start: { label: "Start time", words: ["start", "start time", "from", "begin", "begins", "starts", "time from"] },
  end: { label: "End time", words: ["end", "end time", "to", "until", "ends", "finish", "time to"] },
  time: { label: "Time (start - end)", words: ["time", "timing", "timings", "slot time", "duration", "schedule"] },
  venue: { label: "Venue / room", words: ["venue", "room", "location", "hall", "place", "where", "classroom", "stage"] },
  category: { label: "Type / category", words: ["type", "category", "kind", "event type"] },
  people: { label: "People (performers, faculty)", words: ["performer", "performers", "participants", "faculty", "instructor", "teacher", "speaker", "artist", "team", "host", "coordinator", "organizer", "by"] },
  audience: { label: "Audience (who it is for)", words: ["audience", "batch", "branch", "program", "programme", "semester", "year", "section", "class", "for", "group"] },
  description: { label: "Description / notes", words: ["description", "details", "notes", "remarks", "about", "comments"] },
};

export const norm = (s) => String(s ?? "").toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
const pad = (n) => String(n).padStart(2, "0");
const hhmm = (h, m) => `${pad(h)}:${pad(m)}`;
const toMin = (t) => { const [h, m] = t.split(":").map(Number); return h * 60 + m; };
const fromMin = (n) => hhmm(Math.floor(n / 60) % 24, n % 60);

const MONTHS = { jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6, jul: 7, aug: 8, sep: 9, oct: 10, nov: 11, dec: 12 };
const DAYS = { mon: 1, tue: 2, wed: 3, thu: 4, fri: 5, sat: 6, sun: 7 };
const DAY_RE = /^(mon|tue|wed|thu|fri|sat|sun)[a-z]*\.?$/i;

export const dayNumber = (v) => {
  const s = String(v ?? "").trim();
  return s.length <= 10 && DAY_RE.test(s) ? DAYS[s.slice(0, 3).toLowerCase()] : null;
};

// ---------- value parsers ----------
export function parseDate(v) {
  const s = String(v ?? "").replace(/\(.*?\)/g, "").trim();
  if (!s) return null;
  let x, y, m, d;
  if ((x = s.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})/))) [y, m, d] = [x[1], x[2], x[3]];
  else if ((x = s.match(/^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})/))) {
    [d, m, y] = [x[1], x[2], x[3]]; // day first (Indian format)
    if (+m > 12 && +d <= 12) [d, m] = [m, d];
  } else if ((x = s.match(/^(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]{3,9})\.?,?\s+(\d{4})/))) [d, m, y] = [x[1], MONTHS[x[2].slice(0, 3).toLowerCase()], x[3]];
  else if ((x = s.match(/^([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})/))) [m, d, y] = [MONTHS[x[1].slice(0, 3).toLowerCase()], x[2], x[3]];
  else return null;
  y = +y; m = +m; d = +d;
  if (y < 100) y += 2000;
  const dt = new Date(Date.UTC(y, m - 1, d));
  if (!m || dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d) return null;
  return `${y}-${pad(m)}-${pad(d)}`;
}

export function parseTime(v) {
  const raw = String(v ?? "").trim();
  if (!raw) return null;
  if (/^0?\.\d+$/.test(raw)) { // Excel stores times as a fraction of a day
    const mins = Math.round(parseFloat(raw) * 1440);
    return hhmm(Math.floor(mins / 60) % 24, mins % 60);
  }
  const s = raw.toLowerCase().replace(/\./g, ":").replace(/\s+/g, "");
  const m = s.match(/^(\d{1,2})(?::(\d{2}))?(?::\d{2})?(am|pm|a|p)?$/);
  if (!m) return null;
  let h = +m[1];
  const min = +(m[2] || 0);
  if (min > 59 || h > 24) return null;
  if (m[3]) {
    if (m[3][0] === "p" && h < 12) h += 12;
    if (m[3][0] === "a" && h === 12) h = 0;
  }
  return h > 23 ? null : hhmm(h, min);
}

export function parseRange(v) {
  const parts = String(v ?? "").trim().split(/\s*(?:-|–|—|\bto\b|\btill\b|\buntil\b)\s*/i);
  if (parts.length !== 2) return null;
  const tail = parts[1].toLowerCase().match(/(am|pm)$/);
  let a = parseTime(parts[0]);
  const b = parseTime(parts[1]);
  if (a && b && tail && !/(am|pm)/i.test(parts[0])) {
    const withTail = parseTime(parts[0] + tail[1]);
    if (withTail && withTail <= b) a = withTail; // "10-11 AM" means 10 AM
  }
  return a && b ? [a, b] : null;
}

// ---------- reading the file ----------
export async function readWorkbook(file) {
  let wb;
  if (/\.(csv|tsv)$/i.test(file.name)) {
    wb = XLSX.read(await file.text(), { type: "string", raw: true }); // keep dates as typed
  } else {
    wb = XLSX.read(await file.arrayBuffer(), { cellDates: true });
  }
  const sheets = {};
  for (const name of wb.SheetNames) {
    const rows = XLSX.utils.sheet_to_json(wb.Sheets[name], { header: 1, raw: false, defval: "", dateNF: "yyyy-mm-dd" });
    let filled = 0, width = 0;
    for (const r of rows) {
      const n = r.filter((c) => String(c).trim()).length;
      if (n) filled++;
      if (n > width) width = n;
    }
    if (filled >= 2 && width >= 2) sheets[name] = rows;
  }
  return sheets;
}

// ---------- understanding the columns ----------
function headerScore(header, words) {
  const h = norm(header);
  if (!h) return 0;
  let best = 0;
  for (const w of words) {
    if (h === w) best = Math.max(best, 100);
    else if (w.length > 3 && ` ${h} `.includes(` ${w} `)) best = Math.max(best, 70);
    else if (h.length > 3 && ` ${w} `.includes(` ${h} `)) best = Math.max(best, 40);
  }
  return best;
}

function sniff(values) {
  const n = values.length || 1;
  const frac = (fn) => values.filter(fn).length / n;
  const timeLike = (v) => /\d\s*[:.]\s*\d{2}|\d\s*(am|pm)\b/i.test(v) && !!parseTime(v);
  return {
    date: frac((v) => !!parseDate(v)),
    time: frac(timeLike),
    range: frac((v) => !!parseRange(v)),
    day: frac((v) => !!dayNumber(v)),
    code: frac((v) => /^\*?\s*[A-Z]{2,3}\s?\d{3}[A-Z]?\b/i.test(v)),
  };
}

function fieldScore(f, header, s) {
  let score = headerScore(header, FIELDS[f].words);
  if (f === "date") score = s.date < 0.3 ? score * 0.3 : score + 60;
  else if (f === "start" || f === "end") score = s.time < 0.3 ? score * 0.3 : score + 25;
  else if (f === "time") score = s.range > 0.5 ? score + 70 : s.time > 0.5 ? score + 30 : score * 0.3;
  else if (f === "day") score = s.day < 0.3 ? score * 0.3 : score + 60;
  else if (f === "course_code") score = s.code > 0.6 ? score + 60 : s.code < 0.3 ? score * 0.3 : score;
  return score;
}

export function findHeaderRow(rows) {
  let best = { idx: 0, score: -1 };
  for (let i = 0; i < Math.min(rows.length, 25); i++) {
    const cells = rows[i].map((c) => String(c ?? "").trim());
    const text = cells.filter((c) => c && isNaN(Number(c)) && !parseDate(c) && !parseTime(c));
    const next = rows[i + 1] ? rows[i + 1].filter((c) => String(c ?? "").trim()).length : 0;
    if (text.length < 2 || next === 0) continue;
    const hits = cells.filter((c) => Object.values(FIELDS).some((f) => headerScore(c, f.words) >= 70)).length;
    const score = hits * 5 + text.length + (next >= text.length / 2 ? 2 : 0);
    if (score > best.score) best = { idx: i, score };
  }
  return best.idx;
}

export function autoMap(rows, hdr) {
  const headers = rows[hdr] || [];
  const body = rows.slice(hdr + 1, hdr + 41);
  const cands = [];
  headers.forEach((h, c) => {
    if (!String(h).trim()) return;
    const sample = body.map((r) => String(r[c] ?? "").trim()).filter(Boolean);
    const s = sniff(sample);
    Object.keys(FIELDS).forEach((f) => {
      const score = fieldScore(f, h, s);
      if (score >= 40) cands.push({ c, f, score });
    });
  });
  cands.sort((a, b) => b.score - a.score);

  const mapping = {}, conf = {}, used = new Set();
  for (const { c, f, score } of cands) {
    if (c in mapping || used.has(f)) continue;
    mapping[c] = f; conf[c] = score; used.add(f);
  }
  headers.forEach((h, c) => { if (String(h).trim() && !(c in mapping)) mapping[c] = "extra"; });

  // No title found: use the most "texty" leftover column
  if (!used.has("title") && !used.has("course_code")) {
    let bestC = -1, bestLen = 0;
    headers.forEach((h, c) => {
      if (mapping[c] !== "extra") return;
      const vals = body.map((r) => String(r[c] ?? "").trim()).filter(Boolean)
        .filter((v) => isNaN(Number(v)) && !parseDate(v) && !parseTime(v));
      if (!vals.length) return;
      const avg = vals.reduce((a, v) => a + v.length, 0) / vals.length;
      if (avg > bestLen) { bestLen = avg; bestC = c; }
    });
    if (bestC >= 0) { mapping[bestC] = "title"; conf[bestC] = 30; }
  }
  return { mapping, conf };
}

export function guessKind(cols) {
  if ("day" in cols && !("date" in cols)) return "classes";
  if ("course_code" in cols && "date" in cols && !("title" in cols)) return "exam";
  return "event";
}

// ---------- turning rows into standard records ----------
export function buildRows(rows, hdr, mapping, kindChoice) {
  const headers = (rows[hdr] || []).map(String);
  const cols = {}, extras = [];
  Object.entries(mapping).forEach(([c, f]) => {
    if (f === "extra") extras.push(+c);
    else if (f) cols[f] = +c;
  });
  const kind = kindChoice === "auto" ? guessKind(cols) : kindChoice;
  const out = [];
  let lastDate = "";

  for (let i = hdr + 1; i < rows.length; i++) {
    const r = rows[i];
    if (!r.some((c) => String(c ?? "").trim())) continue;
    const get = (f) => (f in cols ? String(r[cols[f]] ?? "").trim() : "");
    const rec = { line: i + 1, kind, errors: [], warnings: [], date: "", day: null, start: "", end: "" };

    rec.course_code = get("course_code").replace(/^\*/, "").trim().toUpperCase();
    rec.title = get("title");
    rec.venue = get("venue");
    rec.people = get("people");
    rec.audience = get("audience");
    const extra = extras
      .map((c) => [headers[c], String(r[c] ?? "").trim()])
      .filter(([, v]) => v)
      .map(([h, v]) => `${h}: ${v}`);
    rec.description = [get("description"), ...extra].filter(Boolean).join(" · ");

    // date (blank cells inherit the date above, as in merged cells)
    const rawDate = get("date");
    if (rawDate) {
      const d = parseDate(rawDate);
      if (d) { rec.date = d; lastDate = d; } else rec.errors.push(`Date "${rawDate}" not understood`);
    } else if ("date" in cols && lastDate) rec.date = lastDate;

    // weekday
    const rawDay = get("day");
    if (rawDay) {
      rec.day = dayNumber(rawDay);
      if (!rec.day) rec.errors.push(`Weekday "${rawDay}" not understood`);
    } else if (rec.date) rec.day = ((new Date(rec.date + "T00:00:00Z").getUTCDay() + 6) % 7) + 1;

    // times
    const rs = get("start"), re = get("end"), rt = get("time");
    if (rs) { rec.start = parseTime(rs) || ""; if (!rec.start) rec.errors.push(`Start time "${rs}" not understood`); }
    if (re) { rec.end = parseTime(re) || ""; if (!rec.end) rec.errors.push(`End time "${re}" not understood`); }
    if (rt) {
      const rg = parseRange(rt);
      if (rg) [rec.start, rec.end] = rg;
      else {
        const t = parseTime(rt);
        if (t) rec.start = rec.start || t;
        else rec.errors.push(`Time "${rt}" not understood`);
      }
    }

    // title and category
    if (!rec.title && rec.course_code) rec.title = kind === "exam" ? `${rec.course_code} exam` : rec.course_code;
    if (!rec.title) rec.errors.push("No title or course code");
    const cat = get("category").toUpperCase();
    rec.category = cat || (kind === "quiz" || /quiz/i.test(rec.title) ? "QUIZ" : { classes: "CLASS", exam: "EXAM" }[kind] || "EVENT");

    // required fields per kind
    if (kind === "classes") {
      if (!rec.day) rec.errors.push("Weekday missing");
      if (!rec.start || !rec.end) rec.errors.push("Start and end time needed");
    } else {
      if (!rec.date) rec.errors.push("Date missing");
      if (!rec.start) rec.warnings.push("No time found, treated as all day");
    }
    if (rec.start && rec.end && toMin(rec.end) <= toMin(rec.start)) rec.errors.push("End is not after start");
    out.push(rec);
  }

  // Missing end time: use the next item's start, otherwise assume one hour
  out.forEach((rec, i) => {
    if (kind === "classes" || !rec.start || rec.end || rec.errors.length) return;
    const nx = out[i + 1];
    if (nx && nx.date === rec.date && nx.start && toMin(nx.start) > toMin(rec.start)) {
      rec.end = nx.start;
      rec.warnings.push("End time taken from the next item");
    } else {
      rec.end = fromMin(toMin(rec.start) + 60);
      rec.warnings.push("No end time, assumed 1 hour");
    }
  });

  return { kind, rows: out };
}