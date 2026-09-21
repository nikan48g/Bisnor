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

        init: function (containerEl) {
            console.log("[TizenAVPlay] Engine initialized.");
            if (!this.isTizen()) {
                console.log("[TizenAVPlay] Not on Samsung TV. Standard HTML5 player will be used.");
                return;
            }
        },

        play: function (url, title, onProgress, onEnded, onError) {
            this.currentUrl = url;
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

                // Prepare and start
                avplay.prepareAsync(function () {
                    self.duration = avplay.getDuration();
                    avplay.play();
                    self.playerState = 'PLAYING';
                    console.log("[TizenAVPlay] Hardware Playback Started Successfully!");
                }, function (err) {
                    console.error("[TizenAVPlay] Prepare error:", err);
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

        stop: function () {
            if (this.isTizen()) {
                try {
                    window.webapis.avplay.stop();
                    window.webapis.avplay.close();
                } catch (e) {}
                this.playerState = 'NONE';
            }
        }
    };
})();
