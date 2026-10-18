package com.llmlocal.feature.webclipper.di

import com.llmlocal.core.llm.di.REAL_ENGINE_QUALIFIER
import com.llmlocal.feature.webclipper.BrowserViewModel
import com.llmlocal.feature.webclipper.ClipSession
import com.llmlocal.feature.webclipper.ClipSink
import com.llmlocal.feature.webclipper.ExtractionViewModel
import com.llmlocal.feature.webclipper.WebClipConfig
import com.llmlocal.feature.webclipper.WebClipperLabels
import com.llmlocal.feature.webclipper.WebViewHolder
import com.llmlocal.feature.webclipper.capture.ImageDownloaderPictureDownloader
import com.llmlocal.feature.webclipper.capture.PageScanner
import com.llmlocal.feature.webclipper.capture.PictureDownloader
import com.llmlocal.feature.webclipper.capture.WebViewPageScanner
import com.llmlocal.feature.webclipper.readiness.ReadinessSource
import com.llmlocal.feature.webclipper.readiness.ShimModelReadinessObserver
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/** Qualifier for the cache directory temp pictures are downloaded into. */
const val WEBCLIPPER_TEMP_DIR_QUALIFIER = "webclipper-temp-dir"

/**
 * Koin module wiring for the web-clipper feature.
 *
 * Both ViewModels are parameterised, like `llmTaskModule`: the routes call
 * `koinViewModel { parametersOf(config, labels, initialUrl) }` /
 * `parametersOf(candidateId, config, sink, labels)` with the app's
 * `WebClipConfig<T>` / `ClipSink<T>` / `WebClipperLabels`. The real engine
 * is resolved under [REAL_ENGINE_QUALIFIER].
 *
 * `ReadinessSource` is bound to the TEMP shim observer; at merge the lead
 * points it at WS3's `ModelReadinessObserver`.
 */
val webClipperModule: Module = module {
    single { ClipSession() }
    single { WebViewHolder(androidContext()) }
    single<PageScanner> { WebViewPageScanner(pageCapture = get(), holder = get()) }
    single<PictureDownloader> { ImageDownloaderPictureDownloader(delegate = get()) }
    single(named(WEBCLIPPER_TEMP_DIR_QUALIFIER)) { File(androidContext().cacheDir, "webclipper") }

    // TEMP shim: replaced by WS3's ModelReadinessObserver at merge.
    single<ReadinessSource> {
        ShimModelReadinessObserver(
            selectionStore = get(),
            progressStore = get(),
            manager = get(),
            engine = get(named(REAL_ENGINE_QUALIFIER)),
            dispatchers = get(),
        )
    }

    viewModel { params ->
        BrowserViewModel(
            config = params.get<WebClipConfig<*>>(),
            labels = params.get<WebClipperLabels>(),
            scanner = get(),
            session = get(),
            readinessSource = get(),
            dispatchers = get(),
            initialUrl = params.getOrNull<String>(),
        )
    }

    viewModel { params ->
        @Suppress("UNCHECKED_CAST")
        ExtractionViewModel(
            candidateId = params.get<String>(),
            config = params.get<WebClipConfig<*>>() as WebClipConfig<Any>,
            sink = params.get<ClipSink<*>>() as ClipSink<Any>,
            labels = params.get<WebClipperLabels>(),
            session = get(),
            generateTask = get(),
            engine = get(named(REAL_ENGINE_QUALIFIER)),
            pictureDownloader = get(),
            tempDir = get(named(WEBCLIPPER_TEMP_DIR_QUALIFIER)),
            dispatchers = get(),
        )
    }
}
