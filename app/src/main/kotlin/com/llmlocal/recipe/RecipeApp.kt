package com.llmlocal.recipe

import android.app.Application
import com.llmlocal.core.common.di.commonModule
import com.llmlocal.core.data.di.dataModule
import com.llmlocal.core.llm.di.llmModule
import com.llmlocal.core.network.di.networkModule
import com.llmlocal.feature.recipe.di.recipeModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class. Hosts the Koin DI graph.
 *
 * Each module contributes a single concern (common, network, llm, data,
 * feature:recipe). The order in [startKoin] does not matter — Koin
 * resolves bindings lazily.
 */
class RecipeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@RecipeApp)
            modules(
                commonModule,
                networkModule,
                llmModule,
                dataModule,
                recipeModule,
            )
        }
    }
}
