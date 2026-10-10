// Every HTML page the site builds, relative to web/. vite.config.js builds
// exactly these and test/build.test.mjs checks the output has them, so a page
// missing from this list is missing from the site.
export const pages = [
  "index.html",
  "play/index.html",
  "schedule/index.html",
  "rules/index.html",
  "request-access/index.html",
  "contact/index.html",
  "404.html",
];
