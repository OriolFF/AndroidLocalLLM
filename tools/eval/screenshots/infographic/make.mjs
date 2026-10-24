// render card.html -> card.png (the "image-only recipe"), and a page wrapping it as a single <img>
import { createRequire } from 'node:module'; import path from 'node:path'; import fs from 'node:fs';
const require = createRequire(process.env.PUPPETEER_NODE_MODULES + '/'); const puppeteer = require('puppeteer-core');
const here = path.dirname(new URL(import.meta.url).pathname);
const b = await puppeteer.launch({ executablePath: '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: 'new' });
const p = await b.newPage(); await p.setViewport({ width: 900, height: 1000, deviceScaleFactor: 1 });
await p.goto('file://' + here + '/card.html'); await p.screenshot({ path: here + '/card.png', fullPage: true }); await b.close();
fs.writeFileSync(here + '/page.html', '<!doctype html><meta name=viewport content="width=device-width"><body style="margin:0"><img src="card.png" style="width:100%;display:block">');
