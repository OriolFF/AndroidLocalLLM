#!/usr/bin/env python3
"""Run every rendered eval prompt through LiteRT-LM on this machine.

Reads  <prompts>/<fixture>.txt  (written by EvalPromptRenderingTest)
Writes <out>/<fixture>.txt          raw model output (the golden)
       <out>/<fixture>.timing.json  TTFT / total / token counts / settings

Parity with the app: one Engine (CPU, max_num_tokens 4096), a fresh
conversation per prompt, the prompt sent as a single user turn exactly as
LlmTaskRepositoryImpl does (system + "\n\n" + user, no separate system
instruction), sampler top_k 40 / top_p 0.95 / seed 0 (LiteRtLlmEngine
defaults), temperature 0.2 and max_output_tokens 1024 (plan §3.4, the
values ExtractionViewModel sets on LlmRequest).

Only the `litert_lm` wheel (0.18.0, same version as the Android AAR) and the
standard library are needed. Run through run_local_model.sh, which creates
the venv and fetches the model.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import os
import platform
import sys
import time
from pathlib import Path

DEFAULT_PROMPTS = Path("app/recipekeeper/build/eval/prompts")
DEFAULT_OUT = Path("app/recipekeeper/src/test/resources/eval/outputs")


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--model", required=True, help="path to the .litertlm bundle (the same file the app downloads)")
    p.add_argument("--prompts", type=Path, default=DEFAULT_PROMPTS, help=f"directory of <fixture>.txt prompts (default {DEFAULT_PROMPTS})")
    p.add_argument("--out", type=Path, default=DEFAULT_OUT, help=f"where to write goldens (default {DEFAULT_OUT})")
    p.add_argument("--only", action="append", default=[], metavar="FIXTURE", help="run only this fixture (repeatable)")
    p.add_argument("--temperature", type=float, default=0.2)
    p.add_argument("--top-k", type=int, default=40)
    p.add_argument("--top-p", type=float, default=0.95)
    p.add_argument("--seed", type=int, default=0)
    p.add_argument("--max-output-tokens", type=int, default=1024)
    p.add_argument("--max-num-tokens", type=int, default=4096, help="context window, same as LiteRtLlmEngine.DEFAULT_MAX_NUM_TOKENS")
    p.add_argument("--threads", type=int, default=None, help="CPU threads (runtime default when omitted)")
    p.add_argument("--backend", choices=["cpu", "gpu"], default="cpu")
    p.add_argument("--cache-dir", type=Path, default=None, help="LiteRT-LM cache dir (default: <model dir>/.litertlm-cache)")
    p.add_argument("--label", default="", help="free-text label stored in the timing JSON (e.g. host name)")
    p.add_argument("--dry-run", action="store_true", help="list what would run and exit without loading the model")
    p.add_argument("--quiet", action="store_true", help="do not echo streamed output")
    return p.parse_args(argv)


def sha256_of(path: Path, limit_bytes: int | None = None) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while True:
            chunk = f.read(1 << 20)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def discover_prompts(prompts_dir: Path, only: list[str]) -> list[Path]:
    files = sorted(p for p in prompts_dir.glob("*.txt"))
    if only:
        wanted = set(only)
        files = [p for p in files if p.stem in wanted]
        missing = wanted - {p.stem for p in files}
        if missing:
            sys.exit(f"no prompt for: {', '.join(sorted(missing))} (looked in {prompts_dir})")
    return files


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    model = Path(args.model).expanduser()
    prompts = discover_prompts(args.prompts, args.only)
    if not prompts:
        sys.exit(f"no prompts in {args.prompts}; run ./gradlew :app:recipekeeper:testDebugUnitTest --tests '*EvalPromptRenderingTest*' first")

    print(f"model   : {model}")
    print(f"prompts : {args.prompts} ({len(prompts)} files)")
    print(f"outputs : {args.out}")
    print(f"sampler : temperature={args.temperature} top_k={args.top_k} top_p={args.top_p} seed={args.seed} max_output_tokens={args.max_output_tokens} max_num_tokens={args.max_num_tokens} backend={args.backend}")
    for p in prompts:
        print(f"  - {p.stem} ({p.stat().st_size} chars)")
    if args.dry_run:
        print("dry run: model not loaded")
        return 0
    if not model.is_file():
        sys.exit(f"model file not found: {model}")

    import litert_lm as lm  # imported late so --help / --dry-run work without the wheel

    try:  # keep the C++ runtime's INFO chatter out of the transcript
        lm.set_min_log_severity(lm.LogSeverity.WARNING)
    except Exception:  # noqa: BLE001 — cosmetic only
        pass

    cache_dir = args.cache_dir or (model.parent / ".litertlm-cache")
    cache_dir.mkdir(parents=True, exist_ok=True)
    backend = lm.Backend.CPU(thread_count=args.threads) if args.backend == "cpu" else lm.Backend.GPU()

    print("sha256  : computing…", end="", flush=True)
    model_sha = sha256_of(model)
    print(f"\rsha256  : {model_sha}")

    t_init = time.perf_counter()
    engine = lm.Engine(
        str(model),
        backend=backend,
        max_num_tokens=args.max_num_tokens,
        cache_dir=str(cache_dir),
        enable_benchmark=True,
    )
    init_s = time.perf_counter() - t_init
    print(f"engine  : ready in {init_s:.1f}s")

    args.out.mkdir(parents=True, exist_ok=True)
    sampler = lm.SamplerConfig(top_k=args.top_k, top_p=args.top_p, temperature=args.temperature, seed=args.seed)
    failures = 0
    try:
        for i, prompt_file in enumerate(prompts, 1):
            name = prompt_file.stem
            prompt = prompt_file.read_text(encoding="utf-8")
            print(f"\n[{i}/{len(prompts)}] {name}: {len(prompt)} chars", flush=True)
            conv = engine.create_conversation(sampler_config=sampler, max_output_tokens=args.max_output_tokens)
            pieces: list[str] = []
            first_token_at: float | None = None
            error: str | None = None
            started = time.perf_counter()
            try:
                for msg in conv.send_message_async(prompt):
                    text = str(msg)
                    if not text:
                        continue
                    if first_token_at is None:
                        first_token_at = time.perf_counter()
                    # The runtime streams deltas; tolerate a cumulative stream too.
                    if pieces and text.startswith("".join(pieces)) and len(text) > len("".join(pieces)):
                        pieces = [text]
                    else:
                        pieces.append(text)
                    if not args.quiet:
                        sys.stdout.write(text if not (len(pieces) == 1 and pieces[0] == text and len(text) > 200) else text[-200:])
                        sys.stdout.flush()
            except Exception as e:  # noqa: BLE001 — recorded in the timing file
                error = f"{type(e).__name__}: {e}"
                failures += 1
            finished = time.perf_counter()
            output = "".join(pieces)
            bench = None
            try:
                b = conv.get_benchmark_info()
                bench = {
                    "timeToFirstTokenS": b.time_to_first_token_in_second,
                    "prefillTokens": b.last_prefill_token_count,
                    "prefillTokensPerS": b.last_prefill_tokens_per_second,
                    "decodeTokens": b.last_decode_token_count,
                    "decodeTokensPerS": b.last_decode_tokens_per_second,
                }
            except Exception as e:  # noqa: BLE001
                bench = {"error": f"{type(e).__name__}: {e}"}
            try:
                conversation_tokens = conv.token_count
            except Exception:  # noqa: BLE001
                conversation_tokens = None
            conv.close()

            ttft_ms = round((first_token_at - started) * 1000) if first_token_at else None
            total_ms = round((finished - started) * 1000)
            decode_tokens = bench.get("decodeTokens") if isinstance(bench, dict) else None
            hit_cap = bool(decode_tokens and decode_tokens >= args.max_output_tokens)
            timing = {
                "fixture": name,
                "runner": "tools/eval/run_local_model.py (litert_lm python wheel)",
                "litertLmVersion": getattr(lm, "__version__", None) or _wheel_version(),
                "model": model.name,
                "modelBytes": model.stat().st_size,
                "modelSha256": model_sha,
                "backend": args.backend.upper(),
                "threads": args.threads,
                "host": platform.platform(),
                "machine": platform.machine(),
                "label": args.label,
                "startedAt": _dt.datetime.now(_dt.timezone.utc).isoformat(timespec="seconds"),
                "promptChars": len(prompt),
                "outputChars": len(output),
                "ttftMs": ttft_ms,
                "totalMs": total_ms,
                "decodeTokensPerSWall": (round(decode_tokens / ((finished - first_token_at) or 1e-9), 2) if (decode_tokens and first_token_at) else None),
                "maxOutputTokensHit": hit_cap,
                "conversationTokens": conversation_tokens,
                "sampler": {"temperature": args.temperature, "topK": args.top_k, "topP": args.top_p, "seed": args.seed,
                            "maxOutputTokens": args.max_output_tokens, "maxNumTokens": args.max_num_tokens},
                "engineInitS": round(init_s, 2),
                "benchmark": bench,
                "error": error,
            }
            (args.out / f"{name}.txt").write_text(output, encoding="utf-8")
            (args.out / f"{name}.timing.json").write_text(json.dumps(timing, indent=2) + "\n", encoding="utf-8")
            status = "ERROR " + error if error else "ok"
            print(f"\n  → {status}: {len(output)} chars, ttft {ttft_ms} ms, total {total_ms} ms, decode {decode_tokens} tok{' (cap hit)' if hit_cap else ''}")
    finally:
        engine.close()
    print(f"\ndone: {len(prompts) - failures}/{len(prompts)} prompts ok → {args.out}")
    return 1 if failures else 0


def _wheel_version() -> str | None:
    try:
        from importlib.metadata import version

        return version("litert-lm")
    except Exception:  # noqa: BLE001
        return None


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
