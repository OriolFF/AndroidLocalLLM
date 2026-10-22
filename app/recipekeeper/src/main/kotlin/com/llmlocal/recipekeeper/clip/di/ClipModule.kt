package com.llmlocal.recipekeeper.clip.di

import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.recipekeeper.clip.data.RecipeKeeperClipSink
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperClipConfig
import com.llmlocal.recipekeeper.clip.data.detection.RoomDetectionHintsStore
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperTaskConfig
import com.llmlocal.recipekeeper.clip.domain.detection.BuiltInVocabulary
import com.llmlocal.recipekeeper.clip.domain.detection.DeriveDetectionHintsUseCase
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHintsSource
import com.llmlocal.recipekeeper.clip.domain.detection.DetectionHintsStore
import com.llmlocal.recipekeeper.clip.domain.detection.LearnDetectionHintsUseCase
import com.llmlocal.recipekeeper.clip.domain.detection.PenaliseDetectionHintsUseCase
import com.llmlocal.recipekeeper.clip.domain.detection.ResetDetectionHintsUseCase
import com.llmlocal.core.common.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import org.koin.core.module.Module
import com.llmlocal.recipekeeper.R
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Bindings of the "clip" feature: how Recipe Keeper clips a page. Hands
 * `:feature:webclipper` its [WebClipConfig] and [ClipSink] (the sink needs
 * the recipes feature's repository and picture store, so install
 * `recipesModule` too), plus the `LlmTaskConfig` for previews / tests.
 */
val clipModule: Module = module {
    // ---- learned detection (hints) ------------------------------------------
    single { RoomDetectionHintsStore(dao = get()) }
    single<DetectionHintsSource> { get<RoomDetectionHintsStore>() }
    single<DetectionHintsStore> { get<RoomDetectionHintsStore>() }
    single { DeriveDetectionHintsUseCase(knownUnits = BuiltInVocabulary.units, knownHeadings = BuiltInVocabulary.headings) }
    single { LearnDetectionHintsUseCase(derive = get(), store = get()) }
    single { PenaliseDetectionHintsUseCase(store = get()) }
    single { ResetDetectionHintsUseCase(store = get()) }

    single { RecipeKeeperTaskConfig }
    single<WebClipConfig<ClippedRecipe>> { RecipeKeeperClipConfig(untitledTitle = androidContext().getString(R.string.recipekeeper_untitled_recipe)) }
    single<ClipSink<ClippedRecipe>> {
        val config = get<WebClipConfig<ClippedRecipe>>()
        RecipeKeeperClipSink(
            repository = get(), pictures = get(), dispatchers = get(),
            untitledTitle = androidContext().getString(R.string.recipekeeper_untitled_recipe),
            learning = get(),
            learningScope = CoroutineScope(SupervisorJob() + get<DispatcherProvider>().io),
            preferredPictures = { candidate, snapshot, max -> config.rankPictures(candidate, snapshot, max).filter { it.preferred }.map { it.url } },
        )
    }
}
