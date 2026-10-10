// The request-access form: checks answers against the same rules the API
// applies, and posts them to /api/access-requests.
import "./main.js";
import { COMMENTS_MAX, DEVICES, GRADES, isPlayerName } from "../../shared/access-request.js";
import { emailList, emailProblem, sendForm } from "./forms.js";

const form = document.getElementById("request-form");
const devicesBox = document.getElementById("devices");
const playerName = document.getElementById("playerName");
const grade = document.getElementById("grade");
const comments = document.getElementById("comments");
comments.maxLength = COMMENTS_MAX;

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

const emails = emailList(document.getElementById("emails"), document.getElementById("add-email"), "parentEmail");

// The same rules the API applies, checked here so a parent hears about a typo
// before sending rather than from a refusal.
function collect() {
  const name = playerName.value.trim();
  const devices = [...form.querySelectorAll('input[name="devices"]:checked')].map((input) => input.value);
  const parentEmails = emails.values();
  const problems = [];

  if (devices.length === 0) problems.push(["devices", "Tick at least one."]);
  if (!name) problems.push(["playerName", "Enter your child's Minecraft name."]);
  else if (!isPlayerName(name))
    problems.push(["playerName", "That doesn't look like a Minecraft name. Check it against the one shown in the game."]);
  if (!grade.value) problems.push(["grade", "Choose a grade."]);
  const emailError = emailProblem(parentEmails);
  if (emailError) problems.push(["parentEmails", emailError]);

  const body = { playerName: name, devices, grade: grade.value, parentEmails };
  const note = comments.value.trim();
  if (note) body.comments = note;
  return { body, problems };
}

sendForm(form, {
  path: "/api/access-requests",
  collect,
  invalid: "Some of these answers weren't accepted. Check the Minecraft name and the email addresses.",
  label: "Send request",
});
