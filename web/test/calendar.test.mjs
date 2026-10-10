import assert from "node:assert/strict";
import { test } from "node:test";
import { calendarUrls, scheduleHtml } from "../calendar.mjs";

const id = "abc123@group.calendar.google.com";

test("every link is built from the calendar's ID", () => {
  const url = calendarUrls(id);
  assert.equal(url.ics, "https://calendar.google.com/calendar/ical/abc123%40group.calendar.google.com/public/basic.ics");
  assert.equal(url.webcal, "webcal://calendar.google.com/calendar/ical/abc123%40group.calendar.google.com/public/basic.ics");
  assert.equal(url.google, `https://calendar.google.com/calendar/u/0?cid=${encodeURIComponent(btoa(id))}`);
  const page = new URL(url.page);
  assert.equal(page.origin, "https://calendar.google.com");
  assert.equal(page.searchParams.get("src"), id);
});

test("with an ID the page offers each way to subscribe, and a place for the sessions", () => {
  const html = scheduleHtml(id);
  assert.match(html, /<div class="sessions"><p class="sessions-note"><a href="https:\/\/calendar\.google\.com\/calendar\/embed\?/);
  assert.match(html, /href="webcal:\/\//);
  assert.match(html, /data-copy="https:\/\/calendar\.google\.com\/calendar\/ical\/[^"]*">Copy address</);
  assert.match(html, /<p class="address-value address-long" hidden><code>https:\/\/calendar\.google\.com\/calendar\/ical\//);
});

test("with no ID the page says the schedule is coming, and lists nothing", () => {
  const html = scheduleHtml("");
  assert.match(html, /coming soon/);
  assert.doesNotMatch(html, /class="sessions"/);
});
