/** Bisnor Auth + profile sync, aligned with Android's Supabase Auth flow. */
const SUPABASE_CONFIG = {
    url: "https://flhchqkuubuubzxyktnp.supabase.co",
    anonKey: "***REMOVED***"
};

class SupabaseAuthService {
    constructor() {
        this.tokenKey = "bisnor_session_token";
        this.refreshTokenKey = "bisnor_refresh_token";
        this.userIdKey = "bisnor_user_id";
        this.usernameKey = "bisnor_username";
        this.avatarKey = "bisnor_avatar_id";
        this.favoritesKey = "bisnor_local_favorites";
    }

    usernameToEmail(username) { return `bisnor.${username.trim().toLowerCase()}@lerio.ir`; }
    getToken() { return localStorage.getItem(this.tokenKey) || ""; }
    getUserId() { return localStorage.getItem(this.userIdKey) || ""; }
    getUsername() { return localStorage.getItem(this.usernameKey) || "Ú©Ø§Ø±Ø¨Ø± Ù…ÛŒÙ‡Ù…Ø§Ù†"; }
    getAvatarId() { return localStorage.getItem(this.avatarKey) || "avatar_breakingbad"; }
    isLoggedIn() { return Boolean(this.getToken() && this.getUserId()); }

    headers(authenticated = false, extra = {}) {
        return { apikey: SUPABASE_CONFIG.anonKey, Authorization: `Bearer ${authenticated ? this.getToken() : SUPABASE_CONFIG.anonKey}`, ...extra };
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
        if (!/^[a-z0-9_.-]{3,32}$/.test(cleanUser)) return { success: false, message: "Ù†Ø§Ù… Ú©Ø§Ø±Ø¨Ø±ÛŒ Ø¨Ø§ÛŒØ¯ Ø¨ÛŒÙ† Û³ ØªØ§ Û³Û² Ø­Ø±Ù Ø§Ù†Ú¯Ù„ÛŒØ³ÛŒØŒ Ø¹Ø¯Ø¯ ÛŒØ§ Ø®Ø·â€ŒØªÛŒØ±Ù‡ Ø¨Ø§Ø´Ø¯." };
        if ((password || "").trim().length < 6) return { success: false, message: "Ø±Ù…Ø² Ø¹Ø¨ÙˆØ± Ø¨Ø§ÛŒØ¯ Ø­Ø¯Ø§Ù‚Ù„ Û¶ Ú©Ø§Ø±Ø§Ú©ØªØ± Ø¨Ø§Ø´Ø¯." };
        try {
            const response = await fetch(`${SUPABASE_CONFIG.url}/auth/v1/signup`, {
                method: "POST", headers: this.headers(false, { "Content-Type": "application/json" }),
                body: JSON.stringify({ email: this.usernameToEmail(cleanUser), password, data: { username: cleanUser, avatar_id: avatarId } })
            });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok || !this.saveSession(payload, cleanUser, avatarId)) return { success: false, message: "Ø«Ø¨Øªâ€ŒÙ†Ø§Ù… Ø§Ù†Ø¬Ø§Ù… Ø´Ø¯ Ø§Ù…Ø§ Session ÙØ¹Ø§Ù„ Ù†Ø´Ø¯. ØªØ£ÛŒÛŒØ¯ Ø§ÛŒÙ…ÛŒÙ„ Ø¨Ø§ÛŒØ¯ Ø¨Ø±Ø§ÛŒ Ø­Ø³Ø§Ø¨â€ŒÙ‡Ø§ÛŒ Ø¨ÛŒØ³Ù†ÙˆØ± ØºÛŒØ±ÙØ¹Ø§Ù„ Ø¨Ø§Ø´Ø¯." };
            await this.ensureProfile(cleanUser, avatarId);
            return { success: true, message: "Ø«Ø¨Øªâ€ŒÙ†Ø§Ù… Ø¨Ø§ Ù…ÙˆÙÙ‚ÛŒØª Ø§Ù†Ø¬Ø§Ù… Ø´Ø¯." };
        } catch (_) { return { success: false, message: "Ø®Ø·Ø§ Ø¯Ø± Ø§Ø±ØªØ¨Ø§Ø· Ø¨Ø§ Ø³Ø±ÙˆØ± Ø¨ÛŒØ³Ù†ÙˆØ±." }; }
    }

    async login(username, password) {
        const cleanUser = (username || "").trim().toLowerCase();
        if (!cleanUser || !(password || "").trim()) return { success: false, message: "Ù†Ø§Ù… Ú©Ø§Ø±Ø¨Ø±ÛŒ Ùˆ Ø±Ù…Ø² Ø¹Ø¨ÙˆØ± Ø±Ø§ ÙˆØ§Ø±Ø¯ Ú©Ù†ÛŒØ¯." };
        try {
            const response = await fetch(`${SUPABASE_CONFIG.url}/auth/v1/token?grant_type=password`, {
                method: "POST", headers: this.headers(false, { "Content-Type": "application/json" }),
                body: JSON.stringify({ email: this.usernameToEmail(cleanUser), password })
            });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok || !this.saveSession(payload, cleanUser)) return { success: false, message: "Ù†Ø§Ù… Ú©Ø§Ø±Ø¨Ø±ÛŒ ÛŒØ§ Ø±Ù…Ø² Ø¹Ø¨ÙˆØ± Ù†Ø§Ø¯Ø±Ø³Øª Ø§Ø³Øª." };
            await this.syncDown();
            return { success: true, message: "ÙˆØ±ÙˆØ¯ Ø¨Ø§ Ù…ÙˆÙÙ‚ÛŒØª Ø§Ù†Ø¬Ø§Ù… Ø´Ø¯!" };
        } catch (_) { return { success: false, message: "Ø®Ø·Ø§ Ø¯Ø± Ø§ØªØµØ§Ù„ Ø¨Ù‡ Ø§ÛŒÙ†ØªØ±Ù†Øª ÛŒØ§ Ø³Ø±ÙˆØ± Ø§Ø¨Ø±ÛŒ." }; }
    }

    async ensureProfile(username, avatarId) {
        const response = await fetch(`${SUPABASE_CONFIG.url}/rest/v1/profiles`, {
            method: "POST", headers: this.headers(true, { "Content-Type": "application/json", Prefer: "resolution=ignore-duplicates" }),
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
        const response = await fetch(`${SUPABASE_CONFIG.url}/rest/v1/profiles?user_id=eq.${encodeURIComponent(this.getUserId())}`, {
            method: "PATCH", headers: this.headers(true, { "Content-Type": "application/json" }),
            body: JSON.stringify({ avatar_id: this.getAvatarId(), favorites_data: await this.compress(JSON.stringify(this.getFavorites())) })
        });
        return response.ok;
    }
    async syncDown() {
        if (!this.isLoggedIn()) return false;
        const response = await fetch(`${SUPABASE_CONFIG.url}/rest/v1/profiles?user_id=eq.${encodeURIComponent(this.getUserId())}&select=avatar_id,favorites_data`, { headers: this.headers(true) });
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
