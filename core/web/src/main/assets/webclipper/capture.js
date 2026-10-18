// capture.js — page-capture IIFE injected into the WebView by WebViewPageCapture.
//
// Owned by WS2 (:core:web). Must be an IIFE that RETURNS AN OBJECT (not a
// stringified one) so `WebView.evaluateJavascript` yields single-encoded
// JSON. Schema (mapped by PageSnapshotMapper.kt — keep the two in sync):
//   {
//     url: string,
//     title: string|null,
//     siteName: string|null,
//     jsonLd: string[],                 // raw JSON-LD script texts
//     microdata: object[],              // pseudo-JSON-LD for [itemtype*="schema.org/Recipe"] nodes
//     og: { [key]: string },            // og:* / twitter:* meta
//     readable: { title, byline, excerpt, siteName, textContent, length } | null,
//     sections: [{ heading, level, text, imageSrcs, topOffset }],
//     images: [{ src, alt, width, height, naturalWidth, naturalHeight, top, inRecipeCard }],
//     truncated: boolean,               // size guard tripped (Readability skipped)
//   }
//
// Readability: WebViewPageCapture wraps this file in an outer function
// together with Readability.js and Readability-readerable.js, so the
// `Readability` constructor is visible as a free identifier. The script
// also accepts `window.Readability` and degrades to `readable: null` when
// neither is defined, so it can be evaluated on its own for debugging.

(function () {
  "use strict";

  var MAX_SECTIONS = 20;
  var MAX_SECTION_CHARS = 6000;
  var READABILITY_SIZE_GUARD = 4 * 1024 * 1024; // outerHTML chars
  var MIN_DISPLAYED_AREA = 150 * 150;
  var MIN_NATURAL_WIDTH = 300;
  // Site chrome and non-rendered elements never contribute section text.
  var SKIPPED_TAGS = ["SCRIPT", "STYLE", "NOSCRIPT", "TEMPLATE", "NAV", "HEADER", "FOOTER", "ASIDE", "IFRAME", "SVG"];
  var RECIPE_CARD_SELECTOR =
    '[itemtype*="Recipe"], .wprm-recipe, .tasty-recipes, .mv-create-card, [class*="recipe"]';

  // ----- helpers -----------------------------------------------------------

  function absoluteUrl(src) {
    if (!src || typeof src !== "string") return null;
    var trimmed = src.trim();
    if (!trimmed || /^(data|blob|javascript):/i.test(trimmed)) return null;
    try {
      var url = new URL(trimmed, document.baseURI);
      if (url.protocol !== "http:" && url.protocol !== "https:") return null;
      return url.toString();
    } catch (e) {
      return null;
    }
  }

  function collapse(text) {
    return (text || "").replace(/\s+/g, " ").trim();
  }

  var BLOCK_TAGS = /^(P|DIV|LI|UL|OL|DL|DT|DD|TR|TABLE|THEAD|TBODY|SECTION|ARTICLE|FIGURE|FIGCAPTION|BLOCKQUOTE|PRE|H[1-6]|BR|HR|FORM|FIELDSET|DETAILS|SUMMARY)$/;

  /** Block-aware textContent: newlines around block elements, used when `innerText` is unavailable. */
  function blockText(el) {
    var out = "";
    var child = el.firstChild;
    while (child) {
      if (child.nodeType === 3) {
        out += child.textContent;
      } else if (child.nodeType === 1 && SKIPPED_TAGS.indexOf(child.tagName) === -1) {
        var block = BLOCK_TAGS.test(child.tagName);
        if (block) out += "\n";
        out += child.tagName === "BR" ? "" : blockText(child);
        if (block) out += "\n";
      }
      child = child.nextSibling;
    }
    return out;
  }

  /** Visible text: `innerText` honours CSS and skips script/style; falls back to a block-aware walk. */
  function visibleText(el) {
    if (!el) return "";
    if (el.nodeType === 3) return collapse(el.textContent);
    if (el.nodeType !== 1) return "";
    var tag = el.tagName;
    if (SKIPPED_TAGS.indexOf(tag) !== -1) return "";
    var text = typeof el.innerText === "string" ? el.innerText : blockText(el);
    return (text || "").replace(/[ \t\f\v\u00a0]+/g, " ").replace(/\s*\n\s*/g, "\n").trim();
  }

  function isHeading(el) {
    return !!el && el.nodeType === 1 && /^H[1-3]$/.test(el.tagName);
  }

  function containsHeading(el) {
    return !!el && el.nodeType === 1 && !!el.querySelector && !!el.querySelector("h1, h2, h3");
  }

  function documentTop(el) {
    if (!el || !el.getBoundingClientRect) return 0;
    var rect = el.getBoundingClientRect();
    return Math.round((rect.top || 0) + (window.scrollY || window.pageYOffset || 0));
  }

  function dedupe(arr) {
    var seen = {};
    var out = [];
    for (var i = 0; i < arr.length; i++) {
      var v = arr[i];
      if (v && !seen[v]) {
        seen[v] = true;
        out.push(v);
      }
    }
    return out;
  }

  // ----- structured data ---------------------------------------------------

  function jsonLdScriptTexts() {
    var out = [];
    var nodes = document.querySelectorAll('script[type="application/ld+json"]');
    for (var i = 0; i < nodes.length; i++) {
      var raw = nodes[i].textContent;
      if (raw && raw.trim().length > 0) out.push(raw);
    }
    return out;
  }

  /** Value of a microdata property element, per the HTML microdata spec. */
  function microdataValue(el) {
    var tag = el.tagName;
    var v = null;
    if (tag === "META") v = el.getAttribute("content");
    else if (tag === "IMG" || tag === "AUDIO" || tag === "VIDEO" || tag === "EMBED" || tag === "IFRAME" || tag === "SOURCE") v = absoluteUrl(pickImageSrc(el) || el.getAttribute("src"));
    else if (tag === "A" || tag === "AREA" || tag === "LINK") v = absoluteUrl(el.getAttribute("href"));
    else if (tag === "TIME") v = el.getAttribute("datetime") || collapse(el.textContent);
    else if (tag === "DATA" || tag === "METER") v = el.getAttribute("value");
    else if (el.hasAttribute("content")) v = el.getAttribute("content");
    else v = collapse(el.textContent);
    return v == null ? null : String(v).trim();
  }

  /** Nearest ancestor with `itemscope`, or null. */
  function itemScopeOf(el) {
    var node = el.parentElement;
    while (node) {
      if (node.hasAttribute("itemscope")) return node;
      node = node.parentElement;
    }
    return null;
  }

  function addProp(obj, key, value) {
    if (value == null || value === "") return;
    var existing = obj[key];
    if (Array.isArray(existing)) existing.push(value);
    else if (existing !== undefined) obj[key] = [existing, value];
    else obj[key] = value;
  }

  /**
   * Microdata item → pseudo-JSON-LD object (recursive). Properties
   * belonging to nested `itemscope`s become nested objects, so a
   * `recipeInstructions` HowToStep ends up as `{ "@type": "HowToStep", text }`
   * rather than leaking its `text` into the recipe root.
   */
  function microdataItem(root, depth) {
    var typeAttr = root.getAttribute("itemtype") || "";
    var types = typeAttr.split(/\s+/).filter(Boolean).map(function (t) {
      return t.replace(/^https?:\/\/schema\.org\//i, "");
    });
    var obj = { "@context": "https://schema.org" };
    if (types.length === 1) obj["@type"] = types[0];
    else if (types.length > 1) obj["@type"] = types;
    var props = root.querySelectorAll("[itemprop]");
    for (var p = 0; p < props.length; p++) {
      var el = props[p];
      if (itemScopeOf(el) !== root) continue; // belongs to a nested item
      var names = (el.getAttribute("itemprop") || "").split(/\s+/).filter(Boolean);
      if (names.length === 0) continue;
      var value =
        el.hasAttribute("itemscope") && depth < 4
          ? microdataItem(el, depth + 1)
          : microdataValue(el);
      for (var n = 0; n < names.length; n++) addProp(obj, names[n], value);
    }
    return obj;
  }

  function microdataRecipeNodes() {
    var roots = document.querySelectorAll('[itemscope][itemtype*="schema.org/Recipe"]');
    var nodes = [];
    for (var r = 0; r < roots.length; r++) {
      if (itemScopeOf(roots[r])) {
        // Nested recipe (e.g. inside an ItemList): still emit it, but only
        // if no enclosing root is itself a Recipe (that one carries it).
        var outer = itemScopeOf(roots[r]);
        var nestedInRecipe = false;
        while (outer) {
          if ((outer.getAttribute("itemtype") || "").indexOf("schema.org/Recipe") !== -1) nestedInRecipe = true;
          outer = itemScopeOf(outer);
        }
        if (nestedInRecipe) continue;
      }
      try {
        nodes.push(microdataItem(roots[r], 0));
      } catch (e) {
        /* skip a malformed card */
      }
    }
    return nodes;
  }

  function openGraphMap() {
    var og = {};
    var metas = document.querySelectorAll(
      'meta[property^="og:"], meta[name^="og:"], meta[property^="twitter:"], meta[name^="twitter:"]'
    );
    for (var i = 0; i < metas.length; i++) {
      var key = metas[i].getAttribute("property") || metas[i].getAttribute("name");
      var content = metas[i].getAttribute("content");
      if (!key || !content) continue;
      key = key.trim().toLowerCase();
      if (og[key] === undefined) og[key] = content.trim(); // first wins (og:image is usually best first)
    }
    return og;
  }

  // ----- images ------------------------------------------------------------

  /** Largest candidate of a `srcset` by width/density descriptor. */
  function largestSrcsetEntry(srcset) {
    if (!srcset) return null;
    var best = null;
    var bestScore = -1;
    var entries = srcset.split(",");
    for (var i = 0; i < entries.length; i++) {
      var parts = entries[i].trim().split(/\s+/);
      if (!parts[0]) continue;
      var score = 0;
      var d = parts[1] || "";
      if (/w$/i.test(d)) score = parseFloat(d) || 0;
      else if (/x$/i.test(d)) score = (parseFloat(d) || 1) * 1000;
      else score = i; // no descriptor: keep document order, later wins
      if (score > bestScore) {
        bestScore = score;
        best = parts[0];
      }
    }
    return best;
  }

  /**
   * Resolve the real image URL. Lazy-loading plugins put a 1×1 `data:` or
   * blank placeholder in `src`, so every candidate is tried in order and
   * `data:`/`blob:` URLs are skipped rather than returned.
   */
  function pickImageSrc(img) {
    if (!img) return null;
    var candidates = [
      img.currentSrc,
      img.getAttribute("src"),
      img.getAttribute("data-src"),
      img.getAttribute("data-lazy-src"),
      img.getAttribute("data-original"),
      img.getAttribute("data-srcset") ? largestSrcsetEntry(img.getAttribute("data-srcset")) : null,
      img.getAttribute("data-lazy-srcset") ? largestSrcsetEntry(img.getAttribute("data-lazy-srcset")) : null,
      largestSrcsetEntry(img.getAttribute("srcset")),
    ];
    for (var i = 0; i < candidates.length; i++) {
      var abs = absoluteUrl(candidates[i]);
      if (abs) return abs;
    }
    return null;
  }

  function inRecipeCard(img) {
    if (!img.closest) return false;
    var card = img.closest(RECIPE_CARD_SELECTOR);
    // `[class*="recipe"]` is broad: a <body class="single-recipe"> would
    // flag every image on the page, so page-level containers don't count.
    return !!card && card !== document.body && card !== document.documentElement;
  }

  /**
   * Every <img> that passed the size filter (displayed area ≥ 150×150 or
   * natural width > 300), deduplicated by URL, in DOM order.
   */
  function buildImages() {
    var imgs = document.querySelectorAll("img");
    var out = [];
    var seen = {};
    for (var i = 0; i < imgs.length; i++) {
      var img = imgs[i];
      var src = pickImageSrc(img);
      if (!src || seen[src]) continue;
      var rect = img.getBoundingClientRect ? img.getBoundingClientRect() : { width: 0, height: 0, top: 0 };
      var w = Math.round(rect.width || 0);
      var h = Math.round(rect.height || 0);
      var nw = img.naturalWidth || 0;
      var nh = img.naturalHeight || 0;
      if (w * h < MIN_DISPLAYED_AREA && nw <= MIN_NATURAL_WIDTH) continue;
      seen[src] = true;
      out.push({
        src: src,
        alt: collapse(img.getAttribute("alt")) || null,
        width: w,
        height: h,
        naturalWidth: nw,
        naturalHeight: nh,
        top: documentTop(img),
        inRecipeCard: inRecipeCard(img),
      });
    }
    return out;
  }

  function imageSrcsIn(el) {
    var out = [];
    if (!el || !el.querySelectorAll) return out;
    if (el.tagName === "IMG") {
      var own = pickImageSrc(el);
      if (own) out.push(own);
    }
    var imgs = el.querySelectorAll("img");
    for (var j = 0; j < imgs.length; j++) {
      var s = pickImageSrc(imgs[j]);
      if (s) out.push(s);
    }
    return out;
  }

  // ----- sections ----------------------------------------------------------

  /**
   * Append the text/images of every node from [start] onwards (siblings in
   * document order) to [acc] until a heading is met. A wrapper that
   * contains a heading is descended into rather than skipped, so an intro
   * `<div class="entry-content"><p>…</p><h2>` still contributes its
   * paragraphs. Returns true when a heading (or the size cap) stopped the
   * walk, false when the siblings simply ran out.
   */
  function collectUntilHeading(start, acc) {
    for (var s = start; s; s = s.nextSibling) {
      if (s.nodeType === 3) {
        appendText(acc, collapse(s.textContent));
        continue;
      }
      if (s.nodeType !== 1) continue;
      if (isHeading(s)) return true;
      if (containsHeading(s)) {
        if (collectUntilHeading(s.firstChild, acc)) return true;
        continue;
      }
      appendText(acc, visibleText(s));
      acc.imageSrcs = acc.imageSrcs.concat(imageSrcsIn(s));
      if (acc.chars >= MAX_SECTION_CHARS) return true;
    }
    return false;
  }

  function appendText(acc, text) {
    if (!text) return;
    acc.parts.push(text);
    acc.chars += text.length + 1;
  }

  /**
   * Walk h1–h3 in document order. Each section collects the content that
   * follows its heading until the next heading. When a heading's siblings
   * run out (headings are usually wrapped in a <header> or a card <div>)
   * the walk climbs to the parent's next sibling, so content after the
   * wrapper is still attributed to the nearest preceding heading.
   */
  function buildSections() {
    var body = document.body;
    if (!body) return [];
    var headings = body.querySelectorAll("h1, h2, h3");
    var sections = [];

    if (headings.length === 0) {
      sections.push({
        heading: null,
        level: 0,
        text: visibleText(body).slice(0, MAX_SECTION_CHARS),
        imageSrcs: [],
        topOffset: 0,
      });
      return sections;
    }

    var leading = leadingTextBefore(headings[0]);
    if (leading.length > 0) {
      sections.push({ heading: null, level: 0, text: leading.slice(0, MAX_SECTION_CHARS), imageSrcs: [], topOffset: 0 });
    }

    for (var i = 0; i < headings.length && sections.length < MAX_SECTIONS; i++) {
      var h = headings[i];
      var acc = { parts: [], chars: 0, imageSrcs: [] };
      var stopped = collectUntilHeading(h.nextSibling, acc);
      var node = h.parentElement;
      while (!stopped && node && node !== body) {
        stopped = collectUntilHeading(node.nextSibling, acc);
        node = node.parentElement;
      }
      sections.push({
        heading: collapse(h.textContent) || null,
        level: parseInt(h.tagName.substring(1), 10),
        text: acc.parts.join("\n").slice(0, MAX_SECTION_CHARS),
        imageSrcs: dedupe(acc.imageSrcs),
        topOffset: documentTop(h),
      });
    }
    return sections;
  }

  /**
   * Intro text before the first heading: the heading's preceding siblings,
   * then its parent's, up to two levels (further up is usually site
   * chrome, and <nav>/<header>/<footer>/<aside> are skipped anyway).
   */
  function leadingTextBefore(firstHeading) {
    var parts = [];
    var node = firstHeading;
    for (var level = 0; level < 3 && node && node !== document.body; level++) {
      var chunk = [];
      for (var prev = node.previousSibling; prev; prev = prev.previousSibling) {
        var t = visibleText(prev);
        if (t) chunk.unshift(t);
      }
      parts = chunk.concat(parts);
      node = node.parentElement;
    }
    return parts.join("\n").trim();
  }

  // ----- readability -------------------------------------------------------

  var sizeGuardTripped = false;

  function runReadability() {
    var Ctor = typeof Readability === "function" ? Readability : window.Readability;
    if (typeof Ctor !== "function") return null;
    var outer = document.documentElement ? document.documentElement.outerHTML : "";
    if (outer.length > READABILITY_SIZE_GUARD) {
      sizeGuardTripped = true;
      return null;
    }
    try {
      var clone = document.cloneNode(true);
      var article = new Ctor(clone, { charThreshold: 500 }).parse();
      if (!article) return null;
      var text = article.textContent || "";
      return {
        title: article.title || null,
        byline: article.byline || null,
        excerpt: article.excerpt || null,
        siteName: article.siteName || null,
        textContent: text,
        length: typeof article.length === "number" ? article.length : text.length,
      };
    } catch (e) {
      return null;
    }
  }

  // ----- main ---------------------------------------------------------------

  var readable = runReadability();
  var sections = buildSections();
  var images = buildImages();
  var og = openGraphMap();

  return {
    url: document.URL || location.href || "",
    title: collapse(document.title) || null,
    siteName: og["og:site_name"] || (readable && readable.siteName) || null,
    jsonLd: jsonLdScriptTexts(),
    microdata: microdataRecipeNodes(),
    og: og,
    readable: readable,
    sections: sections,
    images: images,
    truncated: sizeGuardTripped,
  };
})();
