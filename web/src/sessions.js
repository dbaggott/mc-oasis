// The upcoming sessions as the Schedule page lists them: each one's day and
// time where it happens, whatever zone the visitor is in.
export function sessionRows(sessions, timeZone, now = new Date()) {
  const part = (options) => new Intl.DateTimeFormat("en-US", { timeZone, ...options });
  const weekday = part({ weekday: "short" });
  const day = part({ day: "numeric" });
  const month = part({ month: "short" });
  const isoDate = part({ year: "numeric", month: "2-digit", day: "2-digit" });
  const time = part({ hour: "numeric", minute: "2-digit" });

  return sessions.map(({ start, end, title, description }) => {
    const from = new Date(start);
    const to = new Date(end);
    const [{ value: m }, , { value: d }, , { value: y }] = isoDate.formatToParts(from);
    return {
      date: `${y}-${m}-${d}`,
      weekday: weekday.format(from),
      day: day.format(from),
      month: month.format(from),
      time: time.formatRange(from, to),
      title,
      description,
      underway: from <= now && now < to,
    };
  });
}

