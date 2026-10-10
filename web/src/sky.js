// The sky behind the gazebo for each hour on the visitor's own clock, from
// midnight, matched to the in-game sky at that hour (tick 0 is 6 AM).
export const SKY_BY_HOUR = [
  "night", "night", "night", "night",
  "predawn", "sunrise", "dawn", "dawn",
  "day", "day", "day", "day", "day", "day", "day", "day",
  "day", "day", "day",
  "evening", "evening", "evening", "evening",
  "night",
];

export const SKIES = new Set(SKY_BY_HOUR);

// The sky to show: the one `asked` names, if it names one, else the hour's.
export const skyFor = (asked, hour) => (SKIES.has(asked) ? asked : SKY_BY_HOUR[hour]);
