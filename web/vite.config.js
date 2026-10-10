import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig } from "vite";
import { pages } from "./pages.mjs";
import { scheduleHtml } from "./calendar.mjs";
import { navHtml } from "./chrome.mjs";
import { dirtSvg, logoImg, logoSvg } from "./logo.mjs";
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

// The art logo.mjs draws, as files at the site's root: the logo every page's
// <img> shares, and the dirt tile the stylesheet repeats. <!-- logo --> in a
// page becomes the large logo.
const artFiles = {
  "logo.svg": () => logoSvg().svg,
  "dirt.svg": dirtSvg,
};

function art() {
  return {
    name: "oasis-art",
    configureServer(server) {
      for (const [file, draw] of Object.entries(artFiles)) {
        server.middlewares.use(`/${file}`, (req, res) => {
          res.setHeader("Content-Type", "image/svg+xml");
          res.end(draw());
        });
      }
    },
    generateBundle() {
      for (const [file, draw] of Object.entries(artFiles)) {
        this.emitFile({ type: "asset", fileName: file, source: draw() });
      }
    },
    transformIndexHtml(html) {
      return html.replace("<!-- logo -->", logoImg("logo-art"));
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
