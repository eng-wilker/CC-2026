const fs = require("node:fs/promises");
const { execFileSync } = require("node:child_process");
const path = require("node:path");

const apiVersion = "2024-07-01";
const endpoint = process.env.SEARCH_ENDPOINT;
const adminKey = process.env.SEARCH_ADMIN_KEY;
const indexName = process.env.SEARCH_INDEX;
const keyFile = process.env.SEARCH_KEY_FILE;

for (const name of [
  "SEARCH_ENDPOINT",
  "SEARCH_ADMIN_KEY",
  "SEARCH_INDEX",
  "SEARCH_KEY_FILE",
  "SEARCH_RESOURCE_GROUP",
  "SEARCH_SERVICE_NAME",
  "COSMOSDB_ENDPOINT",
  "COSMOSDB_KEY",
  "COSMOSDB_DATABASE",
]) {
  if (!process.env[name]) throw new Error(`${name} is required`);
}

async function request(pathname, method, body) {
  const response = await fetch(`${endpoint}${pathname}?api-version=${apiVersion}`, {
    method,
    headers: {
      "api-key": adminKey,
      "content-type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const responseBody = await response.text();
  if (!response.ok) {
    throw new Error(`Azure AI Search ${method} ${pathname} failed (${response.status}): ${responseBody}`);
  }
  return responseBody ? JSON.parse(responseBody) : null;
}

function getQueryKey() {
  const command = [
    "search",
    "query-key",
    "list",
    "--resource-group",
    process.env.SEARCH_RESOURCE_GROUP,
    "--service-name",
    process.env.SEARCH_SERVICE_NAME,
    "--output",
    "json",
  ];
  const keys = JSON.parse(execFileSync("az", command, { encoding: "utf8" }));
  const existing = keys.find((key) => key.name === "cc26-functions-query");
  if (existing?.key) return existing.key;

  const created = JSON.parse(execFileSync("az", [
    "search",
    "query-key",
    "create",
    "--resource-group",
    process.env.SEARCH_RESOURCE_GROUP,
    "--service-name",
    process.env.SEARCH_SERVICE_NAME,
    "--name",
    "cc26-functions-query",
    "--output",
    "json",
  ], { encoding: "utf8" }));
  if (typeof created.key !== "string" || created.key.length === 0) {
    throw new Error("Azure CLI did not return a Search query key");
  }
  return created.key;
}

async function configure() {
  await request(`/indexes/${indexName}`, "PUT", {
    name: indexName,
    fields: [
      { name: "id", type: "Edm.String", key: true, searchable: false, filterable: true },
      { name: "title", type: "Edm.String", searchable: true, analyzer: "en.lucene" },
      { name: "description", type: "Edm.String", searchable: true, analyzer: "en.lucene" },
      { name: "owner", type: "Edm.String", filterable: true },
      { name: "endTime", type: "Edm.DateTimeOffset", filterable: true, sortable: true },
      { name: "minimumPrice", type: "Edm.Double", filterable: true, sortable: true },
      { name: "status", type: "Edm.String", filterable: true },
      { name: "imageId", type: "Edm.String", searchable: false },
    ],
  });

  await request("/datasources/auctions-cosmos", "PUT", {
    name: "auctions-cosmos",
    type: "cosmosdb",
    credentials: {
      connectionString: `AccountEndpoint=${process.env.COSMOSDB_ENDPOINT};AccountKey=${process.env.COSMOSDB_KEY};Database=${process.env.COSMOSDB_DATABASE}`,
    },
    container: {
      name: "auctions",
      query: "SELECT c.id, c.title, c.description, c.owner, c.endTime, c.minimumPrice, c.status, c.imageId, c._ts FROM c WHERE c._ts >= @HighWaterMark ORDER BY c._ts",
    },
    dataChangeDetectionPolicy: {
      "@odata.type": "#Microsoft.Azure.Search.HighWaterMarkChangeDetectionPolicy",
      highWaterMarkColumnName: "_ts",
    },
  });

  await request("/indexers/auctions-indexer", "PUT", {
    name: "auctions-indexer",
    dataSourceName: "auctions-cosmos",
    targetIndexName: indexName,
    schedule: { interval: "PT5M" },
  });

  const queryKey = getQueryKey();
  await fs.mkdir(path.dirname(keyFile), { recursive: true });
  await fs.writeFile(keyFile, queryKey, { mode: 0o600 });
  await fs.chmod(keyFile, 0o600);
}

configure().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
