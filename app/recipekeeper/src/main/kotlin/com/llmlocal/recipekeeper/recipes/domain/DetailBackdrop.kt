package com.llmlocal.recipekeeper.recipes.domain

import kotlinx.coroutines.flow.Flow

/**
 * How the recipe detail screen colours its background from the recipe's
 * first picture (an "Appearance" setting). Pure domain value; the
 * presentation layer maps it to the design system's backdrop style. Every
 * option is static (no animation) so none costs battery while on screen.
 */
enum class DetailBackdrop {
    /** Plain theme surface. */
    NONE,

    /** A flat wash of the picture's main colour. */
    TINT,

    /** The picture itself, blurred and dimmed. */
    PHOTO,
    ;

    companion object {
        val DEFAULT: DetailBackdrop = TINT

        /** [valueOf] that tolerates unknown or stale stored names (e.g. the removed animated styles). */
        fun fromStorageName(name: String?): DetailBackdrop =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/** Persisted choice of [DetailBackdrop]. */
interface DetailBackdropRepository {
    /** The saved backdrop, [DetailBackdrop.DEFAULT] until the user picks one. */
    val backdrop: Flow<DetailBackdrop>

    suspend fun set(backdrop: DetailBackdrop)
}
