package com.llmlocal.recipekeeper.recipes.presentation.previews

import com.llmlocal.core.llm.download.DownloadState
import com.llmlocal.core.llm.download.PerModelProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.recipekeeper.recipes.domain.ClippedRecipe
import com.llmlocal.recipekeeper.recipes.domain.RecipeOrigin
import com.llmlocal.recipekeeper.recipes.domain.RecipeSummary

/** Hand-built data shared by the `@Preview`s in this package. */
internal object SampleData {

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val CLIPPED_AT = 1_760_745_600_000L // 2026-10-18

    val recipe = ClippedRecipe(
        id = "r1",
        title = "Roasted tomato and white bean soup",
        description = "A weeknight soup that tastes like it simmered all afternoon.",
        servings = "4",
        prepMinutes = 15,
        cookMinutes = 40,
        totalMinutes = 55,
        ingredients = listOf(
            "1 kg ripe tomatoes, halved",
            "2 tbsp olive oil",
            "1 onion, roughly chopped",
            "3 cloves garlic, unpeeled",
            "400 g tin cannellini beans, drained",
            "750 ml vegetable stock",
            "A handful of basil leaves",
            "Salt and black pepper",
        ),
        steps = listOf(
            "Heat the oven to 200 °C. Toss the tomatoes, onion and garlic with the oil on a tray; roast for 30 minutes until blistered.",
            "Squeeze the garlic from its skins. Tip everything into a pan with the stock and simmer for 10 minutes.",
            "Blend until smooth, stir in the beans and basil, then season to taste.",
            "Serve with toasted sourdough and a drizzle of oil.",
        ),
        notes = "Freezes well for up to three months. Add a parmesan rind to the pan for extra depth.",
        sourceUrl = "https://example.com/recipes/roasted-tomato-soup",
        sourceHost = "example.com",
        pictures = emptyList(),
        truncated = false,
        clippedAt = CLIPPED_AT,
        checkedIngredients = setOf(0, 1),
    )

    /** Same recipe with (non-existent) picture paths so the hero/pager layouts render. */
    val recipeWithPictures = recipe.copy(
        pictures = listOf("/data/preview/hero.jpg", "/data/preview/step1.jpg", "/data/preview/step2.jpg"),
        checkedIngredients = emptySet(),
    )

    /** Clipped offline: no pictures came through, the detail offers a retry. */
    val picturesFailedRecipe = recipe.copy(
        id = "r3",
        picturesFailed = true,
        pictureCandidates = listOf("https://example.com/img/soup-hero.jpg", "https://example.com/img/soup-bowl.jpg"),
        checkedIngredients = emptySet(),
    )

    val truncatedRecipe = recipe.copy(
        id = "r2",
        title = "Overnight oats with roasted plums",
        steps = recipe.steps.take(2),
        truncated = true,
        notes = null,
    )

    val favouriteRecipe = recipe.copy(isFavourite = true)

    val createdRecipe = recipe.copy(
        id = "r6",
        title = "Grandma's lentil soup",
        origin = RecipeOrigin.CREATED,
        sourceUrl = "",
        sourceHost = "",
        pictures = emptyList(),
        truncated = false,
        editedAt = CLIPPED_AT + DAY_MS,
    )

    val imagesRecipe = recipe.copy(
        id = "r7",
        title = "Aunt Maria's paella",
        origin = RecipeOrigin.IMAGES,
        sourceUrl = "",
        sourceHost = "",
        pictures = emptyList(),
        truncated = false,
    )

    val imagesSummary = RecipeSummary(
        "r7", imagesRecipe.title, "", 50, null, false, CLIPPED_AT - 2 * DAY_MS, origin = RecipeOrigin.IMAGES,
    )

    val editedRecipe = recipe.copy(editedAt = CLIPPED_AT + DAY_MS, isFavourite = true)

    val createdSummary = RecipeSummary(
        "r6", createdRecipe.title, "", 45, null, false, CLIPPED_AT - 4 * DAY_MS,
        isFavourite = true, origin = RecipeOrigin.CREATED,
    )

    val favouriteSummaries: List<RecipeSummary> = listOf(
        RecipeSummary("r1", recipe.title, "example.com", 55, null, false, CLIPPED_AT, isFavourite = true),
        createdSummary,
    )

    val summaries: List<RecipeSummary> = listOf(
        RecipeSummary("r1", recipe.title, "example.com", 55, null, false, CLIPPED_AT, isFavourite = true),
        RecipeSummary("r2", truncatedRecipe.title, "breakfast.blog", 20, null, true, CLIPPED_AT - DAY_MS),
        RecipeSummary("r3", "Pan con tomate", "tapas.example", 10, null, false, CLIPPED_AT - 2 * DAY_MS),
        RecipeSummary("r4", "Slow-cooked lamb shoulder with anchovies, rosemary and garlic", "sunday.example", 240, null, false, CLIPPED_AT - 3 * DAY_MS),
        RecipeSummary("r5", "Miso brown butter cookies", "bakes.example", null, null, false, CLIPPED_AT - 5 * DAY_MS),
        createdSummary,
    )

    val downloadingModelName: String = LlmModelCatalog.DEFAULT_MODEL.displayName

    val downloading = PerModelProgress(
        modelId = LlmModelCatalog.DEFAULT_MODEL.id,
        state = DownloadState.RUNNING,
        bytesRead = 1_100L * 1024 * 1024,
        totalBytes = LlmModelCatalog.DEFAULT_MODEL.sizeBytes,
        speedBytesPerSec = 12L * 1024 * 1024,
        etaSeconds = 130,
        updatedAt = CLIPPED_AT,
    )

    val streamedPartial: String = """
        Title: Roasted tomato and white bean soup
        Description: A weeknight soup that tastes like it simmered all afternoon.
        Servings: 4
        PrepMinutes: 15
        CookMinutes: 40
        Ingredients:
        - 1 kg ripe tomatoes, halved
        - 2 tbsp olive oil
        - 1 onion, roughly chopped
        - 3 cloves gar
    """.trimIndent()

    val streamedComplete: String = """
        Title: Roasted tomato and white bean soup
        Description: A weeknight soup that tastes like it simmered all afternoon.
        Servings: 4
        PrepMinutes: 15
        CookMinutes: 40
        Ingredients:
        - 1 kg ripe tomatoes, halved
        - 2 tbsp olive oil
        - 1 onion, roughly chopped
        - 3 cloves garlic, unpeeled
        Steps:
        1. Roast the tomatoes, onion and garlic for 30 minutes.
        2. Simmer with the stock for 10 minutes, then blend.
        3. Stir in the beans and basil; season.
        Notes:
        Freezes well for up to three months.

    """.trimIndent()
}
