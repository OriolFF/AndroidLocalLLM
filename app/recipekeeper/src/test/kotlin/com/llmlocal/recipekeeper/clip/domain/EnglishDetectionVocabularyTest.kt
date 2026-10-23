package com.llmlocal.recipekeeper.clip.domain

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** English must be fully covered by the text-tier detector, alongside ES/CA/EL (user requirement 2026-10-23). */
class EnglishDetectionVocabularyTest {

    private val quantity = RecipeKeeperClipConfig.quantityLine
    private val ingredients = RecipeKeeperClipConfig.ingredientsWord
    private val instructions = RecipeKeeperClipConfig.instructionsWord

    @Test
    fun `english quantity lines match`() {
        listOf(
            "2 cups flour", "½ tsp salt", "1/2 onion, finely chopped", "3 eggs", "200 g butter", "1 fl oz lime juice",
            "2 sprigs thyme", "1 sheet puff pastry", "1 head garlic", "2 stalks celery", "1 knob of butter", "a pinch of salt",
            "two eggs", "half a lemon", "a handful of parsley", "a few basil leaves", "one large onion", "1 large egg, beaten",
            "- 4 chicken thighs", "• 1 can chopped tomatoes", "2 tbsp olive oil", "1 quart chicken stock", "a splash of milk",
        ).forEach { line -> assertWithMessage(line).that(quantity.containsMatchIn(line)).isTrue() }
    }

    @Test
    fun `prose and promo lines do not match`() {
        listOf(
            "One of the best cakes I have ever made, and my family agrees completely!",
            "A quick note before we start: preheat the oven while you gather everything.",
            "Share this recipe with your friends.",
        ).forEach { line -> assertWithMessage(line).that(quantity.containsMatchIn(line)).isFalse() }
    }

    @Test
    fun `english heading words match`() {
        listOf("Ingredients", "What you'll need", "You will need", "Shopping list").forEach {
            assertWithMessage(it).that(ingredients.containsMatchIn(it)).isTrue()
        }
        listOf("Instructions", "Method", "Directions", "Step by step", "How to make it", "Cooking instructions", "What to do").forEach {
            assertWithMessage(it).that(instructions.containsMatchIn(it)).isTrue()
        }
    }
}
