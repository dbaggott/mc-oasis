// Telling the operator a form was sent, through the SNS topic shared by every
// app in the production account (accounts/production in
// dbaggott/infrastructure). The topic's subscriptions decide where it goes;
// this only publishes.
//
// Never throws. By the time it runs the submission is stored, so a failed
// publish costs the notification and not the submission, and answering 500
// would tell a parent their request was lost when it was not.
import { CONSOLE_DEVICES, DEVICES, editionOf, GRADES } from "../../shared/access-request.js";

// Never anything the parent typed: SNS refuses a subject over 99 characters or
// holding a line break, and a refused publish is a silently missing
// notification. Console requests are marked here, so they stand out in an
// inbox without being opened.
export function requestSubject(request) {
  return hasConsole(request) ? "Oasis SMP access request (console)" : "Oasis SMP access request";
}

function hasConsole(request) {
  return request.devices.some((device) => CONSOLE_DEVICES.includes(device));
}

// Plain text for a mail client, one fact per line, then the parent's comments
// last, so free text with line breaks in it cannot push a fact out of place.
export function requestBody(request, build) {
  const lines = [
    `player:   ${request.playerName}`,
    `edition:  ${editionOf(request.devices) === "bedrock" ? "Bedrock" : "unknown (computer only)"}`,
    `devices:  ${request.devices.map((device) => DEVICES[device]).join(", ")}`,
    `console:  ${hasConsole(request) ? "yes" : "no"}`,
    `grade:    ${GRADES[request.grade]}`,
    `parents:  ${request.parentEmails.join(", ")}`,
    "",
    `request:  ${request.id}`,
    `api:      ${build.commit ?? "(not a built image)"}`,
  ];
  // The whitelist takes a Bedrock player by Floodgate UUID, not gamertag
  // (apps/mc-oasis/variables.tf in dbaggott/infrastructure).
  if (editionOf(request.devices) === "bedrock") {
    lines.push("", "Bedrock: whitelist by Floodgate UUID, from the gamertag's XUID.");
  } else {
    lines.push(
      "",
      "Computer only: Java or Bedrock. Check the name with Mojang (Java) and Xbox (Bedrock);",
      "if it exists on only one, that's the edition. If both, ask the parent which.",
    );
  }
  if (request.comments) {
    lines.push("", "they said:", request.comments);
  }
  return lines.join("\n");
}

export const MESSAGE_SUBJECT = "Oasis SMP message";

// The sender's addresses, then the message last, for the same reason as a
// request's comments.
export function messageBody(message, build) {
  return [
    `from:     ${message.emails.join(", ")}`,
    "",
    `message:  ${message.id}`,
    `api:      ${build.commit ?? "(not a built image)"}`,
    "",
    "they said:",
    message.message,
  ].join("\n");
}

// How each kind of submission is announced.
const FORMATS = {
  request: { subject: requestSubject, body: requestBody },
  message: { subject: () => MESSAGE_SUBJECT, body: messageBody },
};

// With no topic the submission is still stored; only the announcement is
// skipped. That is a development instance, not a fault.
export function createNotifier(topicArn, build) {
  if (!topicArn) return async () => {};

  let publisher = null;
  const makePublisher = async () => {
    const { SNSClient, PublishCommand } = await import("@aws-sdk/client-sns");
    const client = new SNSClient({});
    return (subject, message) => client.send(new PublishCommand({ TopicArn: topicArn, Subject: subject, Message: message }));
  };

  return async (kind, item) => {
    try {
      publisher ??= makePublisher();
      const { subject, body } = FORMATS[kind];
      await (await publisher)(subject(item), body(item, build));
    } catch (e) {
      // Forget a failed client, or one bad construction would fail every
      // publish for the life of a reused Lambda process.
      publisher = null;
      console.error(`failed to announce ${kind} ${item.id}:`, e?.name || "error");
    }
  };
}
