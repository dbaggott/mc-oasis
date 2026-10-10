import assert from "node:assert/strict";
import { test } from "node:test";
import { timeOfDay } from "../src/time-of-day.js";

test("every hour of the clock falls in one part of the day", () => {
  const parts = Array.from({ length: 24 }, (_, hour) => timeOfDay(hour));
  assert.deepEqual(parts, [
    ...Array(5).fill("night"),
    ...Array(2).fill("dawn"),
    ...Array(10).fill("day"),
    ...Array(3).fill("sunset"),
    ...Array(4).fill("night"),
  ]);
});
