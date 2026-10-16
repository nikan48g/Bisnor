interface Env { ASSETS: Fetcher; IRANFLIX_API_KEY: string; IRANFLIX_ORIGINS: string; }

const allowed = /^\/api\/(?:movie\/by\/filtres|serie\/by\/filtres|search|season\/by\/serie)\//;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const pathname = decodeURIComponent(url.pathname);
    if (!pathname.startsWith("/api/")) return env.ASSETS.fetch(request);
    if (request.method !== "GET" || !allowed.test(pathname) || !env.IRANFLIX_API_KEY) {
      return Response.json({ error: "unsupported catalog request" }, { status: 400 });
    }
    const path = `${pathname}${url.search}`.replace("{API_KEY}", env.IRANFLIX_API_KEY);
    for (const origin of env.IRANFLIX_ORIGINS.split(",").map(value => value.trim()).filter(Boolean)) {
      try {
        const upstream = await fetch(`${origin}${path}`, { headers: { Accept: "application/json" } });
        if (upstream.ok) {
          return new Response(upstream.body, { headers: { "content-type": "application/json", "cache-control": "public, max-age=60" } });
        }
      } catch { /* try the next allowlisted origin */ }
    }
    return Response.json({ error: "catalog temporarily unavailable" }, { status: 503 });
  }
};
