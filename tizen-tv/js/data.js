/**
 * Bisnor Web Clients - Media Data Service
 * 100% Real Live Media Catalog from Iranflix (Movies, Series, Top IMDb, Animations)
 */

class MediaDataService {
    constructor() {
        // Do not expose the historic bundled snapshot: it contains upstream records whose
        // title and metadata no longer match.  Only data returned by the live provider is
        // eligible for display.
        this.catalog = [];
        this.exploreLoaded = false;
        this.exploreLoadPromise = null;
        this.pendingRequests = new Map();
        this.initBridgeListener();
        this.servers = [
            "https://server-hi-speed-iran.info",
            "https://hostinnegar.com",
            "https://windowsdiba.info"
        ];
        this.apiKey = "4F5A9C3D9A86FA54EACEDDD635185";
        this.next = new window.NextMovieProvider();
        this.sourceMode = localStorage.getItem("bisnor_source") || "both";
        this.nmCache = new Map();
        this.nextMatchCache = new Map();
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
                    }, 22000);
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

        // Direct live API fallback. No public proxy and no bundled catalog are used.
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
            url: s.url,
            origin: 'iranflix'
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

    // ------------------------------------------------------------ source mode

    usesNext() {
        return this.sourceMode === "next" || this.sourceMode === "both";
    }

    usesIranflix() {
        return this.sourceMode === "iranflix" || this.sourceMode === "both";
    }

    setSourceMode(mode) {
        const normalized = (mode === "next" || mode === "iranflix" || mode === "both") ? mode : "both";
        if (normalized === this.sourceMode) return;
        this.sourceMode = normalized;
        localStorage.setItem("bisnor_source", normalized);
        this.catalog = [];
        this.exploreLoaded = false;
        this.exploreLoadPromise = null;
        this.nmCache.clear();
    }

    /** One entry per title: Next Movie rows lead, IranFlix metadata wins on matches. */
    mergeCollections(iranflixItems, nextItems) {
        const ir = (iranflixItems || []).filter(Boolean);
        const nm = (nextItems || []).filter(Boolean);
        if (!nm.length) return ir;
        if (!ir.length) return nm;
        const index = new window.BisnorMerge.MatchIndex(ir);
        const used = new Set();
        const merged = [];
        nm.forEach(item => {
            const match = index.find(item);
            if (match && !used.has(match.id)) {
                used.add(match.id);
                merged.push(window.BisnorMerge.mergePair(match, item));
            } else {
                merged.push(item);
            }
        });
        ir.forEach(item => {
            if (!used.has(item.id)) merged.push(item);
        });
        return merged;
    }

    async combine(iranflixLoader, nextLoader) {
        const iranflix = this.usesIranflix() ? await iranflixLoader() : [];
        const nextItems = this.usesNext() ? await nextLoader() : [];
        const items = this.mergeCollections(iranflix, nextItems);
        this.mergeIntoCatalog(items);
        return items;
    }

    async getNextList(kind) {
        const cached = this.nmCache.get(kind);
        if (cached && Date.now() - cached.at < 5 * 60 * 1000) return cached.data.slice();
        let items = [];
        try {
            if (kind === "top") {
                items = (await this.next.browse({ sortKey: "imdb_rating", page: 1 }))
                    .map(x => this.next.toMedia(x)).filter(Boolean)
                    .filter(item => item.imdb > 0 && item.imdb < 9.9);
            } else if (kind === "anime") {
                const slides = await this.next.home();
                let genreId = 0;
                ((slides && slides.genres) || []).forEach(genre => {
                    const title = genre.title || "";
                    if (!genreId && (title.includes("انیمیشن") || title.includes("انیمه"))) genreId = genre.id;
                });
                if (genreId) {
                    items = (await this.next.browse({ genreIds: [genreId], page: 1 }))
                        .map(x => this.next.toMedia(x)).filter(Boolean);
                }
            } else {
                const type = kind === "series" ? "series" : "movie";
                items = (await this.next.browse({ type, page: 1 }))
                    .map(x => this.next.toMedia(x)).filter(Boolean);
            }
        } catch (_) {
            items = [];
        }
        this.nmCache.set(kind, { at: Date.now(), data: items });
        return items.slice();
    }

    async getLatestMovies(page = 0) {
        return this.combine(
            async () => {
                const raw = await this.fetchEndpoint(`/api/movie/by/filtres/0/created/${page}/{API_KEY}/`);
                return Array.isArray(raw) ? raw.map(x => this.cleanMedia(x)).filter(Boolean) : [];
            },
            async () => page === 0 ? this.getNextList("latest") : []
        );
    }

    async getPopularSeries(page = 0) {
        return this.combine(
            async () => {
                const raw = await this.fetchEndpoint(`/api/serie/by/filtres/0/created/${page}/{API_KEY}/`);
                return Array.isArray(raw) ? raw.map(x => this.cleanMedia(x)).filter(Boolean) : [];
            },
            async () => page === 0 ? this.getNextList("series") : []
        );
    }

    async getTopImdb(page = 0) {
        return this.combine(
            async () => {
                const raw = await this.fetchEndpoint(`/api/movie/by/filtres/0/imdb/${page}/{API_KEY}/`);
                return Array.isArray(raw) ? raw.map(x => this.cleanMedia(x)).filter(Boolean) : [];
            },
            async () => page === 0 ? this.getNextList("top") : []
        );
    }

    async getAnimations() {
        return this.combine(
            async () => {
                const raw = await this.fetchEndpoint("/api/movie/by/filtres/3/created/0/{API_KEY}/");
                return Array.isArray(raw) ? raw.map(x => this.cleanMedia(x)).filter(Boolean) : [];
            },
            async () => this.getNextList("anime")
        );
    }

    async getExploreCatalog() {
        if (this.exploreLoaded) return this.catalog;
        if (this.exploreLoadPromise) return this.exploreLoadPromise;

        // Match Android's Explore contract: four IMDb pages, three latest-movie
        // pages and three series pages. Page zero may already be present from Home;
        // mergeIntoCatalog de-duplicates those records by their live API id.
        const requests = [
            this.getTopImdb(0),
            this.getTopImdb(1),
            this.getTopImdb(2),
            this.getTopImdb(3),
            this.getLatestMovies(0),
            this.getLatestMovies(1),
            this.getLatestMovies(2),
            this.getPopularSeries(0),
            this.getPopularSeries(1),
            this.getPopularSeries(2)
        ];

        this.exploreLoadPromise = Promise.allSettled(requests).then(() => {
            this.exploreLoaded = true;
            return this.catalog;
        }).finally(() => {
            this.exploreLoadPromise = null;
        });

        return this.exploreLoadPromise;
    }

    async search(query) {
        if (!query || !query.trim()) return this.getExploreCatalog();
        const q = query.trim();
        // Iranflix expects path-safe percent encoding. A '+' is treated literally by
        // this endpoint, so multi-word titles such as "One Piece" otherwise return 0.
        const [iranflixItems, nextItems] = await Promise.all([
            this.usesIranflix()
                ? this.fetchEndpoint(`/api/search/${encodeURIComponent(q)}/{API_KEY}/`)
                    .then(raw => (raw && Array.isArray(raw.posters)) ? raw.posters.map(x => this.cleanMedia(x)).filter(Boolean) : [])
                    .catch(() => [])
                : Promise.resolve([]),
            this.usesNext()
                ? this.next.search(q).then(items => items.map(x => this.next.toMedia(x)).filter(Boolean)).catch(() => [])
                : Promise.resolve([])
        ]);

        const combined = this.mergeCollections(iranflixItems, nextItems);
        if (combined.length) {
            this.mergeIntoCatalog(combined);
            return combined;
        }
        // Local query match fallback
        const lower = q.toLowerCase();
        return this.catalog.filter(x => {
            const title = (x.title || '').toLowerCase();
            const desc = (x.description || '').toLowerCase();
            const genres = (x.genres || []).map(g => g.title).join(' ').toLowerCase();
            return title.includes(lower) || desc.includes(lower) || genres.includes(lower);
        });
    }

    parseIndex(title, fallback) {
        const text = window.BisnorMerge.normalizeDigits(title || "");
        const cleaned = text
            .replace(/\b(?:2160|1440|1080|720|480|360|4k|8k)\b/gi, " ")
            .replace(/(?:زیرنویس|چسبیده|دوبله|پارسی|کیفیت|صوت|زبان|فصل|قسمت|season|episode)/gi, " ");
        const digit = cleaned.match(/(\d+)/);
        if (digit) return Number(digit[1]);
        const ordinals = { اول: 1, یکم: 1, دوم: 2, سوم: 3, چهارم: 4, پنجم: 5, ششم: 6, هفتم: 7, هشتم: 8, نهم: 9, دهم: 10 };
        for (const [word, value] of Object.entries(ordinals)) {
            if (text.includes(word)) return value;
        }
        return fallback;
    }

    mergeSeasons(iranflixSeasons, nextSeasons) {
        const ir = (iranflixSeasons || []).filter(Boolean);
        const nm = (nextSeasons || []).filter(Boolean);
        if (!nm.length) return ir;
        if (!ir.length) return nm;
        const byNumber = new Map();
        ir.forEach((season, index) => byNumber.set(Number(season.number) || index + 1, { ...season, episodes: (season.episodes || []).slice() }));
        nm.forEach((season, index) => {
            const number = Number(season.number) || index + 1;
            const existing = byNumber.get(number);
            if (!existing) {
                byNumber.set(number, { ...season, episodes: (season.episodes || []).slice() });
                return;
            }
            existing.episodes = this.mergeEpisodes(existing.episodes || [], season.episodes || []);
        });
        return [...byNumber.entries()].sort((a, b) => a[0] - b[0]).map(entry => entry[1]);
    }

    mergeEpisodes(iranflixEpisodes, nextEpisodes) {
        const byNumber = new Map();
        iranflixEpisodes.forEach((episode, index) => byNumber.set(Number(episode.number) || index + 1, { ...episode, sources: (episode.sources || []).slice() }));
        nextEpisodes.forEach((episode, index) => {
            const number = Number(episode.number) || index + 1;
            const existing = byNumber.get(number);
            if (!existing) {
                byNumber.set(number, { ...episode, sources: (episode.sources || []).slice() });
                return;
            }
            const seen = new Set((existing.sources || []).map(src => (src.url || '').split('?')[0]));
            existing.sources = [
                ...(existing.sources || []),
                ...(episode.sources || []).filter(src => !seen.has((src.url || '').split('?')[0]))
            ];
        });
        return [...byNumber.entries()].sort((a, b) => a[0] - b[0]).map(entry => entry[1]);
    }

    /** Next Movie id of a title the list matching missed; looked up once and cached. */
    async resolveNextId(media) {
        if (!media || !this.usesNext() || !media.title) return 0;
        if (media.nmId) return media.nmId;
        const key = `${window.BisnorMerge.normalizeTitle(media.title)}|${media.type === 'serie' ? 'series' : 'movie'}`;
        if (this.nextMatchCache.has(key)) return this.nextMatchCache.get(key);
        try {
            const results = await this.next.search(media.title, media.type === 'serie' ? 'series' : 'movie');
            const index = new window.BisnorMerge.MatchIndex([media]);
            for (const raw of results) {
                const candidate = this.next.toMedia(raw);
                if (!candidate) continue;
                if (index.find(candidate)) {
                    this.nextMatchCache.set(key, candidate.nmId);
                    return candidate.nmId;
                }
            }
        } catch (_) {}
        return 0;
    }

    async ensureNextId(media) {
        if (!media || !this.usesNext() || media.nmId) return media && media.nmId;
        const id = await this.resolveNextId(media);
        if (id) {
            media.nmId = id;
            media.ids = { ...(media.ids || {}), next: id };
            if (media.source !== 'next') media.source = 'both';
        }
        return id;
    }

    /** Fills the Next Movie details/links a list payload does not carry. */
    async hydrateMedia(media) {
        if (!media || !this.usesNext()) return media;
        if (!media.nmId) await this.ensureNextId(media);
        if (!media.nmId) return media;
        try {
            const details = await this.next.details(media.nmId);
            if (details) {
                if (!media.description) media.description = details.description || '';
                if (!media.image) media.image = details.poster || '';
                if (!media.cover) media.cover = media.image;
                if (!media.year) media.year = Number(details.year) || 0;
                if (!media.imdb) media.imdb = Number.parseFloat(details.imdb_rating) || 0;
                if (!media.duration) media.duration = details.duration || '';
                if ((!media.genres || !media.genres.length) && Array.isArray(details.genres)) {
                    media.genres = details.genres
                        .map(genre => ({ id: genre.id || 0, title: (genre.title || '').trim() }))
                        .filter(genre => genre.title);
                }
                if ((!media.country || !media.country.length) && Array.isArray(details.countries)) {
                    media.country = details.countries
                        .map(country => ({ id: country.id || 0, title: (country.title || '').trim(), image: country.image || "" }))
                        .filter(country => country.title);
                }
            }
            if (media.type !== 'serie') {
                const links = await this.next.links(media.nmId);
                const extra = ((links && links.movie_links) || [])
                    .map(link => this.next.toSource(link)).filter(Boolean);
                if (extra.length) {
                    const seen = new Set((media.sources || []).map(src => (src.url || '').split('?')[0]));
                    media.sources = [
                        ...(media.sources || []),
                        ...extra.filter(src => !seen.has(src.url.split('?')[0]))
                    ];
                }
            }
        } catch (_) {}
        return media;
    }

    async getSeriesSeasons(mediaOrId) {
        const media = (mediaOrId && typeof mediaOrId === 'object') ? mediaOrId : { id: mediaOrId };
        const iranflixId = media.iranflix || ((media.id || 0) > 0 ? media.id : 0);
        if (!media.nmId && this.usesNext()) await this.ensureNextId(media);
        const nmId = media.nmId || 0;
        const wantIranflix = this.usesIranflix() && media.source !== 'next' && iranflixId > 0;
        const wantNext = this.usesNext() && nmId > 0;

        const [iranflixSeasons, nextSeasons] = await Promise.all([
            wantIranflix ? this.iranflixSeasons(iranflixId) : Promise.resolve([]),
            wantNext
                ? this.next.links(nmId).then(links => this.next.toSeasonList(links)).catch(() => [])
                : Promise.resolve([])
        ]);
        return this.mergeSeasons(iranflixSeasons, nextSeasons);
    }

    async iranflixSeasons(seriesId) {
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
                        url: src.url,
                        origin: 'iranflix'
                    }));

                    if (cleanSources.length > 0) {
                        const epTitleFinal = epTitle && epTitle !== "null" ? epTitle : `قسمت ${epIdx + 1}`;
                        episodes.push({
                            id: ep.id || (idx * 100 + epIdx),
                            title: epTitleFinal,
                            duration: ep.duration || '45 دقیقه',
                            sources: cleanSources,
                            number: this.parseIndex(epTitleFinal, epIdx + 1)
                        });
                    }
                });

                if (episodes.length > 0) {
                    seasons.push({ id: s.id, title, episodes, number: this.parseIndex(title, idx + 1) });
                }
            });
            return seasons;
        }
        return [];
    }

    mergeIntoCatalog(items) {
        items.forEach(item => {
            const index = this.catalog.findIndex(c => c.id === item.id);
            if (index >= 0) {
                if (item.source === 'both' && this.catalog[index].source !== 'both') {
                    this.catalog[index] = item;
                }
                return;
            }
            const nmId = item.nmId || 0;
            if (nmId) {
                const sameNext = this.catalog.findIndex(c => (c.nmId || 0) === nmId);
                if (sameNext >= 0) {
                    if (item.source === 'both') this.catalog[sameNext] = item;
                    return;
                }
            }
            this.catalog.push(item);
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
