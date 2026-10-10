// Reads plugins/OasisRules/rules.txt the way the OasisRules plugin does
// (Rules.java): one rule per line, trimmed, with a line in [brackets] heading
// the rules after it, and blank lines and lines starting with # left out. Rules
// before any heading come in a section with no heading.
export function parseRules(text) {
  const sections = [];
  for (const line of text.split(/\r?\n/).map((l) => l.trim())) {
    if (line === "" || line.startsWith("#")) continue;
    if (line.startsWith("[") && line.endsWith("]")) {
      sections.push({ heading: line.slice(1, -1).trim(), rules: [] });
    } else {
      if (sections.length === 0) sections.push({ heading: null, rules: [] });
      sections.at(-1).rules.push(line);
    }
  }
  return sections;
}

const ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
const escape = (text) => text.replace(/[&<>"']/g, (c) => ESCAPES[c]);

// Numbered on across sections, as in game, so "rule 7" means one rule.
export function rulesHtml(sections) {
  let first = 1;
  const parts = sections.map(({ heading, rules }) => {
    const items = rules.map((rule) => `<li>${escape(rule)}</li>`).join("");
    const list = `<ol start="${first}">${items}</ol>`;
    first += rules.length;
    return heading === null ? list : `<h2>${escape(heading)}</h2>${list}`;
  });
  return `<div class="rules">${parts.join("")}</div>`;
}
