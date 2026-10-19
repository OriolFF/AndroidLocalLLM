package com.llmlocal.recipekeeper.clip.domain

/**
 * Incremental parse of the model's streamed output, for the
 * skeleton-that-fills-in on the extraction screen. Only completed lines
 * are promoted into fields; whatever is still being generated sits in
 * [tail] and renders dimmed with the streaming cursor.
 */
data class RecipeDraft(
    val title: String?,
    val description: String?,
    val servings: String?,
    val prepMinutes: Int?,
    val cookMinutes: Int?,
    val ingredients: List<String>,
    val steps: List<String>,
    val notes: String?,
    val tail: String,
)
