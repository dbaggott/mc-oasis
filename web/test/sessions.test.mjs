import assert from "node:assert/strict";
import { test } from "node:test";
import { sessionRows, zoneName } from "../src/sessions.js";

const zone = "America/Los_Angeles";

test("a session is dated and timed where it happens, not where the visitor is", () => {
  // 23:00 UTC on the 2nd is 3pm on the 2nd in Los Angeles, and the 3rd in most of the world.
  const [row] = sessionRows(
    [{ start: "2026-11-02T23:00:00.000Z", end: "2026-11-03T00:30:00.000Z", title: "Oasis Minecraft", description: null }],
    zone,
    new Date("2026-10-09T00:00:00Z"),
  );
  // Intl spaces the range with narrow and thin spaces, which \s matches.
  assert.match(row.time, /^3:00\s–\s4:30\sPM$/u);
  assert.deepEqual(
    { ...row, time: undefined },
    {
      date: "2026-11-02",
      weekday: "Mon",
      day: "2",
      month: "Nov",
      time: undefined,
      title: "Oasis Minecraft",
      description: null,
      underway: false,
    },
  );
});

test("a session is under way from its start until its end", () => {
  const session = { start: "2026-11-02T23:00:00.000Z", end: "2026-11-03T00:30:00.000Z", title: "t", description: "<b>d</b>" };
  const underway = (now) => sessionRows([session], zone, new Date(now))[0].underway;
  assert.equal(underway("2026-11-02T22:59:59Z"), false);
  assert.equal(underway("2026-11-02T23:00:00Z"), true);
  assert.equal(underway("2026-11-03T00:29:59Z"), true);
  assert.equal(underway("2026-11-03T00:30:00Z"), false);
  assert.equal(sessionRows([session], zone)[0].description, "<b>d</b>");
});

test("the zone is named as people say it", () => {
  assert.equal(zoneName(zone), "Pacific Time");
});
