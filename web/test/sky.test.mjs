// Checks sky.js against the built stylesheet — run `npm run build` first.
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import { SKIES, SKY_BY_HOUR, skyFor } from "../src/sky.js";
import { logoSvg } from "../logo.mjs";

test("every hour of the clock has a sky", () => {
  assert.equal(SKY_BY_HOUR.length, 24);
  assert.ok(SKY_BY_HOUR.every(Boolean));
});

test("a sky asked for by name is shown whatever the hour", () => {
  assert.equal(skyFor("night", 12), "night");
});

test("anything but a sky's name falls back to the hour's sky", () => {
  for (const asked of [null, "", "noon", "constructor"]) {
    assert.equal(skyFor(asked, 12), SKY_BY_HOUR[12]);
  }
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
        new RegExp(`\\[data-sky="?${sky}"?\\]\\{[^}]*--sky-${format}:url\\([^)]*\\.${format}`),
        `no ${format} picture for the ${sky} sky`,
      );
    }
  }
});

test("the logo has a target for every sky", () => {
  assert.equal(logoSvg().targets.length, SKIES.size);
});

// Points in a word's units: the middle of a block of the O's stroke, and of the
// hole inside it.
test("a logo target covers its letter but not the hole in it", () => {
  const [o] = logoSvg().targets;
  const covers = (px, py) => o.rects.some(([x, y, w, h]) => px >= x && px < x + w && py >= y && py < y + h);
  assert.ok(covers(2, 14), "the O's left stroke");
  assert.ok(!covers(10, 14), "the O's hole");
});
