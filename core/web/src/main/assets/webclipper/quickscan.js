// quickscan.js — tiny count-only script for the proactive clip-button badge.
// Returns the number of schema.org Recipe-like nodes on the page (JSON-LD
// (JSON-LD; microdata/RDFa only when JSON-LD found none, since pages
// routinely mark up the same recipe in both). Returns 0 on parse errors. Loaded separately so
// `quickScanStructured` doesn't pay the Readability cost on every page.
//
// Must be an IIFE returning a number so evaluateJavascript yields a
// single-encoded primitive.

(function () {
  "use strict";

  function countRecipeLikeInJsonLd(node) {
    if (!node) return 0;
    if (Array.isArray(node)) {
      var sum = 0;
      for (var i = 0; i < node.length; i++) {
        sum += countRecipeLikeInJsonLd(node[i]);
      }
      return sum;
    }
    if (typeof node !== "object") return 0;
    var types = node["@type"];
    var typeList = Array.isArray(types) ? types : types ? [types] : [];
    var isRecipe = typeList.some(function (t) {
      return (
        t === "Recipe" ||
        (typeof t === "string" && /(?:^|\/)Recipe$/.test(t))
      );
    });
    var found = isRecipe ? 1 : 0;
    if (Array.isArray(node["@graph"])) {
      for (var g = 0; g < node["@graph"].length; g++) {
        found += countRecipeLikeInJsonLd(node["@graph"][g]);
      }
    }
    if (node["mainEntity"]) {
      found += countRecipeLikeInJsonLd(node["mainEntity"]);
    }
    return found;
  }

  // Some sites wrap the block in an HTML comment or CDATA (old Yoast builds).
  function stripWrappers(text) {
    var t = (text || "").trim();
    if (t.indexOf("<!--") === 0) t = t.substring(4).replace(/-->\s*$/, "").trim();
    if (t.indexOf("<![CDATA[") === 0) t = t.substring(9).replace(/\]\]>\s*$/, "").trim();
    return t;
  }

  var count = 0;

  // JSON-LD: walk every application/ld+json script block.
  var nodes = document.querySelectorAll('script[type="application/ld+json"]');
  for (var i = 0; i < nodes.length; i++) {
    var text = stripWrappers(nodes[i].textContent);
    if (!text) continue;
    var parsed;
    try {
      parsed = JSON.parse(text);
    } catch (e) {
      continue;
    }
    count += countRecipeLikeInJsonLd(parsed);
  }

  // Microdata + RDFa roots: only when JSON-LD found nothing, otherwise the
  // same recipe described twice would be counted twice.
  if (count === 0) {
    count += document.querySelectorAll(
      '[itemscope][itemtype*="schema.org/Recipe"]'
    ).length;
    count += document.querySelectorAll('[typeof~="Recipe"]').length;
  }

  return count;
})();