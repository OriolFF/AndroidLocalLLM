package com.llmlocal.core.llm.model.catalog

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.model.discovery.ModelDiscoverySource
import com.llmlocal.core.model.LlmModelDescriptor
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelCatalogRepositoryTest {

    private fun model(id: String, size: Long = 100) = LlmModelDescriptor(
        id = id, displayName = id, description = "", url = "https://x/$id", sizeBytes = size, filename = "$id.litertlm",
    )

    private class FakeStore : DiscoveredModelsStore {
        val state = MutableStateFlow(DiscoveredModels(emptyList(), null))
        override val discovered: Flow<DiscoveredModels> = state
        override suspend fun save(models: List<LlmModelDescriptor>, refreshedAtMillis: Long) {
            state.value = DiscoveredModels(models, refreshedAtMillis)
        }
    }

    private class FakeDiscovery(var result: () -> List<LlmModelDescriptor>) : ModelDiscoverySource {
        var calls = 0
        override suspend fun discover(): List<LlmModelDescriptor> { calls++; return result() }
    }

    private class Dispatchers(d: CoroutineDispatcher) : DispatcherProvider {
        override val io = d
        override val default = d
        override val main = d
    }

    private val builtIn = listOf(model("built", 50))
    private val store = FakeStore()
    private var now = 1_000_000L

    private fun repository(discovery: FakeDiscovery, installed: Set<String> = emptySet(), dispatcher: CoroutineDispatcher) =
        ModelCatalogRepository(
            builtIn = builtIn,
            discovery = discovery,
            store = store,
            dispatchers = Dispatchers(dispatcher),
            isInstalled = { it.id in installed },
            staleAfterMillis = 1_000,
            clock = { now },
        )

    @Test
    fun `built-ins come first and discovered duplicates are dropped`() = runTest {
        val discovery = FakeDiscovery { listOf(model("big", 300), model("built", 999), model("small", 10)) }
        val repo = repository(discovery, dispatcher = StandardTestDispatcher(testScheduler))

        val result = repo.refresh()

        assertThat(result).isEqualTo(Outcome.Success(CatalogRefresh(newModels = 2, skipped = false)))
        assertThat(repo.models.first().map { it.id }).containsExactly("built", "small", "big").inOrder()
        assertThat(repo.models.first().first { it.id == "built" }.sizeBytes).isEqualTo(50)
    }

    @Test
    fun `fresh cache skips discovery unless forced`() = runTest {
        val discovery = FakeDiscovery { listOf(model("a")) }
        val repo = repository(discovery, dispatcher = StandardTestDispatcher(testScheduler))

        repo.refresh()
        now += 500
        assertThat(repo.refresh()).isEqualTo(Outcome.Success(CatalogRefresh(0, skipped = true)))
        assertThat(discovery.calls).isEqualTo(1)

        repo.refresh(force = true)
        assertThat(discovery.calls).isEqualTo(2)

        now += 2_000
        repo.refresh()
        assertThat(discovery.calls).isEqualTo(3)
    }

    @Test
    fun `failure keeps the cached list`() = runTest {
        val discovery = FakeDiscovery { listOf(model("a")) }
        val repo = repository(discovery, dispatcher = StandardTestDispatcher(testScheduler))
        repo.refresh()
        now += 5_000
        discovery.result = { throw IOException("offline") }

        val result = repo.refresh()

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(repo.models.first().map { it.id }).containsExactly("built", "a").inOrder()
    }

    @Test
    fun `an installed model that vanished upstream is kept`() = runTest {
        val discovery = FakeDiscovery { listOf(model("a"), model("b")) }
        val repo = repository(discovery, installed = setOf("a"), dispatcher = StandardTestDispatcher(testScheduler))
        repo.refresh()
        now += 5_000
        discovery.result = { listOf(model("b")) }

        repo.refresh()

        assertThat(repo.models.first().map { it.id }).containsExactly("built", "a", "b")
    }

    @Test
    fun `snapshot mirrors the cache once started`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repo = repository(FakeDiscovery { emptyList() }, dispatcher = dispatcher)
        store.state.value = DiscoveredModels(listOf(model("cached")), 1L)

        assertThat(repo.findById("cached")).isNull()
        repo.start(this)
        advanceUntilIdle()
        assertThat(repo.findById("cached")).isNotNull()
        assertThat(repo.find("built")).isNotNull()
        coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
    }
}
