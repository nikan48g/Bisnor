/**
 * Samsung Tizen 5.0 (2019 Q-series) runs Chromium 63. Two library calls used by
 * the shared PWA client are newer than that engine. Without these, the client
 * throws at render time and the TV shows an empty shell.
 */
(function () {
    'use strict';

    if (typeof Array.prototype.flatMap !== 'function') {
        Array.prototype.flatMap = function (mapper, thisArg) {
            return Array.prototype.concat.apply([], this.map(mapper, thisArg));
        };
    }

    if (typeof Promise.allSettled !== 'function') {
        Promise.allSettled = function (iterable) {
            var pending = Array.prototype.map.call(iterable, function (item) {
                return Promise.resolve(item).then(
                    function (value) { return { status: 'fulfilled', value: value }; },
                    function (reason) { return { status: 'rejected', reason: reason }; }
                );
            });
            return Promise.all(pending);
        };
    }

    if (typeof Element.prototype.replaceChildren !== 'function') {
        Element.prototype.replaceChildren = function () {
            while (this.firstChild) this.removeChild(this.firstChild);
            if (arguments.length) Element.prototype.append.apply(this, arguments);
        };
    }

    // Chromium 63 has no AbortSignal.timeout, so a stalled request would hang
    // forever and the UI would stay empty with no error. Race every fetch
    // against a default deadline (callers can pass window.__tvFetchTimeout).
    var nativeFetch = window.fetch;
    if (typeof nativeFetch === 'function' && typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout !== 'function') {
        window.fetch = function (input, init) {
            var options = init || {};
            var deadline = options.__tvFetchTimeout || 15000;
            var request;
            try {
                request = nativeFetch.call(window, input, options);
            } catch (error) {
                return Promise.reject(error);
            }
            return new Promise(function (resolve, reject) {
                var settled = false;
                var timer = setTimeout(function () {
                    if (settled) return;
                    settled = true;
                    reject(new Error('timeout after ' + deadline + 'ms'));
                }, deadline);
                request.then(function (response) {
                    if (settled) return;
                    settled = true;
                    clearTimeout(timer);
                    resolve(response);
                }, function (error) {
                    if (settled) return;
                    settled = true;
                    clearTimeout(timer);
                    reject(error);
                });
            });
        };
    }
})();
