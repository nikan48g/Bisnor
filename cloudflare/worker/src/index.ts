interface Env {
  ASSETS: Fetcher;
  IRANFLIX_API_KEY: string;
  IRANFLIX_ORIGINS: string;
  SUPABASE_URL: string;
  SUPABASE_ANON_KEY: string;
}

const allowed = /^\/api\/(?:movie\/by\/filtres|serie\/by\/filtres|search|season\/by\/serie)\//;
const EDGE_TTL_SECONDS = 300;

// Only the endpoints the web/desktop client actually calls, so the worker
// cannot be repurposed as an open proxy into the database.
const SUPABASE_ALLOWED = /^\/sb\/(?:auth\/v1\/(?:token|signup|logout)|rest\/v1\/profiles)$/;
const SUPABASE_MAX_BODY_BYTES = 256 * 1024;
const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, prefer, accept, x-client-info",
  "Access-Control-Allow-Methods": "GET, POST, PATCH, OPTIONS",
  "Access-Control-Max-Age": "86400"
};

async function fetchUpstream(path: string, env: Env): Promise<Response> {
  for (const origin of env.IRANFLIX_ORIGINS.split(",").map(value => value.trim()).filter(Boolean)) {
    try {
      const upstream = await fetch(`${origin}${path}`, { headers: { Accept: "application/json" } });
      if (upstream.ok) {
        return new Response(upstream.body, {
          headers: {
            "content-type": "application/json",
            "cache-control": `public, max-age=${EDGE_TTL_SECONDS}`
          }
        });
      }
    } catch { /* try the next allowlisted origin */ }
  }
  return Response.json({ error: "catalog temporarily unavailable" }, { status: 503 });
}

async function proxySupabase(request: Request, url: URL, env: Env): Promise<Response> {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }
  if (!SUPABASE_ALLOWED.test(url.pathname)) {
    return Response.json({ error: "unsupported auth request" }, { status: 404, headers: CORS_HEADERS });
  }
  if (!env.SUPABASE_URL || !env.SUPABASE_ANON_KEY) {
    return Response.json({ error: "auth temporarily unavailable" }, { status: 503, headers: CORS_HEADERS });
  }
  const methodAllowed = url.pathname.startsWith("/sb/auth/")
    ? request.method === "POST"
    : ["GET", "POST", "PATCH"].includes(request.method);
  if (!methodAllowed) {
    return Response.json({ error: "unsupported auth request" }, { status: 405, headers: CORS_HEADERS });
  }

  const body = ["GET", "HEAD"].includes(request.method) ? null : await request.arrayBuffer();
  if (body && body.byteLength > SUPABASE_MAX_BODY_BYTES) {
    return Response.json({ error: "payload too large" }, { status: 413, headers: CORS_HEADERS });
  }

  const headers = new Headers();
  for (const name of ["content-type", "prefer", "accept", "range", "x-client-info"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  // The project key stays on the worker; the browser only ever sends the
  // signed-in user's own access token (or nothing at all).
  headers.set("apikey", env.SUPABASE_ANON_KEY);
  headers.set("Authorization", request.headers.get("Authorization") || `Bearer ${env.SUPABASE_ANON_KEY}`);

  const upstream = await fetch(`${env.SUPABASE_URL}${url.pathname.slice(3)}${url.search}`, {
    method: request.method,
    headers,
    body
  });

  const responseHeaders = new Headers(CORS_HEADERS);
  const contentType = upstream.headers.get("content-type");
  if (contentType) responseHeaders.set("content-type", contentType);
  responseHeaders.set("cache-control", "no-store");
  return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const pathname = decodeURIComponent(url.pathname);
    if (pathname.startsWith("/sb/")) return proxySupabase(request, url, env);
    if (!pathname.startsWith("/api/")) return env.ASSETS.fetch(request);
    if (request.method !== "GET" || !allowed.test(pathname) || !env.IRANFLIX_API_KEY) {
      return Response.json({ error: "unsupported catalog request" }, { status: 400 });
    }

    const path = `${pathname}${url.search}`.replace("{API_KEY}", env.IRANFLIX_API_KEY);
    const cache = caches.default;
    const cacheKey = new Request(url.toString(), { method: "GET" });
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    const response = await fetchUpstream(path, env);
    if (response.ok) {
      ctx.waitUntil(cache.put(cacheKey, response.clone()));
    }
    return response;
  }
};
