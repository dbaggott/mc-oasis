// The session schedule, kept in a public Google Calendar. Everything the
// Schedule page offers is built from its ID: Google's agenda view, embedded;
// a link that adds the calendar to a Google account; and the iCalendar feed
// Apple Calendar, Outlook and the rest subscribe to. A subscriber's calendar
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
    showTabs: "0",
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
    '<p class="lede">Add the schedule to your own calendar and changes to it will show up there on their own.</p>',
    '<div class="subscribe">',
    `<a class="mc-button mc-button-small" href="${escape(url.google)}" target="_blank" rel="noopener">Add to Google Calendar</a>`,
    `<a class="mc-button mc-button-small" href="${escape(url.webcal)}">Subscribe in Apple Calendar or Outlook</a>`,
    "</div>",
    '<div class="address">',
    '<p class="address-label">Calendar address, for any other calendar app</p>',
    `<p class="address-value address-long"><code>${escape(url.ics)}</code></p>`,
    `<button class="mc-button mc-button-small copy" type="button" data-copy="${escape(url.ics)}">Copy</button>`,
    "</div>",
    `<iframe class="calendar" src="${escape(url.embed)}" title="Oasis SMP session schedule" loading="lazy"></iframe>`,
  ].join("");
}
