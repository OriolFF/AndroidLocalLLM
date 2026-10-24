package com.llmlocal.recipekeeper.eval

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.di.REAL_ENGINE_QUALIFIER
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.engine.LlmImage
import com.llmlocal.core.llm.engine.LlmRequest
import com.llmlocal.core.llm.engine.LlmToken
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperParser
import com.llmlocal.recipekeeper.clip.domain.RecipeKeeperPromptBuilder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

/**
 * I1 device spike: sends pictures through the app's own [LlmEngine] (vision
 * on CPU in debug builds) against the model in `files/llm/`.
 *
 * Reads `*.png`/`*.jpg` from `imagesDir` (default `/data/local/tmp/eval/vision`),
 * sorted by name. Runs (1) the first image alone at each budget in `budgets`
 * (default `280,560`; 560 must be clamped to 280 by the engine), then (2) every
 * image together at the first budget. Logs TTFT, total time, native heap and
 * peak PSS (sampled every 250 ms), and whether the output parses with
 * [RecipeKeeperParser]. Not part of the unit-test run; install the test APK by
 * hand (never `connectedDebugAndroidTest`).
 */
@RunWith(AndroidJUnit4::class)
class VisionSpikeTest {

    private val tag = "VisionSpikeTest"

    @Test
    fun imagesThroughTheEngine() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val dir = File(args.getString("imagesDir") ?: "/data/local/tmp/eval/vision")
        val budgets = (args.getString("budgets") ?: "280,560").split(',').map { it.trim().toInt() }
        val files = dir.listFiles { f -> f.extension.lowercase() in setOf("png", "jpg", "jpeg") }
            .orEmpty().sortedBy { it.name }
        assumeTrue("no images in $dir", files.isNotEmpty())

        val engine = GlobalContext.get().get<LlmEngine>(LlmEngine::class, named(REAL_ENGINE_QUALIFIER))
        logMemory("before init")
        val initStart = SystemClock.elapsedRealtime()
        when (val init = engine.initialize()) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> fail("initialize failed: ${init.error}")
        }
        Log.i(tag, "init ${SystemClock.elapsedRealtime() - initStart} ms, capabilities=${engine.capabilities()}")
        logMemory("after init")
        assertTrue("engine reports no vision", engine.capabilities().vision)

        fun image(f: File) = LlmImage(
            bytes = f.readBytes(),
            mimeType = if (f.extension.lowercase() == "png") "image/png" else "image/jpeg",
        )

        val runs = budgets.map { b -> "single@$b" to (listOf(files.first()) to b) } +
            listOf("all${files.size}@${budgets.first()}" to (files to budgets.first()))
        var parsedOk = 0
        for ((name, run) in runs) {
            val (images, budget) = run
            val request = LlmRequest(
                prompt = "Extract the recipe from these pictures.",
                systemInstruction = RecipeKeeperPromptBuilder.SYSTEM,
                maxOutputTokens = 1024,
                temperature = 0.2,
                images = images.map(::image),
                visualTokenBudget = budget,
            )
            val peakPss = AtomicInteger(0)
            val sampling = AtomicBoolean(true)
            val sampler = thread(isDaemon = true) {
                while (sampling.get()) {
                    val info = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
                    peakPss.accumulateAndGet(info.totalPss, ::maxOf)
                    Thread.sleep(250)
                }
            }
            val out = StringBuilder()
            var ttft: Long? = null
            var error: Throwable? = null
            val start = SystemClock.elapsedRealtime()
            engine.generateStream(request).collect { t ->
                when (t) {
                    is LlmToken.Partial -> {
                        if (ttft == null) ttft = SystemClock.elapsedRealtime() - start
                        out.append(t.text)
                    }
                    LlmToken.Done -> Unit
                    is LlmToken.Error -> error = t.error
                }
            }
            val total = SystemClock.elapsedRealtime() - start
            sampling.set(false)
            sampler.join()
            val parsed = RecipeKeeperParser().parse(out.toString())
            if (parsed != null) parsedOk++
            Log.i(tag, "RUN $name images=${images.size} ttft=${ttft}ms total=${total}ms chars=${out.length} " +
                "peakPssKb=${peakPss.get()} parsed=${parsed != null} error=$error")
            Log.i(tag, "OUTPUT $name:\n$out")
            logMemory("after $name")
            File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "eval/vision-$name.txt")
                .apply { parentFile?.mkdirs() }.writeText(out.toString())
            assertTrue("$name failed: $error", error == null)
        }
        Log.i(tag, "done: parsed $parsedOk/${runs.size}")
        Unit
    }

    private fun logMemory(label: String) {
        val info = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        Log.i(tag, "MEM $label nativeHeapKb=${Debug.getNativeHeapAllocatedSize() / 1024} pssKb=${info.totalPss}")
    }
}
