/**
 * Bisnor Desktop - Main Application Controller
 * Connects UI, Media Service, Supabase, Player, Downloads, Profile, and Settings.
 */

// 11 Pop-Culture Characters (matching Android AuthManager.FUNNY_AVATARS exactly)
const CHARACTERS = [
    { id: "avatar_breakingbad", name: "والتر وایت (بریکینگ بد)", file: "avatar_breakingbad.png" },
    { id: "avatar_luffy", name: "لوفی (وان پیس)", file: "avatar_luffy.png" },
    { id: "avatar_wednesday", name: "ونزدی آدامز", file: "avatar_wednesday.png" },
    { id: "avatar_nami", name: "نامی (وان پیس)", file: "avatar_nami.png" },
    { id: "avatar_garfield", name: "گارفیلد", file: "avatar_garfield.png" },
    { id: "avatar_bluey", name: "بلویی", file: "avatar_bluey.png" },
    { id: "avatar_bingo", name: "بینگو", file: "avatar_bingo.png" },
    { id: "avatar_carmen", name: "کارمن سندیگو", file: "avatar_carmen.png" },
    { id: "avatar_film", name: "کلاکت سینما", file: "avatar_film.png" },
    { id: "avatar_theater", name: "ماسک نمایش", file: "avatar_theater.png" },
    { id: "avatar_star", name: "ستاره طلایی", file: "avatar_star.png" }
];

let deferredPwaInstallPrompt = null;
window.addEventListener("beforeinstallprompt", event => {
    event.preventDefault();
    deferredPwaInstallPrompt = event;
    window.app?.refreshInstallButton();
});

class BisnorApp {
    constructor() {
        this.isNativeDesktop = Boolean(window.chrome?.webview);
        this.isStandalonePwa = window.matchMedia?.("(display-mode: standalone)").matches || navigator.standalone === true;
        this.isMobileWeb = !this.isNativeDesktop && (navigator.userAgentData?.mobile ?? /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent));
        this.tasteStorageKey = this.isNativeDesktop ? "bisnor_taste_v1" : "bisnor_pwa_taste_v1";
        this.currentTab = "home";
        this.activeMedia = null;
        this.selectedAvatarForPicker = "avatar_breakingbad";
        this.selectedPlayerChoice = localStorage.getItem("bisnor_player") || "bisnor";
        this.authMode = "login";
        this.currentGenre = "all";
        this.searchQuery = "";
        this.favoritesSearchQuery = "";
        this.favoritesWatchFilter = "all";
        this.searchDebounceTimer = null;
        this.sortBy = "imdb";
        this.suggestedMedia = null;
        this.activePerson = null;
        this.taste = this.loadTaste();

        this.settings = {
            theme: localStorage.getItem("bisnor_theme") || "dark",
            player: localStorage.getItem("bisnor_player") || "bisnor",
            autoNext: localStorage.getItem("bisnor_auto_next") !== "false",
            autoNextMinutes: parseInt(localStorage.getItem("bisnor_auto_next_min") || "2", 10),
            contentWarning: localStorage.getItem("bisnor_content_warn") !== "false"
        };
        
        // Watch analytics state
        this.analytics = JSON.parse(localStorage.getItem("bisnor_analytics") || JSON.stringify({
            watchMinutes: 460,
            simulatedDataGb: 4.8,
            moviesWatched: 3,
            seriesEpisodesWatched: 8
        }));
    }

    init() {
        this.applyTheme(this.settings.theme);
        this.applyPlatformIdentity();
        this.setupInstallExperience();
        this.setupNavigation();
        this.setupEventListeners();
        this.loadProfile();
        this.loadSettings();
        this.renderHomeTab();
        this.renderExploreTab();
        this.renderFavoritesTab();
        this.renderAvatarPickerGrid();
        this.setupNativeMessageListener();
        this.postNative("getAppInfo");
        if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
            navigator.serviceWorker.register("sw.js").catch(() => {});
        }
    }

    loadTaste() {
        try {
            const stored = JSON.parse(localStorage.getItem(this.tasteStorageKey) || "{}");
            return { genres: stored.genres || {}, people: stored.people || {}, viewed: stored.viewed || {}, watched: stored.watched || {} };
        } catch (_) {
            return { genres: {}, people: {}, viewed: {}, watched: {} };
        }
    }

    saveTaste() {
        localStorage.setItem(this.tasteStorageKey, JSON.stringify(this.taste));
    }

    applyPlatformIdentity() {
        if (this.isNativeDesktop) return;
        document.body.classList.add("web-client");
        const text = (id, value) => {
            const element = document.getElementById(id);
            if (element) element.textContent = value;
        };
        text("platform-brand-badge", "نسخه وب • PWA برای iOS");
        text("downloads-setting-title", "مدیریت دانلودهای مرورگر");
        text("platform-about-title", "بیسنور وب (Bisnor PWA)");
        text("tv-app-version-about", "نسخه آزمایشی وب • نصب‌پذیر روی iOS، اندروید و ویندوز");
        text("detail-external-player-label", "پخش در مرورگر");
        text("player-error-desc", "این فرمت در مرورگر فعلی قابل کدگشایی نیست. می‌توانید فایل را دانلود کنید یا کیفیت دیگری را انتخاب کنید.");
    }

    setupInstallExperience() {
        if (this.isNativeDesktop) return;
        const installButton = document.getElementById("btn-install-pwa");
        const guide = document.getElementById("pwa-install-guide");
        const closeGuide = () => { if (guide) guide.style.display = "none"; };
        document.getElementById("btn-close-install-guide")?.addEventListener("click", closeGuide);
        document.getElementById("btn-install-guide-done")?.addEventListener("click", closeGuide);
        guide?.addEventListener("click", event => { if (event.target === guide) closeGuide(); });
        installButton?.addEventListener("click", async () => {
            if (deferredPwaInstallPrompt) {
                deferredPwaInstallPrompt.prompt();
                await deferredPwaInstallPrompt.userChoice;
                deferredPwaInstallPrompt = null;
                this.refreshInstallButton();
                return;
            }
            const isIos = /iPhone|iPad|iPod/i.test(navigator.userAgent);
            const title = document.getElementById("pwa-install-title");
            const description = document.getElementById("pwa-install-description");
            if (title) title.textContent = isIos ? "نصب بیسنور روی آیفون" : "نصب بیسنور روی گوشی";
            if (description) description.innerHTML = isIos
                ? "در Safari روی دکمهٔ <strong>Share</strong> بزنید و سپس <strong>Add to Home Screen</strong> را انتخاب کنید."
                : "از منوی مرورگر گزینهٔ <strong>Install app</strong> یا <strong>Add to Home screen</strong> را انتخاب کنید.";
            if (guide) guide.style.display = "flex";
        });
        window.addEventListener("appinstalled", () => {
            deferredPwaInstallPrompt = null;
            this.isStandalonePwa = true;
            this.refreshInstallButton();
        });
        this.refreshInstallButton();
    }

    refreshInstallButton() {
        const button = document.getElementById("btn-install-pwa");
        if (!button) return;
        button.style.display = this.isMobileWeb && !this.isStandalonePwa ? "inline-flex" : "none";
    }

    rememberMedia(media) {
        if (!media) return;
        (media.genres || []).forEach(genre => {
            if (genre.title) this.taste.genres[genre.title] = (this.taste.genres[genre.title] || 0) + 1;
        });
        (media.people || []).forEach(person => {
            if (person.name) this.taste.people[person.name] = (this.taste.people[person.name] || 0) + 1;
        });
        this.taste.viewed[String(media.id)] = Date.now();
        this.saveTaste();
    }

    // --- Native Windows Interop ---
    postNative(action, payload = {}) {
        if (window.chrome && window.chrome.webview) {
            window.chrome.webview.postMessage({ action, ...payload });
        } else {
            console.log("[Native Interop Simulated]", action, payload);
        }
    }

    setupNativeMessageListener() {
        if (window.chrome && window.chrome.webview) {
            window.chrome.webview.addEventListener("message", (event) => {
                const msg = event.data;
                if (!msg) return;
                if (msg.action === "appInfoResponse") {
                    const tvVersion = document.getElementById("tv-app-version-about");
                    if (tvVersion) {
                        tvVersion.textContent = `نسخه v${msg.version} (${msg.channel}) • مجهز به ${msg.runtime}`;
                    }
                } else if (msg.action === "updateCheckResult") {
                    this.handleUpdateCheckResult(msg);
                }
            });
        }
    }

    handleUpdateCheckResult(msg) {
        if (msg.hasNewVersion) {
            const accept = confirm(
                `نسخه جدید بیسنور دسکتاپ (${msg.latestVersion}) در دسترس است!\n\n` +
                `نسخه فعلی شما: v${msg.currentVersion}\n\n` +
                `روش‌های ارتقا:\n` +
                `۱. دستور ترمینال WinGet:\n   ${msg.wingetCommand}\n\n` +
                `۲. دانلود مستقیم فایل نصاب از صفحه رسمی گیت‌هاب.\n\n` +
                `آیا می‌خواهید صفحه انتشار گیت‌هاب باز شود؟`
            );
            if (accept && msg.releaseUrl) {
                this.postNative("openUrl", { url: msg.releaseUrl });
            }
        } else {
            alert(`شما از جدیدترین نسخه بیسنور دسکتاپ (v${msg.currentVersion}) استفاده می‌کنید. 🚀`);
        }
    }

    // --- Theme Management ---
    applyTheme(theme) {
        if (theme === "system") {
            const isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
            document.body.className = isDark ? "theme-dark" : "theme-light";
        } else {
            document.body.className = theme === "light" ? "theme-light" : "theme-dark";
        }
        if (!this.isNativeDesktop) document.body.classList.add("web-client");
        localStorage.setItem("bisnor_theme", theme);
        this.settings.theme = theme;

        document.querySelectorAll(".chip-theme").forEach(chip => {
            chip.classList.toggle("active", chip.getAttribute("data-theme") === theme);
        });
    }

    // --- Tab Navigation (Matches Android 1:1) ---
    setupNavigation() {
        const navItems = document.querySelectorAll(".bottom-nav-pill .nav-item");
        navItems.forEach(btn => {
            btn.addEventListener("click", () => {
                const tab = btn.getAttribute("data-tab");
                this.switchTab(tab.replace("tab-", ""));
            });
        });

        // Header Avatar shortcut -> Settings tab
        const headerAvatar = document.getElementById("header-user-avatar");
        if (headerAvatar) {
            headerAvatar.addEventListener("click", () => this.switchTab("settings"));
        }

        // Header Search shortcut -> Explore tab
        const headerSearch = document.getElementById("btn-header-search");
        if (headerSearch) {
            headerSearch.addEventListener("click", () => {
                this.switchTab("explore");
                setTimeout(() => document.getElementById("input-explore-search")?.focus(), 80);
            });
        }
    }

    switchTab(tabName) {
        this.currentTab = tabName;
        document.querySelectorAll(".tab-pane").forEach(pane => pane.classList.remove("active"));
        document.querySelectorAll(".nav-item").forEach(item => item.classList.remove("active"));

        const targetPane = document.getElementById(`tab-${tabName}`);
        const targetNav = document.querySelector(`.nav-item[data-tab="tab-${tabName}"]`);
        if (targetPane) targetPane.classList.add("active");
        if (targetNav) targetNav.classList.add("active");

        // Specific Tab hooks
        if (tabName === "home") this.renderHomeTab();
        if (tabName === "explore") this.renderExploreTab();
        if (tabName === "favorites") this.renderFavoritesTab();
        if (tabName === "settings") this.loadProfile();
    }

    // --- Event Listeners Setup ---
    setupEventListeners() {
        // Explore search input
        const searchInput = document.getElementById("input-explore-search");
        const clearBtn = document.getElementById("btn-clear-search");
        if (searchInput) {
            searchInput.addEventListener("input", (e) => {
                this.searchQuery = e.target.value;
                if (clearBtn) clearBtn.style.display = this.searchQuery ? "block" : "none";
                clearTimeout(this.searchDebounceTimer);
                if (!this.searchQuery.trim()) {
                    this.renderExploreTab();
                    return;
                }
                this.searchDebounceTimer = setTimeout(() => this.renderExploreTab(), 350);
            });
        }
        if (clearBtn) {
            clearBtn.addEventListener("click", () => {
                if (searchInput) searchInput.value = "";
                this.searchQuery = "";
                clearTimeout(this.searchDebounceTimer);
                clearBtn.style.display = "none";
                this.renderExploreTab();
            });
        }

        const favoritesSearch = document.getElementById("input-favorites-search");
        const clearFavoritesSearch = document.getElementById("btn-clear-favorites-search");
        favoritesSearch?.addEventListener("input", event => {
            this.favoritesSearchQuery = event.target.value.trim().toLocaleLowerCase();
            if (clearFavoritesSearch) clearFavoritesSearch.style.display = this.favoritesSearchQuery ? "block" : "none";
            this.renderFavoritesTab();
        });
        clearFavoritesSearch?.addEventListener("click", () => {
            if (favoritesSearch) favoritesSearch.value = "";
            this.favoritesSearchQuery = "";
            clearFavoritesSearch.style.display = "none";
            this.renderFavoritesTab();
        });
        document.querySelectorAll("#favorites-filter-chips .chip").forEach(chip => chip.addEventListener("click", event => {
            const target = event.currentTarget;
            this.favoritesWatchFilter = target.dataset.watchFilter || "all";
            document.querySelectorAll("#favorites-filter-chips .chip").forEach(item => item.classList.toggle("active", item === target));
            this.renderFavoritesTab();
        }));

        // Genre chips
        document.querySelectorAll("#genres-filter-chips .chip").forEach(chip => {
            chip.addEventListener("click", (e) => {
                document.querySelectorAll("#genres-filter-chips .chip").forEach(c => c.classList.remove("active"));
                e.currentTarget.classList.add("active");
                this.currentGenre = e.currentTarget.getAttribute("data-genre") || "all";
                this.renderExploreTab();
            });
        });

        // Sorting selector
        document.getElementById("select-sort-by")?.addEventListener("change", (e) => {
            this.sortBy = e.target.value;
            this.renderExploreTab();
        });

        // Sync Favorites button
        document.getElementById("btn-sync-favorites")?.addEventListener("click", async () => {
            if (!window.supabaseAuth.isLoggedIn()) {
                this.openAuthModal();
            } else {
                await window.supabaseAuth.syncDown();
                this.renderFavoritesTab();
                alert("واچ‌لیست شما با سرور ابری Supabase همگام شد. ✨");
            }
        });

        // Detail Modal buttons
        document.getElementById("btn-close-detail")?.addEventListener("click", () => this.closeDetailModal());
        document.getElementById("btn-close-person")?.addEventListener("click", () => this.closePersonProfile());
        document.getElementById("btn-person-follow")?.addEventListener("click", () => this.togglePersonPreference());
        document.getElementById("btn-detail-play-now")?.addEventListener("click", () => {
            if (this.activeMedia) this.playMedia(this.activeMedia);
        });
        document.getElementById("btn-detail-external-player")?.addEventListener("click", () => {
            if (this.activeMedia) {
                const streamUrl = this.getStreamUrl(this.activeMedia);
                this.postNative("launchExternalPlayer", { 
                    player: this.settings.player, 
                    url: streamUrl, 
                    title: this.activeMedia.title 
                });
            }
        });
        document.getElementById("btn-detail-favorite")?.addEventListener("click", () => {
            if (!this.activeMedia) return;
            const isFav = window.supabaseAuth.toggleFavorite(this.activeMedia);
            this.updateDetailFavButton(isFav);
            if (this.currentTab === "favorites") this.renderFavoritesTab();
        });

        // In-app Video Player controls
        const video = document.getElementById("html-video-player");
        const playPauseBtn = document.getElementById("btn-player-play-pause");
        const seekbar = document.getElementById("player-seekbar");
        const timeLabel = document.getElementById("player-time-label");

        document.getElementById("btn-close-player")?.addEventListener("click", () => this.closePlayerModal());
        document.getElementById("btn-player-open-external")?.addEventListener("click", () => {
            if (this.currentPlayingUrl) {
                const title = document.getElementById("player-media-title")?.textContent || "Bisnor Media";
                this.postNative("launchExternalPlayer", { player: this.settings.player === "bisnor" ? "vlc" : this.settings.player, url: this.currentPlayingUrl, title });
            }
        });

        // Error overlay buttons
        document.getElementById("btn-player-err-open-vlc")?.addEventListener("click", () => {
            if (this.currentPlayingUrl) {
                const title = document.getElementById("player-media-title")?.textContent || "Bisnor Media";
                this.postNative("launchExternalPlayer", { player: "vlc", url: this.currentPlayingUrl, title });
                this.closePlayerModal();
            }
        });
        document.getElementById("btn-player-err-open-default")?.addEventListener("click", () => {
            if (this.currentPlayingUrl) {
                const title = document.getElementById("player-media-title")?.textContent || "Bisnor Media";
                this.postNative("launchExternalPlayer", { player: "potplayer", url: this.currentPlayingUrl, title });
                this.closePlayerModal();
            }
        });
        document.getElementById("btn-player-err-download")?.addEventListener("click", () => {
            if (this.currentPlayingUrl) {
                this.postNative("downloadWithIDM", { url: this.currentPlayingUrl });
            }
        });
        document.getElementById("btn-player-err-close")?.addEventListener("click", () => {
            this.closePlayerModal();
        });

        playPauseBtn?.addEventListener("click", () => {
            if (!video) return;
            if (video.paused) {
                video.play();
                playPauseBtn.textContent = "⏸️";
            } else {
                video.pause();
                playPauseBtn.textContent = "▶️";
            }
        });

        document.getElementById("btn-player-forward")?.addEventListener("click", () => {
            if (video) video.currentTime = Math.min(video.currentTime + 10, video.duration || 0);
        });
        document.getElementById("btn-player-rewind")?.addEventListener("click", () => {
            if (video) video.currentTime = Math.max(video.currentTime - 10, 0);
        });
        document.getElementById("btn-player-fullscreen")?.addEventListener("click", () => {
            const container = document.querySelector(".player-container");
            if (!document.fullscreenElement) {
                container?.requestFullscreen?.();
            } else {
                document.exitFullscreen?.();
            }
        });

        if (video) {
            video.addEventListener("timeupdate", () => {
                if (seekbar && video.duration) {
                    seekbar.value = (video.currentTime / video.duration) * 100;
                    if (timeLabel) timeLabel.textContent = `${this.formatTime(video.currentTime)} / ${this.formatTime(video.duration)}`;
                }
            });
            video.addEventListener("ended", () => {
                if (playPauseBtn) playPauseBtn.textContent = "▶️";
                this.recordWatchTime(30);
                if (this.settings.autoNext) this.playNextWebEpisode();
            });
            video.addEventListener("error", (e) => {
                console.warn("[Player Video Error]", video.error, e);
                // HTML5 video could not decode this container or codec (e.g. MKV/HEVC)
                const errOverlay = document.getElementById("player-error-overlay");
                if (errOverlay) {
                    errOverlay.style.display = "flex";
                }
            });
        }

        seekbar?.addEventListener("input", (e) => {
            if (video && video.duration) {
                video.currentTime = (e.target.value / 100) * video.duration;
            }
        });

        // Settings Category 1: User Profile & Auth
        document.getElementById("btn-profile-action")?.addEventListener("click", () => this.openAuthModal());
        document.getElementById("btn-profile-change-avatar")?.addEventListener("click", () => this.openAvatarPicker());
        document.getElementById("btn-profile-logout")?.addEventListener("click", () => this.onLogout());
        document.getElementById("card-user-profile")?.addEventListener("click", (e) => {
            if (e.target.tagName === "BUTTON") return;
            if (!window.supabaseAuth.isLoggedIn()) {
                this.openAuthModal();
            } else {
                this.openAvatarPicker();
            }
        });

        // Settings Category 2: Downloads
        document.getElementById("btn-setting-downloads")?.addEventListener("click", () => {
            this.postNative("openFolder");
        });
        document.getElementById("switch-segmented-download")?.addEventListener("change", (e) => {
            localStorage.setItem("bisnor_segmented_dl", e.target.checked);
        });

        // Settings Category 3: Themes & Player Choice
        document.querySelectorAll(".chip-theme").forEach(btn => {
            btn.addEventListener("click", () => {
                const th = btn.getAttribute("data-theme");
                this.applyTheme(th);
            });
        });

        document.getElementById("switch-auto-next")?.addEventListener("change", (e) => {
            this.settings.autoNext = e.target.checked;
            localStorage.setItem("bisnor_auto_next", e.target.checked);
        });

        document.getElementById("switch-content-warning")?.addEventListener("change", (e) => {
            this.settings.contentWarning = e.target.checked;
            localStorage.setItem("bisnor_content_warn", e.target.checked);
        });

        document.getElementById("btn-setting-player-choice")?.addEventListener("click", () => {
            this.openPlayerChoiceModal();
        });

        document.getElementById("btn-setting-auto-next-time")?.addEventListener("click", () => {
            this.openAutoNextTimeModal();
        });

        // Settings Category 4: About & GitHub
        document.getElementById("btn-check-update")?.addEventListener("click", () => {
            this.postNative("checkUpdates");
        });

        document.getElementById("btn-open-github")?.addEventListener("click", () => {
            this.postNative("openUrl", { url: "https://github.com/nikan48g/Bisnor" });
        });

        // Auth Form
        document.getElementById("btn-close-auth")?.addEventListener("click", () => this.closeAuthModal());
        document.getElementById("btn-switch-auth-mode")?.addEventListener("click", () => this.toggleAuthMode());
        document.getElementById("form-auth")?.addEventListener("submit", (e) => this.onAuthSubmit(e));

        // Avatar Picker
        document.getElementById("btn-close-avatar-picker")?.addEventListener("click", () => this.closeAvatarPicker());
        document.getElementById("btn-save-avatar")?.addEventListener("click", () => this.saveAvatar());

        // Player Choice Modal
        document.getElementById("btn-close-player-choice")?.addEventListener("click", () => this.closePlayerChoiceModal());
        document.querySelectorAll(".player-option-card").forEach(card => {
            card.addEventListener("click", () => {
                document.querySelectorAll(".player-option-card").forEach(c => c.classList.remove("active"));
                card.classList.add("active");
                this.selectedPlayerChoice = card.getAttribute("data-player");
            });
        });
        document.getElementById("btn-save-player-choice")?.addEventListener("click", () => {
            this.settings.player = this.selectedPlayerChoice;
            localStorage.setItem("bisnor_player", this.selectedPlayerChoice);
            this.updatePlayerSettingLabel();
            this.closePlayerChoiceModal();
        });

        // Auto Next Time Modal
        document.getElementById("btn-close-auto-next-time")?.addEventListener("click", () => this.closeAutoNextTimeModal());
        document.querySelectorAll(".time-opt-btn").forEach(btn => {
            btn.addEventListener("click", () => {
                document.querySelectorAll(".time-opt-btn").forEach(b => b.classList.remove("active"));
                btn.classList.add("active");
                const mins = parseInt(btn.getAttribute("data-minutes"), 10);
                const label = document.getElementById("tv-auto-next-minutes-label");
                if (mins === 0) {
                    this.settings.autoNext = false;
                    localStorage.setItem("bisnor_auto_next", "false");
                    if (label) label.textContent = "غیرفعال";
                } else {
                    this.settings.autoNext = true;
                    this.settings.autoNextMinutes = mins;
                    localStorage.setItem("bisnor_auto_next", "true");
                    localStorage.setItem("bisnor_auto_next_min", mins);
                    if (label) label.textContent = `${mins} دقیقه مانده به پایان`;
                }
                setTimeout(() => this.closeAutoNextTimeModal(), 200);
            });
        });

        // Random Suggest Modal
        document.getElementById("btn-close-random")?.addEventListener("click", () => this.closeRandomModal());
        document.getElementById("btn-random-next")?.addEventListener("click", () => this.showSmartTasteSuggestion());
        document.getElementById("btn-random-watch-now")?.addEventListener("click", () => {
            if (this.suggestedMedia) {
                this.closeRandomModal();
                this.openDetailModal(this.suggestedMedia);
            }
        });
    }

    formatTime(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
    }

    // --- Media Card Component (With Resilient Fallback SVG) ---
    createMediaCard(media) {
        const card = document.createElement("div");
        card.className = "media-card";
        card.tabIndex = 0;

        const genreTitle = (media.genres && media.genres[0]) ? media.genres[0].title : 'فیلم';
        const fallbackSvg = window.getPosterFallbackSvg ? 
            window.getPosterFallbackSvg(media.title, genreTitle, media.year || 2024, (media.imdb || 8.0).toFixed(1)) : 
            "assets/logo.png";

        card.innerHTML = `
            <div class="card-poster-box">
                <img src="${media.image || media.cover || fallbackSvg}" 
                     alt="${media.title}" 
                     loading="lazy" 
                     onerror="this.onerror=null; this.src='${fallbackSvg}';">
                <span class="card-rating-badge">⭐ ${(media.imdb || 8.0).toFixed(1)}</span>
                <span class="card-type-badge">${media.type === 'serie' ? 'سریال' : 'سینمایی'}</span>
            </div>
            <div class="card-info">
                <h4 class="card-title">${media.title}</h4>
                <div class="card-sub">
                    <span>${media.year || 2024}</span> • <span>${genreTitle}</span>
                </div>
            </div>
        `;
        card.addEventListener("click", () => this.openDetailModal(media));
        return card;
    }

    // --- Home Tab ---
    async renderHomeTab() {
        const updateHero = (hero) => {
            if (!hero) return;
            const heroCard = document.getElementById("hero-banner-card");
            if (heroCard) {
                const genreTitle = (hero.genres && hero.genres[0]) ? hero.genres[0].title : 'اکشن';
                const heroFallback = window.getPosterFallbackSvg ? 
                    window.getPosterFallbackSvg(hero.title, genreTitle, hero.year, (hero.imdb || 8.0).toFixed(1)) : 
                    "assets/logo.png";

                heroCard.style.backgroundImage = `url('${hero.cover || hero.image || heroFallback}')`;
                heroCard.innerHTML = `
                    <div class="hero-overlay-gradient"></div>
                    <div class="hero-info-wrap">
                        <div class="hero-rating-badge">⭐ ${(hero.imdb || 8.0).toFixed(1)} IMDb • اثر برتر روز</div>
                        <h1 class="hero-title">${hero.title}</h1>
                        <div class="hero-genre-year">${(hero.genres || []).map(g => g.title).join('، ')} • سال ${hero.year || 2026}</div>
                        <p class="hero-desc">${hero.description || ''}</p>
                        <button class="btn-play-primary" id="btn-hero-play-main">
                            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                            <span>تماشای آنلاین رایگان</span>
                        </button>
                    </div>
                `;
                heroCard.querySelector("#btn-hero-play-main")?.addEventListener("click", (e) => {
                    e.stopPropagation();
                    this.playMedia(hero);
                });
                heroCard.onclick = () => this.openDetailModal(hero);
            }
        };

        const renderRow = (rowId, items) => {
            const row = document.getElementById(rowId);
            if (row && Array.isArray(items) && items.length > 0) {
                row.innerHTML = "";
                items.forEach(m => row.appendChild(this.createMediaCard(m)));
            }
        };

        // Never display the old bundled snapshot as though it were live content.
        const initialHero = window.mediaService.getHeroFeaturedSync ? 
            window.mediaService.getHeroFeaturedSync() : null;
        updateHero(initialHero);

        // Fetch live data in parallel; empty rows are preferable to false metadata.
        Promise.allSettled([
            window.mediaService.getLatestMovies().then(latest => {
                if (latest && latest.length > 0) {
                    renderRow("row-latest-movies", latest);
                    updateHero(latest[0]);
                }
            }),
            window.mediaService.getPopularSeries().then(series => {
                if (series && series.length > 0) {
                    renderRow("row-popular-series", series);
                }
            }),
            window.mediaService.getTopImdb().then(top => {
                if (top && top.length > 0) {
                    renderRow("row-top-imdb", top);
                }
            }),
            window.mediaService.getAnimations().then(anim => {
                if (anim && anim.length > 0) {
                    renderRow("row-animations", anim);
                }
            })
        ]).then(() => {
            this.renderForYou();
            console.log("[BisnorMedia] All home sections synchronized with live Iranflix servers.");
        }).catch(err => {
            console.warn("[BisnorMedia] Live sync notice:", err);
        });
    }

    renderForYou() {
        const section = document.getElementById("for-you-section");
        const row = document.getElementById("row-for-you");
        const subtitle = document.getElementById("for-you-subtitle");
        if (!section || !row) return;
        const items = window.mediaService.catalog || [];
        const favoriteIds = new Set(window.supabaseAuth.getFavorites().map(item => String(item.id)));
        const hasTaste = Object.keys(this.taste.genres).length || Object.keys(this.taste.people).length || favoriteIds.size;
        if (!hasTaste || items.length === 0) {
            section.style.display = "none";
            return;
        }
        const scored = items.filter(item => !favoriteIds.has(String(item.id))).map(item => {
            let score = (item.imdb || 0) * 1.25;
            (item.genres || []).forEach(genre => score += (this.taste.genres[genre.title] || 0) * 3);
            (item.people || []).forEach(person => score += (this.taste.people[person.name] || 0) * 5);
            return { item, score };
        }).sort((a, b) => b.score - a.score).slice(0, 14).map(entry => entry.item);
        row.replaceChildren(...scored.map(item => this.createMediaCard(item)));
        section.style.display = scored.length ? "block" : "none";
        const favoritePerson = Object.entries(this.taste.people).sort((a, b) => b[1] - a[1])[0]?.[0];
        if (subtitle) subtitle.textContent = favoritePerson ? `چون آثار ${favoritePerson} را دوست دارید` : "بر پایهٔ ژانرهای مورد علاقهٔ شما";
    }

    // --- Explore Tab with Search & Filtering ---
    async renderExploreTab() {
        const requestedQuery = this.searchQuery.trim();
        let items = requestedQuery
            ? await window.mediaService.search(requestedQuery)
            : await window.mediaService.getExploreCatalog();

        // Ignore a slower response for text the user has already replaced.
        if (requestedQuery !== this.searchQuery.trim()) return;
        this.renderExplorePeople(requestedQuery, items);

        // 1. Filter by Genre
        if (this.currentGenre && this.currentGenre !== "all") {
            items = items.filter(x => {
                const genres = (x.genres || []).map(g => g.title);
                return genres.some(g => g.includes(this.currentGenre) || this.currentGenre.includes(g));
            });
        }

        // 2. Sort
        if (this.sortBy === "imdb") {
            items.sort((a, b) => (b.imdb || 0) - (a.imdb || 0));
        } else if (this.sortBy === "year") {
            items.sort((a, b) => (b.year || 0) - (a.year || 0));
        } else if (this.sortBy === "title") {
            items.sort((a, b) => (a.title || "").localeCompare(b.title || ""));
        }

        // Render to Grid
        const grid = document.getElementById("grid-explore-results");
        const empty = document.getElementById("explore-empty-state");
        const countBadge = document.getElementById("txt-explore-results-count");

        if (countBadge) {
            countBadge.textContent = `${items.length} اثر یافت شد`;
        }

        if (!grid) return;
        grid.innerHTML = "";

        if (items.length === 0) {
            if (empty) empty.style.display = "block";
        } else {
            if (empty) empty.style.display = "none";
            items.forEach(m => grid.appendChild(this.createMediaCard(m)));
        }
    }

    renderExplorePeople(query, items) {
        const section = document.getElementById("explore-people-results");
        const row = document.getElementById("row-explore-people");
        if (!section || !row) return;
        row.replaceChildren();
        const normalized = query.trim().toLocaleLowerCase();
        if (normalized.length < 2) {
            section.style.display = "none";
            return;
        }
        const seen = new Set();
        const people = items.flatMap(item => item.people || []).filter(person => {
            const key = `${person.name.toLocaleLowerCase()}|${person.role || ""}`;
            if (!person.name.toLocaleLowerCase().includes(normalized) || seen.has(key)) return false;
            seen.add(key);
            return true;
        }).slice(0, 12);
        people.forEach(person => row.appendChild(this.createPersonCard(person)));
        section.style.display = people.length ? "block" : "none";
    }

    createPersonCard(person) {
        const roleLabels = { actor: "بازیگر", director: "کارگردان", writer: "نویسنده" };
        const button = document.createElement("button");
        button.type = "button";
        button.className = "person-card";
        const photo = document.createElement("span");
        photo.className = "person-photo";
        photo.textContent = person.name.slice(0, 1);
        if (person.photo) photo.style.backgroundImage = `url("${person.photo.replace(/"/g, "%22")}")`;
        else window.mediaService.resolvePersonPhoto(person).then(url => {
            if (url && button.isConnected) photo.style.backgroundImage = `url("${url.replace(/"/g, "%22")}")`;
        });
        const role = document.createElement("span");
        role.className = "person-role";
        role.textContent = roleLabels[person.role] || "عامل";
        const name = document.createElement("strong");
        name.textContent = person.name;
        button.append(photo, role, name);
        button.addEventListener("click", event => {
            event.preventDefault();
            event.stopPropagation();
            this.openPersonProfile(person);
        });
        return button;
    }

    // --- Detail Modal with Series Average Size Calculation ---
    openDetailModal(media) {
        this.activeMedia = media;
        this.rememberMedia(media);
        const modal = document.getElementById("modal-detail");
        if (!modal) return;

        const coverEl = document.getElementById("detail-backdrop");
        const posterEl = document.getElementById("detail-poster");
        const fallbackSvg = window.getPosterFallbackSvg ? 
            window.getPosterFallbackSvg(media.title, (media.genres && media.genres[0]) ? media.genres[0].title : 'فیلم', media.year || 2024, (media.imdb || 8.0).toFixed(1)) : 
            "assets/logo.png";

        if (coverEl) {
            coverEl.src = media.cover || media.image || fallbackSvg;
            coverEl.onerror = () => { coverEl.src = fallbackSvg; };
        }
        if (posterEl) {
            posterEl.src = media.image || media.cover || fallbackSvg;
            posterEl.onerror = () => { posterEl.src = fallbackSvg; };
        }

        const titleEl = document.getElementById("detail-title");
        if (titleEl) titleEl.textContent = media.title;

        const badgeType = document.getElementById("detail-badge-type");
        if (badgeType) badgeType.textContent = media.type === 'serie' ? 'سریال' : 'سینمایی';

        const badgeYear = document.getElementById("detail-badge-year");
        if (badgeYear) badgeYear.textContent = `سال ${media.year || 2024}`;

        const badgeImdb = document.getElementById("detail-badge-imdb");
        if (badgeImdb) badgeImdb.textContent = `⭐ ${(media.imdb || 8.0).toFixed(1)}`;

        const badgeCountry = document.getElementById("detail-badge-country");
        if (badgeCountry) badgeCountry.textContent = (media.country && media.country[0]) ? media.country[0].title : 'جهانی';

        const storylineEl = document.getElementById("detail-storyline");
        if (storylineEl) storylineEl.textContent = media.description || 'توضیحاتی برای این اثر ثبت نشده است.';
        this.renderPeople(media.people || []);

        // Series Average Size Calculation
        const avgBox = document.getElementById("detail-series-avg-size");
        const seriesSection = document.getElementById("detail-series-section");
        const movieSourcesSection = document.getElementById("detail-sources-section");

        if (media.type === "serie") {
            if (movieSourcesSection) movieSourcesSection.style.display = "none";
            if (seriesSection) seriesSection.style.display = "block";

            if (!media.seasons || media.seasons.length === 0) {
                this.showSeriesLoadState("در حال دریافت فصل‌ها و قسمت‌ها…");
                // Fetch real seasons from Iranflix API
                window.mediaService.getSeriesSeasons(media.id).then(seasons => {
                    media.seasons = seasons;
                    this.renderSeriesSeasonsUI(media, avgBox);
                }).catch(() => {
                    this.showSeriesLoadState("فصل‌ها از کاتالوگ زنده دریافت نشدند. دوباره تلاش کنید.", true);
                });
            } else {
                this.renderSeriesSeasonsUI(media, avgBox);
            }
        } else {
            if (avgBox) avgBox.style.display = "none";
            if (seriesSection) seriesSection.style.display = "none";
            if (movieSourcesSection) movieSourcesSection.style.display = "block";
            this.renderMovieSources(media.sources || []);
        }

        // Similar row
        this.renderSimilarMedia(media);

        // Fav state
        const isFav = window.supabaseAuth.isFavorite(media.id);
        this.updateDetailFavButton(isFav);

        modal.style.display = "flex";
    }

    renderPeople(people) {
        const section = document.getElementById("detail-people-section");
        const row = document.getElementById("detail-people-row");
        if (!section || !row) return;
        row.replaceChildren();
        if (!people.length) {
            section.style.display = "none";
            return;
        }
        people.forEach(person => row.appendChild(this.createPersonCard(person)));
        section.style.display = "block";
    }

    async openPersonProfile(person) {
        this.activePerson = person;
        this.taste.people[person.name] = (this.taste.people[person.name] || 0) + 1;
        this.saveTaste();
        const modal = document.getElementById("modal-person");
        if (!modal) return;
        const labels = { actor: "بازیگر", director: "کارگردان", writer: "نویسنده" };
        document.getElementById("person-profile-name").textContent = person.name;
        document.getElementById("person-profile-role").textContent = labels[person.role] || "عامل";
        const photo = document.getElementById("person-profile-photo");
        photo.textContent = person.name.slice(0, 1);
        photo.style.backgroundImage = person.photo ? `url("${person.photo.replace(/"/g, "%22")}")` : "";
        if (!person.photo) window.mediaService.resolvePersonPhoto(person).then(url => {
            if (url && this.activePerson === person) photo.style.backgroundImage = `url("${url.replace(/"/g, "%22")}")`;
        });
        this.updatePersonFollowButton();
        modal.style.display = "flex";
        const credits = document.getElementById("person-profile-credits");
        const empty = document.getElementById("person-profile-empty");
        credits.replaceChildren();
        empty.style.display = "none";
        const catalog = await window.mediaService.getExploreCatalog();
        if (this.activePerson !== person) return;
        const personName = person.name.toLocaleLowerCase();
        const matches = catalog.filter(item => (item.people || []).some(candidate => candidate.name.toLocaleLowerCase() === personName));
        credits.replaceChildren(...matches.map(item => this.createMediaCard(item)));
        empty.style.display = matches.length ? "none" : "block";
    }

    closePersonProfile() {
        const modal = document.getElementById("modal-person");
        if (modal) modal.style.display = "none";
        this.activePerson = null;
    }

    togglePersonPreference() {
        if (!this.activePerson) return;
        const name = this.activePerson.name;
        if (this.taste.people[name] >= 10) delete this.taste.people[name];
        else this.taste.people[name] = 10;
        this.saveTaste();
        this.updatePersonFollowButton();
        this.renderForYou();
    }

    updatePersonFollowButton() {
        const button = document.getElementById("btn-person-follow");
        if (!button || !this.activePerson) return;
        const followed = (this.taste.people[this.activePerson.name] || 0) >= 10;
        button.textContent = followed ? "♥ در سلیقهٔ من" : "♡ افزودن به سلیقهٔ من";
        button.classList.toggle("person-following", followed);
    }

    renderSeriesSeasonsUI(media, avgBox) {
        if (!media.seasons || media.seasons.length === 0) {
            this.showSeriesLoadState("برای این سریال هنوز فصل یا قسمتی در کاتالوگ زنده ثبت نشده است.", true);
            return;
        }
        if (avgBox) {
            avgBox.style.display = "block";
            const epCount = media.seasons.reduce((count, season) => count + (season.episodes || []).length, 0);
            avgBox.textContent = `${media.seasons.length} فصل • ${epCount} قسمت در ایران‌فلیکس`;
        }
        this.renderSeasonsAndEpisodes(media.seasons);
    }

    showSeriesLoadState(message, isError = false) {
        const tabs = document.getElementById("detail-seasons-tabs");
        const episodes = document.getElementById("detail-episodes-list");
        const selector = document.getElementById("detail-season-select");
        const position = document.getElementById("detail-season-position");
        if (tabs) tabs.replaceChildren();
        if (selector) selector.replaceChildren();
        if (position) position.textContent = "";
        if (episodes) {
            episodes.innerHTML = `<div class="episodes-load-state ${isError ? "is-error" : ""}">${isError ? "⚠️" : "◌"}<span>${message}</span></div>`;
        }
    }

    closeDetailModal() {
        const modal = document.getElementById("modal-detail");
        if (modal) modal.style.display = "none";
    }

    renderSimilarMedia(media) {
        const row = document.getElementById("detail-similar-row");
        if (!row) return;
        row.innerHTML = "";
        const currentGenre = (media.genres && media.genres[0]) ? media.genres[0].title : "";
        const allItems = window.mediaService.catalog || [];
        const similar = allItems.filter(x => x.id !== media.id && (
            (x.genres || []).some(g => g.title === currentGenre) || x.type === media.type
        )).slice(0, 8);

        similar.forEach(m => row.appendChild(this.createMediaCard(m)));
    }

    renderMovieSources(sources) {
        const container = document.getElementById("detail-sources-list");
        if (!container) return;
        container.innerHTML = "";

        if (!sources || sources.length === 0) {
            container.innerHTML = `
                <div style="padding: 16px; color: var(--on-surface-variant); font-size: 13px; text-align: center; width: 100%;">
                    ⚠️ لینکی برای این اثر در سرور یافت نشد.
                </div>
            `;
            return;
        }

        sources.forEach(src => {
            const item = document.createElement("div");
            item.className = "quality-item-row";
            item.innerHTML = `
                <div class="quality-info">
                    <span class="quality-name">${src.quality}</span>
                    <span class="quality-badge">${src.type || 'MP4'}</span>
                </div>
                <div class="quality-actions">
                    <button class="btn-play-quality btn-primary-gradient">پخش</button>
                    <button class="btn-dl-quality glass-icon-btn" title="دانلود با IDM">⬇️</button>
                </div>
            `;
            item.querySelector(".btn-play-quality").addEventListener("click", () => {
                this.nextEpisodeContext = null;
                this.playStream(src.url, `${this.activeMedia.title} - ${src.quality}`);
            });
            item.querySelector(".btn-dl-quality").addEventListener("click", () => {
                this.postNative("downloadWithIDM", { url: src.url });
                this.addDownloadTask(this.activeMedia, src.quality);
            });
            container.appendChild(item);
        });
    }

    renderSeasonsAndEpisodes(seasons) {
        const tabsWrap = document.getElementById("detail-seasons-tabs");
        const epsWrap = document.getElementById("detail-episodes-list");
        const position = document.getElementById("detail-season-position");
        const previous = document.getElementById("btn-season-prev");
        const next = document.getElementById("btn-season-next");
        const selector = document.getElementById("detail-season-select");
        if (!tabsWrap || !epsWrap) return;
        tabsWrap.innerHTML = "";
        epsWrap.innerHTML = "";
        let selectedIndex = 0;
        const selectSeason = (index, moveFocus = false) => {
            if (index < 0 || index >= seasons.length) return;
            selectedIndex = index;
            const buttons = Array.from(tabsWrap.querySelectorAll(".season-tab-btn"));
            buttons.forEach(button => button.classList.remove("active"));
            const button = buttons[index];
            if (!button) return;
            if (selector) selector.value = String(index);
            button.classList.add("active");
            button.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
            if (moveFocus) button.focus();
            if (position) position.textContent = `فصل ${index + 1} از ${seasons.length} — با دکمه‌های دو طرف یا اسکرول جابه‌جا شوید`;
            this.renderEpisodeItems(seasons[index].episodes, button.textContent);
        };

        if (selector) selector.innerHTML = "";
        seasons.forEach((season, index) => {
            const btn = document.createElement("button");
            btn.className = `season-tab-btn ${index === 0 ? 'active' : ''}`;
            btn.textContent = this.formatSeasonTitle(season.title, index);
            btn.title = season.title || btn.textContent;
            btn.addEventListener("click", () => selectSeason(index, true));
            tabsWrap.appendChild(btn);
            if (selector) {
                const option = document.createElement("option");
                option.value = String(index);
                option.textContent = btn.textContent;
                selector.appendChild(option);
            }
        });
        if (previous) previous.onclick = () => selectSeason(selectedIndex - 1, true);
        if (next) next.onclick = () => selectSeason(selectedIndex + 1, true);
        if (selector) selector.onchange = () => selectSeason(Number(selector.value), true);
        if (seasons.length > 0) selectSeason(0);
    }

    formatSeasonTitle(title, index) {
        const cleaned = String(title || "")
            .replace(/(?:480|720|1080|2160)p?/gi, "")
            .replace(/(?:زیرنویس|چسبیده|فارسی|دوبله|کیفیت)/g, "")
            .replace(/\s+/g, " ")
            .trim();
        return cleaned && cleaned !== "null" ? cleaned : `فصل ${index + 1}`;
    }

    renderEpisodeItems(episodes, seasonTitle = "") {
        const epsWrap = document.getElementById("detail-episodes-list");
        if (!epsWrap) return;
        epsWrap.innerHTML = "";

        if (!episodes || episodes.length === 0) {
            epsWrap.innerHTML = `
                <div style="padding: 16px; color: var(--on-surface-variant); font-size: 13px; text-align: center; width: 100%;">
                    قسمتی برای این فصل یافت نشد.
                </div>
            `;
            return;
        }

        const heading = document.createElement("div");
        heading.className = "episodes-section-heading";
        heading.textContent = `قسمت‌های ${seasonTitle} • ${episodes.length} قسمت`;
        epsWrap.appendChild(heading);

        episodes.forEach((ep, epIndex) => {
            const sources = ep.sources || [];
            const episodeTitle = ep.title && ep.title !== "null" ? ep.title : `قسمت ${epIndex + 1}`;
            const card = document.createElement("article");
            card.className = "episode-card";
            const head = document.createElement("div");
            head.className = "episode-card-head";
            head.innerHTML = `<span class="episode-number">${epIndex + 1}</span><div><strong>${episodeTitle}</strong><span>${sources.length ? `${sources.length} نسخهٔ قابل پخش` : "لینک پخش ثبت نشده"}</span></div>`;
            const variants = document.createElement("div");
            variants.className = "episode-variant-list";
            sources.forEach(source => {
                const variant = document.createElement("div");
                variant.className = "episode-variant";
                const label = document.createElement("span");
                label.textContent = `${source.quality || "کیفیت اصلی"} • ${(source.type || "پخش").toUpperCase()}`;
                const download = document.createElement("button");
                download.className = "episode-download-btn";
                download.title = "دانلود با IDM";
                download.textContent = "⬇";
                download.addEventListener("click", () => {
                    this.postNative("downloadWithIDM", { url: source.url });
                    this.addDownloadTask(this.activeMedia, episodeTitle);
                });
                const play = document.createElement("button");
                play.className = "episode-play-btn";
                play.textContent = "پخش";
                play.addEventListener("click", () => this.playEpisodeSource(source, episodeTitle, episodes, epIndex));
                variant.append(label, download, play);
                variants.appendChild(variant);
            });
            card.append(head, variants);
            epsWrap.appendChild(card);
        });
    }

    playEpisodeSource(source, episodeTitle, episodes, episodeIndex) {
        this.nextEpisodeContext = { episodes, episodeIndex, source };
        this.playStream(source.url, `${this.activeMedia.title} - ${episodeTitle} - ${source.quality || "کیفیت اصلی"}`);
    }

    playNextWebEpisode() {
        const context = this.nextEpisodeContext;
        const nextEpisode = context?.episodes?.[context.episodeIndex + 1];
        if (!nextEpisode?.sources?.length) return;
        const currentText = `${context.source?.quality || ""} ${context.source?.type || ""}`.toLocaleLowerCase();
        const language = value => value.includes("دوبله") ? "dub" : value.includes("زیرنویس") ? "sub" : "other";
        const quality = value => Number((value.match(/(480|540|720|1080|2160)/) || [])[1]) || 0;
        const matchingLanguage = nextEpisode.sources.filter(source => language(`${source.quality} ${source.type}`.toLocaleLowerCase()) === language(currentText));
        const candidates = matchingLanguage.length ? matchingLanguage : nextEpisode.sources;
        const wantedQuality = quality(currentText);
        const source = candidates.slice().sort((a, b) => Math.abs(quality(`${a.quality} ${a.type}`) - wantedQuality) - Math.abs(quality(`${b.quality} ${b.type}`) - wantedQuality))[0];
        const title = nextEpisode.title && nextEpisode.title !== "null" ? nextEpisode.title : `قسمت ${context.episodeIndex + 2}`;
        this.playEpisodeSource(source, title, context.episodes, context.episodeIndex + 1);
    }

    updateDetailFavButton(isFav) {
        const btn = document.getElementById("btn-detail-fav");
        if (!btn) return;
        if (isFav) {
            btn.classList.add("favorited");
            btn.innerHTML = '❤️ <span>در لیست علاقه‌مندی‌ها</span>';
        } else {
            btn.classList.remove("favorited");
            btn.innerHTML = '🤍 <span>افزودن به علاقه‌مندی‌ها</span>';
        }
    }

    // --- Player Management ---
    playMedia(media) {
        this.nextEpisodeContext = null;
        if (media?.id) {
            this.taste.watched = this.taste.watched || {};
            this.taste.watched[String(media.id)] = Date.now();
            this.saveTaste();
        }
        let streamUrl = "";
        if (media.sources && media.sources.length > 0) {
            streamUrl = media.sources[0].url;
        } else if (media.seasons && media.seasons.length > 0 && media.seasons[0].episodes && media.seasons[0].episodes.length > 0) {
            streamUrl = media.seasons[0].episodes[0].sources?.[0]?.url || "";
        }
        if (!streamUrl) {
            alert("لینکی برای پخش این محتوا موجود نیست.");
            return;
        }
        this.playStream(streamUrl, media.title);
    }

    getStreamUrl(media) {
        if (!media) return "";
        if (media.sources && media.sources.length > 0) return media.sources[0].url;
        if (media.seasons && media.seasons.length > 0 && media.seasons[0].episodes && media.seasons[0].episodes.length > 0) {
            return media.seasons[0].episodes[0].sources?.[0]?.url || "";
        }
        return "";
    }

    playStream(url, title) {
        if (!url) {
            alert("لینکی برای پخش این محتوا موجود نیست.");
            return;
        }
        this.currentPlayingUrl = url;
        this.currentPlayingTitle = title;

        // WebView2 cannot reliably decode MKV/HEVC. Route known incompatible
        // containers straight to the first installed Windows player instead of
        // opening a player that is guaranteed to fail.
        const cleanUrl = url.split("?")[0].toLowerCase();
        if (/\.(mkv|avi|wmv|flv|ts|m2ts)$/.test(cleanUrl)) {
            this.postNative("launchExternalPlayer", { player: "auto", url, title });
            return;
        }

        if (this.settings.player !== "bisnor") {
            // Launch Windows external player configured in Settings
            this.postNative("launchExternalPlayer", { player: this.settings.player, url, title });
            return;
        }

        const modal = document.getElementById("modal-player");
        const video = document.getElementById("html-video-player");
        const titleEl = document.getElementById("player-media-title");
        const errOverlay = document.getElementById("player-error-overlay");
        if (!modal || !video) return;

        if (errOverlay) errOverlay.style.display = "none";
        if (titleEl) titleEl.textContent = title;
        video.src = url;
        video.load();
        modal.style.display = "flex";
        if (modal.requestFullscreen) {
            modal.requestFullscreen().catch(() => {});
        }
        video.play().catch(e => console.log("Auto-play blocked", e));
    }

    closePlayerModal() {
        const modal = document.getElementById("modal-player");
        const video = document.getElementById("html-video-player");
        const errOverlay = document.getElementById("player-error-overlay");
        if (errOverlay) errOverlay.style.display = "none";
        if (video) {
            video.pause();
            video.removeAttribute("src");
            video.load();
        }
        if (modal) modal.style.display = "none";
        if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen().catch(() => {});
    }

    // --- Favorites Tab ---
    renderFavoritesTab() {
        const grid = document.getElementById("grid-favorites");
        const empty = document.getElementById("favorites-empty-state");
        if (!grid) return;
        grid.innerHTML = "";

        const allFavorites = window.supabaseAuth.getFavorites();
        const query = this.favoritesSearchQuery;
        const favorites = allFavorites.filter(media => {
            const matchesQuery = !query || [
                media.title, media.description,
                ...(media.genres || []).map(genre => genre.title),
                ...(media.people || []).map(person => person.name)
            ].join(" ").toLocaleLowerCase().includes(query);
            const isWatched = Boolean(this.taste.watched?.[String(media.id)]);
            const matchesWatch = this.favoritesWatchFilter === "all" ||
                (this.favoritesWatchFilter === "watched" && isWatched) ||
                (this.favoritesWatchFilter === "unwatched" && !isWatched);
            return matchesQuery && matchesWatch;
        });
        const count = document.getElementById("favorites-count-label");
        if (count) count.textContent = `${favorites.length} اثر${favorites.length !== allFavorites.length ? ` از ${allFavorites.length}` : ""}`;
        if (favorites.length === 0) {
            if (empty) empty.style.display = "block";
            grid.style.display = "none";
            return;
        }

        if (empty) empty.style.display = "none";
        grid.style.display = "grid";
        favorites.forEach(m => grid.appendChild(this.createMediaCard(m)));
    }

    // --- Downloads Tab & IDM Integration ---
    addDownloadTask(media, qualityOrEp = "کیفیت اصلی") {
        const task = {
            id: Date.now(),
            mediaId: media.id,
            title: media.title,
            subtitle: qualityOrEp,
            cover: media.image || media.cover,
            progress: 100, // IDM handles external download
            status: "انجام شده (ارسال به دانلودر ویندوز)",
            date: new Date().toLocaleDateString("fa-IR")
        };
        this.downloads.unshift(task);
        localStorage.setItem("bisnor_downloads", JSON.stringify(this.downloads));
        if (this.currentTab === "downloads") this.renderDownloads();
    }

    renderDownloads() {
        const container = document.getElementById("downloads-list");
        const empty = document.getElementById("downloads-empty-state");
        if (!container) return;
        container.innerHTML = "";

        if (this.downloads.length === 0) {
            if (empty) empty.style.display = "block";
            return;
        }

        if (empty) empty.style.display = "none";
        this.downloads.forEach(dl => {
            const item = document.createElement("div");
            item.className = "download-item-card glass-panel";
            item.innerHTML = `
                <img src="${dl.cover}" alt="${dl.title}" class="download-thumb">
                <div class="download-details">
                    <h4>${dl.title}</h4>
                    <span class="dl-sub">${dl.subtitle} • ${dl.date}</span>
                    <span class="dl-status">✅ ${dl.status}</span>
                </div>
                <button class="glass-pill-btn btn-open-dl-file">نمایش در پوشه 📂</button>
            `;
            item.querySelector(".btn-open-dl-file").addEventListener("click", () => {
                this.postNative("openFolder");
            });
            container.appendChild(item);
        });
    }

    // --- 11-Character Pop Culture Avatar Picker with Live Halo Preview ---
    renderAvatarPickerGrid() {
        const grid = document.getElementById("avatars-grid");
        if (!grid) return;
        grid.innerHTML = "";

        CHARACTERS.forEach(char => {
            const item = document.createElement("div");
            item.className = `avatar-item-choice ${char.id === this.selectedAvatarForPicker ? 'selected' : ''}`;
            item.innerHTML = `
                <img src="assets/avatars/${char.file}" alt="${char.name}">
                <span class="avatar-item-label">${char.name.split('(')[0].trim()}</span>
            `;
            item.addEventListener("click", () => {
                this.selectedAvatarForPicker = char.id;
                document.querySelectorAll(".avatar-item-choice").forEach(c => c.classList.remove("selected"));
                item.classList.add("selected");
                this.updateAvatarLivePreview(char);
            });
            grid.appendChild(item);
        });
    }

    updateAvatarLivePreview(char) {
        const previewImg = document.getElementById("img-picker-live-preview");
        const charName = document.getElementById("txt-picker-character-name");
        if (previewImg) previewImg.src = `assets/avatars/${char.file}`;
        if (charName) charName.textContent = char.name;
    }

    openAvatarPicker() {
        const currentAvatarId = window.supabaseAuth.getAvatarId();
        this.selectedAvatarForPicker = currentAvatarId;
        const char = CHARACTERS.find(c => c.id === currentAvatarId) || CHARACTERS[0];
        this.updateAvatarLivePreview(char);
        this.renderAvatarPickerGrid();
        document.getElementById("modal-avatar-picker").style.display = "flex";
    }

    closeAvatarPicker() {
        document.getElementById("modal-avatar-picker").style.display = "none";
    }

    saveAvatar() {
        window.supabaseAuth.setAvatarId(this.selectedAvatarForPicker);
        this.loadProfile();
        this.closeAvatarPicker();
    }

    // --- User Profile & Offline Analytics ---
    loadProfile() {
        const username = window.supabaseAuth.getUsername();
        const avatarId = window.supabaseAuth.getAvatarId();
        const isLoggedIn = window.supabaseAuth.isLoggedIn();

        const char = CHARACTERS.find(c => c.id === avatarId) || CHARACTERS[0];
        const avatarSrc = `assets/avatars/${char.file}`;

        const headerAvatar = document.getElementById("img-header-avatar");
        if (headerAvatar) headerAvatar.src = avatarSrc;

        const profileAvatar = document.getElementById("img-profile-avatar");
        if (profileAvatar) profileAvatar.src = avatarSrc;

        const tvUsername = document.getElementById("tv-profile-username");
        const tvStatus = document.getElementById("tv-profile-sync-status");
        const btnAction = document.getElementById("btn-profile-action");
        const btnAvatar = document.getElementById("btn-profile-change-avatar");
        const btnLogout = document.getElementById("btn-profile-logout");

        if (isLoggedIn) {
            if (tvUsername) tvUsername.textContent = username;
            if (tvStatus) tvStatus.textContent = `متصل به ابر Supabase (${char.name})`;
            if (btnAction) btnAction.style.display = "none";
            if (btnAvatar) btnAvatar.style.display = "inline-block";
            if (btnLogout) btnLogout.style.display = "inline-block";
        } else {
            if (tvUsername) tvUsername.textContent = "ورود به حساب کاربری";
            if (tvStatus) tvStatus.textContent = "جهت مدیریت پروفایل و ذخیره علایق وارد شوید";
            if (btnAction) {
                btnAction.style.display = "inline-block";
                btnAction.textContent = "ورود";
            }
            if (btnAvatar) btnAvatar.style.display = "none";
            if (btnLogout) btnLogout.style.display = "none";
        }

        this.renderAnalytics();
    }

    renderAnalytics() {
        const watchHours = document.getElementById("stat-watch-hours");
        if (watchHours) {
            watchHours.textContent = `${(this.analytics.watchMinutes / 60).toFixed(1)} ساعت`;
        }
        const movieHours = document.getElementById("metric-movie-hours");
        if (movieHours) {
            movieHours.textContent = `${this.analytics.moviesWatched} فیلم`;
        }
        const seriesHours = document.getElementById("metric-series-hours");
        if (seriesHours) {
            seriesHours.textContent = `${this.analytics.seriesEpisodesWatched} قسمت`;
        }
    }

    recordWatchTime(minutes) {
        this.analytics.watchMinutes += minutes;
        this.analytics.simulatedDataGb += (minutes * 0.015);
        this.analytics.moviesWatched += 1;
        localStorage.setItem("bisnor_analytics", JSON.stringify(this.analytics));
        this.renderAnalytics();
    }

    // --- Preferred Player and Auto Next Modals ---
    openPlayerChoiceModal() {
        const modal = document.getElementById("modal-player-choice");
        if (!modal) return;
        this.selectedPlayerChoice = this.settings.player;
        document.querySelectorAll(".player-option-card").forEach(c => {
            c.classList.toggle("active", c.getAttribute("data-player") === this.selectedPlayerChoice);
        });
        modal.style.display = "flex";
    }

    closePlayerChoiceModal() {
        const modal = document.getElementById("modal-player-choice");
        if (modal) modal.style.display = "none";
    }

    updatePlayerSettingLabel() {
        const label = document.getElementById("tv-current-player-label");
        if (!label) return;
        const playerNames = {
            bisnor: "پلیر داخلی بیسنور (پیش‌فرض)",
            vlc: "VLC Media Player",
            potplayer: "PotPlayer (64-bit)",
            kmplayer: "KMPlayer",
            mpc: "MPC-HC (Media Player Classic)"
        };
        label.textContent = playerNames[this.settings.player] || "پلیر داخلی بیسنور (پیش‌فرض)";
    }

    openAutoNextTimeModal() {
        const modal = document.getElementById("modal-auto-next-time");
        if (!modal) return;
        document.querySelectorAll(".time-opt-btn").forEach(btn => {
            const mins = parseInt(btn.getAttribute("data-minutes"), 10);
            if (this.settings.autoNext === false) {
                btn.classList.toggle("active", mins === 0);
            } else {
                btn.classList.toggle("active", mins === this.settings.autoNextMinutes);
            }
        });
        modal.style.display = "flex";
    }

    closeAutoNextTimeModal() {
        const modal = document.getElementById("modal-auto-next-time");
        if (modal) modal.style.display = "none";
    }

    // --- Settings Tab ---
    loadSettings() {
        // Theme chips
        document.querySelectorAll(".chip-theme").forEach(chip => {
            chip.classList.toggle("active", chip.getAttribute("data-theme") === this.settings.theme);
        });

        // Switches
        const switchAuto = document.getElementById("switch-auto-next");
        if (switchAuto) switchAuto.checked = this.settings.autoNext;

        const switchWarn = document.getElementById("switch-content-warning");
        if (switchWarn) switchWarn.checked = this.settings.contentWarning;

        const switchSegmented = document.getElementById("switch-segmented-download");
        if (switchSegmented) {
            switchSegmented.checked = localStorage.getItem("bisnor_segmented_dl") !== "false";
        }

        // Auto next time label
        const nextTimeLabel = document.getElementById("tv-auto-next-minutes-label");
        if (nextTimeLabel) {
            nextTimeLabel.textContent = this.settings.autoNext ? `${this.settings.autoNextMinutes} دقیقه مانده به پایان` : "غیرفعال";
        }

        // Player label
        this.updatePlayerSettingLabel();
    }

    // --- Auth Modal & Supabase Actions ---
    openAuthModal() {
        const modal = document.getElementById("modal-auth");
        if (modal) modal.style.display = "flex";
    }

    closeAuthModal() {
        const modal = document.getElementById("modal-auth");
        if (modal) modal.style.display = "none";
    }

    toggleAuthMode() {
        this.authMode = this.authMode === "login" ? "signup" : "login";
        const title = document.getElementById("auth-title");
        const sub = document.getElementById("auth-subtitle");
        const submitBtn = document.getElementById("btn-submit-auth");
        const switchBtn = document.getElementById("btn-switch-auth-mode");

        if (this.authMode === "login") {
            if (title) title.textContent = "ورود به حساب کاربری";
            if (sub) sub.textContent = "جهت همگام‌سازی واچ‌لیست و تنظیمات سلیقه وارد شوید";
            if (submitBtn) submitBtn.textContent = "ورود به حساب";
            if (switchBtn) switchBtn.textContent = "حساب کاربری ندارید؟ ثبت‌نام امن";
        } else {
            if (title) title.textContent = "ثبت‌نام امن در بیسنور";
            if (sub) sub.textContent = "ایجاد حساب کاربری با رمزنگاری مدرن Supabase";
            if (submitBtn) submitBtn.textContent = "ساخت حساب و ورود";
            if (switchBtn) switchBtn.textContent = "قبلاً ثبت‌نام کرده‌اید؟ ورود به حساب";
        }
    }

    async onAuthSubmit(e) {
        if (e && e.preventDefault) e.preventDefault();
        const username = document.getElementById("auth-username")?.value.trim() || "";
        const password = document.getElementById("auth-password")?.value || "";
        const currentAvatar = window.supabaseAuth.getAvatarId();

        let result;
        if (this.authMode === "login") {
            result = await window.supabaseAuth.login(username, password);
        } else {
            result = await window.supabaseAuth.signup(username, password, currentAvatar);
        }

        if (result.success) {
            alert(result.message);
            this.closeAuthModal();
            this.loadProfile();
        } else {
            alert(`خطا: ${result.message}`);
        }
    }

    onLogout() {
        if (confirm("آیا مایلید از حساب کاربری خود خارج شوید؟")) {
            window.supabaseAuth.logout();
            this.loadProfile();
        }
    }

    // --- "What to watch tonight" Smart Taste Suggestion ---
    async showSmartTasteSuggestion() {
        const catalog = await window.mediaService.getExploreCatalog();
        if (!catalog || catalog.length === 0) return;

        // Pick a top rated movie or series with high rating
        const randomIndex = Math.floor(Math.random() * catalog.length);
        const item = catalog[randomIndex];
        this.suggestedMedia = item;

        const modal = document.getElementById("modal-random-suggest");
        if (!modal) return;

        const cover = document.getElementById("random-cover");
        const rating = document.getElementById("random-rating");
        const title = document.getElementById("random-title");
        const desc = document.getElementById("random-desc");

        if (cover) cover.src = item.cover || item.image;
        if (rating) rating.textContent = `⭐ ${(item.imdb || 8.5).toFixed(1)}`;
        if (title) title.textContent = item.title;
        if (desc) desc.textContent = item.description;

        modal.style.display = "flex";
    }

    closeRandomModal() {
        const modal = document.getElementById("modal-random-suggest");
        if (modal) modal.style.display = "none";
    }
}

// Boot application
window.addEventListener("DOMContentLoaded", () => {
    window.app = new BisnorApp();
    window.app.init();
});
