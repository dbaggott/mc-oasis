// What the contact page sends and POST /api/contact accepts.

export const MESSAGE_MAX = 2_000;

// The largest body the route reads, in bytes of JSON, checked from
// Content-Length before parsing: a full message in any script, at up to four
// bytes a character, alongside the email addresses.
export const CONTACT_BYTES_MAX = 12_000;
