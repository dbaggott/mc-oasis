// The session schedule, kept in a public Google Calendar. Everything the
// Schedule page offers is built from its ID: Google's view of it, embedded,
// opening on the agenda with week and month a choice away; a link that adds
// the calendar to a Google account; and the iCalendar feed Apple Calendar,
// Outlook and the rest subscribe to. A subscriber's calendar
// re-fetches the feed on its own, so a session moved here moves there.
//
// The ID is public by design: it is how a public calendar is shared.
export const CALENDAR_ID = "8c457a44e95e8c60e3d8688ae0dbc9470c26a9c5ec50784f73be297433cf55eb@group.calendar.google.com";

const TIME_ZONE = "America/Los_Angeles";

const escape = (text) => text.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

export function calendarUrls(id) {
  const src = encodeURIComponent(id);
  const feed = `calendar.google.com/calendar/ical/${src}/public/basic.ics`;
  const embed = new URLSearchParams({
    src: id,
    ctz: TIME_ZONE,
    mode: "AGENDA",
    showTitle: "0",
    showPrint: "0",
    showTabs: "1",
    showCalendars: "0",
  });
  return {
    embed: `https://calendar.google.com/calendar/embed?${embed}`,
    // Google's own share link names the calendar by its ID in base64.
    google: `https://calendar.google.com/calendar/u/0?cid=${encodeURIComponent(Buffer.from(id).toString("base64"))}`,
    webcal: `webcal://${feed}`,
    ics: `https://${feed}`,
  };
}

// The Schedule page's body, put in place of <!-- schedule --> at build time.
// With no calendar ID yet, it says so rather than embedding nothing.
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
    `<iframe class="calendar" src="${escape(url.embed)}" title="Oasis SMP session schedule" loading="lazy"></iframe>`,
  ].join("");
}
