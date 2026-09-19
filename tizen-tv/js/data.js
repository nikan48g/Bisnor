/**
 * Bisnor Desktop - Media Data Service
 * 100% Real Live Media Catalog from Iranflix (Movies, Series, Top IMDb, Animations)
 */

class MediaDataService {
    constructor() {
        // Do not expose the historic bundled snapshot: it contains upstream records whose
        // title and metadata no longer match.  Only data returned by the live provider is
        // eligible for display.
        this.catalog = [];
        this.pendingRequests = new Map();
        this.initBridgeListener();
        this.servers = [
            "https://hostinnegar.com",
            "https://server-hi-speed-iran.info"
        ];
        this.apiKey = "4F5A9C3D9A86FA54EACEDDD635185";
        this.adKeywords = [
            "تبلیغ", "ورژن جدید", "اپلیکیشن", "دانلود اپ", "کانال تلگرام", "فیلترشکن", 
            "v2ray", "vpn", "proxy", "simba", "darknama", "نسخه جدید", "بروزرسانی",
            "promot", "update app", "apk", "t.me", "telegram", "کانال"
        ];
    }

    isAdvertisement(title, desc, url = "") {
        const t = (title || "").toLowerCase();
        const d = (desc || "").toLowerCase();
        const u = (url || "").toLowerCase();
        if (this.adKeywords.some(kw => t.includes(kw) || d.includes(kw))) return true;
        if (u.endsWith(".apk") || u.includes("download_app") || u.includes("telegram") || u.includes("t.me")) return true;
        return false;
    }

    initBridgeListener() {
        if (window.chrome && window.chrome.webview) {
            window.chrome.webview.addEventListener("message", (e) => {
                try {
                    let msg = e.data;
                    if (typeof msg === "string") {
                        try {
                            msg = JSON.parse(msg);
                        } catch (_) {}
                    }
                    if (msg && msg.action === "iranflixResponse" && msg.requestId) {
                        const resolver = this.pendingRequests.get(msg.requestId);
                        if (resolver) {
                            this.pendingRequests.delete(msg.requestId);
                            if (msg.success && msg.data) {
                                resolver.resolve(msg.data);
                            } else {
                                resolver.reject(new Error(msg.error || "Bridge request failed"));
                            }
                        }
                    }
                } catch (err) {
                    console.error("[BridgeListener Error]", err);
                }
            });
        }
    }

    async fetchEndpoint(path) {
        // Ensure trailing slash
        const normalizedPath = path.endsWith("/") ? path : path + "/";
        const requestId = "req_" + Date.now() + "_" + Math.random().toString(36).substring(2, 7);

        // Try Native .NET HttpClient bridge first (bypasses browser CORS & SSL cert issues)
        if (window.chrome && window.chrome.webview) {
            try {
                const promise = new Promise((resolve, reject) => {
                    const timer = setTimeout(() => {
                        this.pendingRequests.delete(requestId);
                        reject(new Error("Bridge Timeout (3.5s)"));
                    }, 3500);
                    this.pendingRequests.set(requestId, {
                        resolve: (data) => { clearTimeout(timer); resolve(data); },
                        reject: (err) => { clearTimeout(timer); reject(err); }
                    });
                });

                window.chrome.webview.postMessage({
                    action: "fetchIranflix",
                    requestId,
                    endpoint: normalizedPath
                });

                const rawJson = await promise;
                let parsed = typeof rawJson === "string" ? JSON.parse(rawJson) : rawJson;
                if (typeof parsed === "string") {
                    try { parsed = JSON.parse(parsed); } catch (_) {}
                }
                return parsed;
            } catch (bridgeErr) {
                console.warn("[Bridge Fetch Failed, fallback to proxy]", bridgeErr);
            }
        }

        const cleanPath = normalizedPath.replace(/\{API_KEY\}/g, this.apiKey);

        // A public CORS proxy is not a reliable production dependency.  `catalogProxyUrl`
        // can be set by the site owner to a small HTTPS proxy on the Bisnor domain.  Keep
        // the direct attempt below for providers which later enable CORS.
        if (window.BISNOR_CATALOG_PROXY_URL) {
            try {
                const proxyBase = window.BISNOR_CATALOG_PROXY_URL.replace(/\/$/, "");
                const resp = await fetch(`${proxyBase}?path=${encodeURIComponent(cleanPath)}`, {
                    headers: { "Accept": "application/json" },
                    signal: (typeof AbortSignal !== "undefined" && typeof AbortSignal.timeout === "function") ? AbortSignal.timeout(12000) : undefined
                });
                if (resp.ok) return await resp.json();
            } catch (proxyErr) {
                console.warn("[Bisnor catalog proxy failed]", proxyErr);
            }
        }

        // Temporary compatibility attempt for old installations.  This service is allowed
        // to fail; it is never used as the only production path.
        for (const server of this.servers) {
            try {
                const directUrl = `${server.replace(/\/+$/, "")}${cleanPath}`;
                const proxyUrl = `https://api.allorigins.win/raw?url=${encodeURIComponent(directUrl)}`;
                const resp = await fetch(proxyUrl, {
                    headers: { "Accept": "application/json" },
                    signal: (typeof AbortSignal !== "undefined" && typeof AbortSignal.timeout === "function") ? AbortSignal.timeout(12000) : undefined
                });
                if (resp.ok) {
                    const text = await resp.text();
                    if (text && text.trim().startsWith("[") || text.trim().startsWith("{")) {
                        try { return JSON.parse(text); } catch (_) {}
                    }
                }
            } catch (proxyErr) {
                console.warn("[Proxy Fetch Failed]", proxyErr);
            }
        }

        // Direct fetch fallback (last resort)
        for (const server of this.servers) {
            try {
                const url = `${server.replace(/\/+$/, "")}${cleanPath}`;
                const resp = await fetch(url, { headers: { "Accept": "application/json" } });
                if (resp.ok) {
                    return await resp.json();
                }
            } catch (_) {}
        }
        return null;
    }

    cleanMedia(raw) {
        if (!raw) return null;
        const title = (raw.title || '').trim();
        const desc = (raw.description || '').replace(/\r\n/g, '\n').trim();

        // Filter out ads or invalid titles
        if (!title || this.isAdvertisement(title, desc)) return null;

        const genres = (raw.genres || []).map(g => ({
            id: g.id || 0,
            title: (g.title || '').trim()
        })).filter(g => g.title && !this.adKeywords.some(kw => g.title.toLowerCase().includes(kw)));

        const isAnim = genres.some(g => g.title.includes("انیمیشن") || g.title.includes("کارتون") || g.title.includes("انیمه"));

        // Parse clean IMDb score if available in description
        let score = raw.imdb ? parseFloat(raw.imdb) : 0;
        if (score > 10) score = (score / 10).toFixed(1);
        if (score === 0) {
            const m = desc.match(/(?:IMDb|امتیاز|نمره)\s*[:：\-]?\s*([0-9](?:\.[0-9])?)/i);
            if (m) score = parseFloat(m[1]);
        }
        if (isNaN(score) || score <= 0) score = 7.5;

        // Parse clean storyline from description
        let storyline = desc;
        const storyIndex = desc.indexOf("خلاصه داستان");
        if (storyIndex !== -1) {
            storyline = desc.substring(storyIndex).replace(/خلاصه داستان\s*[:：\-]?/g, '').trim();
        }

        // Clean sources - filter out telegram, apk, or promotional links
        const cleanSources = (raw.sources || []).filter(s => {
            const u = (s.url || '').toLowerCase();
            const q = (s.quality || '').toLowerCase();
            if (!u || !u.startsWith("http")) return false;
            if (u.endsWith(".apk") || u.includes("telegram") || u.includes("t.me")) return false;
            if (this.adKeywords.some(kw => q.includes(kw))) return false;
            return true;
        }).map(s => ({
            id: s.id || Math.random(),
            quality: (s.quality || '1080p').replace(/زیرنویس/g, 'فارسی').trim(),
            type: s.type || 'mkv',
            url: s.url
        }));

        // Optimize image URLs
        const img = raw.image || "";
        const cover = raw.cover || img;

        return {
            id: raw.id,
            type: raw.type === "serie" ? "serie" : "movie",
            title: title,
            description: storyline || desc || 'توضیحاتی برای این اثر ثبت نشده است.',
            year: raw.year || 2024,
            imdb: Number(score.toFixed ? score.toFixed(1) : score),
            rating: raw.rating || 5,
            duration: raw.duration || (raw.type === 'serie' ? 'سریال چند قسمتی' : '120 دقیقه'),
            image: img,
            cover: cover,
            genres: genres.length > 0 ? genres : [{ id: 1, title: 'فیلم سینمایی' }],
            country: raw.country || [{ id: 1, title: 'جهانی' }],
            sources: cleanSources
        };
    }

    async getLatestMovies() {
        const raw = await this.fetchEndpoint("/api/movie/by/filtres/0/created/0/{API_KEY}/");
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return [];
    }

    async getPopularSeries() {
        const raw = await this.fetchEndpoint("/api/serie/by/filtres/0/created/0/{API_KEY}/");
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return [];
    }

    async getTopImdb() {
        const raw = await this.fetchEndpoint("/api/movie/by/filtres/0/imdb/0/{API_KEY}/");
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return [];
    }

    async getAnimations() {
        const raw = await this.fetchEndpoint("/api/movie/by/filtres/3/created/0/{API_KEY}/");
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return [];
    }

    async getExploreCatalog() {
        if (this.catalog && this.catalog.length >= 20) {
            return this.catalog;
        }
        // On TV the initial catalog must not be rendered before the network work
        // completes; returning an empty array here left the Explore tab blank
        // forever because nothing asked it to render again.
        await Promise.all([
            this.getLatestMovies().catch(function () { return []; }),
            this.getPopularSeries().catch(function () { return []; }),
            this.getTopImdb().catch(function () { return []; })
        ]);
        return this.catalog;
    }

    async search(query) {
        if (!query || !query.trim()) return this.getExploreCatalog();
        const encoded = encodeURIComponent(query.trim()).replace(/%20/g, "+");
        const raw = await this.fetchEndpoint(`/api/search/${encoded}/{API_KEY}/`);
        if (raw && Array.isArray(raw.posters) && raw.posters.length > 0) {
            const items = raw.posters.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        // Local query match fallback
        const q = query.trim().toLowerCase();
        return this.catalog.filter(x => {
            const title = (x.title || '').toLowerCase();
            const desc = (x.description || '').toLowerCase();
            const genres = (x.genres || []).map(g => g.title).join(' ').toLowerCase();
            return title.includes(q) || desc.includes(q) || genres.includes(q);
        });
    }

    async getSeriesSeasons(seriesId) {
        const raw = await this.fetchEndpoint(`/api/season/by/serie/${seriesId}/{API_KEY}/`);
        if (Array.isArray(raw)) {
            const seasons = [];
            raw.forEach((s, idx) => {
                const rawTitle = (s.title || '').trim();
                const title = rawTitle && rawTitle !== "null" ? rawTitle : `فصل ${idx + 1}`;
                const episodes = [];
                (s.episodes || []).forEach((ep, epIdx) => {
                    const epTitle = (ep.title || '').trim();
                    const epDesc = (ep.description || '').trim();
                    if (this.isAdvertisement(epTitle, epDesc)) return;

                    const cleanSources = (ep.sources || []).filter(src => {
                        const u = (src.url || '').toLowerCase();
                        if (!u || !u.startsWith("http")) return false;
                        if (u.endsWith(".apk") || u.includes("telegram") || u.includes("t.me")) return false;
                        return true;
                    }).map(src => ({
                        id: src.id || Math.random(),
                        quality: (src.quality || '720p / 1080p').replace(/زیرنویس/g, 'فارسی').trim(),
                        type: src.type || 'mkv',
                        url: src.url
                    }));

                    if (cleanSources.length > 0) {
                        episodes.push({
                            id: ep.id || (idx * 100 + epIdx),
                            title: epTitle && epTitle !== "null" ? epTitle : `قسمت ${epIdx + 1}`,
                            duration: ep.duration || '45 دقیقه',
                            sources: cleanSources
                        });
                    }
                });

                if (episodes.length > 0) {
                    seasons.push({ id: s.id, title, episodes });
                }
            });
            return seasons;
        }
        return [];
    }

    mergeIntoCatalog(items) {
        items.forEach(item => {
            if (!this.catalog.some(c => c.id === item.id)) {
                this.catalog.push(item);
            }
        });
    }

    getHeroFeaturedSync() {
        return (this.catalog && this.catalog.length > 0) ? this.catalog[0] : null;
    }

    async getHeroFeatured() {
        return (this.catalog && this.catalog.length > 0) ? this.catalog[0] : null;
    }
}

window.mediaService = new MediaDataService();
