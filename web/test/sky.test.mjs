// Checks sky.js against the built stylesheet — run `npm run build` first.
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import { SKIES, SKY_BY_HOUR, otherSkies, skyFor } from "../src/sky.js";
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

test("the logo's targets reach every sky, the page's own last", () => {
  const { targets } = logoSvg();
  for (const sky of SKIES) {
    const skies = [...otherSkies(sky), sky];
    assert.equal(skies.length, targets.length);
    assert.deepEqual(new Set(skies), SKIES);
    assert.equal(skies.at(-1), sky);
  }
});

test("every logo target lies within the logo", () => {
  const { width, height, targets } = logoSvg();
  for (const t of targets) {
    assert.ok(t.x >= 0 && t.y >= 0 && t.x + t.width <= width && t.y + t.height <= height, JSON.stringify(t));
  }
});
