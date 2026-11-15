/**
 * Bisnor Cinema - progressive MKV/TS playback for browsers.
 *
 * IranFlix ships most titles as Matroska files, which no browser can demux.
 * This module reads the container with mediabunny, copies the encoded video
 * and audio into fragmented MP4 and feeds Media Source Extensions while the
 * file is still downloading. Seeking past the buffered window restarts the
 * conversion at the requested time instead of waiting for the whole file.
 */
(function () {
    "use strict";

    var VENDOR_URL = "js/vendor/mediabunny.min.mjs";
    var REMUX_PATTERN = /\.(mkv|m2ts|mka|ts)(?:$|[?#])/i;
    var HIGH_WATER_SECONDS = 45;
    var LOW_WATER_SECONDS = 20;
    var KEEP_BEHIND_SECONDS = 90;

    var vendorPromise = null;

    function loadVendor() {
        if (!vendorPromise) {
            vendorPromise = import(new URL(VENDOR_URL, document.baseURI).href).catch(function (error) {
                vendorPromise = null;
                throw error;
            });
        }
        return vendorPromise;
    }

    function canPlay(url) {
        if (!url || typeof MediaSource === "undefined") return false;
        return REMUX_PATTERN.test(String(url));
    }

    function isActive(video) {
        return Boolean(video && video.__bisnorRemux && video.__bisnorRemux.isActive());
    }

    function once(target, event) {
        return new Promise(function (resolve, reject) {
            function ok(event) {
                cleanup();
                resolve(event);
            }
            function bad() {
                cleanup();
                reject(new Error(event));
            }
            function cleanup() {
                target.removeEventListener(event, ok);
                target.removeEventListener("error", bad);
            }
            target.addEventListener(event, ok);
            target.addEventListener("error", bad);
        });
    }

    function wait(ms) {
        return new Promise(function (resolve) { setTimeout(resolve, ms); });
    }

    /**
     * @param {HTMLVideoElement} video
     * @param {{onStatus?: function(string):void, onReady?: function(number):void,
     *          onError?: function(string):void, onAudioDropped?: function(void):void}} options
     */
    function create(video, options) {
        var opts = options || {};
        var destroyed = false;
        var active = false;
        var mb = null;
        var input = null;
        var conversion = null;
        var mediaSource = null;
        var objectUrl = null;
        var sourceBuffer = null;
        var queue = Promise.resolve();
        var totalDuration = 0;
        var generation = 0;
        var mimeType = "";
        var discardAudio = false;

        function status(text) {
            if (!destroyed && typeof opts.onStatus === "function") opts.onStatus(text);
        }

        function fail(detail) {
            if (destroyed) return;
            if (typeof opts.onError === "function") opts.onError(detail);
        }

        function enqueue(task) {
            queue = queue.then(task, task);
            return queue;
        }

        function bufferedEnd() {
            if (!sourceBuffer || !sourceBuffer.buffered.length) return 0;
            return sourceBuffer.buffered.end(sourceBuffer.buffered.length - 1);
        }

        function segmentOperation(run) {
            return new Promise(function (resolve, reject) {
                if (destroyed || !sourceBuffer) {
                    reject(new Error("session-closed"));
                    return;
                }
                var buffer = sourceBuffer;
                function done() { cleanup(); resolve(); }
                function failed() { cleanup(); reject(new Error("source-buffer-error")); }
                function cleanup() {
                    buffer.removeEventListener("updateend", done);
                    buffer.removeEventListener("error", failed);
                }
                buffer.addEventListener("updateend", done);
                buffer.addEventListener("error", failed);
                try {
                    run(buffer);
                } catch (error) {
                    cleanup();
                    reject(error);
                }
            });
        }

        function appendChunk(data) {
            return enqueue(function () {
                return segmentOperation(function (buffer) { buffer.appendBuffer(data); });
            });
        }

        function trimBackBuffer(current) {
            if (!sourceBuffer || !sourceBuffer.buffered.length) return Promise.resolve();
            var start = sourceBuffer.buffered.start(0);
            if (current - start <= KEEP_BEHIND_SECONDS) return Promise.resolve();
            return enqueue(function () {
                return segmentOperation(function (buffer) {
                    buffer.remove(start, Math.max(start, current - KEEP_BEHIND_SECONDS));
                });
            });
        }

        function teardownPipeline() {
            if (conversion) {
                try { conversion.cancel(); } catch (_) { /* already finished */ }
                conversion = null;
            }
            if (sourceBuffer && mediaSource && mediaSource.readyState === "open") {
                try { mediaSource.endOfStream(); } catch (_) { /* nothing to end */ }
            }
            sourceBuffer = null;
            mediaSource = null;
            if (objectUrl) {
                try { URL.revokeObjectURL(objectUrl); } catch (_) { /* already revoked */ }
                objectUrl = null;
            }
            queue = Promise.resolve();
        }

        function codecMime(videoParams, audioParams) {
            var codecs = videoParams ? [videoParams] : [];
            if (audioParams) codecs.push(audioParams);
            return 'video/mp4; codecs="' + codecs.join(",") + '"';
        }

        function openMediaSource(startAt) {
            mediaSource = new MediaSource();
            objectUrl = URL.createObjectURL(mediaSource);
            video.src = objectUrl;
            return once(mediaSource, "sourceopen").then(function () {
                if (destroyed) throw new Error("session-closed");
                sourceBuffer = mediaSource.addSourceBuffer(mimeType);
                sourceBuffer.mode = "segments";
                // The trimmed conversion restarts timestamps at zero, so shift
                // them back onto the movie timeline the seek bar shows.
                if (startAt > 0.5) sourceBuffer.timestampOffset = startAt;
                if (totalDuration > 0) {
                    try { mediaSource.duration = totalDuration; } catch (_) { /* set later */ }
                }
                if (startAt > 0.5) {
                    try { video.currentTime = startAt; } catch (_) { /* not seekable yet */ }
                }
            });
        }

        function runConversion(startAt, token) {
            var writable = new WritableStream({
                write: function (chunk) {
                    if (destroyed || token !== generation) throw new Error("session-closed");
                    var data = chunk.data instanceof Uint8Array ? chunk.data : new Uint8Array(chunk.data);
                    return appendChunk(data).then(function () {
                        if (destroyed || token !== generation) return;
                        if (!active) {
                            active = true;
                            status("");
                            if (typeof opts.onReady === "function") opts.onReady(totalDuration);
                            video.play().catch(function () { /* user gesture required */ });
                        }
                        var ahead = bufferedEnd() - video.currentTime;
                        if (ahead <= HIGH_WATER_SECONDS) return null;
                        return (function waitForRoom() {
                            return wait(250).then(function () {
                                if (destroyed || token !== generation) return;
                                var room = bufferedEnd() - video.currentTime;
                                if (room > LOW_WATER_SECONDS) return waitForRoom();
                                return trimBackBuffer(video.currentTime);
                            });
                        })();
                    });
                }
            });

            var output = new mb.Output({
                format: new mb.Mp4OutputFormat({ fastStart: "fragmented" }),
                target: new mb.StreamTarget(writable, { chunked: true, chunkSize: 1024 * 1024 })
            });

            var conversionOptions = {
                input: input,
                output: output,
                copy: { mode: "forced" }
            };
            if (startAt > 0.5) conversionOptions.trim = { start: startAt };
            if (discardAudio) conversionOptions.audio = { discard: true };

            return mb.Conversion.init(conversionOptions).then(function (result) {
                if (!result.isValid) throw new Error("conversion-invalid");
                conversion = result;
                return result.execute();
            });
        }

        function startSession(startAt) {
            generation += 1;
            var token = generation;
            active = false;
            teardownPipeline();
            status(startAt > 0.5 ? "در حال آماده‌سازی پخش از این نقطه…" : "در حال آماده‌سازی پخش…");
            return openMediaSource(startAt)
                .then(function () { return runConversion(startAt, token); })
                .then(function () {
                    if (destroyed || token !== generation) return;
                    try { mediaSource && mediaSource.readyState === "open" && mediaSource.endOfStream(); } catch (_) { /* noop */ }
                    if (totalDuration > 0) {
                        try { mediaSource.duration = totalDuration; } catch (_) { /* noop */ }
                    }
                })
                .catch(function (error) {
                    if (destroyed || token !== generation) return;
                    var message = String((error && error.message) || error);
                    if (message === "session-closed" || message.indexOf("AbortError") >= 0) return;
                    fail(message === "conversion-invalid" ? "codec" : "remux-error");
                });
        }

        function start(url, startAt) {
            status("در حال خواندن فایل…");
            return loadVendor()
                .then(function (mod) {
                    mb = mod;
                    input = new mb.Input({ source: new mb.UrlSource(url), formats: mb.ALL_FORMATS });
                    return input.getPrimaryVideoTrack();
                })
                .then(function (videoTrack) {
                    if (!videoTrack) throw new Error("no-video-track");
                    return Promise.all([
                        videoTrack.getCodecParameterString(),
                        videoTrack.getDisplayWidth(),
                        input.getPrimaryAudioTrack(),
                        input.computeDuration()
                    ]);
                })
                .then(function (values) {
                    var videoParams = values[0];
                    var audioTrack = values[2];
                    totalDuration = values[3] || 0;
                    if (!videoParams) throw new Error("unknown-codec");
                    var videoMime = codecMime(videoParams, null);
                    if (!MediaSource.isTypeSupported(videoMime)) {
                        throw new Error("unsupported-video-codec");
                    }
                    if (!audioTrack) {
                        discardAudio = true;
                        mimeType = videoMime;
                        status("این نسخه صدای جدا ندارد.");
                    } else {
                        return audioTrack.getCodecParameterString().then(function (audioParams) {
                            var withAudio = codecMime(videoParams, audioParams);
                            if (audioParams && MediaSource.isTypeSupported(withAudio)) {
                                mimeType = withAudio;
                            } else {
                                discardAudio = true;
                                mimeType = videoMime;
                                if (typeof opts.onAudioDropped === "function") opts.onAudioDropped();
                            }
                        });
                    }
                })
                .then(function () {
                    return startSession(Math.max(0, startAt || 0));
                })
                .catch(function (error) {
                    if (destroyed) return;
                    var message = String((error && error.message) || error);
                    if (message === "session-closed") return;
                    status("");
                    fail(/no-video|unknown-codec|unsupported-video-codec|conversion/.test(message) ? "codec" : "remux-error");
                });
        }

        function seek(time) {
            if (destroyed || !sourceBuffer || !Number.isFinite(time)) return;
            var start = sourceBuffer.buffered.length ? sourceBuffer.buffered.start(0) : 0;
            var end = bufferedEnd();
            if (time >= start && time <= end) {
                try { video.currentTime = time; } catch (_) { /* ignore */ }
                return;
            }
            return startSession(Math.max(0, time));
        }

        function destroy() {
            destroyed = true;
            active = false;
            teardownPipeline();
            input = null;
        }

        var controller = {
            start: start,
            seek: seek,
            destroy: destroy,
            // True from the moment the MediaSource exists, not just once the
            // first segment lands: the shell must not seek the blob URL or
            // treat its startup errors as a codec failure.
            isActive: function () { return Boolean(mediaSource); },
            duration: function () { return totalDuration; }
        };
        video.__bisnorRemux = controller;
        return controller;
    }

    window.BisnorRemux = {
        canPlay: canPlay,
        isActive: isActive,
        create: create
    };
})();
