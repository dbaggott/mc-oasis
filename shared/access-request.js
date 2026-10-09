// What the request-access page sends and POST /api/access-requests accepts.
// Both import this file, so the form and the route cannot disagree about a
// field or a limit.
//
// The form is filled in by a parent, for their child. It asks for no more than
// it takes to set up the child's access and to reach the parent about it: no
// names, no ages, nothing the child types themselves.

export const PLATFORMS = {
  java: "Java Edition",
  bedrock: "Bedrock Edition",
};

// Bedrock only: Java runs on a computer and nowhere else. Anything but a
// computer or a phone is a console, and consoles can't add a server by address,
// so a request naming one is the signal that console joining is wanted.
export const DEVICES = {
  computer: "Computer",
  mobile: "Phone or tablet",
  xbox: "Xbox",
  playstation: "PlayStation",
  switch: "Nintendo Switch",
  other: "Something else",
};

export const CONSOLE_DEVICES = ["xbox", "playstation", "switch", "other"];

export const GRADES = {
  K: "Kindergarten",
  1: "1st grade",
  2: "2nd grade",
  3: "3rd grade",
  4: "4th grade",
  5: "5th grade",
  6: "6th grade",
  7: "7th grade",
  8: "8th grade",
  9: "9th grade",
  10: "10th grade",
  11: "11th grade",
  12: "12th grade",
};

// A Java username, as the server's whitelist takes it (apps/mc-oasis in
// dbaggott/infrastructure validates the same pattern).
export const JAVA_NAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/;

// A Bedrock gamertag. Kept loose on purpose: Xbox has changed its gamertag
// rules more than once, and a refused real gamertag costs a request, where a
// malformed one costs a reply. Letters, digits and spaces, with the "#1234"
// suffix newer gamertags carry.
export const BEDROCK_NAME_PATTERN = /^[A-Za-z0-9][A-Za-z0-9 _]{0,15}(#[0-9]{1,4})?$/;

export const PARENT_EMAILS_MAX = 3;
export const EMAIL_MAX = 254;

// The honeypot. The page hides a field by this name, and anything in it came
// from something filling the form blind; the route drops that request while
// answering as if it had kept it.
export const TRAP_FIELD = "website";

// The largest body the route reads, in bytes of JSON, checked from
// Content-Length before parsing.
export const REQUEST_BYTES_MAX = 4_000;

// Requests are deleted this long after they arrive. Long enough to act on one;
// short enough that nothing about a child is kept indefinitely. The page tells
// parents this, so it reads the same constant.
export const REQUEST_RETENTION_DAYS = 90;
