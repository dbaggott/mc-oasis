// The request-access form: shows the questions that apply to the chosen
// edition, checks answers against the same rules the API applies, and posts
// them to /api/access-requests.
import "./main.js";
import {
  BEDROCK_NAME_PATTERN,
  DEVICES,
  EMAIL_MAX,
  GRADES,
  JAVA_NAME_PATTERN,
  PARENT_EMAILS_MAX,
  REQUEST_RETENTION_DAYS,
  TRAP_FIELD,
} from "../../shared/access-request.js";

const form = document.getElementById("request-form");
const thanks = document.getElementById("thanks");
const submit = document.getElementById("submit");
const devicesFieldset = document.getElementById("devices-fieldset");
const devicesBox = document.getElementById("devices");
const playerName = document.getElementById("playerName");
const playerNameLabel = document.getElementById("playerName-label");
const playerNameHint = document.getElementById("playerName-hint");
const grade = document.getElementById("grade");
const emails = document.getElementById("emails");
const addEmail = document.getElementById("add-email");

const NAME_PROMPTS = {
  java: {
    label: "Their Java username",
    hint: "The name shown in the launcher: 3 to 16 letters, numbers or underscores.",
  },
  bedrock: {
    label: "Their Xbox gamertag",
    hint: "The gamertag on the Microsoft account they sign in to Minecraft with, including any #1234 at the end.",
  },
};

for (const [value, label] of Object.entries(DEVICES)) {
  const choice = document.createElement("label");
  choice.className = "choice";
  const input = document.createElement("input");
  input.type = "checkbox";
  input.name = "devices";
  input.value = value;
  const text = document.createElement("span");
  text.textContent = label;
  choice.append(input, text);
  devicesBox.append(choice);
}

// GRADES is keyed by value, and an object lists integer-like keys first ("1"
// before "K"), so the order is spelled out here.
for (const value of ["K", ...Array.from({ length: 12 }, (_, i) => String(i + 1))]) {
  grade.add(new Option(GRADES[value], value));
}

document.getElementById("privacy").textContent =
  "We use these answers only to let your child in and to write to you about it. " +
  `We keep each request for ${REQUEST_RETENTION_DAYS} days, and we're sent an email copy of it when it arrives. ` +
  "This site sets no cookies and runs no tracking.";

function addEmailField() {
  const index = emails.querySelectorAll("input").length;
  const input = document.createElement("input");
  input.type = "email";
  input.name = "parentEmails";
  input.id = `parentEmail-${index}`;
  input.autocomplete = index === 0 ? "email" : "off";
  input.maxLength = EMAIL_MAX;
  if (index === 0) input.required = true;
  else input.setAttribute("aria-label", `Another parent's email address (${index + 1})`);
  emails.append(input);
  addEmail.hidden = index + 1 >= PARENT_EMAILS_MAX;
  return input;
}
addEmailField();
addEmail.addEventListener("click", () => addEmailField().focus());

function platform() {
  return form.elements.platform.value;
}

form.elements.platform.forEach((radio) =>
  radio.addEventListener("change", () => {
    const chosen = platform();
    devicesFieldset.hidden = chosen !== "bedrock";
    playerNameLabel.textContent = NAME_PROMPTS[chosen].label;
    playerNameHint.textContent = NAME_PROMPTS[chosen].hint;
    clearErrors();
  }),
);

function showError(field, message) {
  const el = document.getElementById(`${field}-error`);
  el.textContent = message;
  el.hidden = false;
}

function clearErrors() {
  for (const el of form.querySelectorAll(".error")) {
    el.hidden = true;
    el.textContent = "";
  }
}

// The same rules the API applies (shared/access-request.js), checked here so a
// parent hears about a typo before sending rather than from a refusal.
function collect() {
  const chosen = platform();
  const name = playerName.value.trim();
  const devices = [...form.querySelectorAll('input[name="devices"]:checked')].map((input) => input.value);
  const parentEmails = [...emails.querySelectorAll("input")].map((input) => input.value.trim()).filter(Boolean);
  const problems = [];

  if (!chosen) problems.push(["platform", "Choose Java Edition or Bedrock Edition."]);
  if (chosen === "bedrock" && devices.length === 0) problems.push(["devices", "Tick at least one."]);
  if (!name) problems.push(["playerName", "Enter your child's Minecraft name."]);
  else if (chosen === "java" && !JAVA_NAME_PATTERN.test(name))
    problems.push(["playerName", "A Java username is 3 to 16 letters, numbers or underscores, with no spaces."]);
  else if (chosen === "bedrock" && !BEDROCK_NAME_PATTERN.test(name))
    problems.push(["playerName", "That doesn't look like a gamertag. Check it against the one shown in Minecraft."]);
  if (!grade.value) problems.push(["grade", "Choose a grade."]);
  if (parentEmails.length === 0) problems.push(["parentEmails", "Enter your email address."]);
  else if (!parentEmails.every((email) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)))
    problems.push(["parentEmails", "Check the email addresses: one doesn't look right."]);

  const body = { platform: chosen, playerName: name, grade: grade.value, parentEmails };
  if (chosen === "bedrock") body.devices = devices;
  const trap = form.elements[TRAP_FIELD].value;
  if (trap) body[TRAP_FIELD] = trap;
  return { body, problems };
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearErrors();

  const { body, problems } = collect();
  if (problems.length > 0) {
    for (const [field, message] of problems) showError(field, message);
    form.querySelector(".error:not([hidden])")?.closest("fieldset")?.scrollIntoView({ behavior: "smooth", block: "start" });
    return;
  }

  submit.disabled = true;
  submit.textContent = "Sending…";
  try {
    const res = await fetch("/api/access-requests", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    if (res.status === 400) {
      showError("form", "Some of these answers weren't accepted. Check the Minecraft name and the email addresses.");
    } else if (res.status === 429) {
      showError("form", "We've had a lot of requests from your connection. Please try again in an hour.");
    } else if (!res.ok) {
      showError("form", "Something on our end didn't work, and your request wasn't sent. Please try again.");
    } else {
      form.hidden = true;
      thanks.hidden = false;
      thanks.focus();
      return;
    }
  } catch {
    showError("form", "Your request couldn't be sent. Check your connection and try again.");
  }
  submit.disabled = false;
  submit.textContent = "Send request";
});
