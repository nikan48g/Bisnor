/**
 * Bisnor Cinema - Samsung Tizen Native AVPlay Hardware Video Engine
 * Uses webapis.avplay for seamless playback of MKV, MP4, HLS, 4K HEVC, and Dolby Audio on Samsung Smart TVs.
 */
(function () {
    'use strict';

    window.TizenAVPlayEngine = {
        isTizen: function () {
            return typeof window.webapis !== 'undefined' && typeof window.webapis.avplay !== 'undefined';
        },

        playerState: 'NONE', // NONE, IDLE, READY, PLAYING, PAUSED
        currentUrl: null,
        duration: 0,
        timer: null,
        prepareTimer: null,

        init: function (containerEl) {
            console.log("[TizenAVPlay] Engine initialized.");
            if (!this.isTizen()) {
                console.log("[TizenAVPlay] Not on Samsung TV. Standard HTML5 player will be used.");
                return;
            }
        },

        play: function (url, title, onProgress, onEnded, onError, startAt) {
            this.currentUrl = url;
            this.subtitleIndex = -1;
            this.clearPrepareTimer();
            console.log("[TizenAVPlay] Starting playback for:", title, url);

            if (!this.isTizen()) {
                console.warn("[TizenAVPlay] webapis.avplay not available. Fallback to HTML5 video.");
                return false;
            }

            try {
                var avplay = window.webapis.avplay;

                // Stop any existing stream
                try {
                    avplay.stop();
                    avplay.close();
                } catch (e) {}

                // Open new stream
                avplay.open(url);

                // Samsung AVPlay coordinates use the logical 1920x1080 TV plane and
                // are scaled by the TV. Browser screen dimensions can report 960x540
                // or 1280x720 and would leave playback covering only part of the panel.
                avplay.setDisplayRect(0, 0, 1920, 1080);
                avplay.setDisplayMethod('PLAYER_DISPLAY_MODE_AUTO_ASPECT_RATIO');

                var self = this;
                var listener = {
                    onbufferingstart: function () {
                        console.log("[TizenAVPlay] Buffering started...");
                        var loadEl = document.getElementById("player-loading-spinner");
                        if (loadEl) loadEl.style.display = "block";
                    },
                    onbufferingprogress: function (percent) {
                        console.log("[TizenAVPlay] Buffering: " + percent + "%");
                    },
                    onbufferingcomplete: function () {
                        console.log("[TizenAVPlay] Buffering complete.");
                        var loadEl = document.getElementById("player-loading-spinner");
                        if (loadEl) loadEl.style.display = "none";
                    },
                    oncurrentplaytime: function (currentTimeMs) {
                        var curSec = Math.floor(currentTimeMs / 1000);
                        var durSec = Math.floor(self.duration / 1000);
                        if (typeof onProgress === 'function') {
                            onProgress(curSec, durSec);
                        }
                    },
                    onevent: function (eventType, eventData) {
                        console.log("[TizenAVPlay] Event: " + eventType, eventData);
                    },
                    onerror: function (errorType) {
                        console.error("[TizenAVPlay] Playback error: " + errorType);
                        if (typeof onError === 'function') onError(errorType);
                    },
                    onstreamcompleted: function () {
                        console.log("[TizenAVPlay] Stream completed.");
                        self.playerState = 'IDLE';
                        if (typeof onEnded === 'function') onEnded();
                    }
                };

                avplay.setListener(listener);

                // Some MKV/audio combinations leave prepareAsync pending forever on
                // Tizen 5/6 firmware: there is neither a callback nor an onerror, so
                // the viewer only sees the spinner. Fail over after a hard deadline.
                var prepareTimedOut = false;
                this.prepareTimer = setTimeout(function () {
                    if (self.playerState === 'PLAYING') return;
                    prepareTimedOut = true;
                    console.error("[TizenAVPlay] prepareAsync timed out for", url);
                    try { avplay.stop(); } catch (ignore) {}
                    try { avplay.close(); } catch (ignore) {}
                    self.playerState = 'NONE';
                    if (typeof onError === 'function') onError('prepare-timeout');
                }, 20000);

                // Prepare and start
                avplay.prepareAsync(function () {
                    self.clearPrepareTimer();
                    if (prepareTimedOut) return;
                    self.duration = avplay.getDuration();
                    // Reapply after preparation: several Tizen 5 models discard the
                    // display rectangle set while the player is still IDLE.
                    avplay.setDisplayRect(0, 0, 1920, 1080);
                    avplay.play();
                    self.playerState = 'PLAYING';
                    // Resuming after a quality switch: jump back to the spot the
                    // viewer was watching instead of restarting the title.
                    if (startAt > 1) {
                        try { avplay.seekTo(Math.floor(startAt * 1000)); } catch (ignore) {}
                    }
                    // Prefer the first embedded text track. This is deliberately
                    // best-effort: a source without an embedded subtitle cannot
                    // receive one from AVPlay alone.
                    setTimeout(function () { self.selectSubtitle(0); }, 1000);
                    console.log("[TizenAVPlay] Hardware Playback Started Successfully!");
                }, function (err) {
                    self.clearPrepareTimer();
                    console.error("[TizenAVPlay] Prepare error:", err);
                    try { avplay.stop(); } catch (ignore) {}
                    try { avplay.close(); } catch (ignore) {}
                    self.playerState = 'NONE';
                    if (typeof onError === 'function') onError(err);
                });

                return true;
            } catch (err) {
                console.error("[TizenAVPlay] Execution exception:", err);
                if (typeof onError === 'function') onError(err);
                return false;
            }
        },

        pause: function () {
            if (this.isTizen() && this.playerState === 'PLAYING') {
                try {
                    window.webapis.avplay.pause();
                    this.playerState = 'PAUSED';
                } catch (e) {}
            }
        },

        resume: function () {
            if (this.isTizen() && this.playerState === 'PAUSED') {
                try {
                    window.webapis.avplay.play();
                    this.playerState = 'PLAYING';
                } catch (e) {}
            }
        },

        seek: function (timeSeconds) {
            if (this.isTizen()) {
                try {
                    window.webapis.avplay.seekTo(timeSeconds * 1000);
                } catch (e) {}
            }
        },

        selectSubtitle: function (offset) {
            if (!this.isTizen()) return { ok: false, message: 'پخش‌کنندهٔ تلویزیون در دسترس نیست.' };
            try {
                var avplay = window.webapis.avplay;
                var tracks = avplay.getTotalTrackInfo() || [];
                var textTracks = [];
                for (var i = 0; i < tracks.length; i += 1) if (String(tracks[i].type || '').toUpperCase() === 'TEXT') textTracks.push(tracks[i]);
                if (!textTracks.length) return { ok: false, message: 'این فایل ترک زیرنویس داخلی قابل‌پخش روی TV ندارد.' };
                var current = typeof this.subtitleIndex === 'number' ? this.subtitleIndex : -1;
                var next = (current + (offset || 1) + textTracks.length) % textTracks.length;
                if (typeof avplay.setSilentSubtitle === 'function') avplay.setSilentSubtitle(false);
                avplay.setSelectTrack('TEXT', textTracks[next].index);
                this.subtitleIndex = next;
                return { ok: true, message: 'زیرنویس ' + (next + 1) + ' از ' + textTracks.length + ' فعال شد.' };
            } catch (error) {
                return { ok: false, message: 'فعال‌سازی زیرنویس ممکن نشد: ' + String(error) };
            }
        },

        stop: function () {
            this.clearPrepareTimer();
            if (this.isTizen()) {
                try {
                    window.webapis.avplay.stop();
                    window.webapis.avplay.close();
                } catch (e) {}
                this.playerState = 'NONE';
            }
        },

        clearPrepareTimer: function () {
            if (this.prepareTimer) {
                clearTimeout(this.prepareTimer);
                this.prepareTimer = null;
            }
        }
    };
})();
