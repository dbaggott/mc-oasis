// The sky behind the gazebo for each hour on the visitor's own clock, from
// midnight: the sunrise's pictures, played backward, stand in for the evening.
export const SKY_BY_HOUR = [
  "night", "night", "night", "night",
  "indigo", "lavender", "pink", "low-sun", "golden",
  "day", "day", "day", "day", "day", "day", "day", "day",
  "golden", "low-sun", "afterglow", "lavender", "indigo",
  "night", "night",
];

export const skyAt = (hour) => SKY_BY_HOUR[hour];
