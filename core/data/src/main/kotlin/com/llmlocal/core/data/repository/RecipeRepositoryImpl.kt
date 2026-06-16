package com.llmlocal.core.data.repository

import com.llmlocal.core.domain.model.RecipeEvent
import com.llmlocal.core.domain.repository.RecipeRepository
import com.llmlocal.core.llm.engine.LlmEngine
import com.llmlocal.core.llm.engine.LlmToken
import com.llmlocal.core.llm.parser.RecipeParser
import com.llmlocal.core.llm.prompt.RecipePromptBuilder
import com.llmlocal.core.model.Ingredient
import com.llmlocal.core.model.Recipe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.transform

/**
 * [RecipeRepository] implementation that uses the on-device LLM.
 *
 * It wires together:
 *  - [LlmEngine]        — generates the text (injected per call so the
 *    caller can swap between the real engine and a fake / demo one)
 *  - [RecipePromptBuilder] — formats the prompt
 *  - [RecipeParser]     — extracts the structured [Recipe]
 *
 * The streamed text is *cumulative* — we keep a running buffer and re-parse
 * it on every new chunk. The final [RecipeEvent.Complete] is emitted when
 * the LLM reports `done` and we have a fully-formed parsed recipe.
 */
class RecipeRepositoryImpl(
    private val promptBuilder: RecipePromptBuilder,
    private val parser: RecipeParser,
) : RecipeRepository {

    override fun generateRecipeStream(
        engine: LlmEngine,
        ingredients: List<Ingredient>,
    ): Flow<RecipeEvent> {
        val prompt = buildString {
            append(promptBuilder.systemInstruction())
            append("\n\n")
            append(promptBuilder.userPrompt(ingredients))
        }

        val buffer = StringBuilder()

        return engine.generateStream(prompt)
            .transform { token ->
                when (token) {
                    is LlmToken.Partial -> {
                        buffer.append(token.text)
                        emit(RecipeEvent.Token(token.text))
                    }
                    LlmToken.Done -> {
                        val finalRecipe: Recipe = parser.parse(buffer.toString())
                            ?: Recipe(
                                title = "Untitled",
                                ingredients = emptyList(),
                                steps = listOf(buffer.toString()),
                                notes = "Could not parse structured recipe from model output.",
                            )
                        emit(RecipeEvent.Complete(finalRecipe))
                    }
                    is LlmToken.Error -> {
                        emit(RecipeEvent.Failed(token.error))
                    }
                }
            }
            .catch { e -> emit(RecipeEvent.Failed(e)) }
    }
}
