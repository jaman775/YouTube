// Bundles src/potoken.js and bgutils-js into one self-contained HTML page for the app.
import { build } from 'esbuild';
import { mkdirSync, writeFileSync } from 'node:fs';

const result = await build({
  entryPoints: ['src/potoken.js'],
  bundle: true,
  format: 'iife',
  platform: 'browser',
  target: ['chrome90'],
  minify: true,
  write: false,
  legalComments: 'none',
});
const js = result.outputFiles[0].text.replace(/<\/script/gi, '<\\/script');
const html = `<!doctype html><html><head><meta charset="utf-8"><title>po</title></head><body><script>${js}</script></body></html>\n`;
mkdirSync('dist', { recursive: true });
writeFileSync('dist/potoken.html', html);
console.log('potoken.html', html.length, 'bytes');
