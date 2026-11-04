// Runs app/src/main/assets/browser/inject.js inside jsdom and checks what it does to a YouTube-like page.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';

const script = readFileSync(new URL('../../app/src/main/assets/browser/inject.js', import.meta.url), 'utf8');

function page(path) {
  const dom = new JSDOM('<!doctype html><body><ytm-app><ytm-browse><div id="content">feed</div></ytm-browse></ytm-app></body>', {
    url: 'https://m.youtube.com' + path, runScripts: 'outside-only', pretendToBeVisual: true,
  });
  const calls = [];
  dom.window.Drishyopot = { menu: (id) => calls.push(['menu', id]), openVideo: (u) => calls.push(['video', u]) };
  dom.window.__drishyopotConfig = {
    hideShorts: true, tabs: 3,
    labels: { title: 'Drishyopot', voice: 'Voice search', tabs: 'Tabs', settings: 'Settings', downloads: 'Downloads' },
  };
  dom.window.eval(script);
  return { dom, calls };
}
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

// 1. The menu appears on the You page, first inside ytm-browse, with every entry and no emoji.
{
  const { dom } = page('/feed/library');
  await wait(600);
  const doc = dom.window.document;
  const menu = doc.getElementById('drishyopot-menu');
  assert.ok(menu, 'menu injected');
  assert.equal(menu.parentElement.tagName.toLowerCase(), 'ytm-browse');
  assert.equal(menu.parentElement.firstElementChild, menu);
  assert.equal(menu.querySelectorAll('.dp-chip').length, 5);
  assert.equal(menu.querySelectorAll('.dp-row').length, 10);
  assert.ok(menu.textContent.includes('Tabs (3)'));
  assert.ok(menu.textContent.includes('Settings'));
  assert.ok(menu.querySelectorAll('svg path').length >= 15, 'icons are SVG paths');
  assert.ok(!/[\u{1F300}-\u{1FAFF}\u2600-\u27BF]/u.test(menu.textContent), 'no emoji in the menu');
  console.log('ok  menu appears on the You page');
}

// 2. Entries call the app.
{
  const { dom, calls } = page('/feed/library');
  await wait(600);
  const rows = [...dom.window.document.querySelectorAll('#drishyopot-menu button')];
  rows.find((b) => b.textContent.includes('Settings')).click();
  rows.find((b) => b.textContent.includes('Voice')).click();
  assert.deepEqual(calls, [['menu', 'settings'], ['menu', 'voice']]);
  console.log('ok  entries reach the app');
}

// 3. No menu elsewhere; leaving the You page removes it; coming back adds it again.
{
  const { dom } = page('/');
  await wait(600);
  assert.equal(dom.window.document.getElementById('drishyopot-menu'), null);
  dom.window.history.pushState({}, '', '/feed/you');
  await wait(600);
  assert.ok(dom.window.document.getElementById('drishyopot-menu'));
  dom.window.history.pushState({}, '', '/results?search_query=x');
  await wait(600);
  assert.equal(dom.window.document.getElementById('drishyopot-menu'), null);
  console.log('ok  menu follows the You page');
}

// 4. If YouTube re-renders and drops the menu, it comes back; a config change rebuilds it with new labels.
{
  const { dom } = page('/feed/library');
  await wait(600);
  dom.window.document.getElementById('drishyopot-menu').remove();
  await wait(600);
  assert.ok(dom.window.document.getElementById('drishyopot-menu'));
  dom.window.__drishyopotConfig = { hideShorts: false, tabs: 1, labels: { title: 'দৃশ্যপট', settings: 'সেটিংস' } };
  dom.window.__drishyopotApply();
  const menu = dom.window.document.getElementById('drishyopot-menu');
  assert.ok(menu.textContent.includes('সেটিংস') && menu.textContent.includes('দৃশ্যপট'));
  console.log('ok  menu re-renders and re-localises');
}

// 5. Video links go to the app; ordinary links are untouched.
{
  const { dom, calls } = page('/');
  const doc = dom.window.document;
  doc.body.insertAdjacentHTML('beforeend', '<a id="v" href="/watch?v=dQw4w9WgXcQ">v</a><a id="c" href="/@someone">c</a><a id="s" href="/shorts/dQw4w9WgXcQ">s</a>');
  const click = (id) => { const ev = new dom.window.MouseEvent('click', { bubbles: true, cancelable: true, composed: true }); doc.getElementById(id).dispatchEvent(ev); return ev.defaultPrevented; };
  assert.equal(click('v'), true);
  assert.equal(click('s'), true);
  assert.equal(click('c'), false);
  assert.deepEqual(calls.map((c) => c[0]), ['video', 'video']);
  console.log('ok  video links routed natively');
}

// 6. Ads are stripped from YouTube JSON; Shorts CSS follows the setting.
{
  const { dom } = page('/');
  const data = JSON.parse(JSON.stringify({
    responseContext: {},
    adPlacements: [{ x: 1 }],
    contents: [{ richItemRenderer: { content: { adSlotRenderer: {} } } }, { richItemRenderer: { content: { videoRenderer: { id: 'keep' } } } }],
  }));
  const parsed = dom.window.JSON.parse(JSON.stringify(data));
  assert.equal(parsed.adPlacements, undefined);
  assert.equal(parsed.contents.length, 1);
  assert.equal(parsed.contents[0].richItemRenderer.content.videoRenderer.id, 'keep');
  dom.window.__drishyopotApply();
  assert.ok(dom.window.document.getElementById('drishyopot-style').textContent.includes('ytm-reel-shelf-renderer'));
  dom.window.__drishyopotConfig = { hideShorts: false, tabs: 1, labels: {} };
  dom.window.__drishyopotApply();
  assert.ok(!dom.window.document.getElementById('drishyopot-style').textContent.includes('ytm-reel-shelf-renderer'));
  console.log('ok  ads stripped, Shorts setting honoured');
}
dom_exit();
function dom_exit() { console.log('all page-script tests passed'); process.exit(0); }
