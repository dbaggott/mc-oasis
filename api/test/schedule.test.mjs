import assert from "node:assert/strict";
import { test } from "node:test";
import { FEED_MAX_AGE_MS, scheduleReader, SESSIONS_SHOWN, upcomingSessions } from "../src/schedule.js";

// Shaped as Google exports the calendar: its zone spelled out, then the events.
const calendar = (...events) =>
  [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "BEGIN:VTIMEZONE",
    "TZID:America/Los_Angeles",
    "BEGIN:DAYLIGHT",
    "TZOFFSETFROM:-0800",
    "TZOFFSETTO:-0700",
    "DTSTART:19700308T020000",
    "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU",
    "END:DAYLIGHT",
    "BEGIN:STANDARD",
    "TZOFFSETFROM:-0700",
    "TZOFFSETTO:-0800",
    "DTSTART:19701101T020000",
    "RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU",
    "END:STANDARD",
    "END:VTIMEZONE",
    ...events.flatMap((lines) => ["BEGIN:VEVENT", ...lines, "END:VEVENT"]),
    "END:VCALENDAR",
  ].join("\r\n");

const at = (local) => `TZID=America/Los_Angeles:${local}`;

// Mondays and Thursdays, 3:00 to 4:30, from Monday 26 October 2026: across the
// clocks going back on 1 November.
const weekly = (...extra) => [
  "UID:weekly@test",
  `DTSTART;${at("20261026T150000")}`,
  `DTEND;${at("20261026T163000")}`,
  "RRULE:FREQ=WEEKLY;BYDAY=MO,TH",
  "SUMMARY:Oasis Minecraft",
  ...extra,
];

const window = (from, days = 30, limit = 100) => ({
  from: new Date(from),
  until: new Date(new Date(from).getTime() + days * 24 * 60 * 60 * 1000),
  limit,
});

const starts = (sessions) => sessions.map((s) => s.start);

test("a repeating event is expanded into its occurrences, at the local time either side of a clock change", () => {
  const sessions = upcomingSessions(calendar(weekly()), window("2026-10-26T00:00:00Z", 8));
  assert.deepEqual(starts(sessions), [
    "2026-10-26T22:00:00.000Z",
    "2026-10-29T22:00:00.000Z",
    "2026-11-02T23:00:00.000Z",
  ]);
  assert.deepEqual(sessions[0], {
    start: "2026-10-26T22:00:00.000Z",
    end: "2026-10-26T23:30:00.000Z",
    title: "Oasis Minecraft",
    description: null,
  });
});

test("a session under way is still listed; one that has ended is not", () => {
  const sessions = upcomingSessions(calendar(weekly()), window("2026-10-29T23:00:00Z", 5));
  assert.deepEqual(starts(sessions), ["2026-10-29T22:00:00.000Z", "2026-11-02T23:00:00.000Z"]);
  const later = upcomingSessions(calendar(weekly()), window("2026-10-29T23:30:00Z", 5));
  assert.deepEqual(starts(later), ["2026-11-02T23:00:00.000Z"]);
});

test("an occurrence deleted from the series is left out", () => {
  const sessions = upcomingSessions(calendar(weekly(`EXDATE;${at("20261029T150000")}`)), window("2026-10-26T00:00:00Z", 8));
  assert.deepEqual(starts(sessions), ["2026-10-26T22:00:00.000Z", "2026-11-02T23:00:00.000Z"]);
});

test("an occurrence moved on its own is listed where it now stands, with its own description, and one cancelled on its own is left out", () => {
  const moved = [
    "UID:weekly@test",
    `RECURRENCE-ID;${at("20261029T150000")}`,
    `DTSTART;${at("20261030T160000")}`,
    `DTEND;${at("20261030T170000")}`,
    "SUMMARY:Oasis Minecraft (Friday this week)",
    'DESCRIPTION:Moved for the <a href="https://www.oasis-smp.com/">assembly</a>.',
  ];
  const cancelled = [
    "UID:weekly@test",
    `RECURRENCE-ID;${at("20261102T150000")}`,
    `DTSTART;${at("20261102T150000")}`,
    `DTEND;${at("20261102T163000")}`,
    "STATUS:CANCELLED",
    "SUMMARY:Oasis Minecraft",
  ];
  const sessions = upcomingSessions(calendar(weekly(), moved, cancelled), window("2026-10-26T00:00:00Z", 11));
  assert.deepEqual(sessions, [
    { start: "2026-10-26T22:00:00.000Z", end: "2026-10-26T23:30:00.000Z", title: "Oasis Minecraft", description: null },
    {
      start: "2026-10-30T23:00:00.000Z",
      end: "2026-10-31T00:00:00.000Z",
      title: "Oasis Minecraft (Friday this week)",
      description: 'Moved for the <a href="https://www.oasis-smp.com/">assembly</a>.',
    },
    { start: "2026-11-05T23:00:00.000Z", end: "2026-11-06T00:30:00.000Z", title: "Oasis Minecraft", description: null },
  ]);
});

test("one-off events are listed alongside the repeating ones, in start order", () => {
  const party = [
    "UID:party@test",
    `DTSTART;${at("20261027T180000")}`,
    `DTEND;${at("20261027T190000")}`,
    "SUMMARY:Halloween build party",
  ];
  const past = ["UID:past@test", `DTSTART;${at("20261001T150000")}`, `DTEND;${at("20261001T160000")}`, "SUMMARY:Gone"];
  const sessions = upcomingSessions(calendar(past, weekly(), party), window("2026-10-26T00:00:00Z", 4));
  assert.deepEqual(
    sessions.map((s) => s.title),
    ["Oasis Minecraft", "Halloween build party", "Oasis Minecraft"],
  );
});

test("the list stops at the limit, and at the horizon", () => {
  assert.equal(upcomingSessions(calendar(weekly()), window("2026-10-26T00:00:00Z", 365, 5)).length, 5);
  assert.equal(upcomingSessions(calendar(weekly()), window("2026-10-26T00:00:00Z", 7)).length, 2);
});

test("a calendar with nothing ahead lists nothing", () => {
  assert.deepEqual(upcomingSessions(calendar(), window("2026-10-26T00:00:00Z")), []);
  const ended = weekly().map((line) => (line.startsWith("RRULE") ? `${line};COUNT=4` : line));
  assert.deepEqual(upcomingSessions(calendar(ended), window("2026-11-06T01:00:00Z")), []);
});

function reader({ feeds, now }) {
  let fetches = 0;
  const fetchFeed = async () => {
    const feed = feeds[fetches++];
    if (feed instanceof Error) throw feed;
    return feed;
  };
  return { upcoming: scheduleReader({ fetchFeed, now: () => now.value }), fetches: () => fetches };
}

test("the reader re-fetches the feed only once its copy is stale", async () => {
  const now = { value: Date.parse("2026-10-26T00:00:00Z") };
  const moved = calendar([
    "UID:once@test",
    `DTSTART;${at("20261027T150000")}`,
    `DTEND;${at("20261027T160000")}`,
    "SUMMARY:Moved",
  ]);
  const { upcoming, fetches } = reader({ feeds: [calendar(weekly()), moved], now });

  assert.equal((await upcoming()).length, SESSIONS_SHOWN);
  now.value += FEED_MAX_AGE_MS - 1;
  await upcoming();
  assert.equal(fetches(), 1);

  now.value += 1;
  assert.deepEqual((await upcoming()).map((s) => s.title), ["Moved"]);
  assert.equal(fetches(), 2);
});

test("when a re-fetch fails the last copy stands in until the next try; with no copy the reader throws", async () => {
  const now = { value: Date.parse("2026-10-26T00:00:00Z") };
  const { upcoming, fetches } = reader({ feeds: [new Error("down"), calendar(weekly()), new Error("down")], now });

  await assert.rejects(upcoming(), /down/);
  assert.equal((await upcoming()).length, SESSIONS_SHOWN);

  now.value += FEED_MAX_AGE_MS;
  assert.equal((await upcoming()).length, SESSIONS_SHOWN);
  await upcoming();
  assert.equal(fetches(), 3);
});
