import assert from "node:assert/strict";
import { test } from "node:test";
import { parseRules, rulesHtml } from "../rules.mjs";

test("comments and blank lines are left out, and rules are trimmed", () => {
  assert.deepEqual(parseRules("# a comment\n\n  Be kind.  \r\n#another\nNo griefing.\n"), [
    { heading: null, rules: ["Be kind.", "No griefing."] },
  ]);
});

test("a line in brackets heads the rules after it", () => {
  assert.deepEqual(parseRules("[ Chat ]\nBe kind.\n[Building]\nNo griefing.\nNo stealing.\n"), [
    { heading: "Chat", rules: ["Be kind."] },
    { heading: "Building", rules: ["No griefing.", "No stealing."] },
  ]);
});

test("rules are numbered on across headings", () => {
  assert.equal(
    rulesHtml(parseRules("[Chat]\nBe kind.\nNo slurs.\n[Building]\nNo griefing.")),
    '<div class="rules"><h2>Chat</h2><ol start="1"><li>Be kind.</li><li>No slurs.</li></ol>' +
      '<h2>Building</h2><ol start="3"><li>No griefing.</li></ol></div>',
  );
});

test("a rule or heading is escaped, never parsed as markup", () => {
  assert.equal(
    rulesHtml([{ heading: "<i>", rules: ['Use <b> & "quotes"'] }]),
    '<div class="rules"><h2>&lt;i&gt;</h2><ol start="1"><li>Use &lt;b&gt; &amp; &quot;quotes&quot;</li></ol></div>',
  );
});
