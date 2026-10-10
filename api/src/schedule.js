// The upcoming sessions, read from the calendar's public iCalendar feed. Google
// serves the feed without CORS headers, so a browser can't read it itself.
import ICAL from "ical.js";
import { TIME_ZONE } from "../../shared/calendar.js";

// How many sessions the Schedule page lists, and how far ahead it looks for
// them. Past either, a visitor subscribes rather than reads.
export const SESSIONS_SHOWN = 12;
export const HORIZON_DAYS = 365;

// Each request re-reads the feed at most this often, so a moved session shows
// within minutes while a busy page costs Google one fetch per instance.
export const FEED_MAX_AGE_MS = 5 * 60 * 1000;

const DAY = 24 * 60 * 60 * 1000;

// The first `limit` sessions in `ics` that have not ended by `from` and start
// before `until`, in start order: each repeating event expanded into its
// occurrences, with any occurrence the calendar moved or cancelled taken as it
// now stands.
//
// An all-day event, or a time given with no zone, is read in TIME_ZONE: the
// day or the hour where the sessions happen.
export function upcomingSessions(ics, { from, until, limit }) {
  const calendar = new ICAL.Component(ICAL.parse(ics));
  const zone = calendar.getTimeZoneByID(TIME_ZONE) ?? ICAL.Timezone.utcTimezone;
  const instant = (time) => {
    if (time.zone !== ICAL.Timezone.localTimezone) return time.toJSDate();
    const zoned = time.clone();
    zoned.isDate = false;
    zoned.zone = zone;
    return zoned.toJSDate();
  };

  const vevents = calendar.getAllSubcomponents("vevent");
  const exceptions = Map.groupBy(
    vevents.filter((v) => v.hasProperty("recurrence-id")),
    (v) => v.getFirstPropertyValue("uid"),
  );

  const sessions = [];
  for (const vevent of vevents) {
    if (vevent.hasProperty("recurrence-id")) continue;
    const event = new ICAL.Event(vevent, { exceptions: exceptions.get(vevent.getFirstPropertyValue("uid")) ?? [] });
    const found = sessions.length;
    for (const occurrence of occurrences(event)) {
      if (instant(occurrence) >= until || sessions.length - found === limit) break;
      const { item, startDate, endDate } = event.getOccurrenceDetails(occurrence);
      if (item.component.getFirstPropertyValue("status") === "CANCELLED") continue;
      const start = instant(startDate);
      const end = instant(endDate);
      if (end <= from || start >= until) continue;
      sessions.push({
        start: start.toISOString(),
        end: end.toISOString(),
        allDay: startDate.isDate,
        title: item.summary,
        // HTML, as Google's editor writes it.
        description: item.description || null,
      });
    }
  }
  return sessions.sort((a, b) => a.start.localeCompare(b.start)).slice(0, limit);
}

// When an event happens as its calendar first set it: once, or each time its
// rule repeats.
function* occurrences(event) {
  if (!event.isRecurring()) {
    yield event.startDate;
    return;
  }
  const expansion = event.iterator();
  for (let next = expansion.next(); next; next = expansion.next()) yield next;
}

// The upcoming sessions as of each call, from a feed re-fetched once it is
// older than FEED_MAX_AGE_MS. When a re-fetch fails the last good copy stands
// in until the next try; with none, the call throws.
export function scheduleReader({ fetchFeed, now = Date.now }) {
  let feed = null;
  let fetchedAt = -Infinity;

  return async function upcoming() {
    const at = now();
    if (at - fetchedAt >= FEED_MAX_AGE_MS) {
      try {
        feed = await fetchFeed();
        fetchedAt = at;
      } catch (err) {
        if (feed === null) throw err;
        // Counted as a fetch, so a Google outage costs one slow request per
        // interval rather than every request.
        fetchedAt = at;
        console.error("schedule feed fetch failed; serving the last copy:", err?.name || "error");
      }
    }
    return upcomingSessions(feed, {
      from: new Date(at),
      until: new Date(at + HORIZON_DAYS * DAY),
      limit: SESSIONS_SHOWN,
    });
  };
}

// The feed at `url`, given up on after a few seconds so a slow Google can't run
// the request into the Lambda's timeout.
export function feedFetcher(url) {
  return async () => {
    const res = await fetch(url, { signal: AbortSignal.timeout(4000) });
    if (!res.ok) throw new Error(`feed answered ${res.status}`);
    return res.text();
  };
}
