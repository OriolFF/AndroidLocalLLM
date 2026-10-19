#!/usr/bin/env bash
# scripts/herdr-spawn.sh — spawn a Claude Code subagent in a herdr-managed pane.
#
# Usage:
#   herdr-spawn.sh <name> <brief-abs-path>
#                  [--worktree <branch>] [--direction right|down]
#                  [--label "<text>"] [--permission-mode auto]
#                  [--model <alias>] [--timeout-ms <ms>]
#                  [--base <ref>] [--config-dir <path>] [--skip-prompt]
#
# Claude config: the agent runs with CLAUDE_CONFIG_DIR exported in its pane
# shell. Default = the caller's CLAUDE_CONFIG_DIR, else ~/.claude-work (the
# user's Anthropic profile; plain `claude` on this machine uses ~/.claude,
# which routes to a different provider). Override with --config-dir.
#
# Requires HERDR_ENV=1. The caller must already be in a herdr pane.
# Lives in this repo so every agent that follows the herdr-subagents skill can
# reproduce the same spawn sequence. Exit status: 0 on success, 1 on herdr
# failure, 2 on bad arguments.

set -euo pipefail

# ---- arg parsing ---------------------------------------------------------

NAME=""
BRIEF=""
WORKTREE_BRANCH=""
DIRECTION=""
LABEL=""
PERMISSION_MODE="auto"
MODEL=""
TIMEOUT_MS=90000
SKIP_PROMPT=0
BASE_REF=""
CONFIG_DIR="${CLAUDE_CONFIG_DIR:-$HOME/.claude-work}"

usage() {
  cat >&2 <<USAGE
Usage: $0 <name> <brief-abs-path>
         [--worktree <branch>] [--direction right|down]
         [--label TEXT] [--permission-mode MODE]
         [--model ALIAS] [--timeout-ms MS]
         [--base REF] [--config-dir PATH] [--skip-prompt]

Spawns Claude Code as a herdr subagent. Must be run from a herdr pane
(HERDR_ENV=1). Prints the JSON of 'herdr agent get <name>' on success.
USAGE
  exit 2
}

die() { echo "herdr-spawn: $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --worktree)       WORKTREE_BRANCH="$2"; shift 2 ;;
    --direction)      DIRECTION="$2"; shift 2 ;;
    --label)          LABEL="$2"; shift 2 ;;
    --permission-mode) PERMISSION_MODE="$2"; shift 2 ;;
    --model)          MODEL="$2"; shift 2 ;;
    --timeout-ms)     TIMEOUT_MS="$2"; shift 2 ;;
    --base)           BASE_REF="$2"; shift 2 ;;
    --config-dir)     CONFIG_DIR="$2"; shift 2 ;;
    --skip-prompt)    SKIP_PROMPT=1; shift ;;
    -h|--help)        usage ;;
    --)               shift; break ;;
    -*)               echo "herdr-spawn: unknown flag $1" >&2; usage ;;
    *)
      if   [[ -z "$NAME"  ]]; then NAME="$1"; shift
      elif [[ -z "$BRIEF" ]]; then BRIEF="$1"; shift
      else echo "herdr-spawn: unexpected positional arg: $1" >&2; usage
      fi
      ;;
  esac
done

[[ -z "$NAME"  ]] && usage
[[ -z "$BRIEF" ]] && usage
[[ -f "$BRIEF" ]] || die "brief not found: $BRIEF"
[[ "${HERDR_ENV:-}" = 1 ]] || die "must run from a herdr pane (HERDR_ENV != 1)"
[[ -n "${HERDR_PANE_ID:-}"     ]] || die "HERDR_PANE_ID is not set"
[[ -n "${HERDR_WORKSPACE_ID:-}" ]] || die "HERDR_WORKSPACE_ID is not set"

NAME_RE='^[a-z][a-z0-9_-]{0,31}$'
[[ "$NAME" =~ $NAME_RE ]] || die "name must match $NAME_RE (got: $NAME)"

LABEL="${LABEL:-$NAME}"
[[ -d "$CONFIG_DIR" ]] || die "Claude config dir not found: $CONFIG_DIR"

# ---- json helper ---------------------------------------------------------
# jq is preferred (always present on macOS via Xcode CLT or Homebrew);
# fall back to python3 if jq is missing.
if command -v jq >/dev/null 2>&1; then
  json_get() { jq -r "$1"; }
else
  json_get() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)"; }
fi

# ---- topology ------------------------------------------------------------

if [[ -n "$WORKTREE_BRANCH" ]]; then
  BASE_REF="${BASE_REF:-$(git -C "$PWD" rev-parse --abbrev-ref HEAD 2>/dev/null || echo master)}"
  echo "herdr-spawn: creating worktree '$WORKTREE_BRANCH' (base $BASE_REF, label '$LABEL')" >&2
  WT_JSON=$(herdr worktree create --cwd "$PWD" \
            --branch "$WORKTREE_BRANCH" --base "$BASE_REF" \
            --label "$LABEL" --no-focus) \
    || die "herdr worktree create failed"

  ROOT_PANE_ID=$(printf '%s' "$WT_JSON" | json_get '.result.root_pane.pane_id')
  WT_PATH=$(printf '%s' "$WT_JSON" | json_get '.result.worktree.path // empty')
  [[ -n "$WT_PATH" ]] || die "could not read worktree path from herdr response"

  if [[ -f "$PWD/local.properties" ]]; then
    echo "herdr-spawn: copying local.properties into worktree" >&2
    cp "$PWD/local.properties" "$WT_PATH/local.properties"
  fi
else
  # Geometry: right if caller pane > 140 cols, else down. Override with --direction.
  if [[ -z "$DIRECTION" ]]; then
    LAYOUT=$(herdr pane layout --current) || die "herdr pane layout failed"
    WIDTH=$(printf '%s' "$LAYOUT" \
            | python3 -c 'import json,sys; L=json.load(sys.stdin)["result"]["layout"]["panes"]; print(next((p["rect"]["width"] for p in L if p["pane_id"]==sys.argv[1]), ""))' "$HERDR_PANE_ID")
    if [[ -n "$WIDTH" && "$WIDTH" -gt 140 ]]; then
      DIRECTION="right"
    else
      DIRECTION="down"
    fi
    echo "herdr-spawn: caller pane width=$WIDTH → direction=$DIRECTION" >&2
  fi

  SPLIT_JSON=$(herdr pane split --current --direction "$DIRECTION" \
               --cwd "$PWD" --no-focus) \
    || die "herdr pane split failed"
  ROOT_PANE_ID=$(printf '%s' "$SPLIT_JSON" | json_get '.result.pane.pane_id')
fi

echo "herdr-spawn: root pane = $ROOT_PANE_ID" >&2

# ---- select the Claude profile in the pane shell -------------------------
# `herdr agent start` launches the canonical `claude` binary; the profile is
# chosen by CLAUDE_CONFIG_DIR, so export it in the pane's shell first.

echo "herdr-spawn: using CLAUDE_CONFIG_DIR=$CONFIG_DIR" >&2
herdr pane run "$ROOT_PANE_ID" "export CLAUDE_CONFIG_DIR='$CONFIG_DIR'; echo CFG_OK=\$CLAUDE_CONFIG_DIR" >/dev/null \
  || die "could not export CLAUDE_CONFIG_DIR in pane $ROOT_PANE_ID"
# Verify the shell really picked it up before starting the agent (a pane whose
# previous occupant is still exiting can swallow the export line).
herdr pane wait-output "$ROOT_PANE_ID" --match "CFG_OK=$CONFIG_DIR" --timeout 8000 >/dev/null \
  || die "pane $ROOT_PANE_ID did not confirm CLAUDE_CONFIG_DIR; is it at an interactive prompt?"

# ---- start claude --------------------------------------------------------

CLAUDE_ARGS=(--permission-mode "$PERMISSION_MODE" --name "$LABEL")
if [[ -n "$MODEL" ]]; then CLAUDE_ARGS+=(--model "$MODEL"); fi

herdr agent start "$NAME" --kind claude \
  --pane "$ROOT_PANE_ID" --timeout "$TIMEOUT_MS" \
  -- "${CLAUDE_ARGS[@]}" \
  || die "herdr agent start failed (see output above)"

# ---- prompt with brief ---------------------------------------------------

if [[ $SKIP_PROMPT -eq 0 ]]; then
  # Inside a worktree, prefer the brief's path within that checkout: a read
  # outside the agent's working directory triggers a permission prompt and
  # blocks the agent until someone answers it.
  PROMPT_BRIEF="$BRIEF"
  if [[ -n "${WT_PATH:-}" && "$BRIEF" == "$PWD/"* ]]; then
    REL="${BRIEF#"$PWD/"}"
    if [[ -f "$WT_PATH/$REL" ]]; then
      PROMPT_BRIEF="$REL"
    else
      echo "herdr-spawn: warning: $REL is not committed in the worktree; the agent will ask permission to read it from the main checkout" >&2
    fi
  fi
  herdr agent prompt "$NAME" "Read $PROMPT_BRIEF and execute it completely." \
    --wait --until working --timeout 20000 \
    || die "herdr agent prompt failed (agent may be blocked — inspect with 'herdr agent get $NAME')"

  # Claude Code sometimes shows a one-time informational notice
  # ("Enter to continue · Esc to cancel") right after the first prompt and
  # herdr reports the agent as blocked. Dismiss exactly that notice; leave
  # any real permission or question dialog for the user.
  if herdr agent wait "$NAME" --until blocked --timeout 25000 >/dev/null 2>&1; then
    if herdr agent read "$NAME" --source visible 2>/dev/null | grep -q "Enter to continue"; then
      herdr agent send-keys "$NAME" enter >/dev/null && echo "herdr-spawn: dismissed startup notice" >&2
    else
      echo "herdr-spawn: agent '$NAME' is blocked on a dialog — inspect with 'herdr agent read $NAME --source visible' and ask the user" >&2
    fi
  fi
fi

# ---- report --------------------------------------------------------------
# Print a one-line summary on stderr, then the agent JSON for callers to parse.
cat >&2 <<EOF
herdr-spawn: spawned '$NAME' in pane $ROOT_PANE_ID
  watch:    herdr agent read  $NAME --source recent-unwrapped --lines 200
  wait:     herdr agent wait  $NAME --until done --timeout 600000
  close:    herdr pane close  $ROOT_PANE_ID
EOF
herdr agent get "$NAME"
