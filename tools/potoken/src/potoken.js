// PO-token generator for Drishyopot.
// Runs inside a hidden WebView whose base URL is https://www.youtube.com/, so the BotGuard
// requests below are same-origin. Kotlin calls window.__po(callId, videoId) and receives the result
// through the `Android` bridge (onResult / onError).
import { BotGuardClient, getChallenge } from 'bgutils-js/botguard';
import { WebPoMinter } from 'bgutils-js/webpo';
import { buildURL, getHeaders } from 'bgutils-js/utils';

const REQUEST_KEY = 'O43z0dpjhgX20SCx4KAo';

/** @type {{ minter: WebPoMinter, visitorData: string, expiresAt: number } | null} */
let session = null;
let starting = null;

export function looksLikeVisitorData(value) {
  return typeof value === 'string' && value.length >= 11 && /^Cg[A-Za-z0-9_-]/.test(value) &&
    /^[A-Za-z0-9_%=.-]+$/.test(value);
}

/** Depth-first search for something that looks like a visitor-data string. */
export function findVisitorData(node, depth = 0) {
  if (depth > 8 || node == null) return null;
  if (typeof node === 'string') return looksLikeVisitorData(node) ? node : null;
  if (Array.isArray(node)) {
    for (const item of node) {
      const found = findVisitorData(item, depth + 1);
      if (found) return found;
    }
  }
  return null;
}

export async function fetchVisitorData(fetchFn) {
  try {
    const response = await fetchFn('https://www.youtube.com/sw.js_data');
    if (response.ok) {
      const text = (await response.text()).replace(/^\)\]\}'\s*/, '');
      const found = findVisitorData(JSON.parse(text));
      if (found) return found;
    }
  } catch (_) { /* fall through to the HTML page */ }
  const page = await fetchFn('https://www.youtube.com/');
  const html = await page.text();
  const match = html.match(/"VISITOR_DATA":"([^"]+)"/) || html.match(/"visitorData":"([^"]+)"/);
  if (!match) throw new Error('Could not find visitor data');
  return match[1];
}

async function fetchIntegrityToken(fetchFn, botguardResponse) {
  const response = await fetchFn(buildURL('GenerateIT', true), {
    method: 'POST',
    headers: getHeaders(),
    body: JSON.stringify([REQUEST_KEY, botguardResponse]),
  });
  if (!response.ok) throw new Error('GenerateIT failed: HTTP ' + response.status);
  const [integrityToken, estimatedTtlSecs, mintRefreshThreshold] = await response.json();
  if (typeof integrityToken !== 'string') throw new Error('No integrity token');
  return { integrityToken, estimatedTtlSecs, mintRefreshThreshold };
}

export async function startSession(fetchFn = (...a) => fetch(...a), globalObject = globalThis) {
  const challenge = await getChallenge({ requestKey: REQUEST_KEY, fetchFunction: fetchFn, useYouTubeAPI: true });
  const script = challenge.interpreterJavascript && challenge.interpreterJavascript.privateDoNotAccessOrElseSafeScriptWrappedValue;
  if (!script) throw new Error('No BotGuard interpreter in challenge');
  if (!challenge.globalName || !challenge.program) throw new Error('Incomplete BotGuard challenge');

  new Function(script).call(globalObject);

  const client = await BotGuardClient.create({
    globalObject,
    globalName: challenge.globalName,
    program: challenge.program,
  });
  const webPoSignalOutput = [];
  const botguardResponse = await client.snapshot({ webPoSignalOutput });
  const integrity = await fetchIntegrityToken(fetchFn, botguardResponse);
  const minter = await WebPoMinter.create({ integrityToken: integrity.integrityToken }, webPoSignalOutput);
  const visitorData = await fetchVisitorData(fetchFn);
  const ttlMs = (Number(integrity.estimatedTtlSecs) || 3600) * 1000;
  return { minter, visitorData, expiresAt: Date.now() + ttlMs * 0.8 };
}

async function ensureSession(fetchFn, globalObject) {
  if (session && Date.now() < session.expiresAt) return session;
  if (!starting) {
    starting = startSession(fetchFn, globalObject)
      .then((s) => { session = s; return s; })
      .finally(() => { starting = null; });
  }
  return starting;
}

/** Returns { visitorData, playerRequestPoToken, streamingDataPoToken }. */
export async function generate(videoId, fetchFn = (...a) => fetch(...a), globalObject = globalThis) {
  if (!videoId) throw new Error('videoId required');
  let s = await ensureSession(fetchFn, globalObject);
  try {
    return await mintAll(s, videoId);
  } catch (firstError) {
    // The integrity token may have been invalidated: start over once.
    session = null;
    s = await ensureSession(fetchFn, globalObject);
    return await mintAll(s, videoId);
  }
}

async function mintAll(s, videoId) {
  const playerRequestPoToken = await s.minter.mintAsWebsafeString(videoId);
  const streamingDataPoToken = await s.minter.mintAsWebsafeString(s.visitorData);
  return { visitorData: s.visitorData, playerRequestPoToken, streamingDataPoToken };
}

export function resetForTests() { session = null; starting = null; }

// ---- Bridge to the app ------------------------------------------------------------------------
if (typeof window !== 'undefined' && typeof window.Android !== 'undefined') {
  window.__po = async function (callId, videoId) {
    try {
      const result = await generate(videoId);
      window.Android.onResult(callId, JSON.stringify(result));
    } catch (e) {
      window.Android.onError(callId, String((e && e.message) || e));
    }
  };
  window.Android.onReady();
}
