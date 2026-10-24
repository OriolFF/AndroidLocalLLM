package com.llmlocal.feature.webclipper.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.llmlocal.feature.webclipper.data.CacheDirTempPictureStore
import com.llmlocal.feature.webclipper.domain.ImagePolicy
import com.llmlocal.feature.webclipper.fakes.FakeLocalImageImporter
import com.llmlocal.feature.webclipper.fakes.TestDispatchers
import com.llmlocal.feature.webclipper.fakes.preparedImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PrepareImagesUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val dir get() = File(temp.root, "webclipper")
    private val files get() = dir.listFiles()?.map { it.name }.orEmpty()

    private fun TestScope.useCase(importer: FakeLocalImageImporter) = PrepareImagesUseCase(
        importer = importer,
        tempPictures = CacheDirTempPictureStore(dir),
        dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
    )

    private val policy = ImagePolicy(enabled = true, maxImages = 3, tileImages = false)

    @Test
    fun `order is kept and every file lives in the temp store`() = runTest {
        val result = useCase(FakeLocalImageImporter())(listOf("a", "b", "c"), policy)

        assertThat(result.images.map { it.sha256 }).containsExactly("sha-a", "sha-b", "sha-c").inOrder()
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.capped).isFalse()
        assertThat(result.images.map { it.file.parentFile }).containsExactly(dir, dir, dir)
        assertThat(files).hasSize(3)
        assertThat(result.images.map { it.tileOf }).containsExactly(null, null, null)
    }

    @Test
    fun `more pictures than the cap are dropped and flagged, without importing the extras`() = runTest {
        val importer = FakeLocalImageImporter()

        val result = useCase(importer)(listOf("a", "b", "c", "d", "e"), policy)

        assertThat(result.images.map { it.sha256 }).containsExactly("sha-a", "sha-b", "sha-c").inOrder()
        assertThat(result.capped).isTrue()
        assertThat(importer.imported).containsExactly("a", "b", "c")
        assertThat(files).hasSize(3)
    }

    @Test
    fun `a picture that cannot be read is skipped and counted, the rest goes on`() = runTest {
        val importer = FakeLocalImageImporter(failing = setOf("b"))

        val result = useCase(importer)(listOf("a", "b", "c"), policy)

        assertThat(result.images.map { it.sha256 }).containsExactly("sha-a", "sha-c").inOrder()
        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.capped).isFalse()
        assertThat(files).hasSize(2)
    }

    @Test
    fun `an unreadable batch yields no images and every skip`() = runTest {
        val result = useCase(FakeLocalImageImporter(failing = setOf("a", "b")))(listOf("a", "b"), policy)

        assertThat(result.images).isEmpty()
        assertThat(result.skipped).isEqualTo(2)
        assertThat(files).isEmpty()
    }

    @Test
    fun `a failed pick does not use up the cap`() = runTest {
        val result = useCase(FakeLocalImageImporter(failing = setOf("a")))(listOf("a", "b", "c", "d"), policy)

        assertThat(result.images).hasSize(3)
        assertThat(result.skipped).isEqualTo(1)
        assertThat(result.capped).isFalse()
    }

    @Test
    fun `tall strips are tiled when the policy asks, tiles keep their source index and the strip is removed`() = runTest {
        val importer = FakeLocalImageImporter(sizes = mapOf("tall" to (100 to 400)), tilesPerTall = 2)

        val result = useCase(importer)(listOf("a", "tall"), policy.copy(tileImages = true))

        assertThat(result.images).hasSize(3)
        assertThat(result.images.map { it.tileOf }).containsExactly(null, 1, 1).inOrder()
        assertThat(result.images.map { it.file.name }).containsNoDuplicates()
        // the untiled strip must not linger in the temp dir
        assertThat(files).hasSize(3)
        assertThat(result.images.drop(1).map { it.sha256 }.all { it.startsWith("tile-") }).isTrue()
    }

    @Test
    fun `tall strips stay whole when tiling is off or the picture is not tall`() = runTest {
        val importer = FakeLocalImageImporter(sizes = mapOf("tall" to (100 to 400), "ok" to (100 to 240)))

        val off = useCase(importer)(listOf("tall"), policy.copy(tileImages = false))
        val notTall = useCase(importer)(listOf("ok"), policy.copy(tileImages = true))

        assertThat(off.images.single().sha256).isEqualTo("sha-tall")
        assertThat(notTall.images.single().sha256).isEqualTo("sha-ok")
    }

    @Test
    fun `the cap applies after tiling and the surplus tiles are deleted`() = runTest {
        val importer = FakeLocalImageImporter(sizes = mapOf("tall" to (100 to 400)), tilesPerTall = 4)

        val result = useCase(importer)(listOf("tall"), policy.copy(tileImages = true, maxImages = 3))

        assertThat(result.images).hasSize(3)
        assertThat(result.capped).isTrue()
        assertThat(files).hasSize(3)
    }

    @Test
    fun `discard deletes the prepared files`() = runTest {
        val store = CacheDirTempPictureStore(dir)
        val prepared = listOf(preparedImage(dir, 0), preparedImage(dir, 1))

        DiscardPreparedImagesUseCase(store)(prepared)

        assertThat(files).isEmpty()
    }
}
