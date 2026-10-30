/**
 * Tizen TV Remote Controller & Spatial Navigation Engine for Bisnor Cinema
 * Handles D-Pad navigation, remote OK/Enter, Return/Back, and Media controls.
 */
(function () {
    'use strict';

    // Register Tizen TV Remote keys
    function registerTizenKeys() {
        if (window.tizen && window.tizen.tvinputdevice) {
            try {
                // Arrows, Enter and Return are delivered without registration. Exit and the
                // number keys are left to the system because the app never handles them.
                var keysToRegister = [
                    'MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop',
                    'MediaFastForward', 'MediaRewind'
                ];
                for (var i = 0; i < keysToRegister.length; i += 1) {
                    try {
                        window.tizen.tvinputdevice.registerKey(keysToRegister[i]);
                    } catch (e) {}
                }
            } catch (err) {
                console.warn('tizen.tvinputdevice error:', err);
            }
        }
    }

    function isShown(el) {
        return !!el && el.style.display !== 'none' && el.style.display !== '';
    }

    // Element that opened the current modal, so Back can return focus to it.
    var returnFocusEl = null;
    var lastBackAt = 0;

    // Handle back/return key of Samsung TV
    document.addEventListener('tizenhwkey', function (e) {
        if (e.keyName === 'back') {
            handleBackAction();
        }
    });

    function restoreFocus() {
        var target = returnFocusEl;
        returnFocusEl = null;
        if (target && document.body.contains(target) && isFocusable(target)) {
            target.focus();
        }
    }

    function handleBackAction() {
        // Some firmwares emit both tizenhwkey 'back' and keydown 10009 for one press.
        var now = Date.now();
        if (now - lastBackAt < 300) return;
        lastBackAt = now;

        // 1. If player is open, close player
        var playerModal = document.getElementById('modal-player');
        if (isShown(playerModal)) {
            if (typeof window.bisnorTvClosePlayer === 'function') { window.bisnorTvClosePlayer(); restoreFocus(); return; }
            if (window.app && typeof window.app.closePlayerModal === 'function') {
                window.app.closePlayerModal();
            } else {
                playerModal.style.display = 'none';
            }
            restoreFocus();
            return;
        }

        // 2. If detail modal is open, close it
        var detailModal = document.getElementById('modal-detail');
        if (isShown(detailModal)) {
            if (window.app && typeof window.app.closeDetailModal === 'function') {
                window.app.closeDetailModal();
            } else {
                detailModal.style.display = 'none';
            }
            restoreFocus();
            return;
        }

        // 3. If any other modal is open
        var activeModal = document.querySelector('.modal.show, .detail-modal.active, .player-container.active, .detail-overlay');
        if (activeModal && activeModal.style.display !== 'none') {
            var closeBtn = activeModal.querySelector('.btn-close, .close-btn, .detail-close-btn');
            if (closeBtn) {
                closeBtn.click();
                return;
            }
        }

        // 4. Otherwise prompt to exit app
        if (window.tizen && window.tizen.application) {
            try {
                window.tizen.application.getCurrentApplication().exit();
            } catch (err) {
                window.history.back();
            }
        }
    }

    function isTextField(el) {
        if (!el || !el.tagName) return false;
        var tag = el.tagName.toLowerCase();
        if (tag === 'textarea') return true;
        if (tag !== 'input') return false;
        var type = (el.getAttribute('type') || 'text').toLowerCase();
        return type === 'text' || type === 'search' || type === 'email' || type === 'password' ||
            type === 'url' || type === 'tel' || type === 'number';
    }

    // Elements that already turn Enter into a click natively; clicking them again would fire twice.
    function activatesNatively(el) {
        if (!el || !el.tagName) return false;
        var tag = el.tagName.toLowerCase();
        return tag === 'button' || tag === 'a' || tag === 'input' || tag === 'select' || tag === 'textarea';
    }

    function setKeyNavMode(on) {
        var body = document.body;
        if (!body) return;
        if (on) body.classList.add('tv-keynav');
        else body.classList.remove('tv-keynav');
    }

    // While the in-player quality sheet is open the D-pad drives the sheet,
    // not the transport controls underneath it.
    function handleQualitySheetKeys(e, key) {
        var sheet = document.getElementById('player-quality-sheet');
        if (!isShown(sheet)) return false;
        if (key === 10009 || key === 27) {
            if (typeof window.bisnorTvCloseQuality === 'function') window.bisnorTvCloseQuality();
            e.preventDefault();
            e.stopPropagation();
            return true;
        }
        if (key === 38 || key === 40) {
            var options = sheet.querySelectorAll('.quality-option');
            if (!options.length) return true;
            var current = -1;
            for (var i = 0; i < options.length; i += 1) if (options[i] === document.activeElement) current = i;
            var next = current + (key === 40 ? 1 : -1);
            if (next < 0) next = 0;
            if (next >= options.length) next = options.length - 1;
            options[next].focus();
            e.preventDefault();
            e.stopPropagation();
            return true;
        }
        if (key === 13 || key === 415) {
            var focused = document.activeElement;
            if (focused && String(focused.className || '').indexOf('quality-option') !== -1) focused.click();
            else {
                var closeBtn = document.getElementById('btn-close-quality-sheet');
                if (closeBtn) closeBtn.click();
            }
            e.preventDefault();
            e.stopPropagation();
            return true;
        }
        return true;
    }

    // Keydown listener for standard keyboard and Samsung TV remote
    window.addEventListener('keydown', function (e) {
        var key = e.keyCode;
        if (handleQualitySheetKeys(e, key)) return;
        var playerModal = document.getElementById('modal-player');
        var playerOpen = isShown(playerModal);

        // AVPlay can consume the default D-pad focus movement. Handle the
        // physical Samsung remote first while playback is active.
        if (playerOpen && typeof window.bisnorTvPlayerCommand === 'function') {
            var command = null;
            if (key === 37 || e.key === 'ArrowLeft' || key === 412) command = 'rewind';
            else if (key === 39 || e.key === 'ArrowRight' || key === 417) command = 'forward';
            else if (key === 13 || e.key === 'Enter' || key === 415 || key === 10252) command = 'toggle';
            else if (key === 19 || e.key === 'MediaPause') command = 'toggle';
            else if (key === 10009 || key === 27 || key === 413) command = 'close';
            else if (key === 38 || e.key === 'ArrowUp') command = 'subtitle';
            else if (key === 40 || e.key === 'ArrowDown') command = 'quality';
            if (command) {
                // Route close through handleBackAction so a duplicate tizenhwkey 'back'
                // for the same press is debounced instead of exiting the app.
                if (command === 'close') handleBackAction();
                else window.bisnorTvPlayerCommand(command);
                e.preventDefault();
                e.stopPropagation();
                return;
            }
        }

        switch (key) {
            case 10009: // Tizen Return / Back
            case 27:    // Esc
                handleBackAction();
                e.preventDefault();
                e.stopPropagation();
                break;

            case 415: // MediaPlay
            case 19:  // MediaPause
            case 10252: // MediaPlayPause
                var playBtn = document.getElementById('btn-player-play-pause');
                if (playBtn) playBtn.click();
                break;

            case 37: // Left
            case 38: // Up
            case 39: // Right
            case 40: // Down
                var active = document.activeElement;
                // Keep caret movement inside text fields; vertical keys still leave the field.
                if (isTextField(active) && (key === 37 || key === 39)) {
                    var len = active.value ? active.value.length : 0;
                    var pos = typeof active.selectionStart === 'number' ? active.selectionStart : 0;
                    if (len && pos > 0 && pos < len) return;
                }
                setKeyNavMode(true);
                navigateSpatial(key);
                // Without this the page also scrolls natively on top of the focus move.
                e.preventDefault();
                break;

            case 13: // Enter / remote OK
                setKeyNavMode(true);
                var focused = document.activeElement;
                if (activatesNatively(focused)) {
                    rememberReturnFocus(focused);
                    break;
                }
                e.preventDefault();
                triggerActiveElement();
                break;
        }
    }, true);

    // Any pointer movement (Magic Remote / mouse) goes back to pointer mode without focus rings.
    function leaveKeyNav() { setKeyNavMode(false); }
    document.addEventListener('mousemove', leaveKeyNav, true);
    document.addEventListener('mousedown', leaveKeyNav, true);

    function isInsideModal(el) {
        var node = el;
        while (node && node !== document.body) {
            if (node.id === 'modal-detail' || node.id === 'modal-player') return true;
            node = node.parentNode;
        }
        return false;
    }

    function rememberReturnFocus(el) {
        if (el && el !== document.body && !isInsideModal(el)) returnFocusEl = el;
        // After the click opens a modal, move focus into it so the next D-pad press works there.
        setTimeout(function () {
            var list = getFocusableElements();
            if (!list.length) return;
            if (indexOf(list, document.activeElement) === -1) focusAndReveal(list[0]);
        }, 50);
    }

    function triggerActiveElement() {
        var focused = document.activeElement;
        if (!focused || focused === document.body) {
            var list = getFocusableElements();
            if (list.length) {
                focusAndReveal(list[0]);
                // First OK only places focus; nothing gets activated unexpectedly.
                return;
            }
        }

        if (focused && focused !== document.body) {
            rememberReturnFocus(focused);
            // HTMLElement.click() already dispatches a bubbling click event.  Dispatching a
            // second synthetic event made every OK press run twice (open then close a modal,
            // or start two player requests), which looks like a dead remote on real TVs.
            focused.click();
        }
    }

    function indexOf(list, item) {
        for (var i = 0; i < list.length; i += 1) if (list[i] === item) return i;
        return -1;
    }

    function isFocusable(el) {
        if (!el || el.disabled || el.tabIndex < 0) return false;
        var style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
        var r = el.getBoundingClientRect();
        return r.width > 0 && r.height > 0;
    }

    function focusAndReveal(el) {
        el.focus();
        try {
            el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
        } catch (ignore) {
            el.scrollIntoView(false);
        }
    }

    function getFocusableElements() {
        var detailModal = document.getElementById('modal-detail');
        var scope = document;
        if (isShown(detailModal)) {
            scope = detailModal;
        } else {
            var playerModal = document.getElementById('modal-player');
            if (isShown(playerModal)) {
                scope = playerModal;
            }
        }

        var nodes = scope.querySelectorAll(
            'button, [href], input, select, textarea, [tabindex="0"], .media-card, .episode-card, .season-tab, .season-tab-btn, .season-scroll-btn, .source-chip-btn, .episode-play-btn, .btn-play-quality, .quality-option, .play-cta-card, .nav-item, .btn-play-primary'
        );

        var result = [];
        for (var i = 0; i < nodes.length; i += 1) {
            // When the detail is closed, ignore controls hidden inside closed modals.
            if (scope === document && isInsideModal(nodes[i])) continue;
            if (isFocusable(nodes[i])) result.push(nodes[i]);
        }
        return result;
    }

    function navigateSpatial(keyCode) {
        var focusable = getFocusableElements();
        if (focusable.length === 0) return;

        var current = document.activeElement;
        if (!current || indexOf(focusable, current) === -1) {
            focusAndReveal(focusable[0]);
            return;
        }

        var curRect = current.getBoundingClientRect();
        var curCenter = { x: curRect.left + curRect.width / 2, y: curRect.top + curRect.height / 2 };

        var bestCandidate = null;
        var bestScore = Infinity;

        focusable.forEach(function (el) {
            if (el === current) return;
            var r = el.getBoundingClientRect();
            var center = { x: r.left + r.width / 2, y: r.top + r.height / 2 };

            var dx = center.x - curCenter.x;
            var dy = center.y - curCenter.y;

            var isValid = false;

            // Direction checks (Supports RTL interface)
            if (keyCode === 38) { // Up
                isValid = dy < -10;
            } else if (keyCode === 40) { // Down
                isValid = dy > 10;
            } else if (keyCode === 37) { // Left
                isValid = dx < -10;
            } else if (keyCode === 39) { // Right
                isValid = dx > 10;
            }

            if (isValid) {
                var mainAxisDist = (keyCode === 38 || keyCode === 40) ? Math.abs(dy) : Math.abs(dx);
                var crossAxisDist = (keyCode === 38 || keyCode === 40) ? Math.abs(dx) : Math.abs(dy);
                var score = mainAxisDist + (crossAxisDist * 2.5);

                if (score < bestScore) {
                    bestScore = score;
                    bestCandidate = el;
                }
            }
        });

        if (bestCandidate) {
            focusAndReveal(bestCandidate);
        }
    }

    // Do not force an initial focus ring: native Tizen pointer input must stay a pointer.
    // The first D-pad press focuses the first control instead (see navigateSpatial).
    window.addEventListener('DOMContentLoaded', function () {
        registerTizenKeys();
    });
})();
