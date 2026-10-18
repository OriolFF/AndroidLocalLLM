package com.llmlocal.recipekeeper

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.llmlocal.core.common.di.commonModule
import com.llmlocal.core.data.di.dataModule
import com.llmlocal.core.llm.di.llmModule
import com.llmlocal.core.llm.download.ModelDownloadWorker
import com.llmlocal.core.network.di.networkModule
import com.llmlocal.core.web.di.webModule
import com.llmlocal.feature.llmtask.di.llmTaskModule
import com.llmlocal.feature.modelmanagement.di.modelManagementModule
import com.llmlocal.feature.webclipper.di.webClipperModule
import com.llmlocal.recipekeeper.config.recipeKeeperConfigModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class for Recipe Keeper. Hosts the Koin DI graph and the
 * WorkManager [Configuration].
 *
 * Same shape as `RecipeApp` / `MedicalApp` plus the web-clipper stack
 * (`webModule`, `webClipperModule`) and this app's own config module.
 * `llmTaskModule` stays installed: it is harmless and keeps the generic
 * task feature available.
 *
 * WorkManager uses the [Configuration.Provider] interface so we can supply
 * a Koin-aware [androidx.work.WorkerFactory]; the default initializer is
 * disabled in `AndroidManifest.xml` for the same reason.
 */
class RecipeKeeperApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .setWorkerFactory(get())
            .build()

    override fun onCreate() {
        super.onCreate()

        // Register the notification channel used by ModelDownloadWorker
        // before any download is kicked off. Idempotent — safe to call on
        // every launch.
        ModelDownloadWorker.ensureNotificationChannel(this)

        startKoin {
            androidLogger(Level.INFO)
            androidContext(this@RecipeKeeperApp)
            modules(
                commonModule,
                networkModule,
                llmModule,
                dataModule,
                webModule,
                llmTaskModule,
                modelManagementModule,
                webClipperModule,
                recipeKeeperConfigModule,
            )
        }
    }
}
