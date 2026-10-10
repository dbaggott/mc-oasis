// The navigation bar every page shares across the top. vite.config.js puts it
// in place of <!-- nav -->, so a page carries the marker and no copy of it.
import { logoImg } from "./logo.mjs";

// Either side of the logo, in order. The last on the right is the call to
// action, drawn apart from the others.
const LEFT = [
  { href: "/", label: "Home" },
  { href: "/play/", label: "Play" },
];
const RIGHT = [
  { href: "/rules/", label: "Rules" },
  { href: "/request-access/", label: "Request Access", cta: true },
];

// The page a link names, from the path Vite hands transformIndexHtml:
// "/play/index.html" and "/play/" are both /play/.
export function pageOf(path) {
  return path.replace(/index\.html$/, "");
}

function links(items, current, id) {
  const lis = items.map(({ href, label, cta }) => {
    const here = href === current ? ' aria-current="page"' : "";
    return `<li><a class="nav-item${cta ? " nav-cta" : ""}" href="${href}"${here}>${label}</a></li>`;
  });
  return `<ul class="nav-links" id="${id}">${lis.join("")}</ul>`;
}

// The bar, the same on every page so that moving between pages never moves it.
export function navHtml(path) {
  const current = pageOf(path);
  return [
    '<a class="skip-link" href="#main">Skip to content</a>',
    '<header class="site-nav">',
    '<nav class="nav" aria-label="Site">',
    '<button class="nav-toggle" type="button" aria-expanded="false" aria-controls="nav-left nav-right" aria-label="Menu">',
    '<span class="nav-toggle-bars" aria-hidden="true"></span>',
    "</button>",
    links(LEFT, current, "nav-left"),
    `<a class="nav-logo" href="/">${logoImg("nav-logo-art")}</a>`,
    links(RIGHT, current, "nav-right"),
    "</nav>",
    "</header>",
  ].join("");
}
