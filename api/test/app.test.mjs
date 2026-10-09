import assert from "node:assert/strict";
import { test } from "node:test";
import { REQUEST_BYTES_MAX, REQUEST_RETENTION_DAYS, TRAP_FIELD } from "../../shared/access-request.js";
import { createApp, REQUESTS_PER_IP_PER_HOUR } from "../src/app.js";
import { requestBody, requestSubject } from "../src/notify.js";
import { memoryStore } from "../src/store.js";

const build = { branch: "main", commit: "abc123", builtAt: "2026-10-09T00:00:00Z" };

const java = { platform: "java", playerName: "Steve_42", grade: "5", parentEmails: ["parent@example.com"] };
const bedrock = {
  platform: "bedrock",
  playerName: "Cool Gamer#1234",
  devices: ["switch", "mobile"],
  grade: "K",
  parentEmails: ["one@example.com", "two@example.com"],
};

function setup({ now = () => 1_000_000 } = {}) {
  const store = memoryStore();
  const announced = [];
  const app = createApp({ store, notify: async (r) => announced.push(r), build, now });
  return { app, store, announced };
}

function post(app, body, headers = {}) {
  const json = JSON.stringify(body);
  return app.request("/api/access-requests", {
    method: "POST",
    headers: { "content-type": "application/json", "content-length": String(json.length), ...headers },
    body: json,
  });
}

test("a Java request is stored, then announced, and answered ok", async () => {
  const { app, store, announced } = setup();
  const res = await post(app, java);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
  assert.equal(store.requests.length, 1);
  const [saved] = store.requests;
  assert.equal(saved.playerName, "Steve_42");
  assert.equal(saved.platform, "java");
  assert.deepEqual(saved.devices, ["computer"]);
  assert.equal(saved.grade, "5");
  assert.deepEqual(saved.parentEmails, ["parent@example.com"]);
  assert.equal(saved.createdAt, 1_000_000);
  assert.equal(saved.expiresAt, 1_000_000 + REQUEST_RETENTION_DAYS * 24 * 60 * 60 * 1000);
  assert.deepEqual(announced, [saved]);
});

test("a Bedrock request keeps its devices and gamertag", async () => {
  const { app, store } = setup();
  assert.equal((await post(app, bedrock)).status, 200);
  const [saved] = store.requests;
  assert.equal(saved.playerName, "Cool Gamer#1234");
  assert.deepEqual(saved.devices, ["switch", "mobile"]);
});

test("a gamertag in another script is accepted", async () => {
  const { app, store } = setup();
  assert.equal((await post(app, { ...bedrock, playerName: "ゲーマー 7" })).status, 200);
  assert.equal(store.requests[0].playerName, "ゲーマー 7");
});

// Java runs on a computer only, so devices sent with a Java request (a parent
// who picked Bedrock, ticked a console, then switched) are not believed.
test("devices sent with a Java request are replaced by computer", async () => {
  const { app, store } = setup();
  assert.equal((await post(app, { ...java, devices: ["xbox"] })).status, 200);
  assert.deepEqual(store.requests[0].devices, ["computer"]);
});

test("parent emails are trimmed, lowercased and deduplicated", async () => {
  const { app, store } = setup();
  const res = await post(app, { ...java, parentEmails: [" Parent@Example.com ", "parent@example.com"] });
  assert.equal(res.status, 200);
  assert.deepEqual(store.requests[0].parentEmails, ["parent@example.com"]);
});

// What the API stores is what the server and the operator need; a field it
// does not know is dropped rather than refused, since the page and the API
// deploy separately and a cached page may send one.
test("a field the API does not know is dropped, not stored", async () => {
  const { app, store } = setup();
  assert.equal((await post(app, { ...java, childName: "Alex" })).status, 200);
  assert.equal("childName" in store.requests[0], false);
});

test("anything in the trap field is answered ok and neither kept nor announced", async () => {
  const { app, store, announced } = setup();
  const res = await post(app, { ...java, [TRAP_FIELD]: "http://spam" });
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
  assert.equal(store.requests.length, 0);
  assert.equal(announced.length, 0);
});

for (const [why, body] of [
  ["no platform", { ...java, platform: undefined }],
  ["an unknown platform", { ...java, platform: "pocket" }],
  ["a Java name that is too short", { ...java, playerName: "ab" }],
  ["a Java name with a space", { ...java, playerName: "Steve 42" }],
  ["a gamertag with a line break", { ...bedrock, playerName: "Gamer\nparents:  forged" }],
  ["a Bedrock request with no devices", { ...bedrock, devices: [] }],
  ["an unknown device", { ...bedrock, devices: ["toaster"] }],
  ["an unknown grade", { ...java, grade: "13" }],
  ["a numeric grade", { ...java, grade: 5 }],
  ["no parent email", { ...java, parentEmails: [] }],
  ["a parent email that is not one", { ...java, parentEmails: ["not an email"] }],
  ["too many parent emails", { ...java, parentEmails: ["a@x.com", "b@x.com", "c@x.com", "d@x.com"] }],
]) {
  test(`${why} is refused`, async () => {
    const { app, store } = setup();
    assert.equal((await post(app, body)).status, 400);
    assert.equal(store.requests.length, 0);
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
  const res = await post(app, java, { "content-length": String(REQUEST_BYTES_MAX + 1) });
  assert.equal(res.status, 413);
  assert.equal(store.requests.length, 0);
});

test("one address is limited per hour; another is not, and the next hour starts over", async () => {
  let clock = 0;
  const { app, store } = setup({ now: () => clock });
  const from = (ip) => ({ "cloudfront-viewer-address": `${ip}:443` });

  for (let i = 0; i < REQUESTS_PER_IP_PER_HOUR; i++) {
    assert.equal((await post(app, java, from("192.0.2.1"))).status, 200);
  }
  assert.equal((await post(app, java, from("192.0.2.1"))).status, 429);
  assert.equal((await post(app, java, from("192.0.2.2"))).status, 200);

  clock = 60 * 60 * 1000;
  assert.equal((await post(app, java, from("192.0.2.1"))).status, 200);
  assert.equal(store.requests.length, REQUESTS_PER_IP_PER_HOUR + 2);
});

test("addresses in one IPv6 /64 share a limit", async () => {
  const { app } = setup();
  for (let i = 0; i < REQUESTS_PER_IP_PER_HOUR; i++) {
    const res = await post(app, java, { "cloudfront-viewer-address": `[2001:db8:0:1::${i + 1}]:443` });
    assert.equal(res.status, 200);
  }
  const res = await post(app, java, { "cloudfront-viewer-address": "[2001:0db8:0000:0001:ffff::1]:443" });
  assert.equal(res.status, 429);
});

test("every response is no-store", async () => {
  const { app } = setup();
  for (const res of [await app.request("/api/"), await post(app, java), await app.request("/api/nope")]) {
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
  const consoleRequest = { ...bedrock, id: "r1", devices: ["xbox"] };
  const computerRequest = { ...java, id: "r2", devices: ["computer"] };
  assert.equal(requestSubject(consoleRequest), "Oasis SMP access request (console)");
  assert.equal(requestSubject(computerRequest), "Oasis SMP access request");
  assert.match(requestBody(consoleRequest, build), /^console: {2}yes$/m);
  assert.match(requestBody(computerRequest, build), /^console: {2}no$/m);
});

test("the notification names everything needed to act on the request", () => {
  const body = requestBody({ ...bedrock, id: "r1" }, build);
  assert.match(body, /^player: {3}Cool Gamer#1234$/m);
  assert.match(body, /^platform: Bedrock Edition$/m);
  assert.match(body, /^devices: {2}Nintendo Switch, Phone or tablet$/m);
  assert.match(body, /^grade: {4}Kindergarten$/m);
  assert.match(body, /^parents: {2}one@example.com, two@example.com$/m);
  assert.match(body, /Floodgate UUID/);
});
