// Where the API keeps what it is sent: DynamoDB in production, memory for
// development and tests. Both answer the same two calls.
//
//   put(kind, item)                              keep one submission of a form
//   hitRateLimit(key, windowMs, limit, now)      count a hit; false once over
//
// `kind` is "request" (an access request) or "message" (the contact form).

// A fixed window: every hit in the same `windowMs`-wide slice of time counts
// against one counter, which starts again at the next slice.
function windowStartOf(now, windowMs) {
  return Math.floor(now / windowMs) * windowMs;
}

export function memoryStore() {
  const items = { request: [], message: [] };
  const counters = new Map();

  return {
    items,

    async put(kind, item) {
      items[kind].push(item);
    },

    async hitRateLimit(key, windowMs, limit, now) {
      const start = windowStartOf(now, windowMs);
      const counter = counters.get(key);
      const hits = counter?.start === start ? counter.hits + 1 : 1;
      counters.set(key, { start, hits });
      return hits <= limit;
    },
  };
}

// One table, because everything in it expires: a submission at its
// `expiresAt`, a rate counter when its window ends. DynamoDB deletes each by its `ttl`, in
// epoch seconds. Deletion by TTL is lazy, so a request can outlive its
// `expiresAt` by a few days; nothing reads a counter from an earlier window,
// since each window is a key of its own.
//
// Each kind shares a partition (REQUEST, MESSAGE), sorted by when they
// arrived, so reading one inbox is a single Query:
//
//   aws dynamodb query --table-name mc-oasis-api \
//     --key-condition-expression "pk = :p" \
//     --expression-attribute-values '{":p":{"S":"REQUEST"}}'
export async function dynamoStore({ table }) {
  // Loaded here so the memory backend never pays for the SDK.
  const { DynamoDBClient } = await import("@aws-sdk/client-dynamodb");
  const { DynamoDBDocumentClient, PutCommand, UpdateCommand } = await import("@aws-sdk/lib-dynamodb");
  const ddb = DynamoDBDocumentClient.from(new DynamoDBClient({}));

  return {
    async put(kind, item) {
      await ddb.send(
        new PutCommand({
          TableName: table,
          Item: {
            pk: kind.toUpperCase(),
            sk: `${new Date(item.createdAt).toISOString()}#${item.id}`,
            ttl: Math.ceil(item.expiresAt / 1000),
            ...item,
          },
        }),
      );
    },

    async hitRateLimit(key, windowMs, limit, now) {
      const start = windowStartOf(now, windowMs);
      // ADD is atomic, so two concurrent hits cannot both read the same count.
      const result = await ddb.send(
        new UpdateCommand({
          TableName: table,
          Key: { pk: `RATE#${key}`, sk: `${start}` },
          UpdateExpression: "ADD hits :one SET #ttl = :ttl",
          ExpressionAttributeNames: { "#ttl": "ttl" },
          ExpressionAttributeValues: { ":one": 1, ":ttl": Math.ceil((start + windowMs) / 1000) },
          ReturnValues: "UPDATED_NEW",
        }),
      );
      return Number(result.Attributes?.hits ?? 1) <= limit;
    },
  };
}
