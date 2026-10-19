# R2 report — `:feature:modelmanagement` refactor

Branch `refactor/r2`. Tests: `:feature:modelmanagement:testDebugUnitTest` (13 tests, 0 failures) and `:app:recipes:assembleDebug :app:medical:assembleDebug :app:recipekeeper:assembleDebug` all pass.

## Package tree (`com.llmlocal.feature.modelmanagement`)
```
presentation/  ModelManagementRoute, ModelManagementScreen, ModelManagementViewModel
  mvi/         ModelManagementState, Intent, Effect, ModelManagementLabels (+ LocalModelManagementLabels)
  components/  ModelCard, ModelDetailSheet, FilterBar, DownloadProgressBar, ByteFormat (internal formatBytes)
domain/        ModelListing, DownloadProgress, DownloadStatus, DownloadWorkStatus, ModelFilters (+SizeBucket/StatusFilter/QuantFilter),
               Sources.kt (ModelCatalogSource, InstalledModelsSource, ModelSelectionSource, DownloadProgressSource, DownloadScheduler, DownloadWorkSource)
  usecase/     ObserveModelListingsUseCase, DownloadModelUseCase, CancelDownloadUseCase, SelectModelUseCase,
               RemoveModelUseCase, FilterModelsUseCase
data/          CoreModelSources.kt (LlmModelCatalogSource, LlmModelInstalledSource, DataStoreModelSelectionSource, StoreDownloadProgressSource),
               ModelDownloadScheduler (implements DownloadScheduler), WorkInfoObserver (implements DownloadWorkSource)
di/            ModelManagementModule.kt (modelManagementModule)
```

## Use cases
| Use case | Responsibility | Depends on |
|---|---|---|
| ObserveModelListingsUseCase(refresh: Flow<Unit>) | catalog + installed + selection + progress + job status → `Flow<List<ModelListing>>`; rescans installed on start, on a SUCCEEDED job, and on `refresh` | ModelCatalogSource, InstalledModelsSource, ModelSelectionSource, DownloadProgressSource, DownloadWorkSource |
| DownloadModelUseCase | enqueue; `Started` / `UnknownModel` | ModelCatalogSource, DownloadScheduler |
| CancelDownloadUseCase | cancel job | DownloadScheduler |
| SelectModelUseCase | `Selected` / `NotInstalled` / `UnknownModel` (extra case so unknown ids stay a silent no-op as before) | ModelCatalogSource, InstalledModelsSource, ModelSelectionSource |
| RemoveModelUseCase | `Removed(descriptor)` / `NothingRemoved`; selection left untouched | ModelCatalogSource, InstalledModelsSource |
| FilterModelsUseCase | pure status/family/size/quant AND filter over `ModelListing`s (replaces top-level `applyFilters`) | — |

`ModelManagementViewModel(observeListings, downloadModel, cancelDownload, selectModel, removeModel, filterModels, dispatchers, labels)`. State is now a `combine(listings, local UI)` + `stateIn`, so there is no mutable shadow copy. `ModelManagementState` fields/Effect/Intent/snackbars are unchanged except `downloading: Map<String, DownloadProgress>` (domain type, was core `PerModelProgress`).

## Moved FQNs (old → new), prefix `com.llmlocal.feature.modelmanagement`
- `.ModelManagementRoute` → `.presentation.ModelManagementRoute`
- `.ModelManagementScreen` → `.presentation.ModelManagementScreen`
- `.ModelManagementViewModel` → `.presentation.ModelManagementViewModel`
- `.mvi.{ModelManagementState,Intent,Effect,ModelManagementLabels,LocalModelManagementLabels,modelManagementLabels}` → `.presentation.mvi.*`
- `.mvi.{ModelFilters,SizeBucket,StatusFilter,QuantFilter}` → `.domain.*`
- `.components.{DownloadProgressBar,ModelCard,FilterBar,ModelDetailSheet}` → `.presentation.components.*`
- `.di.ModelDownloadScheduler` → `.data.ModelDownloadScheduler` (now `enqueue(id: String)`, `workTag()` removed; tag is `ModelDownloadScheduler.WORK_TAG`)
- `.di.WorkInfoObserver` → `.data.WorkInfoObserver` (now `observe()` returns `Flow<List<DownloadWorkStatus>>`)
- `.di.modelManagementModule` — unchanged.

## Koin
`single<…Source>` adapters bound to the 6 domain interfaces (concrete classes only referenced in the module), `factory` use cases, `viewModel { params -> … labels = params.get<ModelManagementLabels>() }`.

## `DownloadProgressBar`
Stays public. Takes the domain `DownloadProgress`; an overload keeps accepting core `PerModelProgress` so Recipe Keeper's `LibraryScreen` (`status.progress`) compiles unchanged.

## Edits outside my module (needed to build the apps) — separate commits
Import-path updates in `app/{recipes,medical,recipekeeper}` (Route/Screen/Labels/State/ModelFilters/DownloadProgressBar), and the three `previews/ModelManagementPreviews.kt` now build `DownloadProgress(status = DownloadStatus.X, …)` instead of `PerModelProgress(state = DownloadState.X, updatedAt = …)`. Lead: expect trivial import conflicts if other streams touched those lines.

## Notes / left for the lead
- Module has no `src/test` before; added `testImplementation` (junit, truth, turbine, coroutines-test) to `feature/modelmanagement/build.gradle.kts`.
- Behaviour nuance: first state emission is now an empty catalog until the first combine (a few ms) instead of the full catalog pre-populated; installed set still starts empty and fills after the disk scan.
- `:core:llm` stays a dependency only for `data/` (and the `PerModelProgress` overload in `DownloadProgressBar`).
