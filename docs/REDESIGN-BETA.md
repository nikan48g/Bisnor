# Bisnor — Redesign Beta (Android)

> Internal build. Not published, not pushed, not tagged.

## 1. What this build is

A production-oriented UI/UX redesign of the existing Bisnor Android client. It keeps the
project's original stack (Kotlin, XML views, Fragments, Material 3, Media3/ExoPlayer,
Retrofit/OkHttp, Supabase, SharedPreferences/SQLite) and changes the product surface:

- A cinematic dark/home experience with a real-data hero carousel.
- A redesigned details screen for movies and series.
- A real library (continue watching, watchlist, watched, personal lists).
- A dedicated discovery/search screen.
- A unified profile that owns account, activity analytics and the settings entry point.
- One player settings sheet + a strengthened seek bar (player engine untouched).

## 2. Repository audit — what existed

| Area | Finding |
| --- | --- |
| UI framework | Android Views + XML, ViewBinding, Fragments hosted by `MainActivity` |
| Navigation | 4 tabs: Home / Explore / Watchlist / Settings (fragment replace, state lost) |
| Catalogue | `RealMediaRepository` calling live IranFlix endpoints (`/api/movie/by/filtres/...`, `/api/search/...`, `/api/season/by/serie/...`) |
| Fake data | `MediaRepository` contained a curated hardcoded TMDB catalogue and Google sample videos, but was **not referenced by any screen** |
| Playback | `PlayerActivity` + Media3 ExoPlayer, external players, progress persistence |
| User data | `FavoritesManager`, `PlaylistsManager`, `PlaybackHistoryManager` (SharedPreferences), `SmartTasteDatabase` (SQLite watch events) |
| Auth/sync | `SupabaseManager` + `AuthManager` (GoTrue + profiles table, compressed payloads) |
| Other platforms | `desktop/` (WPF/WebView) and `tizen-tv/` were not modified |
| Build | AGP 9.3.2, Gradle 9.5, Kotlin 2.2, compileSdk 35, minSdk 24 |

## 3. Data authenticity

Every production screen now reads from exactly one catalogue source:
`RealMediaRepository` (live IranFlix). This was enforced by:

- Deleting the unused hardcoded mock catalogue (`MediaRepository`), its models, network
  client, API interface and mock adapters.
- Removing the fabricated “average episode size” estimates from `MediaMetadataHelper`.
- Removing the fake default `1080p` badge; badges are only shown when the metadata
  actually mentions dubbing/subtitles, or when the item is a series.
- Not inventing ratings, popularity or recommendation signals. The “random pick” uses real
  watch-history genre weights + real IMDb values, and is labelled as such.
- A 7-day cache of **previously fetched real responses** is used only when the network
  fails; the UI says so. Empty/error states are shown when no cached data exists.

Home row selection logic is deterministic and documented in code:
hero = real IMDb-sorted endpoint (fallback: newest); rows are labelled after the endpoint
that produced them (`تازه اضافهشدهها`, `سریالهای تازه`, `برترینهای IMDb`,
`انیمیشن و انیمه`, `سریال و فیلم کرهای`, `اکشن و هیجان`).

## 4. Screens

- **خانه** — greeting (real account or guest), search entry, avatar shortcut, hero carousel
  with auto-advance (disabled when system animations are off), continue watching,
  real catalogue rails, shimmer loading, honest error state.
- **کشف** — debounced real search, actor/director scope, genre chips derived from real
  genre metadata, real filters, recent searches, empty state.
- **کتابخانه** — continue watching / watch later / watched / personal lists, sort,
  list-grid toggle (persisted), search inside the library, contextual resume.
- **پروفایل** — real identity and avatar, real watch statistics, a 17-week activity
  heatmap built from recorded watch events, continue watching, and the settings entry.
- **جزئیات** — full-bleed artwork, poster, real metadata, expandable synopsis, cast/crew
  search, watchlist + share + trailer, season chips, episode rows with real thumbnails,
  per-episode progress and download, similar titles.
- **تنظیمات** — existing settings screen hosted from Profile (no longer a primary tab).

## 5. Player

Unchanged: Media3 engine, URL resolution, gestures, PiP, progress persistence, next
episode, external players, track selection logic.

Changed (presentation only, as authorised):

- One settings button opens a bottom sheet with real sections: subtitles, audio track,
  playback speed, aspect ratio, touch lock, external player.
- Track lists are built from the current `Tracks`; unavailable options are not shown.
- Default `DefaultTimeBar` is restyled at runtime: 6dp rounded track, 20dp thumb,
  Bisnor orange, buffered progress, larger touch target.

## 6. Build environment (Arch laptop)

Host: `nikan@192.168.100.69` (Arch Linux). Nothing was published or pushed.

- JDK 21 (`jdk21-openjdk`), Gradle 9.5 wrapper, Android SDK at `~/Android/Sdk`.
- `dl.google.com` is unreachable from this host, so the SDK and Maven artifacts were
  installed from mirrors (Tencent SDK mirror + Aliyun Google/Public Maven). This is build
  configuration only; no runtime endpoint was changed.
- `local.properties` is machine-local and contains the SDK path plus empty Supabase keys.

Commands:

```bash
cd ~/Projects/Bisnor
./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --console=plain
```

## 7. Verification (actual results)

On the Arch build host (`nikan-arch`, JDK 21, Gradle 9.5, AGP 9.3.2, SDK 35/36):

- `:app:assembleDebug` — **BUILD SUCCESSFUL**
- `:app:testDebugUnitTest` — **BUILD SUCCESSFUL**, 11 tests, 0 failures/errors:
  - ExampleUnitTest (1)
  - SupabaseCompressTest (3)
  - MediaMetadataHelperTest (3)
  - MediaMetadataNoFakeDataTest (2, new)
  - PlayerLauncherHelperTest (2)
- APK: `app/build/outputs/apk/debug/app-debug.apk`, 18 MB,
  `versionCode 33`, `versionName 5.2.0-beta.1`, `minSdk 24`, `targetSdk 35`.
- AAPT manifest verification: package `com.hnn.bisnor`, version metadata correct.

Runtime verification: **not performed**. `adb devices` reported no attached device or
emulator on the build host, and starting an emulator on this VM was outside the agreed
scope. Do not treat compilation as proof that the visual flows work on hardware.

## 9. Install

The APK is a debug-signed, installable Beta build:

```bash
adb install -r ~/bisnor-beta/Bisnor-5.2.0-beta.1-debug.apk
```

It is intentionally **not** uploaded to GitHub Releases, WinGet, any store, or any public
host, and the branch was not pushed.

## 10. Known limitations

- No device/emulator was available, so navigation, scrolling, playback and RTL layout
  were not exercised at runtime in this session.
- The Supabase URL/anon key are wired from the project's existing public desktop client
  configuration. Sign-in and sync were not exercised (no test account was supplied) and
  all authenticated flows therefore remain runtime-unverified.
- Stream “quality” selection is aspect-ratio selection; the catalogue does not expose
  per-stream quality tracks to the player.
- The seek bar uses Media3's public colour/key APIs plus a visual scale for thickness;
  Media3 1.5.1 exposes no public bar-height/thumb-size setters. No seeking logic changed.
- The heatmap uses locally recorded watch events; guests or fresh installs see an empty
  state until they watch something.
- Desktop/Tizen/PWA code paths were not touched.
