// The part of the day an hour on the visitor's own clock falls in, which picks
// the backdrop: the gazebo at dawn, by day, at sunset or by night.
export function timeOfDay(hour) {
  if (hour < 5) return "night";
  if (hour < 7) return "dawn";
  if (hour < 17) return "day";
  if (hour < 20) return "sunset";
  return "night";
}
