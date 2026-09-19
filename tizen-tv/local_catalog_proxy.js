// Development-LAN bridge for Samsung TVs.  It is deliberately not a public
// service: bind it only while testing on the same Wi-Fi as the television.
const http = require("http");
const https = require("https");

const UPSTREAMS = ["https://hostinnegar.com", "https://server-hi-speed-iran.info"];

function fetchUpstream(base, path) {
  return new Promise((resolve, reject) => {
    const request = https.get(base + path, { headers: { Accept: "application/json", "User-Agent": "Bisnor-TV/5.0.8" } }, response => {
      if (response.statusCode !== 200) {
        response.resume();
        reject(new Error(`HTTP ${response.statusCode}`));
        return;
      }
      let body = "";
      response.setEncoding("utf8");
      response.on("data", part => { body += part; });
      response.on("end", () => resolve(body));
    });
    request.setTimeout(15000, () => request.destroy(new Error("upstream timeout")));
    request.on("error", reject);
  });
}

http.createServer(async (request, response) => {
  response.setHeader("Access-Control-Allow-Origin", "*");
  response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  if (request.method === "OPTIONS") return response.writeHead(204).end();
  const url = new URL(request.url, "http://127.0.0.1");
  const path = url.searchParams.get("path") || "";
  if (url.pathname !== "/catalog" || !path.startsWith("/api/")) {
    return response.writeHead(400, { "Content-Type": "application/json" }).end('{"error":"invalid catalog request"}');
  }
  for (const upstream of UPSTREAMS) {
    try {
      const body = await fetchUpstream(upstream, path);
      JSON.parse(body);
      return response.writeHead(200, { "Content-Type": "application/json; charset=utf-8" }).end(body);
    } catch (_) {}
  }
  response.writeHead(502, { "Content-Type": "application/json" }).end('{"error":"catalog unavailable"}');
}).listen(8787, "0.0.0.0", () => console.log("Bisnor TV catalog bridge listening on :8787"));
