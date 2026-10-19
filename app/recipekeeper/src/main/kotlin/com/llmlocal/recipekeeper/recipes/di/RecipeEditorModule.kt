package com.llmlocal.recipekeeper.recipes.di

import com.llmlocal.recipekeeper.recipes.domain.usecase.AddRecipePictureUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.DiscardRecipeEditsUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.LoadRecipeForEditUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.SaveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.SplitPastedLinesUseCase
import com.llmlocal.recipekeeper.recipes.presentation.editor.RecipeEditorViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.UUID

/**
 * Bindings of the recipe editor: its use cases and `RecipeEditorViewModel`
 * (with `parametersOf(recipeId)` from `RecipeEditorRoute`). Needs
 * `RecipeRepository`, `RecipePictureStore`, `PictureImporter` and
 * `DispatcherProvider` from [recipesModule] / the app's other modules.
 */
val recipeEditorModule: Module = module {
    factory { LoadRecipeForEditUseCase(repository = get()) }
    factory {
        SaveRecipeUseCase(
            repository = get(),
            pictureStore = get(),
            dispatchers = get(),
            idFactory = { UUID.randomUUID().toString() },
            clock = { System.currentTimeMillis() },
        )
    }
    factory {
        AddRecipePictureUseCase(
            importer = get(),
            // Same folder the PictureImporter sweeps at app start (bound in recipesModule).
            tempRoot = get(named(EDITOR_TEMP_DIR)),
            dispatchers = get(),
        )
    }
    factory { DiscardRecipeEditsUseCase(dispatchers = get()) }
    factory { SplitPastedLinesUseCase() }

    viewModel { params ->
        RecipeEditorViewModel(
            recipeId = params.getOrNull(),
            savedStateHandle = get(),
            loadForEdit = get(),
            saveRecipe = get(),
            addPicture = get(),
            discardEdits = get(),
            splitPasted = get(),
            dispatchers = get(),
        )
    }
}
