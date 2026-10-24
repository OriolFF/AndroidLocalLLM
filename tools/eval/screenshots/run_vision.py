#!/usr/bin/env python3
"""Run recipe extraction from screenshots through LiteRT-LM (python wheel 0.18.0) on this Mac.

jobs.json: [{"id": "bbc_pancakes__single__b280", "images": ["/abs/a.png", ...], "budget": 280,
             "user": "Extract the recipe from this page.", "system": "<RecipeKeeperPromptBuilder.SYSTEM>"}]
Writes <out>/<id>.txt (raw model output) and <out>/<id>.json (timing, token counts, error).

Parity with tools/eval/run_local_model.py: CPU engine, max_num_tokens 4096 (raise with --max-num-tokens when
many images are sent), top_k 40 / top_p 0.95 / seed 0, temperature 0.2, max_output_tokens 1024, a separate
system message (the app sets systemInstruction=true). Vision backend CPU, max_num_images from --max-images.
The wheel has no public visual-token-budget argument; the budget is set per message through the C API
(litert_lm_conversation_optional_args_set_visual_token_budget) by wrapping Conversation._create_optional_args.
"""
import argparse, ctypes, json, time
from pathlib import Path
import litert_lm as lm
from litert_lm import conversation as _conv

BUDGET = [0]
_orig = _conv.Conversation._create_optional_args


def _patched(self, *a, **kw):
    ptr = _orig(self, *a, **kw)
    if BUDGET[0]:
        if not ptr:
            ptr = self._lib.litert_lm_conversation_optional_args_create()
        fn = self._lib.litert_lm_conversation_optional_args_set_visual_token_budget
        fn.argtypes = [ctypes.c_void_p, ctypes.c_int]
        fn.restype = None
        fn(ptr, BUDGET[0])
    return ptr


_conv.Conversation._create_optional_args = _patched


def patch_engine_vision_cap(cap):
    """Raise the engine's max_vision_tokens_per_image (default 280 = the bundle's max budget) through the C API."""
    from litert_lm import engine as _eng
    lib = _eng._get_lib()
    create = lib.litert_lm_engine_settings_create
    setter = lib.litert_lm_engine_settings_set_max_vision_tokens_per_image
    setter.argtypes = [ctypes.c_void_p, ctypes.c_int]
    setter.restype = None
    create.restype = ctypes.c_void_p

    def wrapped(*a):
        h = create(*a)
        setter(h, cap)
        return h
    lib.litert_lm_engine_settings_create = wrapped


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--jobs", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--max-num-tokens", type=int, default=4096)
    ap.add_argument("--max-images", type=int, default=5)
    ap.add_argument("--max-vision-tokens", type=int, default=0, help="engine cap per image (0 = runtime default 280)")
    ap.add_argument("--skip-existing", action="store_true")
    a = ap.parse_args()
    lm.set_min_log_severity(lm.LogSeverity.WARNING)
    if a.max_vision_tokens:
        patch_engine_vision_cap(a.max_vision_tokens)
    jobs = json.loads(a.jobs.read_text())
    a.out.mkdir(parents=True, exist_ok=True)
    t0 = time.perf_counter()
    engine = lm.Engine(a.model, backend=lm.Backend.CPU(), vision_backend=lm.Backend.CPU(),
                       max_num_tokens=a.max_num_tokens, max_num_images=a.max_images,
                       cache_dir=str(Path(a.model).parent / ".litertlm-cache"), enable_benchmark=True)
    print(f"engine ready in {time.perf_counter() - t0:.1f}s", flush=True)
    sampler = lm.SamplerConfig(top_k=40, top_p=0.95, temperature=0.2, seed=0)
    for j in jobs:
        if a.skip_existing and (a.out / f"{j['id']}.json").exists():
            continue
        BUDGET[0] = j.get("budget", 0)
        conv = engine.create_conversation(sampler_config=sampler, max_output_tokens=1024, system_message=j["system"])
        content = [{"type": "image", "path": p} for p in j["images"]] + [{"type": "text", "text": j["user"]}]
        pieces, first, err = [], None, None
        s = time.perf_counter()
        try:
            for m in conv.send_message_async({"role": "user", "content": content}):
                t = str(m)
                if t:
                    first = first or time.perf_counter()
                    pieces.append(t)
        except Exception as e:  # noqa: BLE001
            err = f"{type(e).__name__}: {e}"
        e_ = time.perf_counter()
        try:
            b = conv.get_benchmark_info()
            bench = dict(ttftS=b.time_to_first_token_in_second, prefillTokens=b.last_prefill_token_count,
                         prefillTokPerS=b.last_prefill_tokens_per_second, decodeTokens=b.last_decode_token_count,
                         decodeTokPerS=b.last_decode_tokens_per_second)
        except Exception as ex:  # noqa: BLE001
            bench = {"error": str(ex)}
        conv.close()
        out = "".join(pieces)
        ttft = round((first - s) * 1000) if first else None
        (a.out / f"{j['id']}.txt").write_text(out)
        (a.out / f"{j['id']}.json").write_text(json.dumps(dict(
            id=j["id"], images=len(j["images"]), budget=j.get("budget"), ttftMs=ttft,
            totalMs=round((e_ - s) * 1000), outputChars=len(out), bench=bench, error=err), indent=1))
        print(f"{j['id']}: ttft {ttft} ms total {round((e_ - s) * 1000)} ms {len(out)} chars "
              f"{bench.get('prefillTokens')} prefill tok {err or ''}", flush=True)
    engine.close()


main()
