// Text-only baseline input: the page's rendered text (document.body.innerText) cut to a 9,000-char window that starts
// just before the first ingredient (the app's budgetChars), prefixed like RecipeKeeperClipConfig.renderLlmInput.
// usage: node dump_text.mjs <outDir> [pageId ...]
import { createRequire } from 'node:module'; import fs from 'node:fs'; import path from 'node:path';
const require = createRequire((process.env.PUPPETEER_NODE_MODULES || process.cwd()) + '/'); const puppeteer = require('puppeteer-core');
const here = path.dirname(new URL(import.meta.url).pathname);
const pages = JSON.parse(fs.readFileSync(path.join(here, 'pages.json'), 'utf8'));
const truth = JSON.parse(fs.readFileSync(path.join(here, 'truth.json'), 'utf8'));
const [out, ...only] = process.argv.slice(2); fs.mkdirSync(out, { recursive: true });
const b = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new' });
for (const p of pages.filter(p => !only.length || only.includes(p.id))) {
  const page = await b.newPage();
  await page.setUserAgent('Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36');
  await page.setViewport({ width: 412, height: 915, deviceScaleFactor: 1, isMobile: true });
  await page.setRequestInterception(true);
  page.on('request', r => (r.resourceType() === 'media' || r.resourceType() === 'image' || /doubleclick|googlesyndication|adsystem|mediavine|adthrive|raptive/.test(r.url())) ? r.abort().catch(() => {}) : r.continue().catch(() => {}));
  try { await page.goto(p.url, { waitUntil: 'domcontentloaded', timeout: 45000 }); } catch (e) {}
  await new Promise(r => setTimeout(r, 6000));
  let r = null;
  for (let i = 0; i < 3 && !r; i++) { try { r = await page.evaluate(() => ({ t: document.body.innerText, title: document.title, host: location.hostname })); } catch (e) { await new Promise(r => setTimeout(r, 3000)); } }
  if (!r) { console.log(p.id, 'no text'); continue; }
  const norm = t => t.replace(/[ \t]+/g, ' ').replace(/\n{2,}/g, '\n').trim();
  const text = norm(r.t);
  const first = truth[p.id].ingredients[0].toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').split(/\s+/).filter(w => w.length > 2).slice(-2).join(' ');
  const low = text.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ');
  // locate in the original text by scanning for the first line containing the needle
  const lines = text.split('\n'); let idx = lines.findIndex(l => l.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ').includes(first));
  const start = Math.max(0, idx - 3);
  const body = lines.slice(start).join('\n').slice(0, 9000);
  fs.writeFileSync(path.join(out, `${p.id}.txt`), `Page: ${r.host} — ${r.title}\n\n${body}`);
  console.log(p.id, 'lines', lines.length, 'start', start, 'chars', body.length);
  await page.close();
}
await b.close(); process.exit(0);
