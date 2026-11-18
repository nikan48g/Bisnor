interface Env {
  ASSETS: Fetcher;
  IRANFLIX_API_KEY: string;
  IRANFLIX_ORIGINS: string;
  SUPABASE_URL: string;
  SUPABASE_ANON_KEY: string;
  MEDIA_HOSTS?: string;
}

const allowed = /^\/api\/(?:movie\/by\/filtres|serie\/by\/filtres|search|season\/by\/serie)\//;
const EDGE_TTL_SECONDS = 300;

// Only the endpoints the web/desktop client actually calls, so the worker
// cannot be repurposed as an open proxy into the database.
const SUPABASE_ALLOWED = /^\/sb\/(?:auth\/v1\/(?:token|signup|logout)|rest\/v1\/profiles)$/;
const SUPABASE_MAX_BODY_BYTES = 256 * 1024;
const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, prefer, accept, range, if-range, x-client-info, platform",
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
            "cache-control": `public, max-age=${EDGE_TTL_SECONDS}`,
            // The Tizen client runs on its own widget origin and cannot send
            // Origin-less requests to the IranFlix API (it has no CORS at all).
            "access-control-allow-origin": "*"
          }
        });
      }
    } catch { /* try the next allowlisted origin */ }
  }
  return Response.json(
    { error: "catalog temporarily unavailable" },
    { status: 503, headers: { "access-control-allow-origin": "*" } }
  );
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

// ------------------------------------------------------------------ media

// Every IranFlix video link is plain HTTP while the PWA and the desktop shell
// run on https, so browsers refuse to load them (mixed content). The worker
// streams the bytes back over HTTPS, forwarding Range so seeking still works.
// Only CDN hosts listed in MEDIA_HOSTS are reachable.
const MEDIA_DEFAULT_HOSTS = "dl150m.info,dl175m.info";
const MEDIA_EXPOSED_HEADERS = "content-length, content-range, accept-ranges";

function mediaHostAllowed(hostname: string, env: Env): boolean {
  const host = hostname.toLowerCase();
  if (host.startsWith("[") || /^\d+\.\d+\.\d+\.\d+$/.test(host)) return false;
  const suffixes = (env.MEDIA_HOSTS || MEDIA_DEFAULT_HOSTS)
    .split(",")
    .map(value => value.trim().toLowerCase())
    .filter(Boolean);
  return suffixes.some(suffix => host === suffix || host.endsWith(`.${suffix}`));
}

async function proxyMedia(request: Request, url: URL, env: Env): Promise<Response> {
  if (request.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }
  if (!["GET", "HEAD"].includes(request.method)) {
    return Response.json({ error: "unsupported media request" }, { status: 405, headers: CORS_HEADERS });
  }
  const raw = url.searchParams.get("u");
  let target: URL;
  try {
    target = new URL(raw || "");
  } catch {
    return Response.json({ error: "invalid media url" }, { status: 400, headers: CORS_HEADERS });
  }
  if (!["http:", "https:"].includes(target.protocol) || !mediaHostAllowed(target.hostname, env)) {
    return Response.json({ error: "media host not allowed" }, { status: 403, headers: CORS_HEADERS });
  }

  const headers = new Headers();
  for (const name of ["range", "if-range", "accept"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  // The CDN rejects requests without a browser-like agent on some edges.
  headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

  const upstream = await fetch(target.toString(), { method: request.method, headers, redirect: "follow" });
  if (!mediaHostAllowed(new URL(upstream.url).hostname, env)) {
    return Response.json({ error: "media host not allowed" }, { status: 403, headers: CORS_HEADERS });
  }

  const responseHeaders = new Headers(CORS_HEADERS);
  for (const name of ["content-type", "content-length", "content-range", "accept-ranges", "last-modified", "etag"]) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  responseHeaders.set("access-control-expose-headers", MEDIA_EXPOSED_HEADERS);
  responseHeaders.set("cache-control", "no-store");
  return new Response(request.method === "HEAD" ? null : upstream.body, {
    status: upstream.status,
    headers: responseHeaders
  });
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

// ----------------------------------------------------------------- short share links
// POST /api/share {data} stores a compressed watchlist and returns /s/<code>;
// GET /api/share/<code> returns {data}; /s/<code> opens the import page.
const SHARE_CODE = /^[A-Za-z0-9]{8}$/;
const SHARE_MAX_BYTES = 65536;

function shareCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  return Array.from(bytes, b => alphabet[b % alphabet.length]).join("");
}

function supabaseHeaders(env: Env): Headers {
  return new Headers({
    apikey: env.SUPABASE_ANON_KEY,
    Authorization: `Bearer ${env.SUPABASE_ANON_KEY}`,
    "Content-Type": "application/json"
  });
}

async function createShare(request: Request, url: URL, env: Env): Promise<Response> {
  const cors = { "access-control-allow-origin": "*" };
  if (!env.SUPABASE_URL || !env.SUPABASE_ANON_KEY) return Response.json({ error: "share unavailable" }, { status: 503, headers: cors });
  const body = await request.text();
  if (body.length > SHARE_MAX_BYTES + 64) return Response.json({ error: "too large" }, { status: 413, headers: cors });
  let data = "";
  try { data = String((JSON.parse(body) as { data?: unknown }).data || ""); } catch { /* handled below */ }
  if (!data || data.length > SHARE_MAX_BYTES || !/^[A-Za-z0-9+/=_-]+$/.test(data)) {
    return Response.json({ error: "invalid list" }, { status: 400, headers: cors });
  }
  for (let attempt = 0; attempt < 3; attempt++) {
    const code = shareCode();
    const res = await fetch(`${env.SUPABASE_URL}/rest/v1/shared_lists`, {
      method: "POST",
      headers: supabaseHeaders(env),
      body: JSON.stringify({ code, payload: data })
    });
    if (res.ok) return Response.json({ code, url: `${url.origin}/s/${code}` }, { headers: cors });
    if (res.status !== 409) return Response.json({ error: "store failed" }, { status: 502, headers: cors });
  }
  return Response.json({ error: "try again" }, { status: 503, headers: cors });
}

async function readShare(code: string, env: Env): Promise<string | null> {
  if (!SHARE_CODE.test(code) || !env.SUPABASE_URL || !env.SUPABASE_ANON_KEY) return null;
  const res = await fetch(`${env.SUPABASE_URL}/rest/v1/shared_lists?code=eq.${code}&select=payload`, { headers: supabaseHeaders(env) });
  if (!res.ok) return null;
  const rows = await res.json() as Array<{ payload?: string }>;
  return rows[0]?.payload || null;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const pathname = decodeURIComponent(url.pathname);
    if (pathname === "/api/social") return socialLinks(request, url, env, ctx);
    if (pathname === "/media") return proxyMedia(request, url, env);
    if (pathname === "/api/share" && request.method === "POST") return createShare(request, url, env);
    if (pathname === "/api/share" && request.method === "OPTIONS") {
      return new Response(null, { headers: { "access-control-allow-origin": "*", "access-control-allow-methods": "POST", "access-control-allow-headers": "content-type" } });
    }
    if (pathname.startsWith("/api/share/")) {
      const data = await readShare(pathname.slice("/api/share/".length), env);
      return data
        ? Response.json({ data }, { headers: { "access-control-allow-origin": "*", "cache-control": "public, max-age=86400" } })
        : Response.json({ error: "not found" }, { status: 404, headers: { "access-control-allow-origin": "*" } });
    }
    if (pathname.startsWith("/s/")) {
      // Short link: resolve to the existing ?data= import page (web, desktop, app links).
      const data = await readShare(pathname.slice(3).replace(/\/$/, ""), env);
      if (!data) return new Response("این لینک اشتراک پیدا نشد یا منقضی شده است.", { status: 404, headers: { "content-type": "text/plain; charset=utf-8" } });
      return Response.redirect(`${url.origin}/import?data=${encodeURIComponent(data)}`, 302);
    }
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
