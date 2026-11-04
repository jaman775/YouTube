// Exercises the glue against a fake BotGuard VM and fake network. It cannot prove Google still
// accepts the flow; it proves our plumbing (challenge parsing, snapshot, minting, caching) is right.
import assert from 'node:assert/strict';
import { generate, resetForTests, findVisitorData, looksLikeVisitorData } from '../src/potoken.js';

const VISITOR = 'CgtBQkNERUZHSElKSyj3jPSzBjIKCgJCRBIEGgAgSg%3D%3D';

// Fake interpreter script: defines globalThis.FakeVM with the same shape as the real BotGuard VM.
const interpreter = `
  globalThis.FakeVM = {
    a: function (program, setup) {
      setup(
        function asyncSnapshot(callback, args) {
          args[2].push(async function getMinter(integrityBytes) {
            return async function mint(binding) {
              const out = new Uint8Array(binding.length + 1);
              out[0] = integrityBytes[0];
              out.set(binding, 1);
              return out;
            };
          });
          callback('BOTGUARD_RESPONSE');
        },
        function () {}, function () {}, function () {}
      );
      return [function () {}];
    }
  };`;

let calls = [];
function makeFetch(overrides = {}) {
  return async (url, init = {}) => {
    calls.push({ url: String(url), body: init.body });
    const u = String(url);
    const reply = (body, ok = true, status = 200) => ({
      ok, status,
      json: async () => body,
      text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
    });
    if (overrides[u]) return overrides[u](init);
    if (u.endsWith('/api/jnn/v1/Create')) {
      return reply([['msg-1', [null, interpreter], [null, '//x'], 'hash', 'PROGRAM', 'FakeVM', null, 'blob']]);
    }
    if (u.endsWith('/api/jnn/v1/GenerateIT')) return reply(['AQID', 3600, 600, 'fallback']);
    if (u.endsWith('/sw.js_data')) return reply(")]}'\n" + JSON.stringify([[null, [[[null, 'x', VISITOR]]]]]));
    return reply('<html></html>');
  };
}

// 1. Full flow
{
  resetForTests(); calls = [];
  const result = await generate('dQw4w9WgXcQ', makeFetch(), globalThis);
  assert.equal(result.visitorData, VISITOR);
  assert.ok(result.playerRequestPoToken.length > 0);
  assert.ok(result.streamingDataPoToken.length > 0);
  assert.notEqual(result.playerRequestPoToken, result.streamingDataPoToken);
  const create = calls.find((c) => c.url.endsWith('/Create'));
  assert.equal(JSON.parse(create.body)[0], 'O43z0dpjhgX20SCx4KAo');
  const gen = calls.find((c) => c.url.endsWith('/GenerateIT'));
  assert.deepEqual(JSON.parse(gen.body), ['O43z0dpjhgX20SCx4KAo', 'BOTGUARD_RESPONSE']);
  console.log('ok  full flow');
}

// 2. Session is reused for the next video
{
  calls = [];
  await generate('abcdefghijk', makeFetch(), globalThis);
  assert.equal(calls.filter((c) => c.url.endsWith('/Create')).length, 0);
  console.log('ok  session cached');
}

// 3. Minted tokens differ by content binding
{
  const a = await generate('AAAAAAAAAAA', makeFetch(), globalThis);
  const b = await generate('BBBBBBBBBBB', makeFetch(), globalThis);
  assert.notEqual(a.playerRequestPoToken, b.playerRequestPoToken);
  assert.equal(a.streamingDataPoToken, b.streamingDataPoToken);
  console.log('ok  bindings');
}

// 4. Visitor data falls back to the HTML page
{
  resetForTests();
  const fetchFn = makeFetch({
    'https://www.youtube.com/sw.js_data': async () => ({ ok: false, status: 404, text: async () => '' }),
    'https://www.youtube.com/': async () => ({ ok: true, status: 200, text: async () => `x"VISITOR_DATA":"${VISITOR}"y` }),
  });
  const result = await generate('dQw4w9WgXcQ', fetchFn, globalThis);
  assert.equal(result.visitorData, VISITOR);
  console.log('ok  visitor fallback');
}

// 5. Errors surface
{
  resetForTests();
  const broken = makeFetch({
    'https://www.youtube.com/api/jnn/v1/GenerateIT': async () => ({ ok: false, status: 403, json: async () => ({}), text: async () => '' }),
  });
  await assert.rejects(() => generate('dQw4w9WgXcQ', broken, globalThis), /GenerateIT failed: HTTP 403/);
  await assert.rejects(() => generate('', makeFetch(), globalThis), /videoId required/);
  console.log('ok  errors');
}

// 6. Visitor-data detection
{
  assert.ok(looksLikeVisitorData(VISITOR));
  assert.ok(!looksLikeVisitorData('hello world'));
  assert.equal(findVisitorData([1, [2, ['nope', VISITOR]]]), VISITOR);
  assert.equal(findVisitorData({ a: 1 }), null);
  console.log('ok  visitor detection');
}
console.log('all PO-token glue tests passed');
