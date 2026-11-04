// Runs speech.js in jsdom against a fake Android bridge and checks the Web Speech API it provides.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';

const script = readFileSync(new URL('../../app/src/main/assets/browser/speech.js', import.meta.url), 'utf8');

function page({ withBridge = true } = {}) {
  const dom = new JSDOM('<!doctype html><html lang="bn"><body></body></html>', { url: 'https://m.youtube.com/', runScripts: 'outside-only' });
  const calls = [];
  if (withBridge) {
    dom.window.DrishyopotSpeech = {
      start: (...a) => calls.push(['start', ...a]),
      stop: () => calls.push(['stop']),
      abort: () => calls.push(['abort']),
    };
  }
  dom.window.eval(script);
  return { w: dom.window, calls };
}

// 1. Defined only when the bridge exists.
{
  assert.equal(page({ withBridge: false }).w.webkitSpeechRecognition, undefined);
  const { w } = page();
  assert.equal(typeof w.webkitSpeechRecognition, 'function');
  assert.equal(w.SpeechRecognition, w.webkitSpeechRecognition);
  console.log('ok  API appears only when the Android bridge is present');
}

// 2. start() reaches the app with the page language, and a full phrase produces Chrome-shaped events.
{
  const { w, calls } = page();
  const rec = new w.webkitSpeechRecognition();
  rec.interimResults = true;
  const log = [];
  rec.onstart = () => log.push('start');
  rec.onresult = (e) => log.push(['result', e.resultIndex, e.results.length, e.results[0][0].transcript, e.results[0].isFinal, e.results[0][0].confidence]);
  rec.onend = () => log.push('end');
  rec.start();
  assert.deepEqual(calls[0], ['start', 'bn', true, false, 1]);
  assert.throws(() => rec.start(), /already started/);
  const send = (o) => w.__dpSpeech.onEvent(o);
  send({ type: 'start' });
  send({ type: 'result', alternatives: [{ t: 'hel', c: 0.5 }], isFinal: false });
  send({ type: 'result', alternatives: [{ t: 'hello world', c: 0.93 }], isFinal: true });
  send({ type: 'end' });
  assert.deepEqual(log, ['start', ['result', 0, 1, 'hel', false, 0.5], ['result', 0, 1, 'hello world', true, 0.93], 'end']);
  rec.start(); // can be reused after end
  console.log('ok  events and results look like Chrome\'s');
}

// 3. Errors reach onerror and addEventListener; abort/stop call through.
{
  const { w, calls } = page();
  const rec = new w.webkitSpeechRecognition();
  const seen = [];
  rec.onerror = (e) => seen.push('on:' + e.error);
  rec.addEventListener('error', (e) => seen.push('ev:' + e.error));
  rec.start();
  w.__dpSpeech.onEvent({ type: 'error', error: 'no-speech' });
  w.__dpSpeech.onEvent({ type: 'end' });
  assert.deepEqual(seen, ['on:no-speech', 'ev:no-speech']);
  rec.start(); rec.stop(); rec.abort();
  assert.deepEqual(calls.slice(-2), [['stop'], ['abort']]);
  console.log('ok  errors, stop and abort');
}
