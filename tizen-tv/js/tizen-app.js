/*
 * Legacy-safe Tizen UI controller.  Q80/Tizen 5 uses an older WebKit/Chromium
 * runtime, so this file deliberately avoids optional chaining and newer Promise APIs.
 */
(function () {
    'use strict';

    var catalog = [];
    var currentGenre = 'all';
    var activeMedia = null;
    var playerPosition = 0;

    function byId(id) { return document.getElementById(id); }
    function text(el, value) { if (el) el.textContent = value || ''; }
    function show(el, value) { if (el) el.style.display = value || 'block'; }

    function escapeHtml(value) {
        return String(value || '').replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
    }

    function card(media) {
        var el = document.createElement('div');
        el.className = 'media-card';
        el.setAttribute('role', 'button');
        el.setAttribute('aria-label', media.title || 'اثر');
        var genre = media.genres && media.genres[0] ? media.genres[0].title : 'فیلم';
        var poster = media.image || media.cover || 'assets/logo.png';
        el.innerHTML = '<div class="card-poster-box"><img src="' + escapeHtml(poster) + '" alt="' +
            escapeHtml(media.title) + '"><span class="card-rating-badge">⭐ ' +
            escapeHtml(media.imdb || '—') + '</span><span class="card-type-badge">' +
            (media.type === 'serie' ? 'سریال' : 'سینمایی') + '</span></div><div class="card-info"><h4 class="card-title">' +
            escapeHtml(media.title) + '</h4><div class="card-sub"><span>' + escapeHtml(media.year || '—') +
            '</span> • <span>' + escapeHtml(genre) + '</span></div></div>';
        el.addEventListener('click', function (event) {
            // Samsung's IME keeps the focused search field alive after a pointer
            // click. Blur it first, then open the detail after the IME closes so
            // remote input cannot be appended to the old query.
            var search = byId('input-explore-search');
            if (search && document.activeElement === search) {
                event.preventDefault();
                search.blur();
                setTimeout(function () { openDetail(media); }, 120);
                return;
            }
            openDetail(media);
        });
        return el;
    }

    function renderRow(id, items) {
        var row = byId(id);
        if (!row || !items || !items.length) return;
        row.innerHTML = '';
        for (var i = 0; i < items.length; i += 1) row.appendChild(card(items[i]));
    }

    function openDetail(media) {
        var modal = byId('modal-detail');
        if (!modal) return;
        activeMedia = media;
        text(byId('detail-title'), media.title);
        text(byId('detail-storyline'), media.description || 'توضیحی برای این اثر ثبت نشده است.');
        text(byId('detail-badge-year'), media.year || '—');
        text(byId('detail-badge-imdb'), '⭐ ' + (media.imdb || '—'));
        text(byId('detail-badge-type'), media.type === 'serie' ? 'سریال' : 'سینمایی');
        var poster = media.cover || media.image || 'assets/logo.png';
        var backdrop = byId('detail-backdrop');
        var detailPoster = byId('detail-poster');
        if (backdrop) backdrop.src = poster;
        if (detailPoster) detailPoster.src = poster;
        var play = byId('btn-detail-play-now');
        if (play) play.onclick = function () { playSource(firstSource(media), media.title); };
        renderSimilar(media);
        if (media.type === 'serie') loadSeasons(media);
        else renderMovieSources(media);
        show(modal, 'flex');
    }

    function firstSource(media) {
        var sources = media && media.sources ? media.sources : [];
        // AVPlay supports many formats, but a directly playable MP4/HLS source
        // is safer than an MKV when the API offers both.
        for (var i = 0; i < sources.length; i += 1) {
            if (sources[i] && sources[i].url && !/تیزر|trailer/i.test(String(sources[i].quality || '')) && /\.(mp4|m3u8)(?:$|[?#])/i.test(sources[i].url)) return sources[i];
        }
        for (var j = 0; j < sources.length; j += 1) {
            if (sources[j] && sources[j].url && !/تیزر|trailer/i.test(String(sources[j].quality || ''))) return sources[j];
        }
        return sources.length ? sources[0] : null;
    }
    function playSource(source, title) {
        if (!source || !source.url) return alert('لینک پخش برای این مورد موجود نیست.');
        var modal = byId('modal-player');
        closeDetail();
        text(byId('player-media-title'), title);
        playerPosition = 0;
        show(modal, 'block');
        document.body.classList.add('avplay-active');
        if (window.TizenAVPlayEngine && window.TizenAVPlayEngine.isTizen()) {
            window.TizenAVPlayEngine.play(source.url, title, function (position, duration) {
                playerPosition = position || 0;
                var seek = byId('player-seekbar');
                if (seek && duration) seek.value = Math.min(100, (playerPosition / duration) * 100);
                var label = byId('player-time-label');
                if (label) label.textContent = formatTime(playerPosition) + ' / ' + formatTime(duration || 0);
                rememberContinue(playerPosition, duration || 0);
            }, function () { closePlayer(); }, function (error) {
                document.body.classList.remove('avplay-active');
                show(byId('player-error-overlay'), 'flex');
                text(byId('player-error-title'), 'پخش این فایل روی تلویزیون ممکن نشد');
                text(byId('player-error-desc'), 'پخش‌کنندهٔ تلویزیون خطا داد: ' + String(error || 'نامشخص'));
            });
        } else { var video = byId('html-video-player'); if (video) { video.src = source.url; video.play(); } }
    }
    function closePlayer() {
        if (window.TizenAVPlayEngine && window.TizenAVPlayEngine.stop) window.TizenAVPlayEngine.stop();
        var video = byId('html-video-player'); if (video) { video.pause(); video.removeAttribute('src'); video.load(); }
        show(byId('modal-player'), 'none');
        document.body.classList.remove('avplay-active');
        if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen();
    }
    window.bisnorTvClosePlayer = closePlayer;
    window.bisnorTvPlayerCommand = function (command) {
        var engine = window.TizenAVPlayEngine;
        if (!engine) return;
        if (command === 'close') { closePlayer(); return; }
        if (command === 'toggle') {
            if (engine.playerState === 'PLAYING') engine.pause(); else engine.resume();
            var toggleButton = byId('btn-player-play-pause'); if (toggleButton) toggleButton.textContent = engine.playerState === 'PLAYING' ? '⏸️' : '▶️';
            return;
        }
        if (command === 'forward') { engine.seek(playerPosition + 10); return; }
        if (command === 'rewind') { engine.seek(Math.max(0, playerPosition - 10)); return; }
        if (command === 'subtitle') {
            var result = engine.selectSubtitle ? engine.selectSubtitle(1) : { ok: false, message: 'زیرنویس در دسترس نیست.' };
            var subtitleButton = byId('btn-player-subtitles'); if (subtitleButton) subtitleButton.textContent = result.ok ? '✓ ' + result.message : '⚠ ' + result.message;
        }
    };
    function formatTime(value) { value = Math.max(0, Math.floor(value || 0)); return ('0' + Math.floor(value / 60)).slice(-2) + ':' + ('0' + (value % 60)).slice(-2); }
    function rememberContinue(position, duration) {
        if (!activeMedia || !duration || position < 8) return;
        var items = [];
        try { items = JSON.parse(localStorage.getItem('bisnor-tizen-continue') || '[]'); } catch (ignore) {}
        var kept = [];
        for (var i = 0; i < items.length; i += 1) if (items[i].media && items[i].media.id !== activeMedia.id) kept.push(items[i]);
        kept.unshift({ media: activeMedia, position: position, duration: duration });
        try { localStorage.setItem('bisnor-tizen-continue', JSON.stringify(kept.slice(0, 12))); } catch (ignore2) {}
        renderContinueWatching();
    }
    function renderContinueWatching() {
        var section = byId('continue-watching-section'); var row = byId('continue-watching-row');
        if (!section || !row) return;
        var items = [];
        try { items = JSON.parse(localStorage.getItem('bisnor-tizen-continue') || '[]'); } catch (ignore) {}
        row.innerHTML = '';
        for (var i = 0; i < items.length; i += 1) if (items[i].media) row.appendChild(card(items[i].media));
        section.style.display = row.children.length ? 'block' : 'none';
    }
    function sourceButton(source, title) {
        var btn = document.createElement('button'); btn.className = 'source-chip-btn';
        btn.textContent = '▶ ' + (source.quality || 'کیفیت اصلی');
        btn.addEventListener('click', function () { playSource(source, title); }); return btn;
    }
    function renderMovieSources(media) {
        show(byId('detail-series-section'), 'none'); show(byId('detail-sources-section'), 'block');
        var list = byId('detail-sources-list'); if (!list) return; list.innerHTML = '';
        var sources = media.sources || [];
        for (var i = 0; i < sources.length; i += 1) if ((sources[i].quality || '').indexOf('تیزر') === -1) list.appendChild(sourceButton(sources[i], media.title));
    }
    function loadSeasons(media) {
        show(byId('detail-sources-section'), 'none'); show(byId('detail-series-section'), 'block');
        text(byId('detail-episodes-list'), 'در حال دریافت فصل‌ها و قسمت‌ها…');
        byId('detail-seasons-tabs').innerHTML = '';
        text(byId('detail-season-position'), '');
        window.mediaService.getSeriesSeasons(media.id).then(function (seasons) { renderSeasons(seasons, media.title); }).catch(function () { text(byId('detail-episodes-list'), 'فصل یا قسمتی پیدا نشد.'); });
    }
    function renderSeasons(seasons, title) {
        var tabs = byId('detail-seasons-tabs'); var episodes = byId('detail-episodes-list');
        var selector = byId('detail-season-select');
        if (!seasons.length) { text(episodes, 'فصل یا قسمتی پیدا نشد.'); return; }
        var selectedIndex = 0;
        function choose(index, focusButton) {
            if (index < 0 || index >= seasons.length) return;
            selectedIndex = index;
            var buttons = tabs.querySelectorAll('button'); for (var b = 0; b < buttons.length; b += 1) buttons[b].classList.remove('active'); buttons[index].classList.add('active');
            if (selector) selector.value = String(index);
            text(byId('detail-season-position'), 'فصل ' + (index + 1) + ' از ' + seasons.length + ' — برای جابه‌جایی از دکمه‌های دو طرف یا ریموت استفاده کنید');
            try { buttons[index].scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' }); } catch (ignore) { buttons[index].scrollIntoView(); }
            if (focusButton) buttons[index].focus();
            episodes.innerHTML = ''; var list = seasons[index].episodes;
            for (var i = 0; i < list.length; i += 1) {
                var sources = list[i].sources || [];
                for (var s = 0; s < sources.length; s += 1) {
                    var ep = document.createElement('div'); ep.className = 'episode-source-row';
                    ep.innerHTML = '<span class="episode-play-icon">▶</span><div class="episode-source-info"><strong>' + escapeHtml(list[i].title) + (sources[s].quality ? ' (' + escapeHtml(sources[s].quality) + ')' : '') + '</strong><span>استریم مستقیم ایران‌فلیکس • ' + escapeHtml((sources[s].type || 'MKV').toUpperCase()) + '</span></div>';
                    var play = sourceButton(sources[s], title + ' — ' + list[i].title); play.className = 'episode-play-btn'; play.textContent = 'پخش'; ep.appendChild(play); episodes.appendChild(ep);
                }
            }
        }
        tabs.innerHTML = '';
        if (selector) selector.innerHTML = '';
        for (var i = 0; i < seasons.length; i += 1) { (function (index) {
            var label = seasons[index].title || ('فصل ' + (index + 1));
            var btn = document.createElement('button'); btn.className = 'season-tab'; btn.textContent = label; btn.addEventListener('click', function () { choose(index, true); }); tabs.appendChild(btn);
            if (selector) { var option = document.createElement('option'); option.value = String(index); option.textContent = label; selector.appendChild(option); }
        }(i)); }
        var prev = byId('btn-season-prev'); var next = byId('btn-season-next');
        if (prev) prev.onclick = function () { choose(selectedIndex - 1, true); };
        if (next) next.onclick = function () { choose(selectedIndex + 1, true); };
        if (selector) selector.onchange = function () { choose(Number(this.value), true); };
        choose(0);
    }
    function renderSimilar(media) { var row = byId('detail-similar-row'); if (!row) return; row.innerHTML = ''; for (var i = 0; i < catalog.length && i < 12; i += 1) if (catalog[i].id !== media.id) row.appendChild(card(catalog[i])); }

    function closeDetail() { show(byId('modal-detail'), 'none'); }

    function renderExplore(items) {
        var grid = byId('grid-explore-results');
        if (!grid) return;
        grid.innerHTML = '';
        for (var i = 0; i < items.length; i += 1) {
            var media = items[i];
            if (currentGenre !== 'all') {
                var genres = media.genres || [];
                var matches = false;
                for (var j = 0; j < genres.length; j += 1) if (genres[j].title === currentGenre) matches = true;
                if (!matches) continue;
            }
            grid.appendChild(card(media));
        }
        text(byId('txt-explore-results-count'), grid.children.length + ' اثر در کاتالوگ');
    }

    function localMatches(query) {
        var q = String(query || '').toLowerCase(); var result = [];
        for (var i = 0; i < catalog.length; i += 1) {
            var media = catalog[i]; var genres = media.genres || []; var genreText = '';
            for (var g = 0; g < genres.length; g += 1) genreText += ' ' + (genres[g].title || '');
            if (!q || (String(media.title || '') + ' ' + String(media.description || '') + genreText).toLowerCase().indexOf(q) !== -1) result.push(media);
        }
        return result;
    }
    function searchCatalog(query) {
        renderExplore(localMatches(query));
        if (!query || !window.mediaService) return;
        window.mediaService.search(query).then(function (items) { merge(items); renderExplore(items && items.length ? items : localMatches(query)); }).catch(function () {});
    }

    function switchTab(name) {
        var panes = document.querySelectorAll('.tab-pane');
        var navs = document.querySelectorAll('.nav-item');
        var i;
        for (i = 0; i < panes.length; i += 1) panes[i].classList.remove('active');
        for (i = 0; i < navs.length; i += 1) navs[i].classList.remove('active');
        var pane = byId('tab-' + name);
        if (pane) pane.classList.add('active');
        var nav = document.querySelector('.nav-item[data-tab="tab-' + name + '"]');
        if (nav) nav.classList.add('active');
        if (name === 'explore') renderExplore(catalog);
    }

    function merge(items) {
        if (!items) return;
        for (var i = 0; i < items.length; i += 1) {
            var exists = false;
            for (var j = 0; j < catalog.length; j += 1) if (catalog[j].id === items[i].id) exists = true;
            if (!exists) catalog.push(items[i]);
        }
    }

    function loadLiveCatalog() {
        if (!window.mediaService) return;
        window.mediaService.getLatestMovies().then(function (items) {
            merge(items); renderRow('row-latest-movies', items); renderExplore(catalog);
        }).catch(function () {});
        window.mediaService.getPopularSeries().then(function (items) {
            merge(items); renderRow('row-popular-series', items); renderExplore(catalog);
        }).catch(function () {});
        window.mediaService.getTopImdb().then(function (items) {
            merge(items); renderRow('row-top-imdb', items); renderExplore(catalog);
        }).catch(function () {});
        window.mediaService.getAnimations().then(function (items) {
            merge(items); renderRow('row-animations', items); renderExplore(catalog);
        }).catch(function () {});
    }

    function init() {
        var navs = document.querySelectorAll('.nav-item');
        for (var i = 0; i < navs.length; i += 1) {
            navs[i].addEventListener('click', function () { switchTab(this.getAttribute('data-tab').replace('tab-', '')); });
        }
        var close = byId('btn-close-detail');
        if (close) close.addEventListener('click', closeDetail);
        function bindPlayerAction(element, action) {
            if (!element) return;
            var lastRun = 0;
            function run(event) {
                if (event) { event.preventDefault(); event.stopPropagation(); }
                var now = Date.now(); if (now - lastRun < 250) return;
                lastRun = now; action.call(element);
            }
            element.addEventListener('click', run, false);
            element.addEventListener('mouseup', run, false);
            element.addEventListener('touchend', run, false);
            element.addEventListener('pointerup', run, false);
        }
        var closePlayerBtn = byId('btn-close-player');
        bindPlayerAction(closePlayerBtn, closePlayer);
        var playPause = byId('btn-player-play-pause');
        bindPlayerAction(playPause, function () {
            if (!window.TizenAVPlayEngine) return;
            if (window.TizenAVPlayEngine.playerState === 'PLAYING') { window.TizenAVPlayEngine.pause(); this.textContent = '▶️'; }
            else { window.TizenAVPlayEngine.resume(); this.textContent = '⏸️'; }
        });
        var forward = byId('btn-player-forward'); bindPlayerAction(forward, function () { if (window.TizenAVPlayEngine) window.TizenAVPlayEngine.seek(playerPosition + 10); });
        var rewind = byId('btn-player-rewind'); bindPlayerAction(rewind, function () { if (window.TizenAVPlayEngine) window.TizenAVPlayEngine.seek(Math.max(0, playerPosition - 10)); });
        var subtitles = byId('btn-player-subtitles'); bindPlayerAction(subtitles, function () {
            var result = window.TizenAVPlayEngine && window.TizenAVPlayEngine.selectSubtitle ? window.TizenAVPlayEngine.selectSubtitle(1) : { ok: false, message: 'زیرنویس در دسترس نیست.' };
            this.textContent = result.ok ? '✓ ' + result.message : '⚠ ' + result.message;
        });
        var seek = byId('player-seekbar'); if (seek) seek.addEventListener('change', function () { if (window.TizenAVPlayEngine && window.TizenAVPlayEngine.duration) window.TizenAVPlayEngine.seek((Number(this.value) / 100) * (window.TizenAVPlayEngine.duration / 1000)); });
        var external = byId('btn-player-open-external'); if (external) external.style.display = 'none';
        var headerSearch = byId('btn-header-search');
        if (headerSearch) headerSearch.addEventListener('click', function () { switchTab('explore'); var field = byId('input-explore-search'); if (field) field.focus(); });
        var input = byId('input-explore-search');
        if (input) input.addEventListener('input', function () {
            searchCatalog(this.value);
        });
        var chips = document.querySelectorAll('#genres-filter-chips .chip');
        for (var c = 0; c < chips.length; c += 1) chips[c].addEventListener('click', function () {
            currentGenre = this.getAttribute('data-genre') || 'all';
            for (var z = 0; z < chips.length; z += 1) chips[z].classList.remove('active');
            this.classList.add('active'); renderExplore(catalog);
        });
        renderContinueWatching();
        loadLiveCatalog();
    }

    document.addEventListener('DOMContentLoaded', init);
})();
