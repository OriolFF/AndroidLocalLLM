# Brief — WS3: shared model readiness, copy debt, design-system primitives

You are the **WS3 agent** for the Recipe Keeper effort in the AndroidLocalLLM repo. You run in a **git worktree** created by herdr (your `pwd`), branched from `master`. The main checkout is `/Users/oriol/Documents/development/projectes/AndroidLocalLLM`; the plan documents there are untracked, so read them by absolute path:

- `/Users/oriol/Documents/development/projectes/AndroidLocalLLM/docs/plans/recipekeeper-app-plan.md` — decisions A9, A10, A11; contract §2.4 (`ModelReadiness`, `ModelReadinessObserver`); **WS3** in section 4 (your steps, files you own, done criteria).
- `/Users/oriol/Documents/development/projectes/AndroidLocalLLM/docs/plans/recipekeeper-ux-spec.md` — §6 (which primitives to add and why) and §7 (the three new `ModelManagementLabels` snackbar templates).
- `CLAUDE.md` in your worktree (repo source of truth).

## Scope (own these files; do not touch others)
- `core/llm/src/main/kotlin/com/llmlocal/core/llm/readiness/**` (new) and `core/llm/src/main/kotlin/com/llmlocal/core/llm/di/LlmModule.kt` (register the observer only; **do not** edit `core/llm/.../engine/**`, another agent owns the engine).
- `feature/llm-task/src/main/kotlin/com/llmlocal/feature/llmtask/LlmTaskViewModel.kt` (delegate readiness to the observer; keep `ModelStatus`/`LlmTaskState` and the screen unchanged).
- `feature/modelmanagement/**` (labels + ViewModel + module + route).
- `core/designsystem/src/main/kotlin/com/llmlocal/core/designsystem/component/**` (new primitives; fix `AppErrorView`).
- `app/recipes/**` and `app/medical/**` labels files and the two streamed cards.
- Tests under the same modules.

## Steps
1. `ModelReadiness` + `ModelReadinessObserver` in `:core:llm` (package `com.llmlocal.core.llm.readiness`) extracted from `LlmTaskViewModel.observeSelection()`/`checkModel()`: selection flow → `flatMapLatest { progressStore.progressFor(id) }` → `isInstalled` → `engine.initialize()`; expose `observe(scope): StateFlow<ModelReadiness>` and `suspend fun recheck()`. States: `NoModel`, `Checking`, `Downloading(progress)`, `Initializing`, `Ready(descriptor)`, `Failed(reason, descriptor?)`. Register `factory { ModelReadinessObserver(get(), get(), get(), get(named(REAL_ENGINE_QUALIFIER)), get()) }` in `llmModule`. Preserve the documented gotcha: re-check when progress state is `null` or `SUCCEEDED`.
2. `LlmTaskViewModel` delegates to the observer and maps `ModelReadiness` → existing `ModelStatus`. No behaviour change for the two shipping apps.
3. `ModelManagementLabels` += `modelSelectedSnackbar: (String) -> String`, `modelRemovedSnackbar: (String) -> String`, `modelNotDownloadedSnackbar: (String) -> String`. `ModelManagementViewModel` receives `labels` via `parametersOf(labels)` from `ModelManagementRoute` (`koinViewModel { parametersOf(labels) }`; the module's `viewModel { params -> ... }` pulls it). Replace the three hardcoded strings. Fill the new fields in `RecipeModelManagementLabels` and `MedicalModelManagementLabels` (recipes: "$name selected for recipe generation"; medical: a neutral medical-appropriate phrase). `AppErrorView(message, modifier, retryLabel: String? = null, onRetry)`: no hardcoded "Retry"; update call sites.
4. New brand-free primitives in `:core:designsystem/component/` (MaterialTheme + `designTokens.successColor` only, KDoc, `@Preview`s with both existing token sets): `PhaseProgress(items: List<PhaseItem>)` with `PhaseStatus { Pending, Active, Done, Failed, Warning }`; `StreamingText(text, isGenerating, warmingUpLabel)` + `StreamingCursor()`; `SkeletonLine(widthFraction: Float)`; `SectionHeader(label, icon)`; `NumberBadge(n)`. If the `android-ux` subagent is available in your session, use it for this step and for the review; otherwise do it yourself following its rules in `.claude/agents/android-ux.md`.
5. Switch `app/recipes/.../output/StreamedRecipeCard.kt` and `app/medical/.../output/StreamedMedicalCard.kt` to the primitives and to `labels.warmingUpLabel` (pass the label in; no hardcoded "Warming up the model…").
6. Tests: `core/llm/src/test/.../readiness/ModelReadinessObserverTest.kt` (Turbine; fakes for stores/manager/engine): fresh install with default selected → download SUCCEEDED → Ready; selection change → re-check; init failure → Failed; model removed → NoModel.
7. Verify: `./gradlew :core:llm:test :app:recipes:assembleDebug :app:medical:assembleDebug lint` in your worktree. If `emulator-5554` is free, install `:app:recipes` and take before/after screenshots of the task screen and model screen to show no visual regression (`adb exec-out screencap -p > /tmp/ws3-after.png`).
8. Commit on your worktree branch only (never master, never push). Message ends with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

## Coordination notes
- A sibling stream (WS0) is creating `app/recipekeeper` in the main checkout by copying `app/recipes`. It will not have your new label fields; the lead fills them at merge time. Mention in your report exactly which new label fields and signatures other brands must provide.
- Do not change `LlmEngine`, `LlmToken`, `LiteRtLlmEngine` (WS1 owns them).
- When done, write a short report to `/Users/oriol/Documents/development/projectes/AndroidLocalLLM/docs/plans/briefs/ws3-report.md` (files changed, new public signatures, test/build results, screenshots, branch + commit hash) and then reply in the terminal with "WS3 done — see ws3-report.md".
