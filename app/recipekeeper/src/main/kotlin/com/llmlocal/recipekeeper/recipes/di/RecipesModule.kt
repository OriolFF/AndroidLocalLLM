package com.llmlocal.recipekeeper.recipes.di

import androidx.room.Room
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.R
import com.llmlocal.feature.webclipper.di.WEBCLIPPER_TEMP_DIR_QUALIFIER
import com.llmlocal.recipekeeper.recipes.data.ContentResolverPictureImporter
import com.llmlocal.recipekeeper.recipes.data.FileRecipePictureStore
import com.llmlocal.recipekeeper.recipes.data.MIGRATION_1_2
import com.llmlocal.recipekeeper.recipes.data.MIGRATION_2_3
import com.llmlocal.recipekeeper.recipes.data.DetectionHintDao
import com.llmlocal.recipekeeper.recipes.data.ReadinessLibraryModelStatusSource
import com.llmlocal.recipekeeper.recipes.data.RecipeDao
import com.llmlocal.recipekeeper.recipes.data.RecipeKeeperDatabase
import com.llmlocal.recipekeeper.recipes.data.RoomRecipeRepository
import com.llmlocal.recipekeeper.recipes.data.WebClipperPictureFetcher
import com.llmlocal.recipekeeper.recipes.domain.LibraryModelStatusSource
import com.llmlocal.recipekeeper.recipes.domain.PictureFetcher
import com.llmlocal.recipekeeper.recipes.domain.PictureImporter
import com.llmlocal.recipekeeper.recipes.domain.RecipeShareTemplates
import com.llmlocal.recipekeeper.recipes.domain.RecipePictureStore
import com.llmlocal.recipekeeper.recipes.domain.RecipeRepository
import com.llmlocal.recipekeeper.recipes.domain.usecase.DeleteRecipeWithUndoUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveLibraryModelStatusUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveLibraryUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ObserveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ResetChecksUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.RetryRecipePicturesUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ShareRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ToggleFavouriteUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.ToggleIngredientCheckedUseCase
import com.llmlocal.recipekeeper.recipes.presentation.recipeKeeperLibraryLabels
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
/** Qualifier of the editor's temp-picture root (`cacheDir/recipe-editor`). */
const val EDITOR_TEMP_DIR = "editor-temp-dir"

val recipesModule: Module = module {
    // ---- data ------------------------------------------------------------
    single<RecipeKeeperDatabase> {
        Room.databaseBuilder(androidContext(), RecipeKeeperDatabase::class.java, RecipeKeeperDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
    single<RecipeDao> { get<RecipeKeeperDatabase>().recipeDao() }
    single<DetectionHintDao> { get<RecipeKeeperDatabase>().detectionHintDao() }
    single<RecipePictureStore> { FileRecipePictureStore(File(androidContext().filesDir, "recipes")) }
    single<RecipeRepository> { RoomRecipeRepository(dao = get(), pictures = get(), dispatchers = get()) }
    single<PictureImporter> { ContentResolverPictureImporter(androidContext(), get()) }
    single(named(EDITOR_TEMP_DIR)) { File(androidContext().cacheDir, ContentResolverPictureImporter.EDITOR_DIR) }
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
    factory { ToggleFavouriteUseCase(repository = get()) }
    factory { ToggleIngredientCheckedUseCase(repository = get()) }
    factory { ResetChecksUseCase(repository = get()) }
    factory { RetryRecipePicturesUseCase(repository = get(), fetcher = get(), store = get(), dispatchers = get()) }
    factory {
        val labels = recipeKeeperLibraryLabels(androidContext().resources)
        val resources = androidContext().resources
        ShareRecipeUseCase(
            repository = get(),
            templates = RecipeShareTemplates(
                clipped = labels.shareTemplate,
                created = labels.shareCreatedTemplate,
                bullet = labels.shareBullet,
                numbered = labels.shareNumbered,
                ingredientsHeader = resources.getString(R.string.recipekeeper_ingredients_header),
                stepsHeader = resources.getString(R.string.recipekeeper_steps_header),
            ),
        )
    }

    // ---- presentation ----------------------------------------------------
    viewModel {
        LibraryViewModel(
            observeLibrary = get(),
            observeModelStatus = get(),
            observeRecipe = get(),
            deleteWithUndo = get(),
            shareRecipe = get(),
            toggleFavourite = get(),
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
            toggleFavourite = get(),
            dispatchers = get(),
        )
    }
}
