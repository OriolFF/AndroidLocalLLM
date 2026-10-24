package com.llmlocal.feature.webclipper.di

import com.llmlocal.core.llm.di.REAL_ENGINE_QUALIFIER
import com.llmlocal.core.llm.readiness.ModelReadinessObserver
import com.llmlocal.feature.webclipper.data.CacheDirTempPictureStore
import com.llmlocal.feature.webclipper.data.DataStoreStartPageRepository
import com.llmlocal.feature.webclipper.data.EngineLlmGenerationSource
import com.llmlocal.feature.webclipper.data.ImageDownloaderPictureDownloader
import com.llmlocal.feature.webclipper.data.InMemoryClipInputInbox
import com.llmlocal.feature.webclipper.data.InMemoryClipSession
import com.llmlocal.feature.webclipper.data.ModelReadinessSource
import com.llmlocal.feature.webclipper.data.WebViewHolder
import com.llmlocal.feature.webclipper.data.WebViewPageScanner
import com.llmlocal.feature.webclipper.domain.ClipSession
import com.llmlocal.feature.webclipper.domain.ClipSink
import com.llmlocal.feature.webclipper.domain.LlmGenerationSource
import com.llmlocal.feature.webclipper.domain.PageScanner
import com.llmlocal.feature.webclipper.domain.PictureDownloader
import com.llmlocal.feature.webclipper.domain.ReadinessSource
import com.llmlocal.feature.webclipper.domain.StartPageRepository
import com.llmlocal.feature.webclipper.domain.TempPictureStore
import com.llmlocal.feature.webclipper.domain.WebClipConfig
import com.llmlocal.feature.webclipper.domain.usecase.CancelClipUseCase
import com.llmlocal.feature.webclipper.domain.usecase.CheckDuplicateUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ClipUseCase
import com.llmlocal.feature.webclipper.domain.input.ClipInputInbox
import com.llmlocal.feature.webclipper.domain.usecase.DiscardPreparedImagesUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ObserveInputCapabilitiesUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ObserveReadinessUseCase
import com.llmlocal.feature.webclipper.domain.usecase.PrepareImagesUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ObserveStartPageUseCase
import com.llmlocal.feature.webclipper.domain.usecase.ScanPageUseCase
import com.llmlocal.feature.webclipper.domain.usecase.SetStartPageUseCase
import com.llmlocal.feature.webclipper.presentation.BrowserViewModel
import com.llmlocal.feature.webclipper.presentation.ExtractionViewModel
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels
import com.llmlocal.feature.webclipper.presentation.images.ImagesClipViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File

/** Qualifier for the cache directory temp pictures are downloaded into. */
const val WEBCLIPPER_TEMP_DIR_QUALIFIER = "webclipper-temp-dir"

/**
 * Koin module wiring for the web-clipper feature: data adapters bound to the
 * domain interfaces, use cases as `factory`, ViewModels parameterised like
 * `llmTaskModule`.
 *
 * The routes call `koinViewModel { parametersOf(config, labels, initialUrl) }` /
 * `parametersOf(candidateId, config, sink, labels)` with the app's
 * `WebClipConfig<T>` / `ClipSink<T>` / `WebClipperLabels`. The real engine
 * is resolved under [REAL_ENGINE_QUALIFIER].
 */
val webClipperModule: Module = module {
    // data -> domain interfaces
    single<ClipSession> { InMemoryClipSession() }
    single<ClipInputInbox> { InMemoryClipInputInbox() }
    single { WebViewHolder(androidContext()) }
    single<PageScanner> { WebViewPageScanner(pageCapture = get(), holder = get()) }
    single<PictureDownloader> { ImageDownloaderPictureDownloader(delegate = get()) }
    single(named(WEBCLIPPER_TEMP_DIR_QUALIFIER)) { File(androidContext().cacheDir, "webclipper") }
    single<TempPictureStore> { CacheDirTempPictureStore(get(named(WEBCLIPPER_TEMP_DIR_QUALIFIER))) }
    single<ReadinessSource> { ModelReadinessSource(get<ModelReadinessObserver>()) }
    single<StartPageRepository> { DataStoreStartPageRepository(androidContext()) }
    single<LlmGenerationSource> {
        EngineLlmGenerationSource(engine = get(named(REAL_ENGINE_QUALIFIER)), generateTask = get())
    }

    // use cases
    factory { ScanPageUseCase(scanner = get(), session = get(), dispatchers = get()) }
    factory { ObserveReadinessUseCase(source = get()) }
    factory { ObserveStartPageUseCase(repository = get()) }
    factory { SetStartPageUseCase(repository = get()) }
    factory { CheckDuplicateUseCase(dispatchers = get()) }
    factory { CancelClipUseCase(llm = get(), session = get()) }
    factory { PrepareImagesUseCase(importer = get(), tempPictures = get(), dispatchers = get()) }
    factory { DiscardPreparedImagesUseCase(tempPictures = get()) }
    factory { ObserveInputCapabilitiesUseCase(readiness = get(), config = get<WebClipConfig<*>>()) }
    factory {
        ClipUseCase<Any>(
            llm = get(),
            pictureDownloader = get(),
            tempPictures = get(),
            session = get(),
            dispatchers = get(),
        )
    }

    viewModel { params ->
        BrowserViewModel(
            config = params.get<WebClipConfig<*>>(),
            labels = params.get<WebClipperLabels>(),
            scanPage = get(),
            observeReadiness = get(),
            observeStartPage = get(),
            session = get(),
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
            clip = get(),
            checkDuplicate = get(),
            cancelClip = get(),
            discardImages = get(),
            dispatchers = get(),
        )
    }

    viewModel { params ->
        ImagesClipViewModel(
            pendingUris = params.getOrNull<List<String>>(),
            savedStateHandle = get(),
            config = params.get<WebClipConfig<*>>(),
            labels = params.get<WebClipperLabels>(),
            session = get(),
            prepareImages = get(),
            discardImages = get(),
            observeCapabilities = get(),
            dispatchers = get(),
        )
    }
}
