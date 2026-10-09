// The server. In production the Lambda Web Adapter forwards each invocation to
// it on $PORT; in development `npm run dev` runs it for Vite to proxy to.
//
// Configured entirely from the environment (apps/mc-oasis-api in
// dbaggott/infrastructure sets it in production):
//
//   PORT                 where to listen                        default 8787
//   STORAGE              "aws" for DynamoDB, else memory
//   TABLE                DynamoDB table                         (STORAGE=aws)
//   FEEDBACK_TOPIC_ARN   where to announce requests; unset skips announcing
//   BUILD_BRANCH, BUILD_COMMIT, BUILD_TIME   set by the image build
import { serve } from "@hono/node-server";
import { createApp } from "./app.js";
import { createNotifier } from "./notify.js";
import { dynamoStore, memoryStore } from "./store.js";

const env = process.env;

// Memory storage on Lambda would accept requests and lose them with the
// process, so an unconfigured function refuses to start instead.
if (env.AWS_LAMBDA_FUNCTION_NAME && env.STORAGE !== "aws") {
  throw new Error("STORAGE=aws is required on Lambda");
}
if (env.STORAGE === "aws" && !env.TABLE) {
  throw new Error("STORAGE=aws needs TABLE");
}

const build = {
  branch: env.BUILD_BRANCH || null,
  commit: env.BUILD_COMMIT || null,
  builtAt: env.BUILD_TIME || null,
};

const store = env.STORAGE === "aws" ? await dynamoStore({ table: env.TABLE }) : memoryStore();
const notify = createNotifier(env.FEEDBACK_TOPIC_ARN, build);
const app = createApp({ store, notify, build });

serve({ fetch: app.fetch, port: Number(env.PORT) || 8787 }, (info) => {
  console.log(`mc-oasis api on http://localhost:${info.port} (storage: ${env.STORAGE === "aws" ? "aws" : "memory"})`);
});
