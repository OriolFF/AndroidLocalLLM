package com.llmlocal.recipekeeper.recipes.di

import androidx.room.Room
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.feature.webclipper.di.WEBCLIPPER_TEMP_DIR_QUALIFIER
import com.llmlocal.recipekeeper.recipes.data.FileRecipePictureStore
import com.llmlocal.recipekeeper.recipes.data.ReadinessLibraryModelStatusSource
import com.llmlocal.recipekeeper.recipes.data.RecipeDao
import com.llmlocal.recipekeeper.recipes.data.RecipeKeeperDatabase
import com.llmlocal.recipekeeper.recipes.data.RoomRecipeRepository
import com.llmlocal.recipekeeper.recipes.data.WebClipperPictureFetcher
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatusSource
import com.llmlocal.recipekeeper.recipes.domain.PictureFetcher
import com.llmlocal.recipekeeper.recipes.domain.RecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import com.llmlocal.recipekeeper.recipes.domain.usecase.DeleteRecipeWithUndoUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveLibraryModelStatusUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveLibraryUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ResetChecksUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.RetryRecipePicturesUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ShareRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ToggleIngredientCheckedUseCase
import com.llmlocal.recipekeeper.recipes.presentation.DefaultRecipeKeeperLibraryLabels
import com.llmlocal.recipekeeper.recipes.presentation.detail.RecipeDetailViewModel
import com.llmlocal.recipekeeper.recipes.presentation.library.LibraryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/**
 * Bindings of the "recipes" feature (library + detail + the shared recipe
 * domain). The only place that knows the concrete data classes.
 *
 *  - data: Room database + DAO, [RecipePictureStore], [RecipeRepository],
 *    [PictureFetcher] (the clipper's downloader), [LibraryModelStatusSource]
 *    (adapter over the shared `ModelReadinessObserver`)
 *  - domain: the use cases; [DeleteRecipeWithUndoUseCase] is a `single`
 *    because the undo window is app-wide
 *  - presentation: [LibraryViewModel], and [RecipeDetailViewModel] with
 *    `parametersOf(recipeId)` from `RecipeDetailRoute`
 */
val recipesModule: Module = module {
    // ---- data ------------------------------------------------------------
    single<RecipeKeeperDatabase> {
        Room.databaseBuilder(androidContext(), RecipeKeeperDatabase::class.java, RecipeKeeperDatabase.NAME).build()
    }
    single<RecipeDao> { get<RecipeKeeperDatabase>().recipeDao() }
    single<RecipePictureStore> { FileRecipePictureStore(File(androidContext().filesDir, "recipes")) }
    single<RecipeRepository> { RoomRecipeRepository(dao = get(), pictures = get(), dispatchers = get()) }
    single<PictureFetcher> {
        WebClipperPictureFetcher(downloader = get(), tempDir = get(named(WEBCLIPPER_TEMP_DIR_QUALIFIER)))
    }
    single<LibraryModelStatusSource> {
        ReadinessLibraryModelStatusSource(
            observer = get(),
            scope = CoroutineScope(SupervisorJob() + get<DispatcherProvider>().default),
        )
    }

    // ---- domain ----------------------------------------------------------
    single { DeleteRecipeWithUndoUseCase(repository = get(), dispatchers = get()) }
    factory { ObserveLibraryUseCase(repository = get(), deleteWithUndo = get()) }
    factory { ObserveRecipeUseCase(repository = get()) }
    factory { ObserveLibraryModelStatusUseCase(source = get()) }
    factory { ToggleIngredientCheckedUseCase(repository = get()) }
    factory { ResetChecksUseCase(repository = get()) }
    factory { RetryRecipePicturesUseCase(repository = get(), fetcher = get(), store = get(), dispatchers = get()) }
    factory { ShareRecipeUseCase(repository = get(), template = DefaultRecipeKeeperLibraryLabels.shareTemplate) }

    // ---- presentation ----------------------------------------------------
    viewModel {
        LibraryViewModel(
            observeLibrary = get(),
            observeModelStatus = get(),
            observeRecipe = get(),
            deleteWithUndo = get(),
            shareRecipe = get(),
            dispatchers = get(),
        )
    }
    viewModel { params ->
        RecipeDetailViewModel(
            recipeId = params.get(),
            observeRecipe = get(),
            toggleIngredient = get(),
            resetChecks = get(),
            deleteWithUndo = get(),
            retryPictures = get(),
            shareRecipe = get(),
            dispatchers = get(),
        )
    }
}
