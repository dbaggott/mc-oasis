// The upcoming sessions as the Schedule page lists them: each one's day and
// time where it happens, whatever zone the visitor is in.
export function sessionRows(sessions, timeZone, now = new Date()) {
  const part = (options) => new Intl.DateTimeFormat("en-US", { timeZone, ...options });
  const weekday = part({ weekday: "short" });
  const day = part({ day: "numeric" });
  const month = part({ month: "short" });
  const isoDate = part({ year: "numeric", month: "2-digit", day: "2-digit" });
  const time = part({ hour: "numeric", minute: "2-digit" });

  const lastDay = part({ weekday: "short", month: "short", day: "numeric" });

  return sessions.map(({ start, end, allDay, title, description }) => {
    const from = new Date(start);
    const to = new Date(end);
    const parts = Object.fromEntries(isoDate.formatToParts(from).map(({ type, value }) => [type, value]));
    return {
      date: `${parts.year}-${parts.month}-${parts.day}`,
      weekday: weekday.format(from),
      day: day.format(from),
      month: month.format(from),
      time: allDay ? allDayLabel(from, to, isoDate, lastDay) : time.formatRange(from, to),
      title,
      description,
      underway: from <= now && now < to,
    };
  });
}


// "All day", or for an event over several days, the day it runs through. An
// all-day event ends at the start of the day after its last.
function allDayLabel(from, to, isoDate, lastDay) {
  const through = new Date(to.getTime() - 1);
  return isoDate.format(through) === isoDate.format(from) ? "All day" : `All day, through ${lastDay.format(through)}`;
}

// The zone's everyday name, "Pacific Time" say.
export function zoneName(timeZone) {
  return new Intl.DateTimeFormat("en-US", { timeZone, timeZoneName: "longGeneric" })
    .formatToParts(new Date())
    .find((part) => part.type === "timeZoneName").value;
}
