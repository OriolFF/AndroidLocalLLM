package com.llmlocal.recipekeeper.config

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.feature.webclipper.di.WEBCLIPPER_TEMP_DIR_QUALIFIER
import com.llmlocal.recipekeeper.detail.RecipeDetailViewModel
import com.llmlocal.recipekeeper.library.LibraryViewModel
import com.llmlocal.recipekeeper.library.ModelStatusSource
import com.llmlocal.recipekeeper.library.PendingRecipeDeletes
import com.llmlocal.recipekeeper.library.ReadinessModelStatusSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin bindings for Recipe Keeper's own UI layer (library + detail).
 *
 * Depends on `RecipeRepository`, which `recipeKeeperConfigModule` (WS5a)
 * provides; installed next to it in `RecipeKeeperApp`.
 *
 *  - [PendingRecipeDeletes] — app-wide undo window for deletes.
 *  - [ModelStatusSource]   — adapter over the shared `ModelReadinessObserver`.
 *  - [LibraryViewModel]    — no parameters.
 *  - [RecipeDetailViewModel] — `parametersOf(recipeId)` from `RecipeDetailRoute`;
 *    "Retry pictures" reuses the clipper's `PictureDownloader` and temp dir.
 */
val recipeKeeperUiModule: Module = module {
    single {
        PendingRecipeDeletes(
            repository = get(),
            dispatchers = get(),
        )
    }

    single<ModelStatusSource> {
        ReadinessModelStatusSource(
            observer = get(),
            scope = CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default),
        )
    }
    viewModel {
        LibraryViewModel(
            repository = get(),
            modelStatusSource = get(),
            pendingDeletes = get(),
            dispatchers = get(),
        )
    }

    viewModel { params ->
        RecipeDetailViewModel(
            recipeId = params.get(),
            repository = get(),
            pendingDeletes = get(),
            dispatchers = get(),
            pictureDownloader = get(),
            pictureStore = get(),
            tempDir = get(named(WEBCLIPPER_TEMP_DIR_QUALIFIER)),
        )
    }
}
