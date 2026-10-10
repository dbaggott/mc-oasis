// What the request-access page sends and POST /api/access-requests accepts,
// beyond what every form shares (forms.js). Both import this file, so the form
// and the route cannot disagree about a field or a limit.
//
// The form is filled in by a parent, for their child. It asks for no more than
// it takes to set up the child's access and to reach the parent about it: no
// names, no ages, nothing the child types themselves.

// Where the child will play. Java runs on a computer and nowhere else, so a
// phone, tablet or console means Bedrock; a computer alone leaves the edition
// open. Consoles can't add a server by address, so a request naming one is the
// signal that console joining is wanted; which console doesn't change that.
export const DEVICES = {
  computer: "Computer",
  mobile: "Phone or tablet",
  console: "Game console",
};

// What a device covers, where its name alone might leave a parent unsure.
export const DEVICE_HINTS = {
  console: "Xbox, PlayStation, Switch",
};

// "bedrock" when the devices settle it, else null: a computer can run either.
export function editionOf(devices) {
  return devices.some((device) => device !== "computer") ? "bedrock" : null;
}

// Listed in key order, which JavaScript gives integer-like keys, so the form
// shows them in this order only while every key is a number.
export const GRADES = {
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
// malformed one costs a reply. Letters in any script, digits, spaces and
// underscores, with the "#1234" suffix newer gamertags carry.
export const BEDROCK_NAME_PATTERN = /^[\p{L}\p{N}][\p{L}\p{N} _]{0,15}(#[0-9]{1,4})?$/u;

// The form doesn't ask which edition, so a name is accepted if it could be
// either.
export function isPlayerName(name) {
  return JAVA_NAME_PATTERN.test(name) || BEDROCK_NAME_PATTERN.test(name);
}

// The optional free-text box: anything the parent wants to add.
export const COMMENTS_MAX = 1_000;

// The largest body the route reads, in bytes of JSON, checked from
// Content-Length before parsing. Room for a full comments box in any script,
// at up to four bytes a character, alongside every other field.
export const REQUEST_BYTES_MAX = 8_000;
