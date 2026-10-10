// Checks the built site in dist/ — run `npm run build` first.
import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import { pages } from "../pages.mjs";
import { logoSvg } from "../logo.mjs";
import { parseRules } from "../rules.mjs";

const dist = resolve(import.meta.dirname, "../dist");

// Each reference is a path from the site's root, or one relative to `dir`.
function assertBuilt(referrer, dir, refs) {
  for (const ref of refs) {
    const file = ref.startsWith("/") ? resolve(dist, `.${ref}`) : resolve(dir, ref);
    assert.ok(existsSync(file), `${referrer} references ${ref}, which was not built`);
  }
}

for (const page of pages) {
  const file = resolve(dist, page);

  test(`${page} is built, and every file it references exists`, () => {
    assert.ok(existsSync(file), `${page} is missing from dist/ — is it built from pages.mjs?`);
    const html = readFileSync(file, "utf8");
    const refs = [...html.matchAll(/(?:src|href)="(\/[^"#]+)"/g)].map((m) => m[1]);
    assert.ok(refs.length > 0, `${page} references nothing`);
    assertBuilt(page, dist, refs.filter((r) => r !== "/" && !r.endsWith("/")));
  });

  // The Content-Security-Policy (modules/static-site in dbaggott/infrastructure)
  // allows scripts and styles from the site's own files only, so an inline one
  // works locally and is refused in production.
  test(`${page} carries no inline script or style`, () => {
    const html = readFileSync(file, "utf8");
    assert.doesNotMatch(html, /<script(?![^>]*\ssrc=)[^>]*>/, "inline <script>");
    assert.doesNotMatch(html, /<style[\s>]/, "inline <style>");
    assert.doesNotMatch(html, /\sstyle="/, "style attribute");
  });
}

// A script can also write a style attribute, by building markup as a string.
// The CSP refuses those the same way, but they never appear in a built page.
test("built scripts write no style attributes", () => {
  const assets = resolve(dist, "assets");
  const scripts = readdirSync(assets, { recursive: true }).filter((file) => file.endsWith(".js"));
  assert.ok(scripts.length > 0, "no built scripts in dist/assets/ — did the build run?");
  for (const script of scripts) {
    const js = readFileSync(resolve(assets, script), "utf8");
    assert.doesNotMatch(js, /style="/, `${script} builds a style attribute`);
  }
});

// Vite inlines small assets as data: URLs, which the CSP refuses for anything
// but images.
test("built assets inline no data: URL other than an image", () => {
  const assets = resolve(dist, "assets");
  for (const file of readdirSync(assets, { recursive: true }).filter((f) => /\.(css|js)$/.test(f))) {
    const text = readFileSync(resolve(assets, file), "utf8");
    const inlined = [...text.matchAll(/data:([a-z]+\/[a-z0-9.+-]+)/gi)].map((m) => m[1]);
    assert.deepEqual(inlined.filter((type) => !type.startsWith("image/")), [], `${file} inlines a non-image`);
  }
});

test("the rules page carries every heading and rule from rules.txt, in order", () => {
  const sections = parseRules(readFileSync(resolve(import.meta.dirname, "../../plugins/OasisRules/rules.txt"), "utf8"));
  assert.ok(sections.some(({ rules }) => rules.length > 0), "rules.txt has no rules");
  const html = readFileSync(resolve(dist, "rules/index.html"), "utf8");
  assert.doesNotMatch(html, /<!-- rules -->/, "the rules marker was left in the page");
  const block = html.match(/<div class="rules">(.*?)<\/div>/s)?.[1] ?? "";
  const unescaped = { "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'" };
  const text = (s) => s.replace(/&(amp|lt|gt|quot|#39);/g, (e) => unescaped[e]);
  const expected = sections.flatMap(({ heading, rules }) => [...(heading === null ? [] : [`h2 ${heading}`]), ...rules.map((r) => `li ${r}`)]);
  const found = [...block.matchAll(/<(h2|li)>(.*?)<\/\1>/g)].map((m) => `${m[1]} ${text(m[2])}`);
  assert.deepEqual(found, expected);
});

// In production a POST reaches the API only with the SHA-256 of its body in
// this header (src/request-access.js says why). Nothing local exercises that
// path, so the built form is checked for it here.
test("the built forms send the body hash the Function URL requires", () => {
  const assets = resolve(dist, "assets");
  const js = readdirSync(assets, { recursive: true })
    .filter((file) => file.endsWith(".js"))
    .map((file) => readFileSync(resolve(assets, file), "utf8"))
    .join("\n");
  assert.match(js, /x-amz-content-sha256/);
  assert.match(js, /subtle\.digest\(\s*["'`]SHA-256["'`]/, "the header is sent, but nothing hashes the body for it");
});

test("every file the stylesheets reference exists", () => {
  const assets = resolve(dist, "assets");
  const sheets = readdirSync(assets).filter((file) => file.endsWith(".css"));
  assert.ok(sheets.length > 0, "no stylesheet was built");
  for (const sheet of sheets) {
    const css = readFileSync(resolve(assets, sheet), "utf8");
    const refs = [...css.matchAll(/url\(\s*["']?([^"')]+?)["']?\s*\)/g)].map((m) => m[1]);
    assertBuilt(sheet, assets, refs.filter((r) => !r.startsWith("data:")));
  }
});

test("the logo is built, and the home page shows the logo large", () => {
  assert.match(readFileSync(resolve(dist, "logo.svg"), "utf8"), /^<svg xmlns="http:\/\/www.w3.org\/2000\/svg"/);
  const html = readFileSync(resolve(dist, "index.html"), "utf8");
  assert.doesNotMatch(html, /<!-- logo -->/, "the logo marker was left in the page");
  assert.match(html, /<img class="logo-art" src="\/logo.svg" alt="Oasis SMP"/);
});

// Which link each page's bar marks as the current page; none for the 404.
const CURRENT = {
  "index.html": "/",
  "play/index.html": "/play/",
  "schedule/index.html": "/schedule/",
  "rules/index.html": "/rules/",
  "request-access/index.html": "/request-access/",
  "contact/index.html": "/contact/",
  "404.html": null,
};

for (const page of pages) {
  test(`${page} carries the shared navigation, marking the right page`, () => {
    const html = readFileSync(resolve(dist, page), "utf8");
    assert.doesNotMatch(html, /<!-- nav -->/, "the nav marker was left in the page");
    assert.match(html, /<nav class="nav[^"]*" aria-label="Site">/);
    const marked = [...html.matchAll(/href="([^"]+)" aria-current="page"/g)].map((m) => m[1]);
    assert.deepEqual(marked, CURRENT[page] ? [CURRENT[page]] : []);
  });
}

// Every page's bar is the same apart from which link it marks, so moving
// between pages never shifts it.
test("every page carries the same navigation bar", () => {
  const bars = pages.map((page) =>
    readFileSync(resolve(dist, page), "utf8")
      .match(/<header class="site-nav">.*?<\/header>/s)[0]
      .replace(/ aria-current="page"/g, ""),
  );
  for (const bar of bars) assert.equal(bar, bars[0]);
});

// The OFL asks that the font's license travel with it.
test("the Monocraft license is published beside the site", () => {
  assert.match(readFileSync(resolve(dist, "licenses/Monocraft-OFL.txt"), "utf8"), /SIL OPEN FONT LICENSE/);
});

test("the schedule page carries the schedule, or says it is coming", () => {
  const html = readFileSync(resolve(dist, "schedule/index.html"), "utf8");
  assert.doesNotMatch(html, /<!-- schedule -->/, "the schedule marker was left in the page");
  assert.match(html, /<div class="sessions">|The session schedule is coming soon/);
});

test("the favicon is built in every form, and every page links it", () => {
  const png = (file) => readFileSync(resolve(dist, file)).subarray(0, 24);
  for (const [file, size] of [["favicon.png", 32], ["apple-touch-icon.png", 180]]) {
    const head = png(file);
    assert.equal(head.toString("latin1", 1, 4), "PNG", `${file} is not a PNG`);
    assert.equal(head.readUInt32BE(16), size, `${file} is not ${size} wide`);
  }
  assert.match(readFileSync(resolve(dist, "favicon.svg"), "utf8"), /^<svg /);
  for (const page of pages) {
    const html = readFileSync(resolve(dist, page), "utf8");
    for (const href of ["/favicon.svg", "/favicon.png", "/apple-touch-icon.png"]) {
      assert.ok(html.includes(`href="${href}"`), `${page} does not link ${href}`);
    }
  }
});

test("the home page's logo carries its targets", () => {
  const html = readFileSync(resolve(dist, "index.html"), "utf8");
  const targets = html.match(/<svg class="logo-targets"[^]*?<\/svg>/);
  assert.ok(targets, "no .logo-targets on the home page");
  assert.equal(targets[0].match(/<path /g).length, logoSvg().targets.length);
});
