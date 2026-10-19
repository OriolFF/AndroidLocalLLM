---
name: herdr-subagents
description: Use PROACTIVELY when inside herdr (`HERDR_ENV=1`) and the lead wants to delegate, parallelise, fan out, "use a subagent", or spawn a background worker — create a sibling pane or worktree via `herdr` so the user can watch every agent in the herdr UI. Covers splitting panes, naming agents, starting `claude`, prompting with a brief file, monitoring (`list` / `get` / `read` / `wait`), and cleanup. Outside herdr, fall back to the Agent tool.
---

# herdr-subagents

Rule: inside herdr, every subagent is a **separate `claude` process in its own herdr pane** (or herdr worktree). Never use the in-process Agent tool — the user wants every agent visible in the herdr UI, not hidden in this session.

If `HERDR_ENV != 1`, say you are not inside herdr and fall back to the Agent tool.

## Confirm the environment first

```bash
test "${HERDR_ENV:-}" = 1 && echo "$HERDR_WORKSPACE_ID $HERDR_TAB_ID $HERDR_PANE_ID"
```

Stop if the check fails. Never call `herdr` from outside a herdr pane.

Read live state before acting:

```bash
herdr pane list --workspace "$HERDR_WORKSPACE_ID"
herdr agent list
```

Do not stop, focus, prompt, or close any pane or agent you did not create.

## Pick the topology

| Stream shape | Choice | Why |
|---|---|---|
| Same repo, disjoint files, no shared build | **Sibling pane in current tab, same cwd** | Default. Shares the working tree, fast. |
| Streams that build / compile independently, or touch the same files | `herdr worktree create …` then `herdr agent start` in its root pane | Independent worktrees avoid lock contention. |
| New workspace / new tab | Only if the user asks | Default to less, not more. |

### Sibling pane (default)

Geometric rule: **right** if the caller pane is wider than 140 cols, **down** otherwise. Avoid more than two same-direction splits in a row. Always `--no-focus` so the user keeps typing here.

```bash
herdr pane layout --current                                  # see width before splitting
herdr pane split --current --direction right|down \
                  --cwd "$PWD" --no-focus
```

Read the new pane id from `.result.pane.pane_id`.

### Worktree

```bash
herdr worktree create --cwd "$PWD" \
                      --branch <branch> --base <integration-branch> \
                      --label "<label>" --no-focus
```

Base the worktree on the branch the stream must build on (for Recipe Keeper that is `recipekeeper/main`, not `master`).

Root pane is `.result.root_pane.pane_id`; the new worktree path is `.result.worktree.path`. **Copy `local.properties` into the new worktree** — it is untracked and gitignored, so Gradle fails without it. In this repo that file is also under `app/<name>/` if any app needs SDK paths there.

Commit the brief before spawning into a worktree and prompt with the **worktree-relative path**; a read outside the agent's working directory triggers Claude Code's "Allow this read outside the working directories?" prompt and blocks the agent. Only reference the main checkout by absolute path for files that cannot be committed, and expect to answer that prompt (option 4, one-time Yes) when you do.

## Brief-file pattern

Always write the task to a file and prompt with a one-line "Read it and execute" — long prompts are rejected by the canonical line-input limit on macOS.

- Plan work: `docs/plans/briefs/<name>.md` in the working tree.
- Non-plan work: `.claude/briefs/<name>.md` (or `/tmp/<name>.md` for throwaways).
- Report: `<name>-report.md` next to the brief.
- Reply line in the terminal: `<name> done — see <name>-report.md`.

Naming for agents and files: `[a-z][a-z0-9_-]{0,31}`, unique among live agents. Reuse the stream id from the plan (`ws2`, `ws5a`, `review-pr-12`).

Write the brief so the subagent has full context: the goal, the inputs it must read (absolute paths when in another checkout), the files it may touch, the steps, the verification, the report path, and the reply-in-terminal convention.

## Start the agent

**Always select the Claude profile first.** On this machine plain `claude` uses `~/.claude`, which routes to a different provider and model; the user's Anthropic profile is `claude-work` = `CLAUDE_CONFIG_DIR=~/.claude-work claude`. `herdr agent start` only launches the canonical `claude` binary, so export the variable in the pane shell before starting (the script does this for you):

```bash
herdr pane run <pane-id> "export CLAUDE_CONFIG_DIR=$HOME/.claude-work"
herdr agent start <name> --kind claude \
  --pane <pane-id> --timeout 90000 \
  -- --permission-mode auto --name "<label>"
```

Prefer `scripts/herdr-spawn.sh <name> <brief> [--worktree <branch> --base <ref>]`; it exports the profile, picks the split direction, copies `local.properties` into a new worktree, prompts with the brief, and dismisses Claude Code's one-time startup notice.

- `--permission-mode auto` matches the lead. Override only if the user asks.
- **Model choice (user rule, 2026-10-19):** test/QA/verification agents must **never** run on the lead's Fable model. Pass `--model sonnet` for multi-scenario device QA (adb driving, screenshots, timings, bug analysis) or `--model haiku` for simple checks (install + launch + logcat, one-scenario re-check, run a Gradle test task and summarise). Implementation/refactor agents may use `--model sonnet`; use the default only for research or design work that needs the strongest model.
- The leading `--` separates herdr args from Claude Code args; everything after is passed through.

Submit the task:

```bash
herdr agent prompt <name> \
  "Read /absolute/path/to/<brief>.md and execute it completely." \
  --wait --until working --timeout 20000
```

`--wait` returns when the first settled state (`idle` / `done` / `blocked`) is observed. `blocked` means an approval UI is waiting — read it with `agent get` / `agent read` and **ask the user**; never auto-answer. One exception: Claude Code's one-time informational notice ("Enter to continue · Esc to cancel") may appear after the first prompt; dismiss exactly that with `herdr agent send-keys <name> enter`.

If you need to wait for a specific end state without prompting, use `herdr agent wait <name> --until done --timeout <ms>`.

## Monitor and steer

```bash
herdr agent list                                      # see every live agent
herdr agent get <name>                                # JSON: state, cwd, pane_id
herdr agent read <name> --source recent-unwrapped --lines 200   # latest output
herdr agent send-keys <name> esc                      # dismiss an unexpected prompt
```

If output is too long for one read, ask the agent to write results to a temp file and read it with the Read tool instead.

## Collect results

1. Read `<brief-name>-report.md` from the path the brief specified.
2. Fall back to `herdr agent read <name> --source recent-unwrapped --lines 400`.
3. If neither is enough, ask the agent to expand the report into a Markdown file at a path you choose.

## Cleanup

```bash
herdr pane close <pane-id>                            # ONLY panes you created
herdr worktree remove --workspace <id> --force        # worktrees you created
```

Never run `herdr server stop`. Never kill the lead's pane.

## Copy-paste checklist

```
[ ] HERDR_ENV=1 confirmed
[ ] live pane + agent list inspected; no collateral edits planned
[ ] topology chosen (sibling pane | worktree)
[ ] brief written to docs/plans/briefs/<name>.md (or /tmp/<name>.md)
[ ] name + label chosen, unique, matches [a-z][a-z0-9_-]{0,31}
[ ] herdr pane split --no-focus  OR  herdr worktree create --no-focus
[ ] local.properties copied into new worktree (if any)
[ ] CLAUDE_CONFIG_DIR exported in the pane (claude-work profile)
[ ] herdr agent start <name> --kind claude --pane <id> -- --permission-mode auto --name "<label>"
[ ] herdr agent prompt <name> "Read <abs brief path> and execute it completely." --wait --until working --timeout 20000
[ ] monitor via agent get / read / wait as needed
[ ] read <name>-report.md
[ ] herdr pane close <pane-id>   (and herdr worktree remove when applicable)
```
