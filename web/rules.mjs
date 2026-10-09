// Reads plugins/OasisRules/rules.txt the way the OasisRules plugin does
// (Rules.java): one rule per line, trimmed, with blank lines and lines starting
// with # left out.
export function parseRules(text) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line !== "" && !line.startsWith("#"));
}

const ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };

export function rulesHtml(rules) {
  const items = rules.map((rule) => `<li>${rule.replace(/[&<>"']/g, (c) => ESCAPES[c])}</li>`);
  return `<ol class="rules">${items.join("")}</ol>`;
}
