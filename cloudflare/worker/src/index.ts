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
  "Access-Control-Allow-Headers": "authorization, content-type, prefer, accept, x-client-info, platform",
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

// Next Movie API is reached through the worker so browsers never deal with its
// missing CORS headers, and the desktop build works from local assets too.
const NEXT_MOVIE_BASE = "https://w.mihan-cdn.com/api";
const NEXT_MOVIE_ALLOWED =
  /^\/nm\/(?:auth\/guest-token|home|movie\/\d+\/(?:details|links|like|rating)|v3\/search|v2\/search|search\/star|view\/\d+\/progress)$/;
const NEXT_MOVIE_CACHEABLE = /^\/nm\/(?:home|movie\/\d+\/(?:details|links))$/;
const NEXT_MOVIE_TTL_SECONDS = 120;

async function proxyNextMovie(request: Request, url: URL, ctx: ExecutionContext): Promise<Response> {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }
  if (!NEXT_MOVIE_ALLOWED.test(url.pathname)) {
    return Response.json({ error: "unsupported catalog request" }, { status: 404, headers: CORS_HEADERS });
  }
  if (!["GET", "POST"].includes(request.method)) {
    return Response.json({ error: "unsupported catalog request" }, { status: 405, headers: CORS_HEADERS });
  }

  const cache = caches.default;
  const cacheable = request.method === "GET" && NEXT_MOVIE_CACHEABLE.test(url.pathname);
  const cacheKey = new Request(url.toString(), { method: "GET" });
  if (cacheable) {
    const cached = await cache.match(cacheKey);
    if (cached) return cached;
  }

  const headers = new Headers();
  headers.set("Accept", "application/json");
  headers.set("Platform", "web_win32/1.0");
  const authorization = request.headers.get("Authorization");
  if (authorization) headers.set("Authorization", authorization);
  if (request.method === "POST") headers.set("Content-Type", "application/json");

  const upstream = await fetch(`${NEXT_MOVIE_BASE}${url.pathname.slice(3)}${url.search}`, {
    method: request.method,
    headers,
    body: request.method === "POST" ? await request.arrayBuffer() : null
  });

  const responseHeaders = new Headers(CORS_HEADERS);
  responseHeaders.set("content-type", upstream.headers.get("content-type") || "application/json");
  responseHeaders.set(
    "cache-control",
    cacheable && upstream.ok
      ? `public, max-age=${NEXT_MOVIE_TTL_SECONDS}`
      : "no-store"
  );
  const response = new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
  if (cacheable && upstream.ok) ctx.waitUntil(cache.put(cacheKey, response.clone()));
  return response;
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

// ----------------------------------------------------------------- social.md

const SOCIAL_MD_PATH = "/social.md";
const SOCIAL_PLATFORMS: Array<[string, RegExp, string]> = [
  ["instagram", /(^|\.)instagram\.com$/i, "اینستاگرام"],
  ["telegram", /(^|\.)(t\.me|telegram\.me|telegram\.org)$/i, "تلگرام"],
  ["x", /(^|\.)(x\.com|twitter\.com)$/i, "ایکس (توییتر)"],
  ["youtube", /(^|\.)(youtube\.com|youtu\.be)$/i, "یوتیوب"],
  ["facebook", /(^|\.)(facebook\.com|fb\.com|fb\.me)$/i, "فیسبوک"],
  ["tiktok", /(^|\.)tiktok\.com$/i, "تیک‌تاک"],
  ["threads", /(^|\.)threads\.net$/i, "تردز"],
  ["linkedin", /(^|\.)linkedin\.com$/i, "لینکدین"],
  ["whatsapp", /(^|\.)(wa\.me|whatsapp\.com)$/i, "واتس‌اپ"],
  ["github", /(^|\.)github\.com$/i, "گیت‌هاب"]
];

function socialEntry(rawLabel: string, rawUrl: string) {
  let url: URL | null = null;
  try { url = new URL(rawUrl); } catch { return null; }
  if (!["http:", "https:", "mailto:"].includes(url.protocol)) return null;
  const host = url.hostname;
  for (const [platform, pattern, label] of SOCIAL_PLATFORMS) {
    if (pattern.test(host)) {
      return { platform, label: rawLabel || label, url: rawUrl };
    }
  }
  return { platform: "website", label: rawLabel || "وب‌سایت", url: rawUrl };
}

export function parseSocialMarkdown(text: string) {
  const links: Array<{ platform: string; label: string; url: string }> = [];
  const seen = new Set<string>();
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const markdown = trimmed.match(/^\[([^\]]+)\]\((mailto:[^)]+|https?:\/\/[^)]+)\)/);
    const candidates: Array<[string, string]> = markdown
      ? [[markdown[1].trim(), markdown[2].trim()]]
      : Array.from(trimmed.matchAll(/mailto:[^\s)]+|https?:\/\/[^\s)]+/g), match => ["", match[0]]);
    for (const [label, url] of candidates) {
      const entry = socialEntry(label, url);
      if (!entry || seen.has(entry.url)) continue;
      seen.add(entry.url);
      links.push(entry);
    }
  }
  return links;
}

async function socialLinks(request: Request, url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const cache = caches.default;
  const cacheKey = new Request(new URL("/api/social", url).toString(), { method: "GET" });
  const cached = await cache.match(cacheKey);
  if (cached) return cached;

  const asset = await env.ASSETS.fetch(new Request(new URL(SOCIAL_MD_PATH, url).toString()));
  if (!asset.ok) return Response.json({ links: [] }, { headers: { "cache-control": "no-store" } });
  const links = parseSocialMarkdown(await asset.text());
  const response = Response.json({ links }, {
    headers: { "cache-control": "public, max-age=300", "access-control-allow-origin": "*" }
  });
  ctx.waitUntil(cache.put(cacheKey, response.clone()));
  return response;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const pathname = decodeURIComponent(url.pathname);
    if (pathname === "/api/social") return socialLinks(request, url, env, ctx);
    if (pathname === "/import" || pathname === "/import/") {
      return env.ASSETS.fetch(new URL("/import.html", url).toString());
    }
    if (pathname.startsWith("/sb/")) return proxySupabase(request, url, env);
    if (pathname.startsWith("/nm/")) return proxyNextMovie(request, url, ctx);
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
