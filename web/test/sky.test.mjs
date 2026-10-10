// Checks sky.js against the built stylesheet — run `npm run build` first.
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import { SKIES, SKY_BY_HOUR } from "../src/sky.js";

test("every hour of the clock has a sky", () => {
  assert.equal(SKY_BY_HOUR.length, 24);
  assert.ok(SKY_BY_HOUR.every(Boolean));
});

test("the stylesheet gives every sky a picture", () => {
  const assets = resolve(import.meta.dirname, "../dist/assets");
  const css = readdirSync(assets)
    .filter((file) => file.endsWith(".css"))
    .map((file) => readFileSync(resolve(assets, file), "utf8"))
    .join("\n");
  for (const sky of SKIES) {
    for (const format of ["avif", "webp"]) {
      assert.match(
        css,
        new RegExp(`\\[data-sky="?${sky}"?\\]\\{--backdrop:url\\([^)]*\\.${format}`),
        `no ${format} picture for the ${sky} sky`,
      );
    }
  }
});
