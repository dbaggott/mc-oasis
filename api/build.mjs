// Bundles the API into one file, dependencies included, so the image carries
// no node_modules and runs no install.
import { build } from "esbuild";

await build({
  entryPoints: [new URL("./src/index.js", import.meta.url).pathname],
  outfile: new URL("./dist/index.js", import.meta.url).pathname,
  bundle: true,
  platform: "node",
  target: "node24",
  format: "esm",
  sourcemap: true,
  // ESM output does not define `require`, which parts of the AWS SDK still call.
  banner: {
    js: "import { createRequire as __createRequire } from 'node:module'; const require = __createRequire(import.meta.url);",
  },
  logLevel: "info",
});
