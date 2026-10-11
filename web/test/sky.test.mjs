// Checks sky.js against the built stylesheet — run `npm run build` first.
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import { runInNewContext } from "node:vm";
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
  assert.equal(logoSvg().targets.filter(({ egg }) => egg === "sky").length, SKIES.size);
});

// Points in a word's units: the middle of a block of the O's stroke, and of the
// hole inside it.
test("a logo target covers its letter but not the hole in it", () => {
  const [o] = logoSvg().targets;
  const covers = (px, py) => o.rects.some(([x, y, w, h]) => px >= x && px < x + w && py >= y && py < y + h);
  assert.ok(covers(2, 14), "the O's left stroke");
  assert.ok(!covers(10, 14), "the O's hole");
});

// Points in "SMP"'s units: the M is its blocks 6 to 10, each block 4 units.
test("the creeper's target is the M's centre block alone", () => {
  const creeper = logoSvg().targets.filter(({ egg }) => egg === "creeper");
  assert.equal(creeper.length, 1);
  const covers = (px, py) => creeper[0].rects.some(([x, y, w, h]) => px >= x && px < x + w && py >= y && py < y + h);
  assert.ok(covers(34, 10), "the M's centre block");
  assert.ok(!covers(34, 2), "the gap above it");
  assert.ok(!covers(26, 26), "the M's left foot");
});

// The head script as built, the one every page references.
const markSkyScript = () => {
  const dist = resolve(import.meta.dirname, "../dist");
  const [, src] = readFileSync(resolve(dist, "index.html"), "utf8").match(/<script src="(\/assets\/mark-sky-[^"]+)"/);
  return readFileSync(resolve(dist, `.${src}`), "utf8");
};

// The head script run as a browser would, at `hour` on a page whose query is `search`.
function markedSky(search, hour) {
  const documentElement = { dataset: {} };
  const Clock = class extends Date {
    getHours() {
      return hour;
    }
  };
  runInNewContext(markSkyScript(), { document: { documentElement }, location: { search }, URLSearchParams, Date: Clock });
  return documentElement.dataset.sky;
}

test("the head script marks the sky sky.js picks", () => {
  for (const search of ["", "?sky=noon", ...[...SKIES].map((sky) => `?sky=${sky}`)]) {
    for (let hour = 0; hour < 24; hour++) {
      assert.equal(markedSky(search, hour), skyFor(new URLSearchParams(search).get("sky"), hour), `${search} at ${hour}`);
    }
  }
});
