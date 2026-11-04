// Web Speech API (SpeechRecognition) for Android WebView, backed by the phone's own recogniser.
// WebView has no webkitSpeechRecognition, so YouTube hides its own microphone button. This defines it,
// and YouTube's built-in voice search then works as it does in Chrome.
(function () {
  'use strict';
  if (window.__dpSpeechInstalled) return;
  var native = window.DrishyopotSpeech;
  if (!native || window.SpeechRecognition || window.webkitSpeechRecognition) return;
  window.__dpSpeechInstalled = true;

  var active = null; // only one recognition runs at a time

  function Alternative(transcript, confidence) { this.transcript = transcript; this.confidence = confidence; }

  function makeResult(alts, isFinal) {
    var r = alts.map(function (a, i) { return new Alternative(a.t, a.c); });
    r.isFinal = isFinal;
    r.item = function (i) { return r[i]; };
    return r;
  }

  function makeList(results) {
    var l = results.slice();
    l.item = function (i) { return l[i]; };
    return l;
  }

  function Ev(type, init) {
    var e = new Event(type);
    for (var k in init) e[k] = init[k];
    return e;
  }

  function SpeechRecognition() {
    this.lang = '';
    this.continuous = false;
    this.interimResults = false;
    this.maxAlternatives = 1;
    this.grammars = null;
    this.serviceURI = '';
    this._target = document.createElement('span'); // event plumbing
    this._results = [];
    this._running = false;
    var self = this;
    ['start', 'end', 'error', 'result', 'nomatch', 'audiostart', 'audioend', 'soundstart', 'soundend', 'speechstart', 'speechend']
      .forEach(function (n) { self['on' + n] = null; });
  }
  var P = SpeechRecognition.prototype;

  P.addEventListener = function (t, f, o) { this._target.addEventListener(t, f, o); };
  P.removeEventListener = function (t, f, o) { this._target.removeEventListener(t, f, o); };
  P.dispatchEvent = function (e) { return this._target.dispatchEvent(e); };

  P._fire = function (type, init) {
    var e = Ev(type, init || {});
    var h = this['on' + type];
    if (typeof h === 'function') { try { h.call(this, e); } catch (x) { console.error(x); } }
    this._target.dispatchEvent(e);
  };

  P.start = function () {
    if (this._running) throw new DOMException('recognition has already started', 'InvalidStateError');
    if (active && active !== this) active.abort();
    active = this;
    this._running = true;
    this._results = [];
    var lang = this.lang || document.documentElement.lang || navigator.language || 'en-US';
    native.start(lang, !!this.interimResults, !!this.continuous, Math.max(1, this.maxAlternatives | 0));
  };
  P.stop = function () { if (this._running) native.stop(); };
  P.abort = function () { if (this._running) native.abort(); };

  // Called from Kotlin: window.__dpSpeech.onEvent({type: ..., ...})
  window.__dpSpeech = {
    onEvent: function (ev) {
      var rec = active;
      if (!rec) return;
      switch (ev.type) {
        case 'result': {
          var res = makeResult(ev.alternatives, !!ev.isFinal);
          var index = rec._results.length;
          if (ev.isFinal) {
            rec._results.push(res);
            rec._partial = false;
            rec._fire('result', { resultIndex: index, results: makeList(rec._results) });
          } else {
            var list = rec._results.concat([res]);
            rec._partial = true;
            rec._fire('result', { resultIndex: index, results: makeList(list) });
          }
          break;
        }
        case 'error':
          rec._fire('error', { error: ev.error, message: ev.message || '' });
          break;
        case 'end':
          rec._running = false;
          rec._partial = false;
          if (active === rec) active = null;
          rec._fire('end');
          break;
        default:
          rec._fire(ev.type); // start, audiostart, soundstart, speechstart, speechend, soundend, audioend, nomatch
      }
    },
  };

  function EventCtor(name, fields) {
    return function (type, init) {
      var e = new Event(type, init);
      fields.forEach(function (f) { e[f] = (init && init[f] !== undefined) ? init[f] : (f === 'resultIndex' ? 0 : ''); });
      return e;
    };
  }

  window.SpeechRecognition = SpeechRecognition;
  window.webkitSpeechRecognition = SpeechRecognition;
  window.SpeechRecognitionEvent = window.webkitSpeechRecognitionEvent = EventCtor('SpeechRecognitionEvent', ['resultIndex', 'results']);
  window.SpeechRecognitionErrorEvent = window.webkitSpeechRecognitionErrorEvent = EventCtor('SpeechRecognitionErrorEvent', ['error', 'message']);
})();
