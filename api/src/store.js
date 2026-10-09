// Where the API keeps what it is sent: DynamoDB in production, memory for
// development and tests. Both answer the same two calls.
//
//   putRequest(request)                          keep one access request
//   hitRateLimit(key, windowMs, limit, now)      count a hit; false once over

// A fixed window: every hit in the same `windowMs`-wide slice of time counts
// against one counter, which starts again at the next slice.
function windowStartOf(now, windowMs) {
  return Math.floor(now / windowMs) * windowMs;
}

export function memoryStore() {
  const requests = [];
  const counters = new Map();

  return {
    requests,

    async putRequest(item) {
      requests.push(item);
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

// One table, because everything in it expires: a request at its `expiresAt`,
// a rate counter when its window ends. DynamoDB deletes each by its `ttl`, in
// epoch seconds. Deletion by TTL is lazy, so a request can outlive its
// `expiresAt` by a few days; nothing reads a counter from an earlier window,
// since each window is a key of its own.
//
// Requests share a partition, sorted by when they arrived, so reading the
// inbox is a single Query:
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
    async putRequest(item) {
      await ddb.send(
        new PutCommand({
          TableName: table,
          Item: {
            pk: "REQUEST",
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
