# Refactor streams — common rules (clean architecture + SOLID)
You are one of four parallel refactor agents. Base: `recipekeeper/main` (your worktree). Read `CLAUDE.md` first — especially **"Architecture rules"** — then `docs/plans/briefs/_common.md` (device/commit rules) and your own brief. Other agents are refactoring other modules at the same time, so **edit only the files your brief assigns**; if you need a change elsewhere, write the exact diff in your report instead.

## Target shape (every feature)
```
<feature package>/
  presentation/   composables, *Route, *Screen, components, previews, *ViewModel, mvi (State/Intent/Effect), labels
  domain/         use cases, domain models, repository/source *interfaces* — pure Kotlin: no Android, no Koin, no Compose
  data/           repository/source implementations, adapters over :core:* (stores, engine, WorkManager, WebView, Room), mappers
  di/             the feature's Koin module(s) — the ONLY place that knows concrete data classes
```
Dependency direction: `presentation → domain ← data`; `presentation` never imports `data`; `domain` never imports `presentation`/`data`/`android.*`/`org.koin.*`.

## Rules
- **ViewModels take use cases only** (+ `DispatcherProvider`, labels/config, route parameters). No repository, store, manager, engine, scheduler, observer, downloader, session or WebView type in a ViewModel constructor. A ViewModel maps use-case results/flows to `State`/`Effect`; it does not orchestrate I/O.
- **Use case = one responsibility (SRP)**, a class with `operator fun invoke(...)` (suspend or returning `Flow`), depending on **domain interfaces** (DIP), never on `:core:*` concrete classes. Name them by intent: `ObserveModelReadiness`, `ClipRecipe`, `RetryRecipePictures`, `DeleteRecipeWithUndo`, … (suffix `UseCase` is fine and consistent with `GenerateLlmTaskUseCase`).
- **Interfaces are small (ISP)**: split a wide source into the facets each use case needs (`ModelInstallSource`, `ModelSelectionSource`, `DownloadProgressSource`, …) rather than one god interface; implementations may be a single adapter class in `data/` implementing several.
- **Open/closed**: brand/config variability stays in injected config objects (`LlmTaskConfig`, `WebClipConfig`, labels, tokens), not in `when(brand)` branches.
- **Liskov**: existing public contracts that other modules implement (`WebClipConfig`, `ClipSink`, `LlmEngine`, `TaskParser`, `PromptBuilder`, `DesignTokens`) keep their semantics; you may move their package, not change behaviour.
- **Public surface stability**: route composables (`LlmTaskRoute`, `ModelManagementRoute`, `WebClipperBrowserRoute`, `WebClipperExtractionRoute`, …), label classes, Koin module *names* (`llmTaskModule`, `modelManagementModule`, `webClipperModule`, `recipeKeeperConfigModule`, `recipeKeeperUiModule`) and MVI type names must keep working for the apps; moving them to `presentation`/`domain` packages is expected — list every moved FQN (old → new) in your report so the lead can fix importers.
- `:core:*` modules are **read-only** for all four streams (they are the shared data sources). Wrap them behind domain interfaces + `data/` adapters inside your feature.
- Tests: **do not run tests on each iteration.** Update the existing unit tests to the new constructors/use cases as you go (fakes implement the new domain interfaces), and run **once at the end**: `./gradlew <your modules>:testDebugUnitTest` plus `:app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug`. Fix what the refactor broke; do not delete tests to make them pass.
- Keep KDoc, MVI naming, `@Immutable` labels, string-style project deps; no brand values or copy outside apps.
- Commit per logical step on your worktree branch; final commit includes `docs/plans/briefs/<id>-report.md`: the new package tree, the use cases created (name → responsibility → interfaces it depends on), every moved public FQN, Koin bindings, test results, and anything left for the lead. Reply `<id> done — see <id>-report.md, branch <branch>, commit <hash>`.
