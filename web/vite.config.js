import { createHash } from "node:crypto";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig } from "vite";
import { pages } from "./pages.mjs";
import { scheduleHtml } from "./calendar.mjs";
import { navHtml } from "./chrome.mjs";
import { faviconPng, faviconSvg, logoImg, logoSvg, logoTargets } from "./logo.mjs";
import { markSkyScript } from "./mark-sky.mjs";
import { parseRules, rulesHtml } from "./rules.mjs";

const repoRoot = resolve(import.meta.dirname, "..");

// The rules players see in game with /rules. The site renders the same file, so
// the two never disagree.
const rulesFile = resolve(repoRoot, "plugins/OasisRules/rules.txt");

// A checkout with no install of its own, a fresh worktree say, resolves its
// packages from a checkout above it, and runs on that checkout's versions.
if (!existsSync(resolve(import.meta.dirname, "node_modules/vite"))) {
  throw new Error(`No packages are installed in ${import.meta.dirname}. Run \`npm ci\` there first.`);
}

// "request-access/index.html" -> "request-access", "index.html" -> "index".
const entryName = (page) => page.replace(/(\/index)?\.html$/, "");

// Every page's <head> runs this before its first paint, so it is named for its
// contents and published under assets/ with the bundle's own files: cached for
// good, never revalidated in the way of a page.
const markSkyFile = `assets/mark-sky-${createHash("sha256").update(markSkyScript()).digest("hex").slice(0, 8)}.js`;

// Files drawn at build time: the script that marks the sky, and at the site's
// root the logo every page's <img> shares and the favicon in each form browsers
// ask for. <!-- logo --> in a page becomes the large logo, and every page's
// <head> gets that script and the favicon links.
const artFiles = {
  [markSkyFile]: { type: "text/javascript", draw: markSkyScript },
  "logo.svg": { type: "image/svg+xml", draw: () => logoSvg().svg },
  "favicon.svg": { type: "image/svg+xml", draw: faviconSvg },
  "favicon.png": { type: "image/png", draw: () => faviconPng(32) },
  "apple-touch-icon.png": { type: "image/png", draw: () => faviconPng(180) },
};

const iconLinks = [
  { rel: "icon", href: "/favicon.svg", type: "image/svg+xml" },
  { rel: "icon", href: "/favicon.png", type: "image/png", sizes: "32x32" },
  { rel: "apple-touch-icon", href: "/apple-touch-icon.png" },
].map((attrs) => ({ tag: "link", attrs, injectTo: "head" }));

const markSky = { tag: "script", attrs: { src: `/${markSkyFile}` }, injectTo: "head" };

function art() {
  return {
    name: "oasis-art",
    configureServer(server) {
      for (const [file, { type, draw }] of Object.entries(artFiles)) {
        server.middlewares.use(`/${file}`, (req, res) => {
          res.setHeader("Content-Type", type);
          res.end(draw());
        });
      }
    },
    generateBundle() {
      for (const [file, { draw }] of Object.entries(artFiles)) {
        this.emitFile({ type: "asset", fileName: file, source: draw() });
      }
    },
    transformIndexHtml(html) {
      return { html: html.replace("<!-- logo -->", logoImg("logo-art") + logoTargets()), tags: [markSky, ...iconLinks] };
    },
  };
}

// The navigation bar every page shares (chrome.mjs), put in place of
// <!-- nav -->, marking the page it is on; and the schedule (calendar.mjs), in
// place of <!-- schedule -->.
function chrome() {
  return {
    name: "oasis-chrome",
    transformIndexHtml(html, ctx) {
      return html.replace("<!-- nav -->", navHtml(ctx.path)).replace("<!-- schedule -->", scheduleHtml());
    },
  };
}

// Replaces <!-- rules --> in a page with the rules as a list, at build time, so
// the rules are in the page itself rather than fetched by a script.
function rules() {
  return {
    name: "oasis-rules",
    configureServer(server) {
      server.watcher.add(rulesFile);
      server.watcher.on("change", (file) => {
        if (file === rulesFile) server.ws.send({ type: "full-reload" });
      });
    },
    transformIndexHtml(html) {
      if (!html.includes("<!-- rules -->")) return html;
      return html.replace("<!-- rules -->", rulesHtml(parseRules(readFileSync(rulesFile, "utf8"))));
    },
  };
}

// The Content-Security-Policy allows data: URLs for images only, so any other
// asset small enough to inline (a font subset, say) must ship as a file.
const inlinableAsset = (filePath) =>
  /\.(avif|gif|ico|jpe?g|png|svg|webp)$/i.test(filePath) ? undefined : false;

export default defineConfig({
  plugins: [art(), chrome(), rules()],
  server: {
    // The form imports ../shared, outside web/.
    fs: { allow: [repoRoot] },
    // `npm run dev:api` serves the API here; in production CloudFront routes
    // /api/* to it on the same origin.
    proxy: { "/api": "http://localhost:8787" },
  },
  build: {
    assetsInlineLimit: inlinableAsset,
    rolldownOptions: {
      input: Object.fromEntries(pages.map((page) => [entryName(page), resolve(import.meta.dirname, page)])),
    },
  },
});
