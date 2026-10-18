package com.llmlocal.recipekeeper.config

import androidx.room.Room
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.llmlocal.feature.webclipper.ClipSink
import com.llmlocal.feature.webclipper.WebClipConfig
import com.llmlocal.recipekeeper.clip.RecipeKeeperClipConfig
import com.llmlocal.recipekeeper.clip.RecipeKeeperClipSink
import com.llmlocal.recipekeeper.clip.RecipeKeeperTaskConfig
import com.llmlocal.recipekeeper.data.RecipeDao
import com.llmlocal.recipekeeper.data.RecipeKeeperDatabase
import com.llmlocal.recipekeeper.data.RecipePictureStore
import com.llmlocal.recipekeeper.data.RoomRecipeRepository
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.domain.RecipeRepository
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Data / config bindings for Recipe Keeper (WS5a). UI bindings (labels,
 * ViewModels) live in `RecipeKeeperUiModule.kt` (WS5b).
 *
 * - Room database + DAO, picture folder store, [RecipeRepository]
 * - [WebClipConfig] / [ClipSink] for `:feature:webclipper`, plus the
 *   `LlmTaskConfig` for previews / tests
 * - Coil [ImageLoader] on the shared Koin [OkHttpClient]; `RecipeKeeperApp`
 *   exposes it through `SingletonImageLoader.Factory` (WS5b)
 */
val recipeKeeperConfigModule: Module = module {
    single<RecipeKeeperDatabase> {
        Room.databaseBuilder(androidContext(), RecipeKeeperDatabase::class.java, RecipeKeeperDatabase.NAME).build()
    }
    single<RecipeDao> { get<RecipeKeeperDatabase>().recipeDao() }
    single { RecipePictureStore(File(androidContext().filesDir, "recipes")) }
    single<RecipeRepository> { RoomRecipeRepository(dao = get(), pictures = get(), dispatchers = get()) }

    single { RecipeKeeperTaskConfig }
    single<WebClipConfig<ClippedRecipe>> { RecipeKeeperClipConfig() }
    single<ClipSink<ClippedRecipe>> { RecipeKeeperClipSink(repository = get(), pictures = get(), dispatchers = get()) }

    single<ImageLoader> {
        val client: OkHttpClient = get()
        ImageLoader.Builder(androidContext())
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}
