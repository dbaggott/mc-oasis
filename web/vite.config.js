import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig } from "vite";
import { pages } from "./pages.mjs";
import { dirtSvg, logoSvg } from "./logo.mjs";
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

// The art logo.mjs draws: the logo, put in place of <!-- logo --> so it arrives
// with the page, and the dirt tile the stylesheet repeats, served at /dirt.svg.
function art() {
  return {
    name: "oasis-art",
    configureServer(server) {
      server.middlewares.use("/dirt.svg", (req, res) => {
        res.setHeader("Content-Type", "image/svg+xml");
        res.end(dirtSvg());
      });
    },
    generateBundle() {
      this.emitFile({ type: "asset", fileName: "dirt.svg", source: dirtSvg() });
    },
    transformIndexHtml(html) {
      return html.replace("<!-- logo -->", logoSvg());
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
  plugins: [art(), rules()],
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
