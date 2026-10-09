import assert from "node:assert/strict";
import { test } from "node:test";
import { parseRules, rulesHtml } from "../rules.mjs";

test("comments and blank lines are left out, and rules are trimmed", () => {
  assert.deepEqual(parseRules("# a comment\n\n  Be kind.  \r\n#another\nNo griefing.\n"), ["Be kind.", "No griefing."]);
});

test("a rule is escaped into the list, never parsed as markup", () => {
  assert.equal(rulesHtml(["Use <b> & \"quotes\""]), '<ol class="rules"><li>Use &lt;b&gt; &amp; &quot;quotes&quot;</li></ol>');
});
