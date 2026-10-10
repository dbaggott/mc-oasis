// The session schedule, kept in a public Google Calendar. The site builds its
// subscribe links from the ID, and the API reads the sessions from the feed.
//
// The ID is public by design: it is how a public calendar is shared.
export const CALENDAR_ID = "8c457a44e95e8c60e3d8688ae0dbc9470c26a9c5ec50784f73be297433cf55eb@group.calendar.google.com";

// Where the sessions happen, and so the zone the site states their times in,
// wherever the visitor is.
export const TIME_ZONE = "America/Los_Angeles";

// The calendar as iCalendar, without its scheme: the site offers it as both
// https:// and webcal://.
export const feedAddress = (id) => `calendar.google.com/calendar/ical/${encodeURIComponent(id)}/public/basic.ics`;
