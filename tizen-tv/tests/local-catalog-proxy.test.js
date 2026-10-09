const assert = require("assert");
const { buildUpstreamUrl, server } = require("../local_catalog_proxy");

const base = "https://hostinnegar.com";
assert.strictEqual(
  buildUpstreamUrl(base, "/api/catalog?page=2").href,
  "https://hostinnegar.com/api/catalog?page=2",
);

for (const path of [
  "https://evil.example/api/catalog",
  "//evil.example/api/catalog",
  "/api/\\evil.example/catalog",
  "/other/catalog",
]) {
  assert.throws(() => buildUpstreamUrl(base, path), /invalid catalog path/);
}

server.close();
console.log("local catalog proxy boundary passed");
