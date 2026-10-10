// The API's routes, built around whatever store and notifier it is handed, so
// tests and the dev server run the same routes as production.
//
//   GET  /api/                  what is running: the build identity
//   ANY  /api/ping              200, having read and discarded any body
//   POST /api/access-requests   a parent asking for their child to be let in
//
// Everything is answered `Cache-Control: no-store`. CloudFront caches nothing
// under /api/*, but it still collapses simultaneous requests that share a cache
// key into one origin request, and no-store is what turns that off.
import { createHash } from "node:crypto";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { zValidator } from "@hono/zod-validator";
import { z } from "zod";
import {
  COMMENTS_MAX,
  DEVICES,
  EMAIL_MAX,
  GRADES,
  isPlayerName,
  PARENT_EMAILS_MAX,
  REQUEST_BYTES_MAX,
  REQUEST_RETENTION_DAYS,
  TRAP_FIELD,
} from "../../shared/access-request.js";

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

// Per client address, per hour window. A household with a few children sends a
// few requests; what this has to stop is a script filling the operator's inbox.
// The windows are fixed, so a burst straddling a window's end can reach twice
// this.
export const REQUESTS_PER_IP_PER_HOUR = 10;

export const AccessRequestSchema = z.object({
  playerName: z.string().trim().refine(isPlayerName),
  devices: z
    .array(z.enum(Object.keys(DEVICES)))
    .min(1)
    .transform((devices) => [...new Set(devices)]),
  grade: z.enum(Object.keys(GRADES)),
  parentEmails: z
    .array(z.string().trim().toLowerCase().max(EMAIL_MAX).pipe(z.email()))
    .min(1)
    .max(PARENT_EMAILS_MAX)
    .transform((emails) => [...new Set(emails)]),
  comments: z.string().trim().max(COMMENTS_MAX).optional(),
  // `unknown`, so that nothing put in the trap can be refused: a validation
  // error names the field, which would tell a bot which one to leave alone.
  [TRAP_FIELD]: z.unknown().optional(),
});

// The viewer's address, from the header CloudFront sets itself when the origin
// request policy asks for it (modules/static-site in dbaggott/infrastructure):
// "<ip>:<port>". Read rather than X-Forwarded-For, whose entries before
// CloudFront's are the caller's to choose. Without the header every request
// shares one counter, which fails loud (429s) rather than open.
// https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/adding-cloudfront-headers.html
//
// An IPv6 viewer is counted by its /64, the block one host is usually given:
// counting single addresses would hand a script a fresh allowance per address.
export function clientIp(c) {
  const address = c.req.header("cloudfront-viewer-address");
  if (!address) return "unknown";
  const ip = address.slice(0, address.lastIndexOf(":")).replace(/^\[|\]$/g, "");
  // IPv4, and IPv4 written inside IPv6 (::ffff:192.0.2.1), is kept whole.
  if (ip.includes(".")) return ip.slice(ip.lastIndexOf(":") + 1);
  return `${ipv6Groups(ip).slice(0, 4).join(":")}::/64`;
}

// The eight groups of an IPv6 address, with "::" expanded and each group
// written without leading zeros, so one /64 always yields one key.
function ipv6Groups(ip) {
  const [head, tail] = ip.toLowerCase().split("::");
  const left = head ? head.split(":") : [];
  const right = tail ? tail.split(":") : [];
  const zeros = tail === undefined ? [] : Array(8 - left.length - right.length).fill("0");
  return [...left, ...zeros, ...right].map((group) => group.replace(/^0+(?=.)/, ""));
}

// The rate-limit counter is keyed by a digest of the address rather than the
// address, so the table doesn't hold a visitor's IP in readable form. The
// digest is unsalted, so it hides an address from a casual read, not from
// someone set on recovering it; each counter is deleted an hour on.
function clientKey(c) {
  return createHash("sha256").update(clientIp(c)).digest("hex");
}

export function createApp({ store, notify, build, now = Date.now }) {
  const app = new Hono();

  app.use("*", async (c, next) => {
    await next();
    c.header("Cache-Control", "no-store");
  });

  // The deploy's GET smoke check lands here, through CloudFront.
  app.get("/api/", (c) => c.json(build));

  // The deploy POSTs a small body here to prove a bodied request gets past the
  // Function URL's signature check, which a GET cannot exercise. So this route
  // answers every method, touches no state, and always says 200.
  app.all("/api/ping", async (c) => {
    if (c.req.raw.body) {
      try {
        // Read rather than left unconsumed, which can reset the connection
        // before the client finishes writing and fail the probe for a reason
        // unrelated to what it tests.
        await c.req.arrayBuffer();
      } catch {
        // A body that fails mid-read says nothing about origin auth.
      }
    }
    return c.json({ ok: true });
  });

  app.post(
    "/api/access-requests",
    // Ahead of the validator, which reads the whole body first. A request with
    // no Content-Length passes; the Function URL's own request cap bounds it.
    async (c, next) => {
      const declared = Number(c.req.header("content-length") || 0);
      if (declared > REQUEST_BYTES_MAX) {
        return c.json({ error: "request too large", limit: REQUEST_BYTES_MAX }, 413);
      }
      return next();
    },
    zValidator("json", AccessRequestSchema),
    async (c) => {
      const allowed = await store.hitRateLimit(`request:ip:${clientKey(c)}`, HOUR, REQUESTS_PER_IP_PER_HOUR, now());
      if (!allowed) return c.json({ error: "too many requests; try again later" }, 429);

      // Answered exactly as an accepted request is, so the trap never shows
      // which field sprang it. Below the counter, so a trapped submission still
      // spends the caller's allowance.
      const { [TRAP_FIELD]: trapped, ...body } = c.req.valid("json");
      if (trapped) return c.json({ ok: true });

      const createdAt = now();
      const request = {
        id: crypto.randomUUID(),
        createdAt,
        expiresAt: createdAt + REQUEST_RETENTION_DAYS * DAY,
        playerName: body.playerName,
        devices: body.devices,
        grade: body.grade,
        parentEmails: body.parentEmails,
        comments: body.comments || null,
      };

      // Stored before it is announced, so a notification never names a request
      // that was not kept; a failed store answers 500 and the parent can retry.
      // Both are awaited because Lambda freezes the process once the response
      // is sent.
      await store.putRequest(request);
      await notify(request);

      return c.json({ ok: true });
    },
  );

  app.notFound((c) => c.json({ error: "not found" }, 404));

  app.onError((err, c) => {
    // Malformed JSON, say, which carries its own response.
    if (err instanceof HTTPException) return err.getResponse();
    // The method and path only: an error's message can quote the request it
    // failed on, and a request carries a child's name and a parent's email.
    console.error(`${c.req.method} ${c.req.path} failed:`, err?.name || "error");
    return c.json({ error: "internal error" }, 500);
  });

  return app;
}
