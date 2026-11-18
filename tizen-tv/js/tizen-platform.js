/**
 * Bisnor TV platform layer.
 *
 * The TV build shares the PWA markup and controller, but playback runs on
 * AVPlay's hardware plane instead of a <video> element.  This module makes the
 * existing element look like a playing video (duration/currentTime/paused plus
 * the metadata/timeupdate/ended events) so the shared player UI keeps working.
 */
(function () {
    "use strict";

    window.BISNOR_TIZEN = true;
    window.BISNOR_CATALOG_ORIGINS = ["https://bisnor-pwa.drive-storage-hnn.workers.dev"];

    var state = {
        video: null,
        duration: 0,
        time: 0,
        paused: true,
        startedAt: 0,
        ticker: null
    };

    function emit(video, name) {
        try { video.dispatchEvent(new Event(name)); } catch (_) { /* old engines */ }
    }

    function stopTicker() {
        if (state.ticker) {
            clearInterval(state.ticker);
            state.ticker = null;
        }
    }

    function bind(video) {
        if (!video || video.__bisnorTvBound) return video;
        video.__bisnorTvBound = true;

        Object.defineProperty(video, "duration", {
            configurable: true,
            get: function () { return state.duration; }
        });
        Object.defineProperty(video, "currentTime", {
            configurable: true,
            get: function () { return state.time; },
            set: function (value) {
                var target = Math.max(0, Number(value) || 0);
                state.time = target;
                if (window.TizenAVPlayEngine) window.TizenAVPlayEngine.seek(target);
            }
        });
        Object.defineProperty(video, "paused", {
            configurable: true,
            get: function () { return state.paused; }
        });
        Object.defineProperty(video, "readyState", {
            configurable: true,
            get: function () { return state.duration ? 4 : 0; }
        });

        video.play = function () {
            state.paused = false;
            if (window.TizenAVPlayEngine) window.TizenAVPlayEngine.resume();
            return Promise.resolve();
        };
        video.pause = function () {
            state.paused = true;
            if (window.TizenAVPlayEngine) window.TizenAVPlayEngine.pause();
        };
        video.load = function () { /* AVPlay owns the stream */ };

        return video;
    }

    function play(video, url, startAt, handlers) {
        var engine = window.TizenAVPlayEngine;
        var hooks = handlers || {};
        bind(video);
        engine.stop();
        state.video = video;
        state.duration = 0;
        state.time = startAt > 0 ? startAt : 0;
        state.paused = false;
        state.startedAt = Date.now();
        stopTicker();

        // The AVPlay plane is drawn beneath the web layer, so the opaque player
        // container would cover the picture with black. Let the hardware plane
        // show through while a TV stream is active.
        var modal = document.getElementById("modal-player");
        if (modal) modal.classList.add("tv-avplay");

        var metadataSent = false;
        var started = engine.play(
            url,
            "Bisnor",
            function (current, duration) {
                state.time = current;
                if (!metadataSent && duration > 0) {
                    metadataSent = true;
                    state.duration = duration;
                    emit(video, "loadedmetadata");
                    if (typeof hooks.onReady === "function") hooks.onReady();
                }
                emit(video, "timeupdate");
            },
            function () {
                state.paused = true;
                stopTicker();
                emit(video, "ended");
            },
            function (detail) {
                state.paused = true;
                stopTicker();
                if (typeof hooks.onError === "function") hooks.onError(detail);
            },
            startAt || 0
        );

        if (!started) return false;

        // oncurrentplaytime is not periodic enough on some firmwares; keep the
        // seek bar and progress saving alive even when it stalls.
        state.ticker = setInterval(function () {
            if (state.paused) return;
            if (!state.duration) {
                var duration = engine.duration ? Math.floor(engine.duration / 1000) : 0;
                if (duration > 0) {
                    state.duration = duration;
                    if (!metadataSent) {
                        metadataSent = true;
                        emit(video, "loadedmetadata");
                    }
                }
            }
            emit(video, "timeupdate");
        }, 1000);

        return true;
    }

    function stop() {
        stopTicker();
        state.paused = true;
        var modal = document.getElementById("modal-player");
        if (modal) modal.classList.remove("tv-avplay");
        if (window.TizenAVPlayEngine) window.TizenAVPlayEngine.stop();
    }

    function inspect() {
        var result = { state: null, tracks: [], selected: null };
        if (!window.webapis || !window.webapis.avplay) return result;
        var avplay = window.webapis.avplay;
        try { result.state = avplay.getState(); } catch (error) { result.state = "error:" + error; }
        try { result.tracks = avplay.getTotalTrackInfo() || []; } catch (error) { result.tracks = "error:" + error; }
        try { result.selected = avplay.getCurrentStreamInfo ? avplay.getCurrentStreamInfo() : null; } catch (error) {}
        try { result.subtitles = avplay.getSubtitlePosition ? avplay.getSubtitlePosition() : null; } catch (error) {}
        return result;
    }

    window.BisnorTvPlayer = {
        isAvailable: function () {
            return Boolean(window.TizenAVPlayEngine && window.TizenAVPlayEngine.isTizen());
        },
        bind: bind,
        play: play,
        stop: stop,
        inspect: inspect,
        state: state
    };
})();
