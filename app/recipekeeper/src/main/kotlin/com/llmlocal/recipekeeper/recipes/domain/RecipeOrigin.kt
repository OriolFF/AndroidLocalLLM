package com.llmlocal.recipekeeper.recipes.domain

/**
 * Where a recipe came from: clipped from a web page, written in the editor,
 * read from pictures, or dictated. Stored as its name in a text column; an
 * unknown name reads back as [CLIPPED], so adding a value needs no migration.
 */
enum class RecipeOrigin { CLIPPED, CREATED, IMAGES, VOICE }

/** Only a clipped recipe has a page behind it: Open original and Re-extract exist for it alone. */
val RecipeOrigin.hasSource: Boolean get() = this == RecipeOrigin.CLIPPED
