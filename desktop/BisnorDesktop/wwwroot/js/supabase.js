/**
 * Bisnor Desktop - Supabase Client & Auth Manager
 * Manages Supabase Auth, Profiles, and Cloud Favorites Sync with local offline fallback.
 */

const SUPABASE_CONFIG = {
    url: "https://flhchqkuubuubzxyktnp.supabase.co",
    anonKey: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZsaGNocWt1dWJ1dWJ6eHlrdG5wIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDY2Njk4MTAsImV4cCI6MjA2MjI0NTgxMH0.l5-lA-67c7e0f2v6a12m6q7r5a-67b7e6f8v4k4a-78"
};

class SupabaseAuthService {
    constructor() {
        this.tokenKey = "bisnor_session_token";
        this.userIdKey = "bisnor_user_id";
        this.usernameKey = "bisnor_username";
        this.avatarKey = "bisnor_avatar_id";
        this.favoritesKey = "bisnor_local_favorites";
    }

    getToken() {
        return localStorage.getItem(this.tokenKey) || "";
    }

    getUserId() {
        return localStorage.getItem(this.userIdKey) || "";
    }

    getUsername() {
        return localStorage.getItem(this.usernameKey) || "کاربر میهمان";
    }

    getAvatarId() {
        return localStorage.getItem(this.avatarKey) || "avatar_breakingbad";
    }

    isLoggedIn() {
        return Boolean(this.getToken() && this.getUserId());
    }

    internalEmail(username) {
        return `${username.trim().toLowerCase()}@accounts.bisnor.local`;
    }

    async signup(username, password, avatarId = "avatar_breakingbad") {
        if (!username || username.length < 3) {
            return { success: false, message: "نام کاربری باید حداقل ۳ کاراکتر باشد." };
        }
        if (!password || password.length < 8) {
            return { success: false, message: "رمز عبور باید حداقل ۸ کاراکتر باشد." };
        }

        const email = this.internalEmail(username);
        try {
            const resp = await fetch(`${SUPABASE_CONFIG.url}/auth/v1/signup`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "apikey": SUPABASE_CONFIG.anonKey
                },
                body: JSON.stringify({
                    email,
                    password,
                    data: {
                        username,
                        avatar_id: avatarId
                    }
                })
            });

            const data = await resp.json();
            if (resp.ok && data.access_token) {
                this.saveSession(data.access_token, data.user?.id || "", username, avatarId);
                return { success: true, message: "حساب کاربری با موفقیت ساخته شد." };
            } else if (resp.ok && data.id) {
                // Email confirmation might be needed or user created
                return { success: true, message: "ثبت‌نام انجام شد. اکنون وارد شوید." };
            } else {
                return { success: false, message: data.msg || data.error_description || "ثبت‌نام ناموفق بود." };
            }
        } catch (e) {
            // Local mode fallback
            this.saveSession("offline_token_" + Date.now(), "usr_local_" + Date.now(), username, avatarId);
            return { success: true, message: "حساب محلی روی سیستم شما با موفقیت ایجاد شد." };
        }
    }

    async login(username, password) {
        if (!username || !password) {
            return { success: false, message: "لطفاً نام کاربری و رمز عبور را وارد کنید." };
        }

        const email = this.internalEmail(username);
        try {
            const resp = await fetch(`${SUPABASE_CONFIG.url}/auth/v1/token?grant_type=password`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "apikey": SUPABASE_CONFIG.anonKey
                },
                body: JSON.stringify({
                    email,
                    password
                })
            });

            const data = await resp.json();
            if (resp.ok && data.access_token) {
                const userId = data.user?.id || "";
                const avatarId = data.user?.user_metadata?.avatar_id || "avatar_breakingbad";
                this.saveSession(data.access_token, userId, username, avatarId);
                await this.syncDown();
                return { success: true, message: "ورود با موفقیت انجام شد." };
            } else {
                return { success: false, message: data.error_description || "نام کاربری یا رمز عبور اشتباه است." };
            }
        } catch (e) {
            // Offline demo login
            this.saveSession("offline_token_demo", "usr_offline", username, "avatar_breakingbad");
            return { success: true, message: "ورود به صورت محلی انجام شد." };
        }
    }

    saveSession(token, userId, username, avatarId) {
        localStorage.setItem(this.tokenKey, token);
        localStorage.setItem(this.userIdKey, userId);
        localStorage.setItem(this.usernameKey, username);
        localStorage.setItem(this.avatarKey, avatarId);
    }

    logout() {
        localStorage.removeItem(this.tokenKey);
        localStorage.removeItem(this.userIdKey);
        localStorage.removeItem(this.usernameKey);
    }

    setAvatarId(avatarId) {
        localStorage.setItem(this.avatarKey, avatarId);
        if (this.isLoggedIn()) {
            this.syncUp();
        }
    }

    // Favorites Sync
    getFavorites() {
        try {
            const raw = localStorage.getItem(this.favoritesKey);
            return raw ? JSON.parse(raw) : [];
        } catch {
            return [];
        }
    }

    saveFavorites(favorites) {
        localStorage.setItem(this.favoritesKey, JSON.stringify(favorites));
        if (this.isLoggedIn()) {
            this.syncUp();
        }
    }

    toggleFavorite(media) {
        const favs = this.getFavorites();
        const index = favs.findIndex(x => x.id === media.id);
        if (index >= 0) {
            favs.splice(index, 1);
            this.saveFavorites(favs);
            return false;
        } else {
            favs.unshift(media);
            this.saveFavorites(favs);
            return true;
        }
    }

    isFavorite(mediaId) {
        const favs = this.getFavorites();
        return favs.some(x => x.id === parseInt(mediaId, 10));
    }

    async syncUp() {
        if (!this.isLoggedIn()) return;
        const userId = this.getUserId();
        const token = this.getToken();
        const favs = this.getFavorites();
        const avatarId = this.getAvatarId();

        try {
            await fetch(`${SUPABASE_CONFIG.url}/rest/v1/profiles?user_id=eq.${userId}`, {
                method: "PATCH",
                headers: {
                    "Content-Type": "application/json",
                    "apikey": SUPABASE_CONFIG.anonKey,
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify({
                    avatar_id: avatarId,
                    favorites_data: btoa(unescape(encodeURIComponent(JSON.stringify(favs))))
                })
            });
        } catch (e) {
            console.warn("SyncUp failed (offline):", e);
        }
    }

    async syncDown() {
        if (!this.isLoggedIn()) return;
        const userId = this.getUserId();
        const token = this.getToken();

        try {
            const resp = await fetch(`${SUPABASE_CONFIG.url}/rest/v1/profiles?user_id=eq.${userId}&select=*`, {
                headers: {
                    "apikey": SUPABASE_CONFIG.anonKey,
                    "Authorization": `Bearer ${token}`
                }
            });
            if (resp.ok) {
                const rows = await resp.json();
                if (rows && rows.length > 0) {
                    const prof = rows[0];
                    if (prof.avatar_id) {
                        localStorage.setItem(this.avatarKey, prof.avatar_id);
                    }
                    if (prof.favorites_data) {
                        try {
                            const raw = decodeURIComponent(escape(atob(prof.favorites_data)));
                            const parsed = JSON.parse(raw);
                            if (Array.isArray(parsed)) {
                                localStorage.setItem(this.favoritesKey, JSON.stringify(parsed));
                            }
                        } catch (err) {
                            console.warn("Error decoding favorites_data:", err);
                        }
                    }
                }
            }
        } catch (e) {
            console.warn("SyncDown failed (offline):", e);
        }
    }
}

window.supabaseAuth = new SupabaseAuthService();
