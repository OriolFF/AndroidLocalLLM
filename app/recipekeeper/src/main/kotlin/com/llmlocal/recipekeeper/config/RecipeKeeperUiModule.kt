package com.llmlocal.recipekeeper.config

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.detail.RecipeDetailViewModel
import com.llmlocal.recipekeeper.library.LibraryViewModel
import com.llmlocal.recipekeeper.library.ModelStatusSource
import com.llmlocal.recipekeeper.library.PendingRecipeDeletes
import com.llmlocal.recipekeeper.library.StoreBackedModelStatusSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin bindings for Recipe Keeper's own UI layer (library + detail).
 *
 * Depends on `RecipeRepository`, which `recipeKeeperConfigModule` (WS5a)
 * provides; installed next to it in `RecipeKeeperApp`.
 *
 *  - [PendingRecipeDeletes] — app-wide undo window for deletes.
 *  - [ModelStatusSource]   — TEMP shim over the selection / progress stores;
 *                            replaced by WS3's `ModelReadinessObserver` at merge.
 *  - [LibraryViewModel]    — no parameters.
 *  - [RecipeDetailViewModel] — `parametersOf(recipeId)` from `RecipeDetailRoute`.
 */
val recipeKeeperUiModule: Module = module {
    single {
        PendingRecipeDeletes(
            repository = get(),
            dispatchers = get(),
        )
    }

    // TEMP shim: replaced by WS3 at merge (bind an adapter over ModelReadinessObserver instead).
    single<ModelStatusSource> {
        StoreBackedModelStatusSource(
            selectionStore = get(),
            progressStore = get(),
            modelManager = get(),
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
        )
    }
}
