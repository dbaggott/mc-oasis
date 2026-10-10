// The Schedule page, built from the calendar's ID (shared/calendar.js): a link
// that adds the calendar to a Google account, the iCalendar feed Apple
// Calendar, Outlook and the rest subscribe to, and the upcoming sessions, which
// src/schedule.js fetches from the API. A subscriber's calendar re-fetches the
// feed on its own, so a session moved there moves here too.
import { CALENDAR_ID, feedAddress, TIME_ZONE } from "../shared/calendar.js";

const escape = (text) => text.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

export function calendarUrls(id) {
  const feed = feedAddress(id);
  const page = new URLSearchParams({ src: id, ctz: TIME_ZONE, mode: "AGENDA" });
  return {
    // Google's own view of the calendar, for when the sessions can't be listed here.
    page: `https://calendar.google.com/calendar/embed?${page}`,
    // Google's own share link names the calendar by its ID in base64.
    google: `https://calendar.google.com/calendar/u/0?cid=${encodeURIComponent(Buffer.from(id).toString("base64"))}`,
    webcal: `webcal://${feed}`,
    ics: `https://${feed}`,
  };
}

// The Schedule page's body, put in place of <!-- schedule --> at build time.
// With no calendar ID yet, it says so rather than listing nothing.
export function scheduleHtml(id = CALENDAR_ID) {
  if (!id) {
    return '<p class="lede">The session schedule is coming soon.</p>';
  }
  const url = calendarUrls(id);
  return [
    '<p class="lede">Add to your own calendar (changes sync automatically):</p>',
    '<div class="subscribe">',
    `<a class="mc-button mc-button-small" href="${escape(url.google)}" target="_blank" rel="noopener">Google Calendar</a>`,
    `<a class="mc-button mc-button-small" href="${escape(url.webcal)}">Apple Calendar or Outlook</a>`,
    `<button class="mc-button mc-button-small" type="button" data-copy="${escape(url.ics)}">Copy address</button>`,
    `<p class="address-value address-long" hidden><code>${escape(url.ics)}</code></p>`,
    "</div>",
    "<h2>Upcoming sessions</h2>",
    '<div class="sessions">',
    // What a visitor without scripts keeps; src/schedule.js replaces it.
    `<p class="sessions-note"><a href="${escape(url.page)}" target="_blank" rel="noopener">See the sessions on Google Calendar</a></p>`,
    "</div>",
  ].join("");
}
