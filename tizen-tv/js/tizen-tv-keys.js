/**
 * Tizen TV remote controller: spatial navigation, back handling and the
 * in-player transport keys for the shared Bisnor shell.
 */
(function () {
    "use strict";

    var returnFocusEl = null;
    var lastBackAt = 0;

    function registerTizenKeys() {
        if (!window.tizen || !window.tizen.tvinputdevice) return;
        ["MediaPlayPause", "MediaPlay", "MediaPause", "MediaStop", "MediaFastForward", "MediaRewind"].forEach(function (key) {
            try { window.tizen.tvinputdevice.registerKey(key); } catch (_) { /* old firmware */ }
        });
    }

    function shown(el) {
        return Boolean(el) && el.style.display !== "none";
    }

    function isTextField(el) {
        if (!el || !el.tagName) return false;
        var tag = el.tagName.toLowerCase();
        if (tag === "textarea") return true;
        if (tag !== "input") return false;
        var type = (el.getAttribute("type") || "text").toLowerCase();
        return ["text", "search", "email", "password", "url", "tel", "number"].indexOf(type) !== -1;
    }

    function nativeActivation(el) {
        if (!el || !el.tagName) return false;
        var tag = el.tagName.toLowerCase();
        return ["button", "a", "input", "select", "textarea"].indexOf(tag) !== -1;
    }

    function isFocusable(el) {
        if (!el || el.disabled) return false;
        var style = window.getComputedStyle(el);
        if (style.display === "none" || style.visibility === "hidden" || style.opacity === "0") return false;
        var rect = el.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
    }

    function focusableList() {
        var scope = document;
        var detail = document.getElementById("modal-detail");
        var player = document.getElementById("modal-player");
        if (shown(detail)) scope = detail;
        else if (shown(player)) scope = player;
        else {
            var open = document.querySelector(".auth-dialog-overlay.active, .detail-overlay.active, .avatar-picker-overlay.active, .random-suggest-overlay.active, #screen-settings.active");
            if (open && shown(open)) scope = open;
        }

        var nodes = scope.querySelectorAll(
            "button, [href], input, select, textarea, [tabindex=\"0\"], .media-card, .clickable-row, " +
            ".player-option-card, .avatar-item-choice, .chip-theme, .person-card, .season-tab, .nav-item"
        );
        var result = [];
        for (var i = 0; i < nodes.length; i += 1) {
            var node = nodes[i];
            if (scope === document && node.closest(".detail-overlay, .player-overlay, .auth-dialog-overlay, .avatar-picker-overlay, .random-suggest-overlay")) continue;
            // Layout divs are not focusable until they get a tabindex.
            if (node.tabIndex < 0 && node.matches(".media-card, .clickable-row, .player-option-card, .avatar-item-choice, .chip-theme, .person-card, .season-tab")) {
                node.tabIndex = 0;
            }
            if (isFocusable(node)) result.push(node);
        }
        return result;
    }

    function reveal(el) {
        el.focus();
        try { el.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "nearest" }); }
        catch (_) { el.scrollIntoView(false); }
    }

    function move(keyCode) {
        var list = focusableList();
        if (!list.length) return;
        var current = document.activeElement;
        if (list.indexOf(current) === -1) { reveal(list[0]); return; }

        var base = current.getBoundingClientRect();
        var cx = base.left + base.width / 2;
        var cy = base.top + base.height / 2;
        var best = null;
        var bestScore = Infinity;

        list.forEach(function (el) {
            if (el === current) return;
            var r = el.getBoundingClientRect();
            var dx = (r.left + r.width / 2) - cx;
            var dy = (r.top + r.height / 2) - cy;
            var valid = keyCode === 38 ? dy < -10 : keyCode === 40 ? dy > 10 : keyCode === 37 ? dx < -10 : dx > 10;
            if (!valid) return;
            var main = (keyCode === 38 || keyCode === 40) ? Math.abs(dy) : Math.abs(dx);
            var cross = (keyCode === 38 || keyCode === 40) ? Math.abs(dx) : Math.abs(dy);
            var score = main + cross * 2.5;
            if (score < bestScore) { bestScore = score; best = el; }
        });

        if (best) reveal(best);
    }

    function rememberFocus(el) {
        if (el && el !== document.body) returnFocusEl = el;
        setTimeout(function () {
            var list = focusableList();
            if (!list.length) return;
            if (list.indexOf(document.activeElement) === -1) reveal(list[0]);
        }, 60);
    }

    function activate() {
        var focused = document.activeElement;
        if (!focused || focused === document.body) {
            var list = focusableList();
            if (list.length) reveal(list[0]);
            return;
        }
        if (!nativeActivation(focused)) rememberFocus(focused);
        focused.click();
    }

    function playerCommand(command) {
        var app = window.app;
        if (!app) return;
        if (command === "close") { app.closePlayerModal(); return; }
        var ids = {
            rewind: "btn-player-rewind",
            forward: "btn-player-forward",
            toggle: "btn-player-play-pause",
            subtitle: "btn-player-subs",
            quality: "btn-player-quality"
        };
        var target = document.getElementById(ids[command]);
        if (target) target.click();
    }

    function sheetOpen() {
        return shown(document.getElementById("player-sheet"));
    }

    function back() {
        var now = Date.now();
        if (now - lastBackAt < 300) return;
        lastBackAt = now;

        if (sheetOpen()) {
            document.getElementById("btn-close-player-sheet")?.click();
            return;
        }
        if (shown(document.getElementById("modal-player"))) {
            window.app?.closePlayerModal();
            if (returnFocusEl && document.body.contains(returnFocusEl)) returnFocusEl.focus();
            return;
        }
        if (shown(document.getElementById("modal-detail"))) {
            window.app?.closeDetailModal();
            if (returnFocusEl && document.body.contains(returnFocusEl)) returnFocusEl.focus();
            return;
        }
        var open = document.querySelector(
            ".auth-dialog-overlay.active, .detail-overlay.active, .avatar-picker-overlay.active, .random-suggest-overlay.active"
        );
        if (open && open.id !== "modal-detail") {
            var closeBtn = open.querySelector(".detail-close-btn, .modal-close-btn, .player-sheet-close");
            if (closeBtn) { closeBtn.click(); return; }
        }
        if (document.getElementById("screen-settings")?.classList.contains("active")) {
            window.app?.closeSettingsScreen();
            return;
        }
        if (window.tizen && window.tizen.application) {
            try { window.tizen.application.getCurrentApplication().exit(); } catch (_) { window.history.back(); }
        }
    }

    document.addEventListener("tizenhwkey", function (event) {
        if (event.keyName === "back") back();
    });

    window.addEventListener("keydown", function (event) {
        var key = event.keyCode;
        var playing = shown(document.getElementById("modal-player"));

        if (playing && !sheetOpen()) {
            var command = null;
            if (key === 37 || key === 412) command = "rewind";
            else if (key === 39 || key === 417) command = "forward";
            else if (key === 13 || key === 415 || key === 10252) command = "toggle";
            else if (key === 19) command = "toggle";
            else if (key === 38) command = "quality";
            else if (key === 40) command = "subtitle";
            else if (key === 10009 || key === 27) command = "close";
            if (command) {
                playerCommand(command);
                event.preventDefault();
                event.stopPropagation();
                return;
            }
        }

        switch (key) {
            case 10009:
            case 27:
                back();
                event.preventDefault();
                event.stopPropagation();
                return;
            case 415:
            case 19:
            case 10252:
                document.getElementById("btn-player-play-pause")?.click();
                event.preventDefault();
                return;
            case 37:
            case 38:
            case 39:
            case 40: {
                var active = document.activeElement;
                if (isTextField(active) && (key === 37 || key === 39)) {
                    var length = active.value ? active.value.length : 0;
                    var pos = typeof active.selectionStart === "number" ? active.selectionStart : 0;
                    if (length && pos > 0 && pos < length) return;
                }
                document.body.classList.add("tv-keynav");
                move(key);
                event.preventDefault();
                return;
            }
            case 13:
                document.body.classList.add("tv-keynav");
                if (nativeActivation(document.activeElement)) return;
                event.preventDefault();
                activate();
                return;
        }
    }, true);

    document.addEventListener("mousemove", function () { document.body.classList.remove("tv-keynav"); }, true);
    document.addEventListener("mousedown", function () { document.body.classList.remove("tv-keynav"); }, true);

    window.addEventListener("DOMContentLoaded", registerTizenKeys);
})();
