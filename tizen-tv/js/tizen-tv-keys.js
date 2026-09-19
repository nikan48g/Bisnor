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
                var keysToRegister = [
                    'MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'Exit', 'Return',
                    'MediaFastForward', 'MediaRewind', '1', '2', '3', '4', '5'
                ];
                keysToRegister.forEach(function (key) {
                    try {
                        tizen.tvinputdevice.registerKey(key);
                    } catch (e) {}
                });
            } catch (err) {
                console.warn('tizen.tvinputdevice error:', err);
            }
        }
    }

    // Handle back/return key of Samsung TV
    document.addEventListener('tizenhwkey', function (e) {
        if (e.keyName === 'back') {
            handleBackAction();
        }
    });

    function handleBackAction() {
        // 1. If player is open, close player
        var playerModal = document.getElementById('modal-player');
        if (playerModal && playerModal.style.display !== 'none' && playerModal.style.display !== '') {
            if (typeof window.bisnorTvClosePlayer === 'function') { window.bisnorTvClosePlayer(); return; }
            if (window.app && typeof window.app.closePlayerModal === 'function') {
                window.app.closePlayerModal();
            } else {
                playerModal.style.display = 'none';
            }
            return;
        }

        // 2. If detail modal is open, close it
        var detailModal = document.getElementById('modal-detail');
        if (detailModal && detailModal.style.display !== 'none' && detailModal.style.display !== '') {
            if (window.app && typeof window.app.closeDetailModal === 'function') {
                window.app.closeDetailModal();
            } else {
                detailModal.style.display = 'none';
            }
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

    // Keydown listener for standard keyboard and Samsung TV remote
    window.addEventListener('keydown', function (e) {
        var key = e.keyCode;

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
        }
    }, true);

    function triggerActiveElement() {
        var focused = document.activeElement;
        if (!focused || focused === document.body) {
            var first = document.querySelector('.media-card, .btn-play-primary, .nav-item');
            if (first) {
                first.focus();
                focused = first;
            }
        }

        if (focused) {
            // HTMLElement.click() already dispatches a bubbling click event.  Dispatching a
            // second synthetic event made every OK press run twice (open then close a modal,
            // or start two player requests), which looks like a dead remote on real TVs.
            focused.click();
        }
    }

    function getFocusableElements() {
        var detailModal = document.getElementById('modal-detail');
        var scope = document;
        if (detailModal && detailModal.style.display !== 'none' && detailModal.style.display !== '') {
            scope = detailModal;
        } else {
            var playerModal = document.getElementById('modal-player');
            if (playerModal && playerModal.style.display !== 'none' && playerModal.style.display !== '') {
                scope = playerModal;
            }
        }

        var candidates = Array.from(scope.querySelectorAll(
            'button, [href], input, select, textarea, [tabindex=\"0\"], .media-card, .episode-card, .season-tab-btn, .btn-play-quality, .nav-item, .btn-play-primary'
        ));

        return candidates.filter(function (el) {
            if (el.disabled) return false;
            var style = window.getComputedStyle(el);
            if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') return false;
            var r = el.getBoundingClientRect();
            return r.width > 0 && r.height > 0;
        });
    }

    function navigateSpatial(keyCode) {
        var focusable = getFocusableElements();
        if (focusable.length === 0) return;

        var current = document.activeElement;
        if (!current || !focusable.includes(current)) {
            focusable[0].focus();
            focusable[0].scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
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
            bestCandidate.focus();
            bestCandidate.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
        }
    }

    // Do not force an initial focus ring: native Tizen pointer input must stay a pointer.
    window.addEventListener('DOMContentLoaded', function () {
        registerTizenKeys();
    });
})();
