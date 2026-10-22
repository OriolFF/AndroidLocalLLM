package com.llmlocal.core.web

import com.google.common.truth.Truth.assertThat
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Bitmap decoding needs the Android runtime, so only the pure parts are
 * covered here: the sub-sampling arithmetic, argument validation, and
 * that a failed fetch leaves no file behind.
 */
class ImageDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private val downloader = ImageDownloader(OkHttpClient(), dispatchers)

    @Test
    fun `inSampleSize is the largest power of two that keeps the long edge at or above the bound`() {
        assertThat(downloader.computeInSampleSize(1000, 800, 1280)).isEqualTo(1)
        assertThat(downloader.computeInSampleSize(1280, 1280, 1280)).isEqualTo(1)
        assertThat(downloader.computeInSampleSize(2560, 1920, 1280)).isEqualTo(2)
        assertThat(downloader.computeInSampleSize(4000, 3000, 1280)).isEqualTo(2)
        assertThat(downloader.computeInSampleSize(3000, 6000, 1280)).isEqualTo(4)
        assertThat(downloader.computeInSampleSize(100, 10000, 800)).isEqualTo(8)
    }

    @Test
    fun `blank url and non-positive bound fail without touching the file system`() = runTest {
        val target = File(tmp.root, "out/hero.jpg")

        val blank = downloader.download("  ", null, target, 1280)
        val bound = downloader.download("https://example.com/a.jpg", null, target, 0)

        assertThat(blank).isInstanceOf(Outcome.Failure::class.java)
        assertThat(bound).isInstanceOf(Outcome.Failure::class.java)
        assertThat(target.parentFile.exists()).isFalse()
    }

    @Test
    fun `a failed fetch returns Failure and leaves no part or target file`() = runTest {
        val target = File(tmp.root, "recipes/42/hero.jpg")

        // Port 9 (discard) is closed on a dev machine: connection refused, fast.
        val outcome = downloader.download("http://127.0.0.1:9/hero.jpg", "http://127.0.0.1:9/", target, 1280)

        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat((outcome as Outcome.Failure).error).isInstanceOf(IOException::class.java)
        assertThat(target.exists()).isFalse()
        assertThat(File(target.parentFile, "hero.jpg.part").exists()).isFalse()
        assertThat(target.parentFile.exists()).isTrue() // created up front, harmless
    }

    @Test
    fun `request carries referer and cookies only when present`() {
        val full = downloader.buildRequest("https://cdn.example/a.jpg", "https://example.com/r", "a=1; b=2")
        assertThat(full.header("Referer")).isEqualTo("https://example.com/r")
        assertThat(full.header("Cookie")).isEqualTo("a=1; b=2")
        assertThat(full.header("User-Agent")).isNotEmpty()

        val bare = downloader.buildRequest("https://cdn.example/a.jpg", " ", null)
        assertThat(bare.header("Referer")).isNull()
        assertThat(bare.header("Cookie")).isNull()
    }
}
