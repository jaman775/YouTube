/* Drishyopot page script: ad stripping, Shorts hiding, and routing video links to the native player. */
(function () {
  if (window.__drishyopotInstalled) return;
  window.__drishyopotInstalled = true;

  var AD_KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams', 'adBreakParams'];
  var AD_RENDERERS = [
    'adSlotRenderer', 'promotedSparklesWebRenderer', 'promotedVideoRenderer', 'compactPromotedVideoRenderer',
    'displayAdRenderer', 'bannerPromoRenderer', 'inFeedAdLayoutRenderer', 'adSlotAndLayoutRenderer',
    'promotedSparklesTextSearchRenderer', 'searchPyvRenderer', 'videoMastheadAdRenderer',
    'brandVideoSingletonRenderer', 'statementBannerRenderer'
  ];

  // True when an array element is itself an ad wrapper (follows object chains only, never arrays,
  // so a section that merely contains an ad is not removed as a whole).
  function isAdItem(item, depth) {
    if (!item || typeof item !== 'object' || Array.isArray(item) || depth > 3) return false;
    for (var k in item) {
      if (!Object.prototype.hasOwnProperty.call(item, k)) continue;
      if (AD_RENDERERS.indexOf(k) !== -1) return true;
      if (isAdItem(item[k], depth + 1)) return true;
    }
    return false;
  }

  function strip(node, depth) {
    if (!node || typeof node !== 'object' || depth > 40) return node;
    if (Array.isArray(node)) {
      for (var i = node.length - 1; i >= 0; i--) {
        if (isAdItem(node[i], 0)) node.splice(i, 1); else strip(node[i], depth + 1);
      }
      return node;
    }
    for (var j = 0; j < AD_KEYS.length; j++) {
      if (AD_KEYS[j] in node) { try { delete node[AD_KEYS[j]]; } catch (e) {} }
    }
    for (var key in node) {
      if (Object.prototype.hasOwnProperty.call(node, key)) strip(node[key], depth + 1);
    }
    return node;
  }

  function looksLikeYoutubeData(r) {
    return r && typeof r === 'object' &&
      (r.responseContext || r.contents || r.playerResponse || r.onResponseReceivedActions || r.adPlacements);
  }

  var origParse = JSON.parse;
  JSON.parse = function (text, reviver) {
    var result = origParse.call(this, text, reviver);
    try { if (looksLikeYoutubeData(result)) strip(result, 0); } catch (e) {}
    return result;
  };

  if (window.Response && Response.prototype.json) {
    var origJson = Response.prototype.json;
    Response.prototype.json = function () {
      return origJson.call(this).then(function (data) {
        try { if (looksLikeYoutubeData(data)) strip(data, 0); } catch (e) {}
        return data;
      });
    };
  }

  // ---- CSS ----------------------------------------------------------------------------------
  var AD_CSS =
    'ytm-promoted-sparkles-web-renderer,ytm-promoted-video-renderer,ytm-companion-slot,ytm-ad-slot-renderer,' +
    'ad-slot-renderer,ytm-display-ad-renderer,ytm-statement-banner-renderer,ytm-banner-promo-renderer,' +
    'ytm-brand-video-singleton-renderer,ytm-in-feed-ad-layout-renderer,ytm-masthead-ad-renderer,' +
    '#masthead-ad,#player-ads,.ad-container,ytm-rich-item-renderer:has(ytm-ad-slot-renderer),' +
    'ytm-rich-section-renderer:has(ytm-statement-banner-renderer){display:none!important}';
  var SHORTS_CSS =
    'ytm-reel-shelf-renderer,ytm-shorts-lockup-view-model,ytm-shorts-lockup-view-model-v2,' +
    'ytm-rich-section-renderer:has(ytm-reel-shelf-renderer),ytm-rich-item-renderer:has(a[href^="/shorts/"]),' +
    'ytm-video-with-context-renderer:has(a[href^="/shorts/"]),ytm-pivot-bar-item-renderer:has(a[href^="/shorts"]),' +
    'a[href="/shorts"]{display:none!important}';

  // ---- The app's menu, shown inside YouTube's "You" page ------------------------------------------------
  var YOU_PATHS = ['/feed/library', '/feed/you', '/feed/account'];
  var MENU_CSS =
    '#drishyopot-menu{font-family:Roboto,Arial,sans-serif;margin:12px 16px 20px;padding:4px 0;border-radius:12px;' +
    'background:var(--yt-spec-badge-chip-background,rgba(127,127,127,.12));color:var(--yt-spec-text-primary,#0f0f0f)}' +
    '@media (prefers-color-scheme:dark){#drishyopot-menu{color:var(--yt-spec-text-primary,#f1f1f1)}}' +
    '#drishyopot-menu h2{font-size:14px;font-weight:500;margin:12px 16px 4px;opacity:.7;letter-spacing:.2px}' +
    '#drishyopot-menu .dp-quick{display:flex;gap:8px;padding:4px 8px 8px;overflow-x:auto}' +
    '#drishyopot-menu .dp-chip{flex:0 0 auto;display:flex;flex-direction:column;align-items:center;gap:4px;min-width:72px;' +
    'padding:10px 8px;border:0;border-radius:12px;background:transparent;color:inherit;font-size:12px}' +
    '#drishyopot-menu .dp-row{display:flex;align-items:center;gap:16px;width:100%;padding:12px 16px;border:0;background:transparent;' +
    'color:inherit;font-size:15px;text-align:start}' +
    '#drishyopot-menu button:active{background:rgba(127,127,127,.2)}' +
    '#drishyopot-menu svg{width:24px;height:24px;fill:currentColor;flex:0 0 auto}';
  var ICONS = {
    voice: 'M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z',
    address: 'M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
    openlink: 'M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z',
    tabs: 'M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h10v4h8v10z',
    newtab: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
    reload: 'M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
    settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
    downloads: 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z',
    queue: 'M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z',
    account: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z',
    engine: 'M17 1.01L7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14zm-1-6h-3V8h-2v5H8l4 4 4-4z',
    about: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z',
    copy: 'M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z',
    share: 'M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z',
    external: 'M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z'
  };
  var QUICK = ['voice', 'address', 'tabs', 'newtab', 'reload'];
  var LIST = ['openlink', 'settings', 'downloads', 'queue', 'account', 'engine', 'about', 'copy', 'share', 'external'];

  function svg(id) {
    var ns = 'http://www.w3.org/2000/svg';
    var el = document.createElementNS(ns, 'svg');
    el.setAttribute('viewBox', '0 0 24 24');
    var path = document.createElementNS(ns, 'path');
    path.setAttribute('d', ICONS[id] || '');
    el.appendChild(path);
    return el;
  }

  function entry(cls, id, label) {
    var b = document.createElement('button');
    b.className = cls;
    b.type = 'button';
    b.appendChild(svg(id));
    var t = document.createElement('span');
    t.textContent = label;
    b.appendChild(t);
    b.addEventListener('click', function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      if (window.Drishyopot && typeof window.Drishyopot.menu === 'function') window.Drishyopot.menu(id);
    });
    return b;
  }

  function buildMenu() {
    var cfg = window.__drishyopotConfig || {};
    var labels = cfg.labels || {};
    var root = document.createElement('div');
    root.id = 'drishyopot-menu';
    var h = document.createElement('h2');
    h.textContent = labels.title || 'Drishyopot';
    root.appendChild(h);
    var quick = document.createElement('div');
    quick.className = 'dp-quick';
    QUICK.forEach(function (id) {
      var label = labels[id] || id;
      if (id === 'tabs' && cfg.tabs) label += ' (' + cfg.tabs + ')';
      quick.appendChild(entry('dp-chip', id, label));
    });
    root.appendChild(quick);
    LIST.forEach(function (id) { root.appendChild(entry('dp-row', id, labels[id] || id)); });
    return root;
  }

  function onYouPage() {
    var p = location.pathname || '';
    for (var i = 0; i < YOU_PATHS.length; i++) if (p.indexOf(YOU_PATHS[i]) === 0) return true;
    return false;
  }

  function rebuildMenu() {
    var old = document.getElementById('drishyopot-menu');
    if (old) old.remove();
    ensureMenu();
  }

  function ensureMenu() {
    var existing = document.getElementById('drishyopot-menu');
    if (!onYouPage()) { if (existing) existing.remove(); return; }
    if (existing && existing.isConnected) return;
    var host = document.querySelector('ytm-browse') || document.querySelector('ytm-app') || document.body;
    if (host) host.insertBefore(buildMenu(), host.firstChild);
  }
  setInterval(function () { try { ensureMenu(); } catch (e) {} }, 400);

  var style = document.createElement('style');
  style.id = 'drishyopot-style';
  function applyCss() {
    var cfg = window.__drishyopotConfig || {};
    style.textContent = AD_CSS + (cfg.hideShorts ? SHORTS_CSS : '') + MENU_CSS;
  }
  window.__drishyopotApply = function () { applyCss(); rebuildMenu(); };
  applyCss();
  (function mount() {
    var root = document.head || document.documentElement;
    if (root) root.appendChild(style); else setTimeout(mount, 5);
  })();

  // ---- Route video links to the native player ----------------------------------------------------
  function toVideoUrl(href) {
    try {
      var u = new URL(href, location.href);
      var yt = /(^|\.)youtube\.com$/.test(u.hostname);
      if (u.hostname === 'youtu.be') return u.href;
      if (!yt) return null;
      if (u.pathname === '/watch' || u.pathname.indexOf('/shorts/') === 0 || u.pathname.indexOf('/live/') === 0) return u.href;
      if (u.pathname === '/playlist' && u.searchParams.get('list')) return u.href;
    } catch (e) {}
    return null;
  }

  function bridgeReady() { return window.Drishyopot && typeof window.Drishyopot.openVideo === 'function'; }

  document.addEventListener('click', function (ev) {
    var path = ev.composedPath ? ev.composedPath() : [];
    var anchor = null;
    for (var i = 0; i < path.length; i++) {
      if (path[i] && path[i].tagName === 'A' && path[i].href) { anchor = path[i]; break; }
    }
    if (!anchor) return;
    var url = toVideoUrl(anchor.href);
    if (url && bridgeReady()) {
      ev.preventDefault();
      ev.stopPropagation();
      ev.stopImmediatePropagation();
      window.Drishyopot.openVideo(url);
    }
  }, true);

  ['pushState', 'replaceState'].forEach(function (fn) {
    var original = history[fn];
    history[fn] = function (state, title, url) {
      var target = url ? toVideoUrl(url) : null;
      if (target && bridgeReady()) { window.Drishyopot.openVideo(target); return; }
      return original.apply(this, arguments);
    };
  });
})();
