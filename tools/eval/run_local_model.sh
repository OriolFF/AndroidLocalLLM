#!/usr/bin/env bash
# Run every rendered eval prompt (app/recipekeeper/build/eval/prompts/*.txt)
# through the same Gemma 4 E2B .litertlm bundle the app uses and write the
# goldens to app/recipekeeper/src/test/resources/eval/outputs/.
#
#   tools/eval/run_local_model.sh [--only FIXTURE]... [--dry-run] [runner args…]
#   tools/eval/run_local_model.sh --device [--serial SERIAL] [--model-path /data/local/tmp/eval/model.litertlm]
#
# Path (a), default — desktop LiteRT-LM on this machine: the `litert-lm`
# Python wheel (same 0.18.0 as the Android AAR, ships liblitert-lm.dylib /
# .so) in a venv under ~/.cache/llmlocal-eval/venv, CPU backend. The model
# is looked up at $LLMLOCAL_EVAL_MODEL, then ~/.cache/llmlocal-eval/, and
# downloaded from the catalog URL when missing (2.59 GB, public, no token).
#
# Path (b), --device — adb-driven instrumented runner: pushes the prompts to
# /data/local/tmp/eval/prompts, runs EvalRunnerTest (src/androidTest) against
# the model the Recipe Keeper app already has in files/llm/ (or a bundle
# pushed to --model-path), pulls outputs + timing JSON back. Needs an arm64
# device with the model downloaded; the shared emulator has neither the
# space nor the model.
#
# Every unknown argument is forwarded to run_local_model.py (see --help).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
cd "$repo"

MODEL_URL="https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
MODEL_NAME="gemma-4-E2B-it.litertlm"
LITERT_LM_VERSION="0.18.0"
CACHE="${LLMLOCAL_EVAL_CACHE:-$HOME/.cache/llmlocal-eval}"
VENV="$CACHE/venv"
PROMPTS="app/recipekeeper/build/eval/prompts"
OUT="app/recipekeeper/src/test/resources/eval/outputs"
APP_ID="com.llmlocal.recipekeeper"

mode="desktop"
serial=""
device_model_path=""
dry_run=0
py_args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      sed -n '2,23p' "$0" | sed 's/^# \{0,1\}//'
      echo
      echo "Python runner options:"
      python3 "$here/run_local_model.py" --help 2>/dev/null | sed -n '/options:/,$p' | sed 's/^/  /' || true
      exit 0 ;;
    --device) mode="device"; shift ;;
    --serial) serial="$2"; shift 2 ;;
    --model-path) device_model_path="$2"; shift 2 ;;
    --dry-run) dry_run=1; py_args+=("$1"); shift ;;
    *) py_args+=("$1"); shift ;;
  esac
done

if [[ ! -d "$PROMPTS" ]] || ! ls "$PROMPTS"/*.txt >/dev/null 2>&1; then
  echo "No prompts in $PROMPTS — generating them:" >&2
  ./gradlew :app:recipekeeper:testDebugUnitTest --tests '*EvalPromptRenderingTest*' --console=plain -q
fi
mkdir -p "$OUT"

# ---------------------------------------------------------------------------
# Path (b): adb-driven instrumented runner
# ---------------------------------------------------------------------------
if [[ "$mode" == "device" ]]; then
  adb_cmd=(adb)
  [[ -n "$serial" ]] && adb_cmd+=(-s "$serial")
  if [[ -z "$serial" ]]; then
    n=$("${adb_cmd[@]}" devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')
    [[ "$n" == "1" ]] || { echo "need exactly one device or --serial (found $n)" >&2; exit 2; }
  fi
  abi=$("${adb_cmd[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')
  echo "device: $("${adb_cmd[@]}" shell getprop ro.product.model | tr -d '\r') ($abi)"
  has_model=0
  if [[ -n "$device_model_path" ]] || "${adb_cmd[@]}" shell run-as "$APP_ID" ls files/llm/ 2>/dev/null | grep -q '\.litertlm$'; then has_model=1; fi
  if [[ $dry_run == 1 ]]; then
    echo "dry run: would push $PROMPTS, run EvalRunnerTest, pull $OUT (model on device: $([[ $has_model == 1 ]] && echo yes || echo NO))"
    exit 0
  fi
  if [[ $has_model == 0 ]]; then
    echo "no .litertlm under $APP_ID/files/llm/ on the device: download the model in Recipe Keeper's model management first, or pass --model-path <file on device>" >&2
    exit 3
  fi
  "${adb_cmd[@]}" shell rm -rf /data/local/tmp/eval/prompts
  "${adb_cmd[@]}" shell mkdir -p /data/local/tmp/eval/prompts
  for f in "$PROMPTS"/*.txt; do "${adb_cmd[@]}" push "$f" /data/local/tmp/eval/prompts/ >/dev/null; done
  "${adb_cmd[@]}" shell chmod -R a+rX /data/local/tmp/eval
  ./gradlew :app:recipekeeper:installDebug :app:recipekeeper:installDebugAndroidTest --console=plain -q
  extra=()
  [[ -n "$device_model_path" ]] && extra+=(-e modelPath "$device_model_path")
  "${adb_cmd[@]}" shell am instrument -w -r \
    -e class "$APP_ID.eval.EvalRunnerTest" \
    -e promptsDir /data/local/tmp/eval/prompts \
    "${extra[@]}" \
    "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner" | tee /tmp/llmlocal-eval-instrument.log
  # Outputs land in the app's private files/eval/outputs; pull via run-as + tar.
  "${adb_cmd[@]}" exec-out run-as "$APP_ID" tar -C files/eval -cf - outputs | tar -C "$OUT/.." -xf -
  echo "pulled → $OUT"
  exit 0
fi

# ---------------------------------------------------------------------------
# Path (a): desktop LiteRT-LM Python wheel
# ---------------------------------------------------------------------------
model="${LLMLOCAL_EVAL_MODEL:-}"
if [[ -z "$model" ]]; then
  for candidate in "$CACHE/$MODEL_NAME" "$HOME/.litert-lm/models/$MODEL_NAME"; do
    [[ -f "$candidate" ]] && { model="$candidate"; break; }
  done
fi
if [[ -z "$model" ]]; then
  model="$CACHE/$MODEL_NAME"
  if [[ $dry_run == 1 ]]; then
    echo "model would be downloaded to $model"
  else
    echo "Downloading $MODEL_NAME (2.59 GB) → $model" >&2
    mkdir -p "$CACHE"
    curl -L -C - --fail -A "Mozilla/5.0" -o "$model" "$MODEL_URL"
  fi
fi

if [[ ! -x "$VENV/bin/python" ]]; then
  echo "Creating venv with litert-lm==$LITERT_LM_VERSION at $VENV" >&2
  mkdir -p "$CACHE"
  if command -v uv >/dev/null 2>&1; then
    uv venv -q --python 3.12 "$VENV" || uv venv -q "$VENV"
    uv pip install -q --python "$VENV/bin/python" "litert-lm==$LITERT_LM_VERSION"
  else
    python3 -m venv "$VENV"
    "$VENV/bin/pip" install -q "litert-lm==$LITERT_LM_VERSION"
  fi
fi

exec "$VENV/bin/python" -I "$here/run_local_model.py" \
  --model "$model" --prompts "$PROMPTS" --out "$OUT" \
  --label "$(hostname -s 2>/dev/null || echo host) $(uname -m)" "${py_args[@]}"
