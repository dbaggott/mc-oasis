import assert from "node:assert/strict";
import { test } from "node:test";
import { COMMENTS_MAX, REQUEST_BYTES_MAX } from "../../shared/access-request.js";
import { CONTACT_BYTES_MAX, MESSAGE_MAX } from "../../shared/contact.js";
import { RETENTION_DAYS, TRAP_FIELD } from "../../shared/forms.js";
import { createApp, SUBMISSIONS_PER_IP_PER_HOUR } from "../src/app.js";
import { MESSAGE_SUBJECT, messageBody, requestBody, requestSubject } from "../src/notify.js";
import { memoryStore } from "../src/store.js";

const build = { branch: "main", commit: "abc123", builtAt: "2026-10-09T00:00:00Z" };

// Named for what the devices make them: a computer alone could be either
// edition; a console means Bedrock.
const computer = { playerName: "Steve_42", devices: ["computer"], grade: "5", parentEmails: ["parent@example.com"] };
const bedrock = {
  playerName: "Cool Gamer#1234",
  devices: ["console", "mobile"],
  grade: "1",
  parentEmails: ["one@example.com", "two@example.com"],
};

function setup({ now = () => 1_000_000 } = {}) {
  const store = memoryStore();
  const announced = [];
  const app = createApp({ store, notify: async (kind, item) => announced.push({ kind, item }), build, now });
  return { app, store, announced, requests: store.items.request, messages: store.items.message };
}

function post(app, body, headers = {}, path = "/api/access-requests") {
  const json = JSON.stringify(body);
  return app.request(path, {
    method: "POST",
    headers: { "content-type": "application/json", "content-length": String(json.length), ...headers },
    body: json,
  });
}

test("a request is stored, then announced, and answered ok", async () => {
  const { app, store, announced } = setup();
  const res = await post(app, computer);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
  assert.equal(store.items.request.length, 1);
  const [saved] = store.items.request;
  assert.equal(saved.playerName, "Steve_42");
  assert.deepEqual(saved.devices, ["computer"]);
  assert.equal(saved.grade, "5");
  assert.deepEqual(saved.parentEmails, ["parent@example.com"]);
  assert.equal(saved.createdAt, 1_000_000);
  assert.equal(saved.expiresAt, 1_000_000 + RETENTION_DAYS * 24 * 60 * 60 * 1000);
  assert.deepEqual(announced, [{ kind: "request", item: saved }]);
});

test("comments are kept, trimmed, and absent ones stored as null", async () => {
  const { app, store } = setup();
  assert.equal((await post(app, { ...computer, comments: "  Plays with her cousin Sam.  " })).status, 200);
  assert.equal((await post(app, computer)).status, 200);
  assert.equal((await post(app, { ...computer, comments: "   " })).status, 200);
  assert.deepEqual(
    store.items.request.map((r) => r.comments),
    ["Plays with her cousin Sam.", null, null],
  );
});

test("comments longer than the limit are refused", async () => {
  const { app, store } = setup();
  assert.equal((await post(app, { ...computer, comments: "x".repeat(COMMENTS_MAX + 1) })).status, 400);
  assert.equal(store.items.request.length, 0);
});

test("a Bedrock request keeps its devices and gamertag", async () => {
  const { app, store } = setup();
  assert.equal((await post(app, bedrock)).status, 200);
  const [saved] = store.items.request;
  assert.equal(saved.playerName, "Cool Gamer#1234");
  assert.deepEqual(saved.devices, ["console", "mobile"]);
});

test("a gamertag in another script is accepted", async () => {
  const { app, store } = setup();
  assert.equal((await post(app, { ...bedrock, playerName: "ゲーマー 7" })).status, 200);
  assert.equal(store.items.request[0].playerName, "ゲーマー 7");
});

test("parent emails are trimmed, lowercased and deduplicated", async () => {
  const { app, store } = setup();
  const res = await post(app, { ...computer, parentEmails: [" Parent@Example.com ", "parent@example.com"] });
  assert.equal(res.status, 200);
  assert.deepEqual(store.items.request[0].parentEmails, ["parent@example.com"]);
});

// What the API stores is what the server and the operator need; a field it
// does not know is dropped rather than refused, since the page and the API
// deploy separately and a cached page may send one.
test("a field the API does not know is dropped, not stored", async () => {
  const { app, store } = setup();
  assert.equal((await post(app, { ...computer, childName: "Alex" })).status, 200);
  assert.equal("childName" in store.items.request[0], false);
});

test("anything in the trap field is answered ok and neither kept nor announced", async () => {
  const { app, store, announced } = setup();
  const res = await post(app, { ...computer, [TRAP_FIELD]: "http://spam" });
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
  assert.equal(store.items.request.length, 0);
  assert.equal(announced.length, 0);
});

for (const [why, body] of [
  ["a name too short for Java and starting with no letter or digit", { ...computer, playerName: "_a" }],
  ["a name longer than either edition allows", { ...computer, playerName: "a".repeat(20) }],
  ["no devices", { ...computer, devices: undefined }],
  ["a gamertag with a line break", { ...bedrock, playerName: "Gamer\nparents:  forged" }],
  ["an empty device list", { ...bedrock, devices: [] }],
  ["an unknown device", { ...bedrock, devices: ["toaster"] }],
  ["an unknown grade", { ...computer, grade: "13" }],
  ["a numeric grade", { ...computer, grade: 5 }],
  ["no parent email", { ...computer, parentEmails: [] }],
  ["a parent email that is not one", { ...computer, parentEmails: ["not an email"] }],
  ["too many parent emails", { ...computer, parentEmails: ["a@x.com", "b@x.com", "c@x.com", "d@x.com"] }],
]) {
  test(`${why} is refused`, async () => {
    const { app, store } = setup();
    assert.equal((await post(app, body)).status, 400);
    assert.equal(store.items.request.length, 0);
  });
}

test("malformed JSON is a 400, not a 500", async () => {
  const { app } = setup();
  const res = await app.request("/api/access-requests", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: "{",
  });
  assert.equal(res.status, 400);
});

test("a body declared over the limit is refused before it is read", async () => {
  const { app, store } = setup();
  const res = await post(app, computer, { "content-length": String(REQUEST_BYTES_MAX + 1) });
  assert.equal(res.status, 413);
  assert.equal(store.items.request.length, 0);
});

test("one address is limited per hour; another is not, and the next hour starts over", async () => {
  let clock = 0;
  const { app, store } = setup({ now: () => clock });
  const from = (ip) => ({ "cloudfront-viewer-address": `${ip}:443` });

  for (let i = 0; i < SUBMISSIONS_PER_IP_PER_HOUR; i++) {
    assert.equal((await post(app, computer, from("192.0.2.1"))).status, 200);
  }
  assert.equal((await post(app, computer, from("192.0.2.1"))).status, 429);
  assert.equal((await post(app, computer, from("192.0.2.2"))).status, 200);

  clock = 60 * 60 * 1000;
  assert.equal((await post(app, computer, from("192.0.2.1"))).status, 200);
  assert.equal(store.items.request.length, SUBMISSIONS_PER_IP_PER_HOUR + 2);
});

test("addresses in one IPv6 /64 share a limit", async () => {
  const { app } = setup();
  for (let i = 0; i < SUBMISSIONS_PER_IP_PER_HOUR; i++) {
    const res = await post(app, computer, { "cloudfront-viewer-address": `[2001:db8:0:1::${i + 1}]:443` });
    assert.equal(res.status, 200);
  }
  const res = await post(app, computer, { "cloudfront-viewer-address": "[2001:0db8:0000:0001:ffff::1]:443" });
  assert.equal(res.status, 429);
});

test("every response is no-store", async () => {
  const { app } = setup();
  for (const res of [await app.request("/api/"), await post(app, computer), await app.request("/api/nope")]) {
    assert.equal(res.headers.get("cache-control"), "no-store");
  }
});

test("GET /api/ reports the build", async () => {
  const { app } = setup();
  assert.deepEqual(await (await app.request("/api/")).json(), build);
});

test("/api/ping answers 200 to a bodied POST", async () => {
  const { app } = setup();
  const res = await app.request("/api/ping", { method: "POST", body: "x".repeat(1000) });
  assert.equal(res.status, 200);
});

test("a console request is marked in the subject; a computer-only one is not", () => {
  const consoleRequest = { ...bedrock, id: "r1", devices: ["console"] };
  const computerRequest = { ...computer, id: "r2", devices: ["computer"] };
  assert.equal(requestSubject(consoleRequest), "Oasis SMP access request (console)");
  assert.equal(requestSubject(computerRequest), "Oasis SMP access request");
  assert.match(requestBody(consoleRequest, build), /^console: {2}yes$/m);
  assert.match(requestBody(computerRequest, build), /^console: {2}no$/m);
});

test("the devices settle the edition: anything but a computer is Bedrock", () => {
  assert.match(requestBody({ ...bedrock, id: "r1" }, build), /^edition: {2}Bedrock$/m);
  assert.match(requestBody({ ...computer, id: "r2" }, build), /^edition: {2}unknown \(computer only\)$/m);
  assert.match(requestBody({ ...computer, id: "r2" }, build), /Check the name with Mojang \(Java\) and Xbox \(Bedrock\)/);
  assert.match(requestBody({ ...computer, id: "r3", devices: ["computer", "mobile"] }, build), /^edition: {2}Bedrock$/m);
});

test("the notification names everything needed to act on the request", () => {
  const body = requestBody({ ...bedrock, id: "r1" }, build);
  assert.match(body, /^player: {3}Cool Gamer#1234$/m);
  assert.match(body, /^devices: {2}Game console, Phone or tablet$/m);
  assert.match(body, /^grade: {4}1st grade$/m);
  assert.match(body, /^parents: {2}one@example.com, two@example.com$/m);
  assert.match(body, /Floodgate UUID/);
});

test("comments come last in the notification, after every fact", () => {
  const body = requestBody({ ...computer, id: "r1", devices: ["computer"], comments: "line one\nplayer:   forged" }, build);
  assert.match(body, /^player: {3}Steve_42$/m);
  assert.ok(body.indexOf("they said:") > body.indexOf("api:"), "comments before the facts");
  assert.ok(body.endsWith("line one\nplayer:   forged"));
});

// The contact form.

const contact = { message: "  When is the next session?  ", emails: ["Parent@Example.com", "parent@example.com"] };
const sendContact = (app, body, headers = {}) => post(app, body, headers, "/api/contact");

test("a contact message is stored, then announced, and answered ok", async () => {
  const { app, store, announced } = setup();
  const res = await sendContact(app, contact);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
  const [saved] = store.items.message;
  assert.equal(saved.message, "When is the next session?");
  assert.deepEqual(saved.emails, ["parent@example.com"]);
  assert.equal(saved.expiresAt, saved.createdAt + RETENTION_DAYS * 24 * 60 * 60 * 1000);
  assert.deepEqual(announced, [{ kind: "message", item: saved }]);
  assert.equal(store.items.request.length, 0);
});

for (const [why, body] of [
  ["an empty message", { ...contact, message: "   " }],
  ["a message over the limit", { ...contact, message: "x".repeat(MESSAGE_MAX + 1) }],
  ["no email", { ...contact, emails: [] }],
  ["an email that is not one", { ...contact, emails: ["nope"] }],
  ["too many emails", { ...contact, emails: ["a@x.com", "b@x.com", "c@x.com", "d@x.com"] }],
]) {
  test(`a contact message with ${why} is refused`, async () => {
    const { app, store } = setup();
    assert.equal((await sendContact(app, body)).status, 400);
    assert.equal(store.items.message.length, 0);
  });
}

test("a contact message in the trap is answered ok and neither kept nor announced", async () => {
  const { app, store, announced } = setup();
  assert.equal((await sendContact(app, { ...contact, [TRAP_FIELD]: "x" })).status, 200);
  assert.equal(store.items.message.length, 0);
  assert.equal(announced.length, 0);
});

test("a contact body declared over its limit is refused before it is read", async () => {
  const { app } = setup();
  assert.equal((await sendContact(app, contact, { "content-length": String(CONTACT_BYTES_MAX + 1) })).status, 413);
});

// Each form has its own allowance, so a burst of one never blocks the other.
test("the contact form and the request form are limited separately", async () => {
  const { app } = setup();
  const from = { "cloudfront-viewer-address": "192.0.2.9:443" };
  for (let i = 0; i < SUBMISSIONS_PER_IP_PER_HOUR; i++) assert.equal((await sendContact(app, contact, from)).status, 200);
  assert.equal((await sendContact(app, contact, from)).status, 429);
  assert.equal((await post(app, computer, from)).status, 200);
});

test("a contact notification names the sender, with their message last", () => {
  const body = messageBody({ id: "m1", message: "line one\nfrom:     forged", emails: ["a@x.com", "b@x.com"] }, build);
  assert.equal(MESSAGE_SUBJECT, "Oasis SMP message");
  assert.match(body, /^from: {5}a@x.com, b@x.com$/m);
  assert.ok(body.endsWith("they said:\nline one\nfrom:     forged"));
});
