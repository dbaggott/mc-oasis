// Telling the operator a request arrived, through the SNS topic shared by every
// app in the production account (accounts/production in
// dbaggott/infrastructure). The topic's subscriptions decide where it goes;
// this only publishes.
//
// Never throws. By the time it runs the request is stored, so a failed publish
// costs the notification and not the request, and answering 500 would tell a
// parent their request was lost when it was not.
import { CONSOLE_DEVICES, DEVICES, GRADES, PLATFORMS } from "../../shared/access-request.js";

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
    `platform: ${PLATFORMS[request.platform]}`,
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
  if (request.platform === "bedrock") {
    lines.push("", "Bedrock: whitelist by Floodgate UUID, from the gamertag's XUID.");
  }
  if (request.comments) {
    lines.push("", "they said:", request.comments);
  }
  return lines.join("\n");
}

// With no topic the request is still stored; only the announcement is skipped.
// That is a development instance, not a fault.
export function createNotifier(topicArn, build) {
  if (!topicArn) return async () => {};

  let publisher = null;
  const makePublisher = async () => {
    const { SNSClient, PublishCommand } = await import("@aws-sdk/client-sns");
    const client = new SNSClient({});
    return (subject, message) => client.send(new PublishCommand({ TopicArn: topicArn, Subject: subject, Message: message }));
  };

  return async (request) => {
    try {
      publisher ??= makePublisher();
      await (await publisher)(requestSubject(request), requestBody(request, build));
    } catch (e) {
      // Forget a failed client, or one bad construction would fail every
      // publish for the life of a reused Lambda process.
      publisher = null;
      console.error(`failed to announce request ${request.id}:`, e?.name || "error");
    }
  };
}
