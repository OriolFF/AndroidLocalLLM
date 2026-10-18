# Brief — create a `herdr-subagents` skill so every subagent spawn becomes a herdr pane

You are working in the AndroidLocalLLM repo (`pwd` = main checkout). The user runs Claude Code inside **herdr** (https://herdr.dev), a terminal workspace manager that recognises coding agents in its panes. The user's rule from now on: **whenever the lead Claude session needs a subagent, it must be started as a separate `claude` process in a new herdr pane (or herdr worktree workspace), never as an in-process Agent-tool subagent**, so the user can watch every agent in the herdr UI.

Your job: create a project-scoped skill (and a helper script) that makes this the default, reliable workflow, then verify it end to end.

## Inputs to read first
1. `herdr --skill` — the official agent skill text for the herdr CLI (authoritative on commands, IDs, safety rules). Also run `herdr agent`, `herdr pane`, `herdr worktree`, `herdr workspace`, `herdr tab` (bare group commands print help; do NOT run bare `herdr`).
2. Confirm you are inside herdr: `test "${HERDR_ENV:-}" = 1` and print `$HERDR_WORKSPACE_ID $HERDR_TAB_ID $HERDR_PANE_ID`.
3. `claude --help` for `--permission-mode`, `--name`, `--model`.
4. Existing project agent definition `.claude/agents/android-ux.md` (shape/tone of project-scoped Claude Code assets) and the skill-creator skill if available in your session (`Skill` tool, `anthropic-skills:skill-creator`) for the SKILL.md format (frontmatter `name`, `description`, body).
5. A working example that already succeeded in this repo today: brief file `docs/plans/briefs/ws3.md`; commands used: `herdr worktree create --cwd "$PWD" --branch <branch> --base master --label "<label>" --no-focus` → pane id from `.result.root_pane.pane_id`; `cp local.properties <worktree>/` (untracked Android SDK path, required for Gradle); `herdr agent start <name> --kind claude --pane <pane-id> --timeout 90000 -- --permission-mode auto --name "<label>"`; `herdr agent prompt <name> "Read <abs path to brief> and execute it completely…" --wait --until working --timeout 20000`.

## Deliverables
1. `.claude/skills/herdr-subagents/SKILL.md` (project-scoped). Frontmatter: `name: herdr-subagents`, a `description` that triggers whenever the lead wants to delegate, parallelise, spawn, fan out, or "use a subagent"/"agent" while `HERDR_ENV=1` (and says to fall back to the Agent tool only when not inside herdr). Body must cover:
   - The rule and why (visibility in herdr).
   - Decision table: sibling pane in current tab + same cwd (default, for tasks on the same working tree with disjoint files) vs `herdr worktree create` (for streams that build/compile independently, or touch shared files) vs new tab/workspace (only if the user asks). Geometry rule: split right if the caller pane is wide (>140 cols), down otherwise; avoid >2 same-direction splits; always `--no-focus`.
   - The brief-file pattern: write the task to `docs/plans/briefs/<name>.md` (or `.claude/briefs/` for non-plan work) and prompt with a one-line "Read <abs path> and execute it completely"; include a report-file convention (`<name>-report.md`) and the "reply in terminal with `<name> done`" convention; why long prompts must not be passed inline (canonical line-input limit).
   - Naming: `[a-z][a-z0-9_-]{0,31}`, unique among live agents; use the stream id (`ws2`, `ws5a`, `review-pr-12`).
   - Starting: `herdr agent start … -- --permission-mode <mode> --name "<label>"`; default `--permission-mode auto` (matches the lead's mode); mention `--model` passthrough.
   - Monitoring: `herdr agent list`, `herdr agent get <name>`, `herdr agent read <name> --source recent-unwrapped --lines 120`, `herdr agent wait <name> --until blocked|idle|done --timeout …`; what `blocked` means (approval UI → inspect and ask the user, never auto-answer); never kill panes you didn't create; never `herdr server stop`.
   - Worktree specifics for this repo: copy `local.properties`; untracked docs in the main checkout must be referenced by absolute path; commit on the branch only, never push; lead merges.
   - Collecting results: read the report file; fall back to `agent read`; if output is too long, ask the agent to write Markdown to a temp path.
   - A copy-paste checklist at the end.
2. `scripts/herdr-spawn.sh` — a small, well-commented bash helper: `herdr-spawn.sh <name> <brief-abs-path> [--worktree <branch>] [--direction right|down] [--label "<text>"] [--permission-mode auto] [--model <alias>]`. It: validates `HERDR_ENV`; creates the pane (split from `$HERDR_PANE_ID`, `--no-focus`, `--cwd "$PWD"`) or the worktree (and copies `local.properties` when present); parses the pane id from JSON with `python3 -c` or `jq` (check which exists; prefer python3 since it's on this Mac); starts the agent; prompts it with the "Read <brief> and execute" line using `--wait --until working --timeout 20000`; prints the JSON of `herdr agent get <name>`; exits non-zero with the herdr error on failure. Make it executable.
3. Add a short "Subagents run as herdr panes" paragraph to `CLAUDE.md` (3–5 lines) pointing at the skill and script, so future sessions follow the rule.
4. **Verify end to end**: use the script to spawn a throwaway agent named `herdr-skill-smoke` in a sibling pane with a tiny brief (e.g. "print the current git branch and `ls app`, then write the output to `/tmp/herdr-skill-smoke.md` and reply 'herdr-skill-smoke done'"); wait for it; read the report; then close ONLY that pane you created (`herdr pane close <id>`), and delete the temp brief. Record the exact commands and outputs in your report.

## Constraints
- Do not modify anything under `app/`, `core/`, `feature/`, `build-logic/`, Gradle files, or `docs/plans/recipekeeper-*.md`. Other agents are editing code right now.
- Do not stop, close, focus or prompt agents/panes you did not create (live ones: `claude` in `w1:p1` is the lead; `ws3` in `w2:p1`).
- Do not commit; leave changes in the tree for the lead to review.
- Follow the project's writing style (concise, imperative). Keep SKILL.md under ~200 lines.

When done, write `docs/plans/briefs/herdr-skill-report.md` (files created, how you verified, anything herdr refused and how you adapted) and reply in the terminal with "herdr-skill done — see herdr-skill-report.md".
