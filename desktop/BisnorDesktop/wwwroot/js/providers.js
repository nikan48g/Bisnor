/**
 * Bisnor - multi-source catalogue layer.
 *
 * IranFlix is the original catalogue; Next Movie is the second provider. Both are
 * probed live. When both are enabled a title is shown once: IranFlix supplies the
 * Persian metadata/poster, while every playable link and every season/episode from
 * either provider is kept.
 */
(function () {
    "use strict";

    const WORKER_ORIGIN = "https://bisnor-pwa.drive-storage-hnn.workers.dev";
    const NM_TOKEN_TTL = 25 * 60 * 1000;

    const JUNK_TITLE = [
        "نسخه جدید", "نسخه وب", "ارسال برنامه", "پشتیبانی برنامه", "دانلود اپ",
        "کانال تلگرام", "تبلیغ", "بروزرسانی", "اپلیکیشن", "فیلترشکن",
        "v2ray", "vpn", "telegram", "t.me", "apk"
    ];

    function isJunkTitle(title) {
        const value = String(title || "").toLocaleLowerCase();
        return !value || JUNK_TITLE.some(keyword => value.includes(keyword));
    }

    function normalizeDigits(value) {
        return String(value ?? "")
            .replace(/[۰-۹]/g, digit => String("۰۱۲۳۴۵۶۷۸۹".indexOf(digit)))
            .replace(/[٠-٩]/g, digit => String("٠١٢٣٤٥٦٧٨٩".indexOf(digit)));
    }

    function normalizeTitle(raw) {
        let value = String(raw || "");
        value = value.replace(/[（(\[{].*?[)）\]}]/g, " ");
        if (value.normalize) value = value.normalize("NFKD");
        value = value.replace(/[\u0300-\u036f]/g, "");
        value = value.replace(/[\u064a\u0649]/g, "\u06cc").replace(/\u0643/g, "\u06a9").replace(/\u0629/g, "\u0647");
        value = value.replace(/[\u064b-\u0652\u0670\u200c\u200f\u200e]/g, " ");
        value = value.toLocaleLowerCase();
        value = value.replace(/[^0-9a-z\u0600-\u06ff ]+/g, " ");
        return value.replace(/\s+/g, " ").trim();
    }

    function trigrams(value) {
        const set = new Set();
        for (let i = 0; i + 3 <= value.length; i += 1) set.add(value.slice(i, i + 3));
        return set;
    }

    function trigramSimilarity(a, b) {
        const left = trigrams(a);
        const right = trigrams(b);
        if (!left.size || !right.size) return 0;
        let shared = 0;
        left.forEach(item => { if (right.has(item)) shared += 1; });
        return shared / Math.min(left.size, right.size);
    }

    function sameKind(a, b) {
        return (a.type === "serie") === (b.type === "serie");
    }

    function yearsCompatible(a, b) {
        const left = Number(a.year) || 0;
        const right = Number(b.year) || 0;
        if (!left || !right) return true;
        return Math.abs(left - right) <= 1;
    }

    /** 0 = different title, 1 = same title. Requires a matching media kind. */
    function matchScore(a, b) {
        if (!a || !b || !sameKind(a, b)) return 0;
        const left = normalizeTitle(a.title);
        const right = normalizeTitle(b.title);
        if (!left || !right) return 0;
        if (left === right) return yearsCompatible(a, b) ? 1 : 0.6;
        const shorter = left.length <= right.length ? left : right;
        const longer = left.length <= right.length ? right : left;
        if (shorter.length >= 5 && longer.includes(shorter)) return yearsCompatible(a, b) ? 0.92 : 0.5;
        if (yearsCompatible(a, b) && trigramSimilarity(left, right) >= 0.8) return 0.78;
        return 0;
    }

    class MatchIndex {
        constructor(items) {
            this.items = (items || []).filter(item => item && item.title);
            this.exact = new Map();
            this.items.forEach(item => {
                const key = `${normalizeTitle(item.title)}|${item.type === "serie" ? "series" : "movie"}`;
                if (!this.exact.has(key)) this.exact.set(key, item);
            });
        }

        find(candidate) {
            if (!candidate || !candidate.title) return null;
            const key = `${normalizeTitle(candidate.title)}|${candidate.type === "serie" ? "series" : "movie"}`;
            const exact = this.exact.get(key);
            if (exact && yearsCompatible(exact, candidate)) return exact;

            let best = null;
            let bestScore = 0;
            for (const item of this.items) {
                const score = matchScore(candidate, item);
                if (score > bestScore) {
                    best = item;
                    bestScore = score;
                }
            }
            return bestScore >= 0.75 ? best : null;
        }
    }

    class NextMovieProvider {
        constructor() {
            this.memory = new Map();
            this.token = "";
            this.tokenAt = 0;
            this.tokenPromise = null;
        }

        base() {
            const deployed = location.hostname.endsWith("workers.dev") || location.hostname.includes("bisnor");
            return `${deployed ? location.origin : WORKER_ORIGIN}/nm`;
        }

        async guestToken(force = false) {
            if (!force && this.token && Date.now() - this.tokenAt < NM_TOKEN_TTL) return this.token;
            if (!force && this.tokenPromise) return this.tokenPromise;
            this.tokenPromise = (async () => {
                try {
                    // The upstream token endpoint answers 500 intermittently, so a
                    // single failure must not leave the whole catalogue empty.
                    for (let attempt = 0; attempt < 3; attempt += 1) {
                        if (attempt) await new Promise(resolve => setTimeout(resolve, 600 * attempt));
                        try {
                            const response = await fetch(`${this.base()}/auth/guest-token`, {
                                method: "POST",
                                headers: { "Content-Type": "application/json" },
                                body: "{}",
                                signal: typeof AbortSignal !== "undefined" && AbortSignal.timeout
                                    ? AbortSignal.timeout(15000)
                                    : undefined
                            });
                            if (response.ok) {
                                const payload = await response.json();
                                const token = (payload && payload.data && payload.data.token) || "";
                                if (token) {
                                    this.token = token;
                                    this.tokenAt = Date.now();
                                    return token;
                                }
                                continue;
                            }
                            // A rejected request will not succeed on retry.
                            if (response.status >= 400 && response.status < 500 && response.status !== 429) return "";
                        } catch (_) {}
                    }
                    return "";
                } finally {
                    this.tokenPromise = null;
                }
            })();
            return this.tokenPromise;
        }

        async request(path, options = {}, retried = false) {
            const token = await this.guestToken();
            if (!token) return null;
            const headers = { Authorization: `Bearer ${token}`, Accept: "application/json" };
            if (options.body !== undefined) headers["Content-Type"] = "application/json";
            try {
                const response = await fetch(`${this.base()}${path}`, {
                    method: options.method || "GET",
                    headers,
                    body: options.body === undefined ? undefined : JSON.stringify(options.body),
                    signal: typeof AbortSignal !== "undefined" && AbortSignal.timeout
                        ? AbortSignal.timeout(20000)
                        : undefined
                });
                if (response.status === 401 && !retried) {
                    await this.guestToken(true);
                    return this.request(path, options, true);
                }
                if (!response.ok) return null;
                const payload = await response.json();
                return payload && payload.data ? payload.data : payload;
            } catch (_) {
                return null;
            }
        }

        async home() {
            if (this.memory.has("home")) return this.memory.get("home");
            const data = await this.request("/home?page=1");
            const slides = (data && data.slides) || {};
            this.memory.set("home", slides);
            return slides;
        }

        async details(id) {
            const key = `details:${id}`;
            if (this.memory.has(key)) return this.memory.get(key);
            const data = await this.request(`/movie/${id}/details`);
            if (data) this.memory.set(key, data);
            return data;
        }

        async links(id) {
            const key = `links:${id}`;
            if (this.memory.has(key)) return this.memory.get(key);
            const data = await this.request(`/movie/${id}/links`);
            if (data) this.memory.set(key, data);
            return data;
        }

        async search(title, type) {
            const body = { title: String(title || "").trim() };
            if (type) body.type = type;
            const data = await this.request("/v3/search?page=1", { method: "POST", body });
            return (data && Array.isArray(data.items)) ? data.items : [];
        }

        async browse(params) {
            const body = {};
            if (params.type) body.type = params.type;
            if (params.genreIds && params.genreIds.length) body.genre_ids = params.genreIds;
            if (params.countryIds && params.countryIds.length) body.country_ids = params.countryIds;
            body.sort_key = params.sortKey || "created_at";
            body.sort_order = "desc";
            const data = await this.request(`/v3/search?page=${params.page || 1}`, { method: "POST", body });
            return (data && Array.isArray(data.items)) ? data.items : [];
        }

        toMedia(raw) {
            if (!raw || !raw.id) return null;
            const title = String(raw.title || "").trim();
            if (isJunkTitle(title)) return null;
            const rating = Number.parseFloat(raw.imdb_rating);
            if (Number.isFinite(rating) && rating >= 9.9 && !raw.description) return null;
            return {
                id: `nm_${raw.id}`,
                nmId: raw.id,
                source: "next",
                ids: { next: raw.id },
                type: raw.type === "series" ? "serie" : "movie",
                title,
                description: raw.description || "",
                year: Number(raw.year) || 0,
                imdb: Number.isFinite(rating) ? rating : 0,
                rating: 0,
                duration: raw.duration || "",
                image: raw.poster || "",
                cover: raw.poster || "",
                genres: [],
                country: [],
                people: [],
                sources: []
            };
        }

        toSeasonList(linksPayload) {
            const seasons = (linksPayload && Array.isArray(linksPayload.seasons)) ? linksPayload.seasons : [];
            return seasons.map((season, index) => {
                const title = String(season.title || "").trim();
                const seasonNumber = extractIndex(title, "فصل", index + 1);
                const episodes = (season.episodes || []).map((episode, epIndex) => ({
                    id: `nm_${episode.id || `${season.id}_${epIndex}`}`,
                    number: extractIndex(String(episode.title || ""), "قسمت", epIndex + 1),
                    title: String(episode.title || `قسمت ${epIndex + 1}`).trim(),
                    description: episode.description || "",
                    duration: episode.duration || null,
                    image: "",
                    sources: (episode.links || []).map(link => this.toSource(link)).filter(Boolean)
                })).filter(episode => episode.sources.length);
                return { id: season.id || `nm_s_${index}`, number: seasonNumber, title: title || `فصل ${index + 1}`, episodes };
            }).filter(season => season.episodes.length);
        }

        toSource(link) {
            if (!link || !link.url) return null;
            const url = String(link.url).replace(/\\\//g, "/");
            if (!/^https?:\/\//i.test(url)) return null;
            return {
                id: link.id || 0,
                quality: String(link.quality || "کیفیت اصلی").trim(),
                type: String(link.type || "").toLowerCase(),
                url,
                origin: "next"
            };
        }
    }

    function extractIndex(text, label, fallback) {
        const digits = normalizeDigits(text || "");
        const match = digits.match(new RegExp(`${label}\\s*[:：-]?\\s*(\\d+)`));
        if (match) return Number(match[1]);
        const plain = digits.match(/(?:^|\D)(\d+)(?:\D|$)/);
        if (plain && digits.trim().length <= 12) return Number(plain[1]);
        return fallback;
    }

    function mergePair(iranflix, next) {
        const merged = { ...iranflix };
        merged.ids = { iranflix: iranflix.id, next: next.nmId };
        merged.nmId = next.nmId;
        merged.source = "both";
        merged.title = iranflix.title || next.title;
        merged.description = iranflix.description || next.description || "";
        merged.image = iranflix.image || next.image;
        merged.cover = iranflix.cover || next.cover;
        merged.imdb = iranflix.imdb || next.imdb;
        merged.year = iranflix.year || next.year;
        merged.sources = iranflix.sources || [];
        merged.nmSources = next.sources || [];
        return merged;
    }

    window.BisnorMerge = {
        normalizeTitle,
        normalizeDigits,
        matchScore,
        MatchIndex,
        mergePair,
        isJunkTitle
    };
    window.NextMovieProvider = NextMovieProvider;
})();
