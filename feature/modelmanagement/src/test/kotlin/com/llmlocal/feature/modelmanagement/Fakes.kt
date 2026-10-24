package com.llmlocal.feature.modelmanagement

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.model.LlmModelDescriptor
import com.llmlocal.core.model.ModelFamily
import com.llmlocal.feature.modelmanagement.domain.DiscoveryResult
import com.llmlocal.feature.modelmanagement.domain.DownloadProgress
import com.llmlocal.feature.modelmanagement.domain.DownloadProgressSource
import com.llmlocal.feature.modelmanagement.domain.DownloadScheduler
import com.llmlocal.feature.modelmanagement.domain.DownloadStatus
import com.llmlocal.feature.modelmanagement.domain.DownloadWorkSource
import com.llmlocal.feature.modelmanagement.domain.DownloadWorkStatus
import com.llmlocal.feature.modelmanagement.domain.InstalledModelsSource
import com.llmlocal.feature.modelmanagement.domain.ModelCatalogSource
import com.llmlocal.feature.modelmanagement.domain.ModelDiscoverySource
import com.llmlocal.feature.modelmanagement.domain.ModelSelectionSource
import com.llmlocal.feature.modelmanagement.presentation.mvi.ModelManagementLabels
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

fun descriptor(
    id: String,
    sizeBytes: Long = 500L * 1024 * 1024,
    family: ModelFamily = ModelFamily.GENERAL,
    tags: List<String> = emptyList(),
) = LlmModelDescriptor(
    id = id, displayName = "Model $id", description = "", url = "https://x/$id",
    sizeBytes = sizeBytes, filename = "$id.litertlm", tags = tags, family = family,
)

fun progress(id: String, status: DownloadStatus = DownloadStatus.RUNNING) = DownloadProgress(
    modelId = id, status = status, bytesRead = 1, totalBytes = 10,
    speedBytesPerSec = 1, etaSeconds = 9,
)

class FakeCatalog(initial: List<LlmModelDescriptor>) : ModelCatalogSource {
    val flow = MutableStateFlow(initial)
    override val models: Flow<List<LlmModelDescriptor>> = flow
    override fun all() = flow.value
}

class FakeDiscovery(var result: DiscoveryResult = DiscoveryResult.Skipped) : ModelDiscoverySource {
    val calls = mutableListOf<Boolean>()
    override suspend fun discover(force: Boolean): DiscoveryResult { calls += force; return result }
}

class FakeInstalled(initial: Set<String> = emptySet()) : InstalledModelsSource {
    val ids = initial.toMutableSet()
    override suspend fun installedIds(): Set<String> = ids.toSet()
    override suspend fun remove(id: String): Boolean = ids.remove(id)
}

class FakeSelection(initial: String? = null) : ModelSelectionSource {
    private val flow = MutableStateFlow(initial)
    override val selectedId: Flow<String?> = flow
    override suspend fun select(id: String) { flow.value = id }
}

class FakeProgress : DownloadProgressSource {
    val flow = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    override val progress: Flow<Map<String, DownloadProgress>> = flow
}

class FakeScheduler : DownloadScheduler {
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override fun enqueue(id: String) { enqueued += id }
    override fun cancel(id: String) { cancelled += id }
}

class FakeWork : DownloadWorkSource {
    val flow = MutableStateFlow<List<DownloadWorkStatus>>(emptyList())
    override fun observe(): Flow<List<DownloadWorkStatus>> = flow
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}
