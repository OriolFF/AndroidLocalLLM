package com.llmlocal.recipekeeper.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.llmlocal.core.common.di.commonModule
import com.llmlocal.core.data.di.dataModule
import com.llmlocal.core.llm.di.llmModule
import com.llmlocal.core.llm.download.ModelDownloadWorker
import com.llmlocal.core.network.di.networkModule
import com.llmlocal.core.web.di.webModule
import com.llmlocal.feature.llmtask.di.llmTaskModule
import com.llmlocal.feature.modelmanagement.di.modelManagementModule
import com.llmlocal.feature.webclipper.di.webClipperModule
import com.llmlocal.recipekeeper.app.di.recipeKeeperModules
import com.llmlocal.recipekeeper.recipes.domain.PictureImporter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Application class for Recipe Keeper. Hosts the Koin DI graph, the
 * WorkManager [Configuration] and Coil's singleton [ImageLoader].
 *
 * Same shape as `RecipeApp` / `MedicalApp` plus the web-clipper stack
 * (`webModule`, `webClipperModule`) and the app's own modules
 * ([recipeKeeperModules]: `appModule`, `recipesModule`, `clipModule`). `llmTaskModule` stays
 * installed: it is harmless and keeps the generic task feature available.
 *
 * WorkManager uses the [Configuration.Provider] interface so we can supply
 * a Koin-aware [androidx.work.WorkerFactory]; the default initializer is
 * disabled in `AndroidManifest.xml` for the same reason.
 */
class RecipeKeeperApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

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
                listOf(
                    commonModule,
                    networkModule,
                    llmModule,
                    dataModule,
                    webModule,
                    llmTaskModule,
                    modelManagementModule,
                    webClipperModule,
                ) + recipeKeeperModules,
            )
        }
        // Editor pictures are imported into a temp dir before Save; a draft that
        // never reached Save (process death, discard) leaves them behind. Sweep
        // anything older than a day off the main thread at every launch.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            get<PictureImporter>().sweep(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24))
        }
    }

    /**
     * Coil picks the Koin-provided loader when `appModule`
     * binds one (built on the shared `OkHttpClient`); otherwise a default
     * loader, which is all the library and detail screens need since they
     * only show local files.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        getKoin().getOrNull<ImageLoader>() ?: ImageLoader.Builder(context).build()
}
