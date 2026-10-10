// The Schedule page: lists the upcoming sessions from /api/schedule in place of
// the link to Google's view of the calendar, which stays if they can't be read.
import "./main.js";
import { sessionRows, zoneName } from "./sessions.js";

const box = document.querySelector(".sessions");
const fallback = box?.firstElementChild;

function note(text, ...extra) {
  const p = document.createElement("p");
  p.className = "sessions-note";
  p.append(text, ...extra);
  box.replaceChildren(p);
}

function span(className, text) {
  const el = document.createElement("span");
  el.className = className;
  el.textContent = text;
  return el;
}

// The calendar is the site's own, so its HTML is shown as written. Text with no
// markup in it, from an editor other than Google's web one, keeps its line breaks.
function description(content) {
  const el = document.createElement("div");
  if (/<[a-z]/i.test(content)) {
    el.className = "session-description";
    el.innerHTML = content;
  } else {
    el.className = "session-description session-description-text";
    el.textContent = content;
  }
  return el;
}

function list({ sessions, timeZone }) {
  if (sessions.length === 0) {
    note("No sessions are scheduled right now. Subscribe above to hear when they are.");
    return;
  }
  const ol = document.createElement("ol");
  for (const row of sessionRows(sessions, timeZone)) {
    const li = document.createElement("li");
    li.className = row.underway ? "session session-underway" : "session";
    const date = document.createElement("time");
    date.className = "session-date";
    date.dateTime = row.date;
    date.append(span("session-weekday", row.weekday), span("session-day", row.day), span("session-month", row.month));
    const what = document.createElement("div");
    what.className = "session-what";
    what.append(span("session-time", row.time), span("session-title", row.title));
    if (row.underway) what.append(span("session-badge", "On now"));
    if (row.description) what.append(description(row.description));
    li.append(date, what);
    ol.append(li);
  }
  const zone = document.createElement("p");
  zone.className = "sessions-note";
  zone.textContent = `Times are ${zoneName(timeZone)}.`;
  box.replaceChildren(ol, zone);
}

if (box && fallback) {
  note("Loading the schedule…");
  try {
    // Longer than a slow cold start takes, so only a stuck request falls back to the link.
    const res = await fetch("/api/schedule", { signal: AbortSignal.timeout(10000) });
    if (!res.ok) throw new Error(`schedule answered ${res.status}`);
    list(await res.json());
  } catch {
    note("The schedule couldn't be loaded here. ", fallback.firstElementChild);
  }
}
