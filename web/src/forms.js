// What the site's forms share: a growing list of email fields, showing and
// clearing errors, and sending to the API, which in production has to carry
// the body's hash.
import { EMAIL_MAX, EMAILS_MAX, TRAP_FIELD } from "../../shared/forms.js";

// Loose on purpose: catches a typo before sending; the API's check is the one
// that decides.
const EMAIL_SHAPE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Email fields in `box`, starting with one, with `addButton` adding another up
// to EMAILS_MAX and hiding once there. The first field's <label> is in the
// page, for="<prefix>-0".
export function emailList(box, addButton, prefix) {
  function add() {
    const index = box.querySelectorAll("input").length;
    const input = document.createElement("input");
    input.type = "email";
    input.id = `${prefix}-${index}`;
    input.autocomplete = index === 0 ? "email" : "off";
    input.maxLength = EMAIL_MAX;
    if (index === 0) input.required = true;
    else input.setAttribute("aria-label", `Another contact email address (${index + 1})`);
    box.append(input);
    addButton.hidden = index + 1 >= EMAILS_MAX;
    return input;
  }
  add();
  addButton.addEventListener("click", () => add().focus());

  return {
    values: () => [...box.querySelectorAll("input")].map((input) => input.value.trim()).filter(Boolean),
  };
}

// The problem with a list of email addresses, or null if there is none.
export function emailProblem(emails) {
  if (emails.length === 0) return "Enter your email address.";
  if (!emails.every((email) => EMAIL_SHAPE.test(email))) return "Check the email addresses: one doesn't look right.";
  return null;
}

// In production the API sits behind a signed Lambda Function URL, and Lambda
// requires the SHA-256 of a request's body in this header before it will run
// anything. CloudFront signs the request but cannot compute the hash itself, so
// without it every submission is refused, and only in production.
async function bodyHash(body) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(body));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Wire a form to post to `path`. `collect()` returns the body to send and any
// problems found, as [field, message] pairs naming a "<field>-error" element.
// On success the form is swapped for the element with id "thanks".
export function sendForm(form, { path, collect, invalid, label }) {
  const submit = form.querySelector('[type="submit"]');
  const thanks = document.getElementById("thanks");

  function showError(field, message) {
    const el = document.getElementById(`${field}-error`);
    el.textContent = message;
    el.hidden = false;
  }

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    for (const el of form.querySelectorAll(".error")) {
      el.hidden = true;
      el.textContent = "";
    }

    const { body, problems } = collect();
    if (problems.length > 0) {
      for (const [field, message] of problems) showError(field, message);
      form.querySelector(".error:not([hidden])")?.closest("fieldset")?.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }
    const trap = form.elements[TRAP_FIELD].value;
    if (trap) body[TRAP_FIELD] = trap;

    submit.disabled = true;
    submit.textContent = "Sending…";
    try {
      // Hashed and sent as the one string, so the hash is of exactly what is sent.
      const json = JSON.stringify(body);
      const res = await fetch(path, {
        method: "POST",
        headers: { "content-type": "application/json", "x-amz-content-sha256": await bodyHash(json) },
        body: json,
      });
      if (res.status === 400) {
        showError("form", invalid);
      } else if (res.status === 429) {
        showError("form", "We've had a lot of these from your connection. Please try again in an hour.");
      } else if (!res.ok) {
        showError("form", "Something on our end didn't work, and nothing was sent. Please try again.");
      } else {
        form.hidden = true;
        thanks.hidden = false;
        thanks.focus();
        return;
      }
    } catch {
      showError("form", "It couldn't be sent. Check your connection and try again.");
    }
    submit.disabled = false;
    submit.textContent = label;
  });
}
