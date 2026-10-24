// Capture screenshots of the pages in pages.json the way a phone would show them.
// usage: node shoot.mjs <outDir> [pageId ...]   (needs puppeteer-core: set PUPPETEER_NODE_MODULES or run from a dir that has it)
// Per page: <id>_v1..vN.png (viewports starting at the ingredients, 1080x2400), <id>_full.png (one tall capture, cut at 12000 px)
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
const require = createRequire((process.env.PUPPETEER_NODE_MODULES || process.cwd()) + '/');
const puppeteer = require('puppeteer-core');
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const here = path.dirname(new URL(import.meta.url).pathname);
const pages = JSON.parse(fs.readFileSync(path.join(here, 'pages.json'), 'utf8'));
const truthFile = path.join(here, 'truth.json');
const truth = fs.existsSync(truthFile) ? JSON.parse(fs.readFileSync(truthFile, 'utf8')) : {};
const [out, ...only] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const VW = 412, VH = 915, DPR = 2.62, MAXSHOTS = 5;
const browser = await puppeteer.launch({ executablePath: CHROME, headless: 'new', args: ['--hide-scrollbars'] });
for (const p of pages.filter(p => !only.length || only.includes(p.id))) {
  const page = await browser.newPage();
  await page.setUserAgent('Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36');
  await page.setViewport({ width: VW, height: VH, deviceScaleFactor: DPR, isMobile: true, hasTouch: true });
  await page.setRequestInterception(true);
  page.on('request', r => (r.resourceType() === 'media' || /doubleclick|googlesyndication|adsystem|mediavine|adthrive|raptive|taboola|outbrain|facebook\.net|googletagmanager|hotjar|ads\./.test(r.url())) ? r.abort().catch(() => {}) : r.continue().catch(() => {}));
  try {
    await page.goto(p.url, { waitUntil: 'domcontentloaded', timeout: 45000 });
  } catch (e) { console.log(p.id, 'goto:', e.message); }
  await new Promise(r => setTimeout(r, 4000));
  const safe = async (fn, ...a) => { for (let i = 0; i < 4; i++) { try { return await page.evaluate(fn, ...a); } catch (e) { await new Promise(r => setTimeout(r, 3000)); } } return null; };
  // dismiss consent walls: click accept buttons (also inside frames), then strip leftover overlays
  const accept = () => { for (const b of document.querySelectorAll('button,a[role=button],a,input[type=button]')) {
    if (/^(accept|agree|i agree|allow all|got it|aceptar|accepter|αποδοχή|συμφωνώ|tout accepter|akzeptieren|ok$)/i.test((b.innerText || b.value || '').trim())) { b.click(); return true; } } return false; };
  for (const fr of page.frames()) { try { await fr.evaluate(accept); } catch (e) {} }
  await new Promise(r => setTimeout(r, 3000));
  await new Promise(r => setTimeout(r, 5000));
  await safe(() => {
    for (const e of document.querySelectorAll('iframe[id*=sp_message],iframe[src*=consent],[id*=consent],[class*=consent],[id*=cookie],[class*=cookie],[id*=onetrust],[class*=cmp],[id^=sp_message]')) { if (e !== document.body && e !== document.documentElement && !e.contains(document.querySelector('h1'))) e.remove(); }
    for (const e of document.querySelectorAll('body *')) { const cs = getComputedStyle(e); if ((cs.position === 'fixed') && e.getBoundingClientRect().height > 150) e.remove(); }
    document.documentElement.style.overflow = 'auto'; if (document.body) { document.body.style.overflow = 'visible'; document.body.style.height = 'auto'; document.body.style.position = 'static'; }
  });
  await new Promise(r => setTimeout(r, 1500));
  // lazy images: scroll through once
  const height = await safe(async () => {
    for (let y = 0; y < Math.min(document.body.scrollHeight, 15000); y += 700) { window.scrollTo(0, y); await new Promise(r => setTimeout(r, 120)); }
    return document.documentElement.scrollHeight;
  });
  // start at the first ingredient: smallest element whose text contains the last two words of truth.json's first ingredient
  const first = truth[p.id] && truth[p.id].ingredients[0];
  const needle = p.needle ? p.needle : first ? first.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').split(/\s+/).filter(w => w.length > 2).slice(-2).join(' ') : '';
  const startY = await safe((re, needle) => {
    const norm = t => (t || '').toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').replace(/\s+/g, ' ');
    let best = null;
    if (needle) for (const e of document.querySelectorAll('li,p,span,div,td,label,a')) {
      if (e.getBoundingClientRect().height === 0 || e.children.length > 6) continue;
      if (norm(e.innerText).includes(needle)) { if (!best || (e.innerText || '').length < (best.innerText || '').length) best = e; }
    }
    if (!best) {
      const rx = new RegExp(re, 'i');
      best = [...document.querySelectorAll('h1,h2,h3,h4,h5,strong,b,p,div,span,li')].find(e => e.children.length < 3 && rx.test((e.innerText || '').trim().slice(0, 40)));
    }
    return best ? Math.max(0, best.getBoundingClientRect().top + window.scrollY - 80) : 0;
  }, p.anchor || '^(ingredients?|ingredientes|ingrédients|υλικά|you.ll need|what you.ll need)', needle);
  const full = (await safe(() => Math.max(document.documentElement.scrollHeight, document.body ? document.body.scrollHeight : 0))) || VH;
  let n = 0;
  for (let y = startY; y < full && n < MAXSHOTS; y += VH * 0.88) {
    await page.evaluate(yy => window.scrollTo(0, yy), y);
    await new Promise(r => setTimeout(r, 400));
    n++;
    await page.screenshot({ path: path.join(out, `${p.id}_v${n}.png`) });
  }
  // one tall capture: from just above the ingredients, ~4.5 viewports tall (a long scroll worth of recipe)
  const clipY = Math.min(Math.floor(startY), Math.max(0, full - 200)), clipH = Math.max(200, Math.min(full - clipY, Math.round(VH * 4.5)));
  await page.screenshot({ path: path.join(out, `${p.id}_full.png`), clip: { x: 0, y: clipY, width: VW, height: clipH }, captureBeyondViewport: true });
  console.log(p.id, 'startY', Math.round(startY), 'height', full, 'shots', n);
  await page.close();
}
await browser.close();
