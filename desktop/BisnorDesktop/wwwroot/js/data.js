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
        this.endpointCache = new Map();
        this.inFlightRequests = new Map();
        this.seasonsCache = new Map();
        this.next = new window.NextMovieProvider();
        this.sourceMode = localStorage.getItem("bisnor_source") || "both";
        this.nmHomePromise = null;
        this.nmRows = null;
        this.nmListCache = new Map();
        this.nextMatchCache = new Map();
        this.nextMatchPending = new Map();
        this.initBridgeListener();
        this.loadCatalogCache();
        this.servers = [
            "https://server-hi-speed-iran.info",
            "https://hostinnegar.com",
            "https://windowsdiba.info"
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

    // ------------------------------------------------------------ source modes

    usesNext() {
        return this.sourceMode === "next" || this.sourceMode === "both";
    }

    usesIranflix() {
        return this.sourceMode === "iranflix" || this.sourceMode === "both";
    }

    setSourceMode(mode) {
        const wanted = ["next", "iranflix", "both"].includes(mode) ? mode : "both";
        if (wanted === this.sourceMode) return false;
        this.sourceMode = wanted;
        localStorage.setItem("bisnor_source", wanted);
        this.catalog = [];
        this.exploreLoaded = false;
        this.exploreLoadPromise = null;
        this.endpointCache.clear();
        this.seasonsCache.clear();
        this.nextMatchCache.clear();
        this.nextMatchPending.clear();
        this.nmRows = null;
        this.nmHomePromise = null;
        this.nmListCache.clear();
        this.next.memory.clear();
        return true;
    }

    async getNextSlides() {
        if (this.nmRows) return this.nmRows;
        if (this.nmHomePromise) return this.nmHomePromise;
        this.nmHomePromise = (async () => {
            try {
                const slides = await this.next.home();
                const rows = (slides.movies || []).map(slide => ({
                    name: slide.name || "",
                    type: slide.type,
                    items: (slide.items || []).map(item => this.next.toMedia(item)).filter(Boolean)
                })).filter(row => row.items.length);
                const genres = (slides.genres || []).map(item => ({ id: item.id, title: item.title || "" }));
                const countries = (slides.countries || []).map(item => ({ id: item.id, title: item.title || "" }));
                this.nmRows = { rows, genres, countries };
                return this.nmRows;
            } catch (_) {
                return { rows: [], genres: [], countries: [] };
            } finally {
                this.nmHomePromise = null;
            }
        })();
        return this.nmHomePromise;
    }

    pickNextRow(kind) {
        const rows = (this.nmRows && this.nmRows.rows) || [];
        const named = (row, words) => words.some(word => row.name.includes(word));
        let row = null;
        if (kind === "series") {
            row = rows.find(item => named(item, ["سریال‌های جدید", "سریال های جدید"]));
        } else if (kind === "anime") {
            row = rows.find(item => named(item, ["انیمه"]));
        } else {
            row = rows.find(item => named(item, ["فیلم‌های جدید", "فیلم های جدید"]));
        }
        const items = row ? row.items.slice() : [];
        if (kind === "series") return items.filter(item => item.type === "serie");
        if (kind === "movie") return items.filter(item => item.type === "movie");
        return items;
    }

    findNextGenre(words) {
        const genres = (this.nmRows && this.nmRows.genres) || [];
        const hit = genres.find(genre => words.some(word => (genre.title || "").includes(word)));
        return hit ? hit.id : 0;
    }

    async getNextList(kind) {
        const cached = this.nmListCache.get(kind);
        if (cached && Date.now() - cached.at < 5 * 60 * 1000) return cached.data.slice();
        const items = await this.loadNextList(kind);
        this.nmListCache.set(kind, { at: Date.now(), data: items });
        return items.slice();
    }

    async loadNextList(kind) {
        await this.getNextSlides();
        if (kind === "top") {
            const items = (await this.next.browse({ sortKey: "imdb_rating", page: 1 }))
                .map(item => this.next.toMedia(item))
                .filter(Boolean)
                .filter(item => item.imdb > 0 && item.imdb < 9.9);
            return items;
        }
        if (kind === "anime" || kind === "korean") {
            const genreId = this.findNextGenre(kind === "anime" ? ["انیمیشن", "انیمه"] : ["کره"]);
            const base = kind === "anime" ? [] : this.pickNextRow("movie");
            if (!genreId) return base;
            const extra = (await this.next.browse({ genreIds: [genreId], page: 1 }))
                .map(item => this.next.toMedia(item))
                .filter(Boolean);
            return [...base, ...extra].filter((item, index, all) => all.findIndex(other => other.id === item.id) === index);
        }
        return this.pickNextRow(kind);
    }

    /** One entry per title: Next items are matched against the IranFlix list. */
    mergeCollections(iranflixItems, nextItems) {
        const irItems = (iranflixItems || []).filter(Boolean);
        const nmItems = (nextItems || []).filter(Boolean);
        if (!this.usesNext() || nmItems.length === 0) return irItems;
        if (!irItems.length) return nmItems;

        const index = new window.BisnorMerge.MatchIndex(irItems);
        const used = new Set();
        const merged = [];
        nmItems.forEach(nextItem => {
            const match = index.find(nextItem);
            if (match && !used.has(match.id)) {
                used.add(match.id);
                merged.push(window.BisnorMerge.mergePair(match, nextItem));
            } else {
                merged.push(nextItem);
            }
        });
        irItems.forEach(item => {
            if (!used.has(item.id)) merged.push(item);
        });
        return merged;
    }

    parseIndex(title, fallback) {
        const text = window.BisnorMerge.normalizeDigits(title || "");
        // Quality and language markers are not season/episode numbers.
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

    dedupeSources(sources) {
        const seen = new Set();
        return (sources || []).filter(source => {
            if (!source || !source.url) return false;
            const key = source.url.split("?")[0];
            if (seen.has(key)) return false;
            seen.add(key);
            return true;
        });
    }

    /**
     * Finds the Next Movie id of an IranFlix title that the list matching missed.
     * The catalogue merge only sees the rows that happen to be loaded together, so a
     * title opened from a watchlist, a stale cache or an unmatched row would otherwise
     * show IranFlix links only while still being present on Next Movie.
     */
    async resolveNextId(media) {
        if (!media || !this.usesNext() || !media.title) return 0;
        const ids = media.ids || {};
        if (ids.next) return Number(ids.next) || 0;
        const key = `${window.BisnorMerge.normalizeTitle(media.title)}|${media.type === "serie" ? "series" : "movie"}|${media.year || 0}`;
        if (this.nextMatchCache.has(key)) return this.nextMatchCache.get(key);
        if (this.nextMatchPending.has(key)) return this.nextMatchPending.get(key);

        const task = (async () => {
            try {
                const results = await this.next.search(media.title, media.type === "serie" ? "series" : "movie");
                const index = new window.BisnorMerge.MatchIndex([media]);
                for (const raw of results) {
                    const candidate = this.next.toMedia(raw);
                    if (!candidate) continue;
                    if (index.find(candidate)) return Number(candidate.nmId) || 0;
                }
            } catch (_) {}
            return 0;
        })().finally(() => this.nextMatchPending.delete(key));

        this.nextMatchPending.set(key, task);
        const id = await task;
        if (id) this.nextMatchCache.set(key, id);
        return id;
    }

    /** Applies a resolved Next id to the media object without touching IranFlix metadata. */
    applyNextId(media, nextId) {
        const id = Number(nextId) || 0;
        if (!media || id <= 0) return media;
        media.ids = { ...(media.ids || {}), next: id };
        media.nmId = id;
        if (media.source !== "next" && media.source !== "both") {
            media.ids.iranflix = media.ids.iranflix || media.id;
            media.source = "both";
        }
        return media;
    }

    /** Fills in the fields that list endpoints do not return. */
    async hydrateMedia(media) {
        if (!media || media.__hydrated) return media;
        media.__hydrated = true;
        if (!this.usesNext()) return media;

        let nextId = Number((media.ids || {}).next) || 0;
        if (!nextId) {
            nextId = await this.resolveNextId(media);
            if (!nextId) return media;
            this.applyNextId(media, nextId);
        }

        try {
            if (media.type === "serie") {
                const details = await this.next.details(nextId);
                if (details) this.applyNextDetails(media, details);
                return media;
            }
            const [details, links] = await Promise.all([
                this.next.details(nextId),
                this.next.links(nextId)
            ]);
            if (details) this.applyNextDetails(media, details);
            const nextSources = ((links && links.movie_links) || [])
                .map(link => this.next.toSource(link))
                .filter(Boolean);
            media.nmSources = nextSources;
            media.sources = this.dedupeSources([...(media.sources || []), ...nextSources]);
        } catch (_) {}
        return media;
    }

    applyNextDetails(media, details) {
        if (!media.description) media.description = details.description || "";
        if (!media.image) media.image = details.poster || "";
        if (!media.cover) media.cover = details.poster || "";
        if (!media.year) media.year = Number(details.year) || 0;
        if (!media.imdb) media.imdb = Number.parseFloat(details.imdb_rating) || 0;
        if (!media.duration) media.duration = details.duration || "";
        if (!media.genres || !media.genres.length) {
            media.genres = (details.genres || []).map(genre => ({ id: genre.id || 0, title: (genre.title || "").trim() })).filter(genre => genre.title);
        }
        if (!media.country || !media.country.length) {
            media.country = (details.countries || []).map(country => ({ id: country.id || 0, title: (country.title || "").trim() })).filter(country => country.title);
        }
    }

    async fetchNextSeasons(nextId) {
        const links = await this.next.links(nextId);
        return this.next.toSeasonList(links);
    }

    mergeSeasons(iranflixSeasons, nextSeasons) {
        const irSeasons = (iranflixSeasons || []).filter(Boolean);
        const nmSeasons = (nextSeasons || []).filter(Boolean);
        if (!nmSeasons.length) return irSeasons;
        if (!irSeasons.length) return nmSeasons;

        const byNumber = new Map();
        irSeasons.forEach((season, index) => byNumber.set(Number(season.number) || index + 1, { ...season, episodes: (season.episodes || []).slice() }));
        nmSeasons.forEach((season, index) => {
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
            existing.sources = this.dedupeSources([...(existing.sources || []), ...(episode.sources || [])]);
        });
        return [...byNumber.entries()].sort((a, b) => a[0] - b[0]).map(entry => entry[1]);
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

    loadCatalogCache() {
        try {
            const stored = JSON.parse(localStorage.getItem("bisnor_catalog_cache_v1") || "null");
            if (!stored || !Array.isArray(stored.items)) return;
            if (Date.now() - Number(stored.at || 0) > 24 * 60 * 60 * 1000) return;
            this.catalog = stored.items;
        } catch (_) {}
    }

    saveCatalogCache() {
        try {
            if (!this.catalog.length) return;
            const payload = JSON.stringify({ at: Date.now(), items: this.catalog });
            if (payload.length > 2_500_000) return;
            localStorage.setItem("bisnor_catalog_cache_v1", payload);
        } catch (_) {}
    }

    async fetchEndpoint(path) {
        const key = path.replace(/\{API_KEY\}/g, this.apiKey);
        const cached = this.endpointCache.get(key);
        if (cached && Date.now() - cached.at < 5 * 60 * 1000) return cached.data;
        if (this.inFlightRequests.has(key)) return this.inFlightRequests.get(key);

        const task = this.fetchEndpointNetwork(path).then(data => {
            if (data) this.endpointCache.set(key, { at: Date.now(), data });
            return data;
        }).finally(() => this.inFlightRequests.delete(key));
        this.inFlightRequests.set(key, task);
        return task;
    }

    async fetchEndpointNetwork(path) {
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

        // A deployed Bisnor Worker serves the PWA and exposes the allowlisted
        // catalogue endpoints on the same origin.  Static previews simply get
        // a 404 here and continue to the configured/direct fallback below.
        if (window.location.protocol === "https:" && !window.chrome?.webview) {
            try {
                const workerResponse = await fetch(`${window.location.origin}${normalizedPath}`, {
                    headers: { "Accept": "application/json" },
                    signal: (typeof AbortSignal !== "undefined" && typeof AbortSignal.timeout === "function") ? AbortSignal.timeout(12000) : undefined
                });
                if (workerResponse.ok) return await workerResponse.json();
            } catch (_) {}
        }

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
        if (isNaN(score) || score <= 0) score = 0;

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
            id: s.id || 0,
            quality: (s.quality || '').replace(/زیرنویس/g, 'فارسی').trim(),
            type: s.type || '',
            url: s.url,
            origin: "iranflix"
        }));

        // Optimize image URLs
        const img = raw.image || "";
        const cover = raw.cover || img;

        return {
            id: raw.id,
            type: raw.type === "serie" ? "serie" : "movie",
            title: title,
            description: storyline || desc || 'توضیحاتی برای این اثر ثبت نشده است.',
            year: raw.year || 0,
            imdb: Number(score.toFixed ? score.toFixed(1) : score),
            rating: raw.rating || 0,
            duration: raw.duration || (raw.type === 'serie' ? 'سریال چند قسمتی' : '120 دقیقه'),
            image: img,
            cover: cover,
            genres: genres.length > 0 ? genres : [{ id: 1, title: 'فیلم سینمایی' }],
            country: raw.country || [{ id: 1, title: 'جهانی' }],
            sources: cleanSources,
            // The provider does not guarantee that credits exist. Preserve the
            // structured form when it sends it, then use the same description
            // labels Android understands as a graceful live-data fallback.
            people: this.extractPeople(raw, desc)
        };
    }

    extractPeople(raw, description) {
        const people = [];
        const add = (value, role) => {
            if (!value) return;
            const values = Array.isArray(value) ? value : String(value).split(/[,،|]/);
            values.forEach(entry => {
                const item = typeof entry === "object" ? entry : { name: entry };
                const name = String(item.name || item.title || item.full_name || "").trim();
                if (!name || name.length > 80) return;
                const photo = String(item.photo || item.image || item.avatar || item.profile_path || "").trim();
                if (!people.some(person => person.role === role && person.name.toLocaleLowerCase() === name.toLocaleLowerCase())) {
                    people.push({ name, role, photo: /^https?:\/\//i.test(photo) ? photo : "" });
                }
            });
        };
        add(raw.actors || raw.cast || raw.stars, "actor");
        add(raw.director || raw.directors, "director");
        add(raw.writer || raw.writers || raw.screenwriter, "writer");

        const labels = [
            { role: "director", pattern: /(?:کارگردان|کارگردانی|سازنده|director)\s*[:：\-]?\s*([^\n\r]+)/i },
            { role: "writer", pattern: /(?:نویسنده|فیلمنامه|writer)\s*[:：\-]?\s*([^\n\r]+)/i },
            { role: "actor", pattern: /(?:بازیگران|ستارگان|با\s*حضور|هنرمندان|بازیگر|actors|cast|stars)\s*[:：\-]?\s*([^\n\r]+)/i }
        ];
        labels.forEach(({ role, pattern }) => add(pattern.exec(description || "")?.[1], role));
        return people.slice(0, 12);
    }

    async resolvePersonPhoto(person) {
        if (!person?.name || person.photo) return person?.photo || "";
        const cacheKey = `bisnor_person_photo_${person.name.toLocaleLowerCase()}`;
        const cached = localStorage.getItem(cacheKey);
        if (cached) return cached === "none" ? "" : cached;
        try {
            const query = encodeURIComponent(person.name);
            const search = await fetch(`https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${query}&language=fa&format=json&origin=*`);
            const entity = (await search.json()).search?.[0]?.id;
            if (!entity) throw new Error("person not found");
            const entityResponse = await fetch(`https://www.wikidata.org/w/api.php?action=wbgetentities&ids=${entity}&props=claims&format=json&origin=*`);
            const fileName = (await entityResponse.json()).entities?.[entity]?.claims?.P18?.[0]?.mainsnak?.datavalue?.value;
            if (!fileName) throw new Error("portrait unavailable");
            const image = await fetch(`https://commons.wikimedia.org/w/api.php?action=query&titles=File:${encodeURIComponent(fileName)}&prop=imageinfo&iiprop=url&iiurlwidth=240&format=json&origin=*`);
            const pages = (await image.json()).query?.pages || {};
            const photo = Object.values(pages)[0]?.imageinfo?.[0]?.thumburl || "";
            localStorage.setItem(cacheKey, photo || "none");
            return photo;
        } catch (_) {
            localStorage.setItem(cacheKey, "none");
            return "";
        }
    }

    async getIranflixLatest(page = 0) {
        const raw = await this.fetchEndpoint(`/api/movie/by/filtres/0/created/${page}/{API_KEY}/`);
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return [];
    }

    async getIranflixSeries(page = 0) {
        const raw = await this.fetchEndpoint(`/api/serie/by/filtres/0/created/${page}/{API_KEY}/`);
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return [];
    }

    async getIranflixTopImdb(page = 0) {
        const raw = await this.fetchEndpoint(`/api/movie/by/filtres/0/imdb/${page}/{API_KEY}/`);
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return [];
    }

    async getIranflixAnimations() {
        const raw = await this.fetchEndpoint("/api/movie/by/filtres/3/created/0/{API_KEY}/");
        if (Array.isArray(raw) && raw.length > 0) {
            const items = raw.map(x => this.cleanMedia(x)).filter(Boolean);
            this.mergeIntoCatalog(items);
            return items;
        }
        return [];
    }

    async combine(kind, page, iranflixFetcher) {
        if (this.sourceMode === "iranflix") {
            const items = await iranflixFetcher();
            this.mergeIntoCatalog(items);
            return items;
        }
        const nextItems = await this.getNextList(kind);
        if (this.sourceMode === "next") {
            this.mergeIntoCatalog(nextItems);
            return nextItems;
        }
        const irItems = await iranflixFetcher();
        const items = this.mergeCollections(irItems, nextItems);
        this.mergeIntoCatalog(items);
        return items;
    }

    async getLatestMovies(page = 0) {
        return this.combine("movie", page, () => this.getIranflixLatest(page));
    }

    async getPopularSeries(page = 0) {
        return this.combine("series", page, () => this.getIranflixSeries(page));
    }

    async getTopImdb(page = 0) {
        return this.combine("top", page, () => this.getIranflixTopImdb(page));
    }

    async getAnimations() {
        return this.combine("anime", 0, () => this.getIranflixAnimations());
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
            this.getPopularSeries(2),
            this.getAnimations()
        ];
        if (this.usesNext()) requests.push(this.getNextList("korean"));

        this.exploreLoadPromise = Promise.allSettled(requests).then(() => {
            this.exploreLoaded = true;
            this.saveCatalogCache();
            return this.catalog;
        }).finally(() => {
            this.exploreLoadPromise = null;
        });

        return this.exploreLoadPromise;
    }

    async searchIranflix(query) {
        // Iranflix expects path-safe percent encoding. A '+' is treated literally by
        // this endpoint, so multi-word titles such as "One Piece" otherwise return 0.
        const encoded = encodeURIComponent(query.trim());
        const raw = await this.fetchEndpoint(`/api/search/${encoded}/{API_KEY}/`);
        return raw && Array.isArray(raw.posters)
            ? raw.posters.map(x => this.cleanMedia(x)).filter(Boolean)
            : [];
    }

    async search(query) {
        if (!query || !query.trim()) return this.getExploreCatalog();
        const q = query.trim();

        const irPromise = this.usesIranflix() ? this.searchIranflix(q).catch(() => []) : Promise.resolve([]);
        const nmPromise = this.usesNext()
            ? this.next.search(q).then(items => items.map(x => this.next.toMedia(x)).filter(Boolean)).catch(() => [])
            : Promise.resolve([]);
        const [irItems, nmItems] = await Promise.all([irPromise, nmPromise]);

        let serverItems = [];
        if (this.sourceMode === "both") {
            serverItems = this.mergeCollections(irItems, nmItems);
        } else if (this.sourceMode === "next") {
            serverItems = nmItems;
        } else {
            serverItems = irItems;
        }
        this.mergeIntoCatalog(serverItems);

        // Always combine the live response with the hydrated catalog. The API may
        // return title matches but omit credits that match a partial person name.
        const qLower = q.toLocaleLowerCase();
        const localMatches = this.catalog.filter(x => {
            const title = (x.title || '').toLocaleLowerCase();
            const desc = (x.description || '').toLocaleLowerCase();
            const genres = (x.genres || []).map(g => g.title).join(' ').toLocaleLowerCase();
            const people = (x.people || []).map(person => person.name).join(' ').toLocaleLowerCase();
            return title.includes(qLower) || desc.includes(qLower) || genres.includes(qLower) || people.includes(qLower);
        });
        return [...serverItems, ...localMatches].filter((item, index, all) =>
            all.findIndex(candidate => String(candidate.id) === String(item.id)) === index
        );
    }

    async getSeriesSeasons(target) {
        const media = target && typeof target === "object" ? target : this.catalog.find(item => String(item.id) === String(target));
        const ids = (media && media.ids) || {};
        const iranflixId = ids.iranflix || (media
            ? (typeof media.id === "number" ? media.id : 0)
            : Number(target) || 0);
        let nextId = ids.next || 0;
        if (!nextId && media && this.usesNext()) {
            nextId = await this.resolveNextId(media);
            if (nextId) this.applyNextId(media, nextId);
        }
        const cacheKey = `merged_${media ? media.id : target}_${this.sourceMode}_${nextId}`;
        const cached = this.seasonsCache.get(cacheKey);
        if (cached && Date.now() - cached.at < 10 * 60 * 1000) return cached.data;

        const irTask = this.usesIranflix() && iranflixId
            ? this.fetchIranflixSeasons(iranflixId).catch(() => [])
            : Promise.resolve([]);
        const nmTask = this.usesNext() && nextId
            ? this.fetchNextSeasons(nextId).catch(() => [])
            : Promise.resolve([]);
        const [irSeasons, nmSeasons] = await Promise.all([irTask, nmTask]);

        let seasons;
        if (this.sourceMode === "next") seasons = nmSeasons;
        else if (this.sourceMode === "iranflix") seasons = irSeasons;
        else seasons = this.mergeSeasons(irSeasons, nmSeasons);

        if (seasons.length) this.seasonsCache.set(cacheKey, { at: Date.now(), data: seasons });
        return seasons;
    }

    async fetchIranflixSeasons(seriesId) {
        const cacheKey = String(seriesId);
        const cached = this.seasonsCache.get(cacheKey);
        if (cached && Date.now() - cached.at < 10 * 60 * 1000) return cached.data;
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
                        id: src.id || 0,
                        quality: (src.quality || '').replace(/زیرنویس/g, 'فارسی').trim(),
                        type: src.type || '',
                        url: src.url,
                        origin: "iranflix"
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
                    seasons.push({ id: s.id, number: this.parseIndex(title, idx + 1), title, episodes: episodes.map((episode, index) => ({ ...episode, number: this.parseIndex(episode.title, index + 1) })) });
                }
            });
            this.seasonsCache.set(cacheKey, { at: Date.now(), data: seasons });
            return seasons;
        }
        return [];
    }

    mergeIntoCatalog(items) {
        const list = (items || []).filter(Boolean);
        if (!list.length) return;
        const matching = this.usesIranflix() && list.some(item => item.source === "next");
        const index = matching
            ? new window.BisnorMerge.MatchIndex(this.catalog.filter(item => item.source !== "next"))
            : null;

        list.forEach(item => {
            const at = this.catalog.findIndex(candidate => candidate.id === item.id);
            if (at >= 0) {
                this.catalog[at] = { ...this.catalog[at], ...item, ids: item.ids || this.catalog[at].ids };
                return;
            }
            if (index && item.source === "next") {
                const match = index.find(item);
                if (match) {
                    const position = this.catalog.indexOf(match);
                    const merged = window.BisnorMerge.mergePair(match, item);
                    merged.__hydrated = match.__hydrated;
                    this.catalog[position] = merged;
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
