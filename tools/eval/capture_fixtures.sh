#!/usr/bin/env bash
# Re-capture the eval fixtures: fetch each page in tools/eval/fixtures.tsv,
# run WS2's capture.js in jsdom (core/web/tools/capture-harness/harness.mjs)
# and write app/recipekeeper/src/test/resources/eval/pages/<fixture>.json.
#
#   tools/eval/capture_fixtures.sh [--only FIXTURE]... [--from-html]
#
# --from-html  reuse tools/eval/html/<fixture>.html instead of fetching
#              (only the fixtures whose HTML is committed, ≤ 300 KB each).
#
# jsdom is installed outside the repo, in $LLMLOCAL_EVAL_CACHE/jsdom
# (default ~/.cache/llmlocal-eval/jsdom), the first time this runs.
# After re-capturing, update the expectations in
# app/recipekeeper/src/test/kotlin/.../eval/EvalFixtures.kt if a site changed,
# then regenerate prompts + goldens (see README.md).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
CACHE="${LLMLOCAL_EVAL_CACHE:-$HOME/.cache/llmlocal-eval}"
JSDOM_DIR="$CACHE/jsdom"
PAGES="$repo/app/recipekeeper/src/test/resources/eval/pages"
HTML_DIR="$here/html"
HARNESS="$repo/core/web/tools/capture-harness/harness.mjs"
UA="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
MAX_COMMIT_BYTES=$((300 * 1024))

only=()
from_html=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    --only) only+=("$2"); shift 2 ;;
    --from-html) from_html=1; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [[ ! -d "$JSDOM_DIR/node_modules/jsdom" ]]; then
  echo "Installing jsdom into $JSDOM_DIR (outside the repo)" >&2
  mkdir -p "$JSDOM_DIR" && (cd "$JSDOM_DIR" && npm init -y >/dev/null && npm install --silent jsdom@26)
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
today="$(date +%F)"
mkdir -p "$PAGES" "$HTML_DIR"

wrap="$tmp/wrap.js"
cat > "$wrap" <<'EOF'
const fs = require("fs");
const [,, inp, out, url, name, html, fetched] = process.argv;
const j = JSON.parse(fs.readFileSync(inp, "utf8"));
const doc = {
  _meta: {
    fixture: name, sourceUrl: url, fetchedAt: fetched,
    fetchedWith: "curl -L -A Mozilla/5.0 (desktop Chrome UA)",
    capturedWith: "core/web/tools/capture-harness/harness.mjs + jsdom (layout faked: images carry no size, so the images[] filter drops them)",
    htmlBytes: +html,
  },
  quickScan: j.quickScan, capture: j.capture,
};
fs.writeFileSync(out, JSON.stringify(doc, null, 1) + "\n");
EOF

while IFS=$'\t' read -r name url fetched _kept; do
  [[ -z "$name" || "$name" == \#* ]] && continue
  if [[ ${#only[@]} -gt 0 ]] && ! printf '%s\n' "${only[@]}" | grep -qx "$name"; then continue; fi
  html="$tmp/$name.html"
  if [[ $from_html == 1 ]]; then
    [[ -f "$HTML_DIR/$name.html" ]] || { echo "$name: no committed HTML, skipping (fetch without --from-html)" >&2; continue; }
    cp "$HTML_DIR/$name.html" "$html"
    stamp="$fetched"
  else
    code=$(curl -L -sS -A "$UA" -H "Accept: text/html,application/xhtml+xml" -H "Accept-Language: en-US,en;q=0.9" --max-time 60 -o "$html" -w "%{http_code}" "$url" || echo "000")
    if [[ "$code" != "200" ]]; then echo "$name: HTTP $code, keeping the previous fixture" >&2; continue; fi
    stamp="$today"
  fi
  bytes=$(wc -c < "$html" | tr -d ' ')
  (cd "$JSDOM_DIR" && node "$HARNESS" "$html" "$url" --json > "$tmp/$name.json")
  node "$wrap" "$tmp/$name.json" "$PAGES/$name.json" "$url" "$name" "$bytes" "$stamp"
  if [[ $from_html == 0 ]]; then
    if (( bytes <= MAX_COMMIT_BYTES )); then cp "$html" "$HTML_DIR/$name.html"; else rm -f "$HTML_DIR/$name.html"; fi
  fi
  echo "$name: html $bytes B → $PAGES/$name.json ($(wc -c < "$PAGES/$name.json" | tr -d ' ') B)"
done < "$here/fixtures.tsv"
