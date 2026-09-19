/**
 * Bisnor Desktop - Supabase Client & Auth Manager
 * Manages Supabase Auth, Profiles, and Cloud Favorites Sync with local offline fallback.
 */

const SUPABASE_CONFIG = {
    url: "https://flhchqkuubuubzxyktnp.supabase.co",
    anonKey: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZsaGNocWt1dWJ1dWJ6eHlrdG5wIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg0MTAxNTcsImV4cCI6MjEwMzk4NjE1N30.ETvgYM4nHQ7gUfbPxFN34z280-wpwG2pHpWSOq86ozs"
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
        const u = localStorage.getItem(this.usernameKey);
        return Boolean(u && u.trim().length > 0);
    }

    async hashPassword(password) {
        const encoder = new TextEncoder();
        const data = encoder.encode(password.trim());
        const hashBuffer = await crypto.subtle.digest("SHA-256", data);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        return hashArray.map(b => b.toString(16).padStart(2, "0")).join("");
    }

    async signup(username, password, avatarId = "avatar_breakingbad") {
        const cleanUser = (username || "").trim().toLowerCase();
        const cleanPass = (password || "").trim();

        if (!/^[a-z0-9_.-]{3,32}$/.test(cleanUser)) {
            return { success: false, message: "نام کاربری باید بین ۳ تا ۳۲ حرف انگلیسی، عدد یا خط‌تیره باشد." };
        }
        if (cleanPass.length < 4) {
            return { success: false, message: "رمز عبور باید حداقل ۴ کاراکتر باشد." };
        }

        try {
            // Check if user already exists
            const checkResp = await fetch(`${SUPABASE_CONFIG.url}/rest/v1/users?username=eq.${cleanUser}&select=*`, {
                headers: {
                    "apikey": SUPABASE_CONFIG.anonKey,
                    "Authorization": `Bearer ${SUPABASE_CONFIG.anonKey}`
                }
            });

            if (checkResp.ok) {
                const existing = await checkResp.json();
                if (Array.isArray(existing) && existing.length > 0) {
                    return { success: false, message: "این نام کاربری قبلاً ثبت شده است." };
                }
            }

            const passHash = await this.hashPassword(cleanPass);
            const now = Date.now();
            const payload = {
                username: cleanUser,
                password_hash: passHash,
                avatar_id: avatarId || "avatar_breakingbad",
                created_at: now
            };

            const insertResp = await fetch(`${SUPABASE_CONFIG.url}/rest/v1/users`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "apikey": SUPABASE_CONFIG.anonKey,
                    "Authorization": `Bearer ${SUPABASE_CONFIG.anonKey}`,
                    "Prefer": "return=minimal"
                },
                body: JSON.stringify(payload)
            });

            if (insertResp.ok || insertResp.status === 201) {
                this.saveSession("token_" + cleanUser, cleanUser, cleanUser, avatarId);
                await this.syncUp();
                return { success: true, message: "حساب کاربری با موفقیت ساخته شد و وارد شدید!" };
            } else {
                const errData = await insertResp.json().catch(() => ({}));
                return { success: false, message: errData.message || "خطا در برقراری ارتباط با سرور." };
            }
        } catch (e) {
            console.error("Supabase signup error:", e);
            // Fallback to local offline session
            this.saveSession("offline_token_" + Date.now(), cleanUser, cleanUser, avatarId);
            return { success: true, message: "حساب محلی روی ویندوز با موفقیت ایجاد شد." };
        }
    }

    async login(username, password) {
        const cleanUser = (username || "").trim().toLowerCase();
        const cleanPass = (password || "").trim();

        if (!cleanUser || !cleanPass) {
            return { success: false, message: "لطفاً نام کاربری و رمز عبور را وارد کنید." };
        }

        try {
            const resp = await fetch(`${SUPABASE_CONFIG.url}/rest/v1/users?username=eq.${cleanUser}&select=*`, {
                headers: {
                    "apikey": SUPABASE_CONFIG.anonKey,
                    "Authorization": `Bearer ${SUPABASE_CONFIG.anonKey}`
                }
            });

            if (resp.ok) {
                const rows = await resp.json();
                if (!Array.isArray(rows) || rows.length === 0) {
                    return { success: false, message: `کاربری با نام «${cleanUser}» یافت نشد.` };
                }

                const userRecord = rows[0];
                const expectedHash = await this.hashPassword(cleanPass);
                if (userRecord.password_hash !== expectedHash) {
                    return { success: false, message: "رمز عبور وارد شده نادرست است." };
                }

                const avatar = userRecord.avatar_id || "avatar_breakingbad";
                this.saveSession("token_" + cleanUser, cleanUser, cleanUser, avatar);
                await this.syncDown(userRecord);
                return { success: true, message: "ورود با موفقیت انجام شد! حساب شما همگام‌سازی گردید." };
            } else {
                return { success: false, message: "خطا در اتصال به سرور بیسنور." };
            }
        } catch (e) {
            console.error("Supabase login error:", e);
            return { success: false, message: "خطا در اتصال به اینترنت یا سرور ابری." };
        }
    }

    saveSession(token, userId, username, avatarId) {
        localStorage.setItem(this.tokenKey, token);
        localStorage.setItem(this.userIdKey, userId);
        localStorage.setItem(this.usernameKey, username);
        localStorage.setItem(this.avatarKey, avatarId || "avatar_breakingbad");
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
        const username = this.getUsername();
        const favs = this.getFavorites();
        const avatarId = this.getAvatarId();

        try {
            // Encode favorites as Base64 JSON (matching Android string storage)
            const jsonStr = JSON.stringify(favs);
            let encodedFavs = "";
            try {
                encodedFavs = btoa(unescape(encodeURIComponent(jsonStr)));
            } catch (_) {
                encodedFavs = jsonStr;
            }

            const payload = {
                avatar_id: avatarId,
                favorites_data: encodedFavs,
                updated_at: Date.now()
            };

            await fetch(`${SUPABASE_CONFIG.url}/rest/v1/users?username=eq.${username}`, {
                method: "PATCH",
                headers: {
                    "Content-Type": "application/json",
                    "apikey": SUPABASE_CONFIG.anonKey,
                    "Authorization": `Bearer ${SUPABASE_CONFIG.anonKey}`
                },
                body: JSON.stringify(payload)
            });
        } catch (e) {
            console.warn("SyncUp failed:", e);
        }
    }

    async syncDown(cachedRecord = null) {
        if (!this.isLoggedIn()) return;
        const username = this.getUsername();

        try {
            let record = cachedRecord;
            if (!record) {
                const resp = await fetch(`${SUPABASE_CONFIG.url}/rest/v1/users?username=eq.${username}&select=*`, {
                    headers: {
                        "apikey": SUPABASE_CONFIG.anonKey,
                        "Authorization": `Bearer ${SUPABASE_CONFIG.anonKey}`
                    }
                });
                if (resp.ok) {
                    const rows = await resp.json();
                    if (rows && rows.length > 0) record = rows[0];
                }
            }

            if (record) {
                if (record.avatar_id) {
                    localStorage.setItem(this.avatarKey, record.avatar_id);
                }
                if (record.favorites_data) {
                    try {
                        let raw = "";
                        try {
                            raw = decodeURIComponent(escape(atob(record.favorites_data)));
                        } catch (_) {
                            raw = record.favorites_data;
                        }
                        const parsed = JSON.parse(raw);
                        if (Array.isArray(parsed) && parsed.length > 0) {
                            localStorage.setItem(this.favoritesKey, JSON.stringify(parsed));
                        }
                    } catch (err) {
                        console.warn("Error decoding favorites_data:", err);
                    }
                }
            }
        } catch (e) {
            console.warn("SyncDown failed:", e);
        }
    }
}

window.supabaseAuth = new SupabaseAuthService();
