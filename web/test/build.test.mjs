// Checks the built site in dist/ — run `npm run build` first.
import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import { pages } from "../pages.mjs";
import { parseRules } from "../rules.mjs";

const dist = resolve(import.meta.dirname, "../dist");

for (const page of pages) {
  const file = resolve(dist, page);

  test(`${page} is built, and every file it references exists`, () => {
    assert.ok(existsSync(file), `${page} is missing from dist/ — is it built from pages.mjs?`);
    const html = readFileSync(file, "utf8");
    const refs = [...html.matchAll(/(?:src|href)="(\/[^"#]+)"/g)].map((m) => m[1]);
    assert.ok(refs.length > 0, `${page} references nothing`);
    for (const ref of refs.filter((r) => r !== "/" && !r.endsWith("/"))) {
      assert.ok(existsSync(resolve(dist, `.${ref}`)), `${page} references ${ref}, which was not built`);
    }
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

test("the home page carries every rule from rules.txt, in order", () => {
  const rules = parseRules(readFileSync(resolve(import.meta.dirname, "../../plugins/OasisRules/rules.txt"), "utf8"));
  assert.ok(rules.length > 0, "rules.txt has no rules");
  const html = readFileSync(resolve(dist, "index.html"), "utf8");
  assert.doesNotMatch(html, /<!-- rules -->/, "the rules marker was left in the page");
  const list = html.match(/<ol class="rules">(.*?)<\/ol>/s)?.[1] ?? "";
  const unescaped = { "&amp;": "&", "&lt;": "<", "&gt;": ">", "&quot;": '"', "&#39;": "'" };
  const items = [...list.matchAll(/<li>(.*?)<\/li>/g)].map((m) => m[1].replace(/&(amp|lt|gt|quot|#39);/g, (e) => unescaped[e]));
  assert.deepEqual(items, rules);
});
