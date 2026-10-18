// Runs capture.js / quickscan.js against an HTML fixture in jsdom, assembling
// the capture script exactly as WebViewPageCapture.assembleCaptureScript does.
//
//   cd <dir with node_modules/jsdom> && node <repo>/core/web/tools/capture-harness/harness.mjs <fixture.html> [url]
//
// Layout data jsdom cannot compute is faked from data-w/data-h/data-nw/data-nh
// attributes on <img>. Not part of the Gradle build; a dev aid for WS6 QA.
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const require = createRequire(join(process.cwd(), "package.json"));
const { JSDOM } = require("jsdom");

const here = dirname(fileURLToPath(import.meta.url));
const assets = join(here, "..", "..", "src", "main", "assets", "webclipper");
const read = (f) => readFileSync(join(assets, f), "utf8");

export function assembleCaptureScript(readability, readerable, capture) {
  let c = capture.trim();
  if (c.endsWith(";")) c = c.slice(0, -1);
  return "(function () {\n" + readability + "\n;\n" + readerable + "\n;\n" + "return (\n" + c + "\n);\n" + "})();";
}

const fixture = process.argv[2];
const url = process.argv[3] || "https://flourandspoon.example/banana-bread/";
const html = readFileSync(fixture, "utf8");
const dom = new JSDOM(html, { url, runScripts: "outside-only", pretendToBeVisual: true });
const { window } = dom;

// Fake layout.
Object.defineProperty(window.HTMLImageElement.prototype, "naturalWidth", { get() { return +(this.getAttribute("data-nw") || 0); } });
Object.defineProperty(window.HTMLImageElement.prototype, "naturalHeight", { get() { return +(this.getAttribute("data-nh") || 0); } });
window.HTMLElement.prototype.getBoundingClientRect = function () {
  const w = +(this.getAttribute("data-w") || 0), h = +(this.getAttribute("data-h") || 0);
  let top = 0, n = this; while (n && n.previousElementSibling) { top += 100; n = n.previousElementSibling; }
  return { width: w, height: h, top, left: 0, right: w, bottom: top + h };
};

const captureScript = assembleCaptureScript(read("Readability.js"), read("Readability-readerable.js"), read("capture.js"));
const quick = window.eval(read("quickscan.js"));
const result = window.eval(captureScript);
if (process.argv.includes("--json")) {
  console.log(JSON.stringify({ quickScan: quick, capture: result }, null, 2));
} else {
  console.log("quickScan:", quick);
  console.log("typeof result:", typeof result, "| url:", result.url, "| title:", result.title, "| siteName:", result.siteName);
  console.log("jsonLd blocks:", result.jsonLd.length, "| microdata:", JSON.stringify(result.microdata));
  console.log("og:", result.og);
  console.log("readable:", result.readable && { title: result.readable.title, byline: result.readable.byline, length: result.readable.length, excerpt: result.readable.excerpt });
  for (const s of result.sections) console.log("section", s.level, JSON.stringify(s.heading), "top", s.topOffset, "imgs", s.imageSrcs, "\n   text:", JSON.stringify(s.text.slice(0, 160)));
  for (const i of result.images) console.log("image", i.src, `${i.width}x${i.height}`, `nat ${i.naturalWidth}x${i.naturalHeight}`, "top", i.top, "card", i.inRecipeCard, "alt", JSON.stringify(i.alt));
  console.log("truncated:", result.truncated);
}
