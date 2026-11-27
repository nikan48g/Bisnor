/** Bisnor Auth + profile sync, aligned with Android's Supabase Auth flow. */
const BISNOR_WORKER_BASE = "https://bisnor-pwa.drive-storage-hnn.workers.dev";
// Supabase is always reached through the worker, which attaches the project key
// server-side, so no database credential ships in the client bundle. Using the worker
// URL unconditionally also keeps login working when the shell is hosted elsewhere
// (GitHub Pages, a file:// build): a relative `/sb` there has no proxy route.
const SUPABASE_CONFIG = {
    url: `${BISNOR_WORKER_BASE}/sb`
};

class SupabaseAuthService {
    constructor() {
        this.tokenKey = "bisnor_session_token";
        this.refreshTokenKey = "bisnor_refresh_token";
        this.userIdKey = "bisnor_user_id";
        this.usernameKey = "bisnor_username";
        this.avatarKey = "bisnor_avatar_id";
        this.favoritesKey = "bisnor_local_favorites";
        this.requestTimeout = 12000;
        this.refreshPromise = null;
    }

    usernameToEmail(username) { return `bisnor.${username.trim().toLowerCase()}@lerio.ir`; }
    getToken() { return localStorage.getItem(this.tokenKey) || ""; }
    getRefreshToken() { return localStorage.getItem(this.refreshTokenKey) || ""; }
    getUserId() { return localStorage.getItem(this.userIdKey) || ""; }
    getUsername() { return localStorage.getItem(this.usernameKey) || "کاربر میهمان"; }
    getAvatarId() { return localStorage.getItem(this.avatarKey) || "avatar_breakingbad"; }
    isLoggedIn() { return Boolean(this.getToken() && this.getUserId()); }

    /** Expiry (ms) of a JWT, or 0 when it cannot be read. Never logs the token. */
    tokenExpiry(token) {
        try {
            const part = String(token || "").split(".")[1];
            if (!part) return 0;
            const padded = part.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - part.length % 4) % 4);
            const claims = JSON.parse(atob(padded));
            return (Number(claims.exp) || 0) * 1000;
        } catch (_) {
            return 0;
        }
    }

    needsRefresh() {
        const token = this.getToken();
        if (!token) return false;
        const expiry = this.tokenExpiry(token);
        return expiry > 0 && expiry - Date.now() < 90 * 1000;
    }

    timeoutSignal(ms = this.requestTimeout) {
        return typeof AbortSignal !== "undefined" && AbortSignal.timeout ? AbortSignal.timeout(ms) : undefined;
    }

    /** Trades the saved refresh token for a fresh session. Concurrent callers share one request. */
    async refreshSession() {
        if (this.refreshPromise) return this.refreshPromise;
        const refreshToken = this.getRefreshToken();
        if (!refreshToken) return false;
        this.refreshPromise = (async () => {
            try {
                const response = await fetch(`${SUPABASE_CONFIG.url}/auth/v1/token?grant_type=refresh_token`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ refresh_token: refreshToken }),
                    signal: this.timeoutSignal()
                });
                if (!response.ok) return false;
                const payload = await response.json().catch(() => ({}));
                if (!payload || !payload.access_token) return false;
                return this.saveSession(payload, this.getUsername(), this.getAvatarId());
            } catch (_) {
                return false;
            } finally {
                this.refreshPromise = null;
            }
        })();
        return this.refreshPromise;
    }

    /** Valid session before a request; renews it when it is about to expire. */
    async ensureSession() {
        if (!this.isLoggedIn()) return false;
        if (!this.needsRefresh()) return true;
        return this.refreshSession();
    }

    /** Authenticated request with one transparent refresh-and-retry on 401. */
    async authFetch(path, options = {}, retried = false) {
        if (!(await this.ensureSession())) return new Response(null, { status: 401 });
        const response = await fetch(`${SUPABASE_CONFIG.url}${path}`, {
            ...options,
            headers: this.headers(true, options.headers || {}),
            signal: options.signal || this.timeoutSignal()
        });
        if (response.status === 401 && !retried) {
            if (await this.refreshSession()) return this.authFetch(path, options, true);
        }
        return response;
    }

    headers(authenticated = false, extra = {}) {
        const headers = { ...extra };
        const token = this.getToken();
        if (authenticated && token) headers.Authorization = `Bearer ${token}`;
        return headers;
    }

    saveSession(payload, username, avatarId = "avatar_breakingbad") {
        const token = payload.access_token || "";
        const userId = payload.user?.id || "";
        if (!token || !userId) return false;
        localStorage.setItem(this.tokenKey, token);
        localStorage.setItem(this.refreshTokenKey, payload.refresh_token || "");
        localStorage.setItem(this.userIdKey, userId);
        localStorage.setItem(this.usernameKey, username);
        localStorage.setItem(this.avatarKey, avatarId);
        return true;
    }

    async signup(username, password, avatarId = "avatar_breakingbad") {
        const cleanUser = (username || "").trim().toLowerCase();
        if (!/^[a-z0-9_.-]{3,32}$/.test(cleanUser)) return { success: false, message: "نام کاربری باید بین ۳ تا ۳۲ حرف انگلیسی، عدد یا خط‌تیره باشد." };
        if ((password || "").trim().length < 6) return { success: false, message: "رمز عبور باید حداقل ۶ کاراکتر باشد." };
        try {
            const response = await fetch(`${SUPABASE_CONFIG.url}/auth/v1/signup`, {
                method: "POST", headers: this.headers(false, { "Content-Type": "application/json" }),
                body: JSON.stringify({ email: this.usernameToEmail(cleanUser), password, data: { username: cleanUser, avatar_id: avatarId } })
            });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok || !this.saveSession(payload, cleanUser, avatarId)) return { success: false, message: "ثبت‌نام انجام شد اما Session فعال نشد. تأیید ایمیل باید برای حساب‌های بیسنور غیرفعال باشد." };
            await this.ensureProfile(cleanUser, avatarId);
            return { success: true, message: "ثبت‌نام با موفقیت انجام شد." };
        } catch (_) { return { success: false, message: "خطا در ارتباط با سرور بیسنور." }; }
    }

    async login(username, password) {
        const cleanUser = (username || "").trim().toLowerCase();
        if (!cleanUser || !(password || "").trim()) return { success: false, message: "نام کاربری و رمز عبور را وارد کنید." };
        try {
            const response = await fetch(`${SUPABASE_CONFIG.url}/auth/v1/token?grant_type=password`, {
                method: "POST", headers: this.headers(false, { "Content-Type": "application/json" }),
                body: JSON.stringify({ email: this.usernameToEmail(cleanUser), password })
            });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok || !this.saveSession(payload, cleanUser)) return { success: false, message: "نام کاربری یا رمز عبور نادرست است." };
            await this.syncDown();
            return { success: true, message: "ورود با موفقیت انجام شد!" };
        } catch (_) { return { success: false, message: "خطا در اتصال به اینترنت یا سرور ابری." }; }
    }

    async ensureProfile(username, avatarId) {
        const response = await this.authFetch(`/rest/v1/profiles`, {
            method: "POST", headers: { "Content-Type": "application/json", Prefer: "resolution=ignore-duplicates" },
            body: JSON.stringify({ user_id: this.getUserId(), username, avatar_id: avatarId })
        });
        if (!response.ok && response.status !== 409) throw new Error("profile-create-failed");
    }

    logout() {
        fetch(`${SUPABASE_CONFIG.url}/auth/v1/logout`, { method: "POST", headers: this.headers(true) }).catch(() => {});
        [this.tokenKey, this.refreshTokenKey, this.userIdKey, this.usernameKey, this.avatarKey].forEach(key => localStorage.removeItem(key));
    }

    getFavorites() { try { return JSON.parse(localStorage.getItem(this.favoritesKey) || "[]"); } catch (_) { return []; } }
    saveFavorites(favorites) { localStorage.setItem(this.favoritesKey, JSON.stringify(favorites)); if (this.isLoggedIn()) this.syncUp().catch(() => {}); }
    toggleFavorite(media) {
        const favorites = this.getFavorites();
        const index = favorites.findIndex(item => String(item.id) === String(media.id));
        if (index >= 0) { favorites.splice(index, 1); this.saveFavorites(favorites); return false; }
        favorites.unshift(media); this.saveFavorites(favorites); return true;
    }
    isFavorite(mediaId) { return this.getFavorites().some(item => String(item.id) === String(mediaId)); }

    async compress(value) {
        if (!("CompressionStream" in window)) throw new Error("compression-not-supported");
        const bytes = new Uint8Array(await new Response(new Blob([value]).stream().pipeThrough(new CompressionStream("gzip"))).arrayBuffer());
        let binary = ""; bytes.forEach(byte => { binary += String.fromCharCode(byte); }); return btoa(binary);
    }
    async decompress(value) {
        if (!value) return "";
        if (!("DecompressionStream" in window)) throw new Error("decompression-not-supported");
        const bytes = Uint8Array.from(atob(value), char => char.charCodeAt(0));
        return new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream("gzip"))).text();
    }

    async syncUp() {
        if (!this.isLoggedIn()) return false;
        const response = await this.authFetch(`/rest/v1/profiles?user_id=eq.${encodeURIComponent(this.getUserId())}`, {
            method: "PATCH", headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ avatar_id: this.getAvatarId(), favorites_data: await this.compress(JSON.stringify(this.getFavorites())) })
        });
        return response.ok;
    }
    async syncDown() {
        if (!this.isLoggedIn()) return false;
        const response = await this.authFetch(`/rest/v1/profiles?user_id=eq.${encodeURIComponent(this.getUserId())}&select=avatar_id,favorites_data`);
        if (!response.ok) return false;
        const profile = (await response.json())[0];
        if (!profile) return false;
        if (profile.avatar_id) localStorage.setItem(this.avatarKey, profile.avatar_id);
        if (profile.favorites_data) {
            const favorites = JSON.parse(await this.decompress(profile.favorites_data));
            if (Array.isArray(favorites)) localStorage.setItem(this.favoritesKey, JSON.stringify(favorites));
        }
        return true;
    }
    async setAvatarId(avatarId) { localStorage.setItem(this.avatarKey, avatarId); if (this.isLoggedIn()) await this.syncUp(); }
}

window.supabaseAuth = new SupabaseAuthService();
