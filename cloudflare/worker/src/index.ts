interface Env {
  ASSETS: Fetcher;
  IRANFLIX_API_KEY: string;
  IRANFLIX_ORIGINS: string;
}

const allowed = /^\/api\/(?:movie\/by\/filtres|serie\/by\/filtres|search|season\/by\/serie)\//;
const EDGE_TTL_SECONDS = 300;

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

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const pathname = decodeURIComponent(url.pathname);
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
