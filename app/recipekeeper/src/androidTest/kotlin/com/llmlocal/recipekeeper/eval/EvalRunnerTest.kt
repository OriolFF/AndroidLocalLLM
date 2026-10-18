package com.llmlocal.recipekeeper.eval

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.llm.di.REAL_ENGINE_QUALIFIER
import com.llmlocal.core.llm.engine.LiteRtLlmEngine
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.engine.LlmRequest
import com.llmlocal.core.llm.engine.LlmToken
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

/**
 * Path (b) of the WS7 eval runner: runs every prompt in a directory on the
 * device through the app's own [LiteRtLlmEngine] (CPU in debug builds)
 * against the model Recipe Keeper already downloaded to `files/llm/`, and
 * writes `<fixture>.txt` + `<fixture>.timing.json` next to each other under
 * `filesDir/eval/outputs/`. `tools/eval/run_local_model.sh --device`
 * pushes the prompts, runs this class and pulls the outputs back.
 *
 * Instrumentation arguments:
 *  - `promptsDir` — directory of `<fixture>.txt` prompts readable by the
 *    app (default `/data/local/tmp/eval/prompts`, where `adb push` puts them)
 *  - `modelPath` — optional `.litertlm` file to use instead of the model
 *    selected in the app (e.g. one pushed to `/data/local/tmp/eval/`);
 *    a fresh CPU [LiteRtLlmEngine] is built around it
 *  - `maxOutputTokens` (default 1024), `temperature` (default 0.2) — the
 *    values the extraction ViewModel sets on [LlmRequest]
 *
 * The prompt is sent as a single user turn, exactly what
 * `LlmTaskRepositoryImpl` does. This test is not part of the unit-test
 * run; it needs a real arm64 device with the model (the shared emulator
 * has neither the space nor the model).
 */
@RunWith(AndroidJUnit4::class)
class EvalRunnerTest {

    private val tag = "EvalRunnerTest"

    @Test
    fun runAllPrompts() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val context = instrumentation.targetContext

        val promptsDir = File(args.getString("promptsDir") ?: DEFAULT_PROMPTS_DIR)
        val maxOutputTokens = args.getString("maxOutputTokens")?.toIntOrNull() ?: DEFAULT_MAX_OUTPUT_TOKENS
        val temperature = args.getString("temperature")?.toDoubleOrNull() ?: DEFAULT_TEMPERATURE
        val modelPath = args.getString("modelPath")

        val prompts = promptsDir.listFiles { f -> f.isFile && f.extension == "txt" }.orEmpty().sortedBy { it.name }
        assumeTrue("no prompts in $promptsDir — push them first (tools/eval/run_local_model.sh --device)", prompts.isNotEmpty())

        val engine: LlmEngine = if (modelPath != null) {
            LiteRtLlmEngine(
                context = context,
                modelPathProvider = { File(modelPath) },
                maxNumTokens = LiteRtLlmEngine.DEFAULT_MAX_NUM_TOKENS,
                backend = Backend.CPU(),
            )
        } else {
            GlobalContext.get().get(LlmEngine::class, named(REAL_ENGINE_QUALIFIER))
        }

        val initStarted = SystemClock.elapsedRealtime()
        when (val init = engine.initialize()) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> fail("engine.initialize() failed: ${init.error} — is the model downloaded in Recipe Keeper's model management?")
        }
        val initMs = SystemClock.elapsedRealtime() - initStarted

        val outDir = File(context.filesDir, OUTPUT_DIR).apply { mkdirs() }
        var failures = 0
        prompts.forEachIndexed { index, promptFile ->
            val name = promptFile.nameWithoutExtension
            val prompt = promptFile.readText()
            Log.i(tag, "[${index + 1}/${prompts.size}] $name: ${prompt.length} chars")

            val output = StringBuilder()
            var firstTokenAt: Long? = null
            var error: Throwable? = null
            val started = SystemClock.elapsedRealtimeNanos()
            try {
                engine.generateStream(LlmRequest(prompt, maxOutputTokens = maxOutputTokens, temperature = temperature))
                    .collect { token ->
                        when (token) {
                            is LlmToken.Partial -> {
                                if (firstTokenAt == null) firstTokenAt = SystemClock.elapsedRealtimeNanos()
                                output.append(token.text)
                            }
                            LlmToken.Done -> Unit
                            is LlmToken.Error -> error = token.error
                        }
                    }
            } catch (t: Throwable) {
                error = t
            }
            val finished = SystemClock.elapsedRealtimeNanos()
            if (error != null) failures++

            val timing = JSONObject()
                .put("fixture", name)
                .put("runner", "EvalRunnerTest (LiteRtLlmEngine on device, src/androidTest)")
                .put("model", modelPath ?: "app selection (files/llm)")
                .put("backend", if (modelPath != null) "CPU" else "app build-type default (debug = CPU)")
                .put("host", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.SUPPORTED_ABIS.firstOrNull()}), Android ${Build.VERSION.RELEASE}")
                .put("startedAt", java.time.Instant.now().toString())
                .put("promptChars", prompt.length)
                .put("outputChars", output.length)
                .put("ttftMs", firstTokenAt?.let { (it - started) / 1_000_000 })
                .put("totalMs", (finished - started) / 1_000_000)
                .put("engineInitS", initMs / 1000.0)
                .put("sampler", JSONObject().put("temperature", temperature).put("maxOutputTokens", maxOutputTokens)
                    .put("maxNumTokens", LiteRtLlmEngine.DEFAULT_MAX_NUM_TOKENS))
                .put("error", error?.toString())
            File(outDir, "$name.txt").writeText(output.toString())
            File(outDir, "$name.timing.json").writeText(timing.toString(2) + "\n")
            Log.i(tag, "  → ${if (error == null) "ok" else "ERROR $error"}: ${output.length} chars, ttft ${timing.opt("ttftMs")} ms, total ${timing.opt("totalMs")} ms")
        }
        Log.i(tag, "done: ${prompts.size - failures}/${prompts.size} ok → ${outDir.absolutePath}")
        assertTrue("$failures prompt(s) failed; see ${outDir.absolutePath}/*.timing.json", failures == 0)
    }

    companion object {
        const val DEFAULT_PROMPTS_DIR = "/data/local/tmp/eval/prompts"
        const val OUTPUT_DIR = "eval/outputs"
        const val DEFAULT_MAX_OUTPUT_TOKENS = 1024
        const val DEFAULT_TEMPERATURE = 0.2
    }
}
