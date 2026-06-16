package com.llmlocal.core.llm.engine

import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.model.Ingredient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [LlmEngine] implementation that does **not** require any model download.
 *
 * Used in "Demo mode": the UI shows a toggle that lets the user try the
 * full recipe-generation flow with a canned, deterministic response. This
 * makes the template usable end-to-end on a fresh install without waiting
 * for a multi-gigabyte model download.
 *
 * The canned response is generated from the prompt itself: we look for an
 * "Ingredients:" block in the prompt and emit a small, structured recipe
 * using those ingredients. The text is emitted as a stream of small
 * `LlmToken.Partial` chunks (with a short delay between them) so the
 * rest of the app behaves exactly as it would with a real LLM.
 */
class FakeLlmEngine : LlmEngine {

    override suspend fun initialize(): Outcome<Unit> = Outcome.Success(Unit)

    override fun cancel() = Unit

    override fun generateStream(prompt: String): Flow<LlmToken> = flow {
        val ingredients = parseIngredientsFromPrompt(prompt)
        val text = cannedRecipe(ingredients)
        // Stream the text in small word-sized chunks with a small delay
        // so the UI animation looks like a real LLM response.
        val parts = text.split(" ")
        for ((index, part) in parts.withIndex()) {
            emit(LlmToken.Partial(if (index == 0) part else " $part"))
            delay(STREAM_DELAY_MS)
        }
        emit(LlmToken.Done)
    }.flowOn(Dispatchers.Default)

    /**
     * Extracts a comma-separated ingredient list from the prompt. The
     * prompt format is fixed by [com.llmlocal.core.llm.prompt.RecipePromptBuilder].
     */
    private fun parseIngredientsFromPrompt(prompt: String): List<String> {
        // The user prompt is the last section, after a "User:" line. We
        // grab everything after the first "Ingredients:" header if present,
        // otherwise the whole thing after "User:".
        val userSection = prompt.substringAfter("User:", prompt)
        val afterIngredients = userSection.substringAfter("Ingredients:", userSection)
        return afterIngredients
            .lineSequence()
            .map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .filter { it.isNotBlank() }
            .toList()
            .ifEmpty {
                listOf("your ingredients")
            }
    }

    private fun cannedRecipe(ingredients: List<String>): String {
        val prettyList = ingredients.joinToString(", ")
        return buildString {
            append("Title: Quick Skillet with ").append(ingredients.firstOrNull() ?: "Everything").append("\n\n")
            append("Ingredients:\n")
            ingredients.forEach { append("- ").append(it).append("\n") }
            if (ingredients.isEmpty()) append("- your ingredients\n")
            append("\nSteps:\n")
            append("1) Prep (about 5 min): ").append(prettyList).append(". Wash and chop the produce; measure the rest.\n")
            append("2) Heat a heavy skillet over medium heat for 1 minute. Add a splash of oil.\n")
            append("3) Aromatics first (about 2 min): add the firmest items from your list and a pinch of salt.\n")
            append("4) Main ingredients (about 8 min): add the rest of the ").append(prettyList)
            append(", stirring occasionally. If anything looks dry, add a tablespoon of water.\n")
            append("5) Finish (about 2 min): taste, adjust salt, and turn off the heat. A squeeze of citrus or a pinch of herbs lifts the whole dish.\n")
            append("6) Plate and rest for 1 minute before serving — this lets the flavours settle.\n")
            append("\nNotes:\n")
            append("- This is a Demo response — the on-device model wasn't loaded. ")
            append("Toggle off Demo mode in the banner above to download the real LLM and get a tailored recipe.\n")
            append("- Total time: about 18 minutes. Serves 2 generously.\n")
        }
    }

    companion object {
        private const val STREAM_DELAY_MS = 25L
    }
}
