package com.llmlocal.recipekeeper.recipes.domain

/** Pure validation of a [RecipeEdit]; used by `SaveRecipeUseCase`. */
object RecipeEditValidator {

    /**
     * [EditorError.TitleRequired] when the title is blank; [EditorError.NeedContent]
     * when no ingredient and no step has non-blank text. Length caps are the
     * ViewModel's job (enforced on input), not reported here.
     */
    fun validate(edit: RecipeEdit): Set<EditorError> = buildSet {
        if (edit.title.isBlank()) add(EditorError.TitleRequired)
        if (edit.ingredients.none { it.text.isNotBlank() } && edit.steps.none { it.text.isNotBlank() }) {
            add(EditorError.NeedContent)
        }
    }
}
