// The contact form: a message and the addresses to reply to, posted to
// /api/contact.
import "./main.js";
import { MESSAGE_MAX } from "../../shared/contact.js";
import { emailList, emailProblem, sendForm } from "./forms.js";

const message = document.getElementById("message");
message.maxLength = MESSAGE_MAX;

const emails = emailList(document.getElementById("emails"), document.getElementById("add-email"), "contactEmail");

function collect() {
  const text = message.value.trim();
  const addresses = emails.values();
  const problems = [];
  if (!text) problems.push(["message", "Write a message."]);
  const emailError = emailProblem(addresses);
  if (emailError) problems.push(["emails", emailError]);
  return { body: { message: text, emails: addresses }, problems };
}

sendForm(document.getElementById("contact-form"), {
  path: "/api/contact",
  collect,
  invalid: "That couldn't be sent as written. Check the email addresses.",
  label: "Send message",
});
