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

  ['ytInitialData', 'ytInitialPlayerResponse'].forEach(function (name) {
    var value;
    try {
      Object.defineProperty(window, name, {
        configurable: true,
        get: function () { return value; },
        set: function (v) { try { strip(v, 0); } catch (e) {} value = v; }
      });
    } catch (e) {}
  });

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

  var style = document.createElement('style');
  style.id = 'drishyopot-style';
  function applyCss() {
    var cfg = window.__drishyopotConfig || {};
    style.textContent = AD_CSS + (cfg.hideShorts ? SHORTS_CSS : '');
  }
  window.__drishyopotApply = applyCss;
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
