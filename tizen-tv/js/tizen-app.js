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

    function providerLabel(origin) { return origin === 'next' ? 'نکست‌مووی' : 'ایران‌فلیکس'; }

    function sourceBadgeHtml(origin) {
        if (!origin) return '';
        var kind = origin === 'next' ? 'src-next' : 'src-iranflix';
        var initial = origin === 'next' ? 'N' : 'iF';
        return '<span class="src-badge"><span class="src-logo ' + kind + '">' + initial + '</span>' +
            providerLabel(origin) + '</span>';
    }

    function qualityOf(source) {
        var raw = String((source && source.quality) || '');
        if (/تیزر|trailer/i.test(raw)) return 'تیزر';
        var match = raw.match(/(2160|1440|1080|720|540|480|360|240)/);
        if (match) return match[1] + 'p';
        var clean = raw.replace(/\([^)]*\)/g, '').trim();
        return clean || 'کیفیت اصلی';
    }

    function qualityRank(source) {
        var match = String((source && source.quality) || '').match(/(2160|1440|1080|720|540|480|360|240)/);
        return match ? Number(match[1]) : 0;
    }

    // Highest quality first, then the container the TV decodes most reliably.
    function playbackChoices(sources) {
        return (sources || []).filter(function (source) { return source && source.url; })
            .slice()
            .sort(function (a, b) { return (qualityRank(b) - qualityRank(a)) || (sourceScore(b) - sourceScore(a)); });
    }

    function versionsText(count) { return count > 1 ? count + ' نسخه' : 'تک‌نسخه'; }

    function card(media) {
        var el = document.createElement('div');
        el.className = 'media-card';
        // A <div> is not focusable by default; without this the D-pad can never land on a card.
        el.tabIndex = 0;
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
        if (play) play.onclick = function () { playSource(sourceList(media), media.title); };
        renderSimilar(media);
        if (media.type === 'serie') loadSeasons(media);
        else renderMovieSources(media);
        show(modal, 'flex');
        // Next Movie rows carry no description/links; fill them and re-render once.
        if (window.mediaService && window.mediaService.hydrateMedia) {
            window.mediaService.hydrateMedia(media).then(function () {
                if (activeMedia !== media) return;
                text(byId('detail-storyline'), media.description || 'توضیحی برای این اثر ثبت نشده است.');
                var filled = media.cover || media.image;
                if (filled) {
                    var back = byId('detail-backdrop'); var art = byId('detail-poster');
                    if (back) back.src = filled;
                    if (art) art.src = filled;
                }
                if (media.type === 'serie') loadSeasons(media); else renderMovieSources(media);
            }).catch(function () {});
        }
    }

    function firstSource(media) {
        return sourceList(media)[0];
    }

    function sourceScore(source) {
        var url = String((source && source.url) || '').split('?')[0].toLowerCase();
        var quality = String((source && source.quality) || '');
        var score = 10;
        // Samsung AVPlay decodes HLS in hardware most reliably; MP4 is the next
        // safest container. MKV can stutter when it carries DTS/HEVC variants.
        if (/\.m3u8$/.test(url)) score += 100;
        else if (/\.mp4$/.test(url)) score += 80;
        if (/1080/.test(quality)) score += 30;
        else if (/720/.test(quality)) score += 20;
        else if (/480/.test(quality)) score += 5;
        // 4K sources exceed what most Tizen 5 panels decode smoothly.
        if (/2160|4k/i.test(quality)) score -= 10;
        if (/تیزر|trailer/i.test(quality)) score -= 1000;
        return score;
    }

    function sourceList(media) {
        var sources = media && media.sources ? media.sources.slice() : [];
        if (!sources.length && activeMedia && activeMedia.sources) sources = activeMedia.sources.slice();
        sources = sources.filter(function (source) {
            return source && source.url && !/تیزر|trailer/i.test(String(source.quality || ''));
        });
        sources.sort(function (a, b) { return sourceScore(b) - sourceScore(a); });
        return sources;
    }

    var playQueue = [];
    var activePlayTitle = '';
    var activeSourceList = [];
    var activeSource = null;
    var htmlFallbackSource = null;

    function playSource(source, title) {
        if (!source) return alert('لینک پخش برای این مورد موجود نیست.');
        var list = Object.prototype.toString.call(source) === '[object Array]' ? source.slice() : [source];
        var choices = playbackChoices(list);
        if (!choices.length) return alert('لینک پخش برای این مورد موجود نیست.');

        closeDetail();
        text(byId('player-media-title'), title);
        playerPosition = 0;
        show(byId('modal-player'), 'block');
        activeSourceList = choices;
        activePlayTitle = title;
        htmlFallbackSource = choices[0];
        playQueue = choices.slice();
        tryNextSource(0);
    }

    function onPlayerProgress(position, duration) {
        playerPosition = position || 0;
        var seek = byId('player-seekbar');
        if (seek && duration) seek.value = Math.min(100, (playerPosition / duration) * 100);
        var label = byId('player-time-label');
        if (label) label.textContent = formatTime(playerPosition) + ' / ' + formatTime(duration || 0);
        rememberContinue(playerPosition, duration || 0);
    }

    function tryNextSource(startAt) {
        var source = playQueue.shift();
        if (!source) {
            playWithHtml5(htmlFallbackSource, activePlayTitle || '', startAt);
            return;
        }
        startSource(source, startAt);
    }

    function startSource(source, startAt) {
        activeSource = source;
        text(byId('player-quality-label'), qualityOf(source));
        document.body.classList.add('avplay-active');
        show(byId('player-error-overlay'), 'none');
        text(byId('player-loading-spinner'), 'در حال آماده‌سازی پخش…');

        if (window.TizenAVPlayEngine && window.TizenAVPlayEngine.isTizen()) {
            window.TizenAVPlayEngine.play(source.url, activePlayTitle, onPlayerProgress, function () { closePlayer(); }, function () {
                // AVPlay rejected this container/codec. Try the next real source
                // before surfacing an error to the viewer.
                if (playQueue.length > 0) {
                    tryNextSource(startAt);
                    return;
                }
                playWithHtml5(htmlFallbackSource, activePlayTitle || '', startAt);
            }, startAt);
        } else {
            playWithHtml5(source, activePlayTitle || '', startAt);
        }
    }

    function switchPlayerSource(index) {
        var source = activeSourceList[index];
        if (!source || !source.url) return;
        closeQualitySheet();
        if (source === activeSource && window.TizenAVPlayEngine &&
            (window.TizenAVPlayEngine.playerState === 'PLAYING' || window.TizenAVPlayEngine.playerState === 'PAUSED')) {
            return;
        }
        var resumeAt = playerPosition || 0;
        playQueue = [];
        for (var i = 0; i < activeSourceList.length; i += 1) {
            if (activeSourceList[i] !== source) playQueue.push(activeSourceList[i]);
        }
        startSource(source, resumeAt);
    }

    function renderQualitySheet() {
        var list = byId('player-quality-list');
        if (!list) return;
        list.innerHTML = '';
        if (activeSourceList.length < 2) {
            list.innerHTML = '<p class="quality-empty">برای این مورد فقط یک نسخه موجود است.</p>';
            return;
        }
        for (var i = 0; i < activeSourceList.length; i += 1) {
            (function (index) {
                var source = activeSourceList[index];
                var btn = document.createElement('button');
                btn.className = 'quality-option' + (source === activeSource ? ' active' : '');
                btn.innerHTML = '<span class="quality-option-name">' + qualityOf(source) +
                    (source === activeSource ? ' • در حال پخش' : '') + '</span>' +
                    sourceBadgeHtml(source.origin) +
                    '<span class="quality-option-meta">' + escapeHtml(String(source.type || 'MKV').toUpperCase()) + '</span>';
                btn.addEventListener('click', function () { switchPlayerSource(index); });
                list.appendChild(btn);
            }(i));
        }
    }

    function openQualitySheet() {
        renderQualitySheet();
        show(byId('player-quality-sheet'), 'flex');
        var first = document.querySelector('#player-quality-list .quality-option');
        if (first && first.focus) first.focus();
    }

    function closeQualitySheet() {
        show(byId('player-quality-sheet'), 'none');
    }
    window.bisnorTvCloseQuality = closeQualitySheet;

    function playWithHtml5(source, title, startAt) {
        if (!source || !source.url) {
            document.body.classList.remove('avplay-active');
            show(byId('player-error-overlay'), 'flex');
            text(byId('player-error-title'), 'پخش این فایل روی تلویزیون ممکن نشد');
            text(byId('player-error-desc'), 'لینک پخش قابل استفاده‌ای برای این اثر پیدا نشد.');
            return;
        }
        if (window.TizenAVPlayEngine && window.TizenAVPlayEngine.stop) window.TizenAVPlayEngine.stop();
        document.body.classList.remove('avplay-active');
        var video = byId('html-video-player');
        if (!video) return;
        show(byId('player-error-overlay'), 'none');
        video.src = source.url;
        video.load();
        if (startAt > 1) {
            video.addEventListener('loadedmetadata', function seekOnce() {
                video.removeEventListener('loadedmetadata', seekOnce);
                try { video.currentTime = startAt; } catch (ignore) {}
            });
        }
        video.play().catch(function () {
            show(byId('player-error-overlay'), 'flex');
            text(byId('player-error-title'), 'پخش این فایل روی تلویزیون ممکن نشد');
            text(byId('player-error-desc'), 'کدک این نسخه توسط تلویزیون پشتیبانی نمی‌شود. نسخه دیگری از فایل را امتحان کنید.');
        });
    }
    function closePlayer() {
        playQueue = [];
        activeSourceList = [];
        activeSource = null;
        var qualitySheet = byId('player-quality-sheet'); if (qualitySheet) qualitySheet.style.display = 'none';
        if (window.TizenAVPlayEngine && window.TizenAVPlayEngine.stop) window.TizenAVPlayEngine.stop();
        var video = byId('html-video-player'); if (video) { video.pause(); video.removeAttribute('src'); video.load(); }
        show(byId('modal-player'), 'none');
        show(byId('player-error-overlay'), 'none');
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
        if (command === 'quality') { openQualitySheet(); return; }
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
    function renderMovieSources(media) {
        show(byId('detail-series-section'), 'none'); show(byId('detail-sources-section'), 'block');
        var list = byId('detail-sources-list'); if (!list) return; list.innerHTML = '';
        var choices = playbackChoices(sourceList(media));
        if (!choices.length) {
            list.innerHTML = '<div class="empty-note">⚠️ لینکی برای این اثر در سرور یافت نشد.</div>';
            return;
        }
        var best = choices[0];
        var cardEl = document.createElement('button');
        cardEl.className = 'play-cta-card';
        cardEl.innerHTML = '<span class="play-cta-icon">▶</span><span class="play-cta-text">' +
            '<strong>پخش فیلم</strong><span class="play-cta-meta"><span>' + qualityOf(best) + '</span>' +
            sourceBadgeHtml(best.origin) + '<span>' + versionsText(choices.length) +
            (choices.length > 1 ? ' • انتخاب کیفیت در پلیر' : '') + '</span></span></span>';
        cardEl.addEventListener('click', function () { playSource(choices, media.title); });
        list.appendChild(cardEl);
    }
    function loadSeasons(media) {
        show(byId('detail-sources-section'), 'none'); show(byId('detail-series-section'), 'block');
        text(byId('detail-episodes-list'), 'در حال دریافت فصل‌ها و قسمت‌ها…');
        byId('detail-seasons-tabs').innerHTML = '';
        text(byId('detail-season-position'), '');
        window.mediaService.getSeriesSeasons(media).then(function (seasons) { renderSeasons(seasons, media.title); }).catch(function () { text(byId('detail-episodes-list'), 'فصل یا قسمتی پیدا نشد.'); });
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
                (function (episode, epIndex) {
                    var sources = playbackChoices(episode.sources || []);
                    var best = sources[0];
                    var epTitle = episode.title || ('قسمت ' + (epIndex + 1));
                    var ep = document.createElement('div'); ep.className = 'episode-source-row';
                    var meta = (best ? qualityOf(best) : 'بدون لینک') +
                        (best ? sourceBadgeHtml(best.origin) : '') +
                        (sources.length > 1 ? '<span>' + sources.length + ' نسخه</span>' : '');
                    ep.innerHTML = '<span class="episode-play-icon">' + (epIndex + 1) + '</span>' +
                        '<div class="episode-source-info"><strong>' + escapeHtml(epTitle) + '</strong>' +
                        '<span class="episode-meta">' + meta + '</span></div>';
                    var play = document.createElement('button');
                    play.className = 'episode-play-btn';
                    play.textContent = 'پخش';
                    play.addEventListener('click', function () { playSource(sources, title + ' — ' + epTitle); });
                    ep.appendChild(play);
                    episodes.appendChild(ep);
                }(list[i], i));
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
        // The data service owns the authoritative, de-duplicated catalogue.
        if (window.mediaService && window.mediaService.catalog) {
            catalog = window.mediaService.catalog;
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
        var qualityBtn = byId('btn-player-quality');
        if (qualityBtn) qualityBtn.addEventListener('click', openQualitySheet);
        var closeQuality = byId('btn-close-quality-sheet');
        if (closeQuality) closeQuality.addEventListener('click', closeQualitySheet);
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
        var sourceChips = document.querySelectorAll('.chip-source');
        function syncSourceChips() {
            var mode = (window.mediaService && window.mediaService.sourceMode) || 'both';
            for (var k = 0; k < sourceChips.length; k += 1) {
                if (sourceChips[k].getAttribute('data-source') === mode) sourceChips[k].classList.add('active');
                else sourceChips[k].classList.remove('active');
            }
        }
        for (var sc = 0; sc < sourceChips.length; sc += 1) sourceChips[sc].addEventListener('click', function () {
            if (!window.mediaService) return;
            window.mediaService.setSourceMode(this.getAttribute('data-source'));
            syncSourceChips();
            catalog = [];
            loadLiveCatalog();
            renderExplore(catalog);
        });
        syncSourceChips();
        renderContinueWatching();
        loadLiveCatalog();
    }

    document.addEventListener('DOMContentLoaded', init);
})();
