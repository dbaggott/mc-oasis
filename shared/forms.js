// What the site's forms share, imported by the pages that send them and by the
// API that takes them, so no side can disagree about a limit.

// Up to this many email addresses to reply to, each at most EMAIL_MAX long.
export const EMAILS_MAX = 3;
export const EMAIL_MAX = 254;

// The honeypot. A page hides a field by this name, and anything in it came from
// something filling the form blind; the route drops that submission while
// answering as if it had kept it. Named so that no browser or password manager
// recognises it and fills it in, which would drop a real one.
export const TRAP_FIELD = "oasis_hp";

// Submissions are deleted this long after they arrive. Long enough to act on
// one; short enough that nothing about a child is kept indefinitely.
export const RETENTION_DAYS = 90;
