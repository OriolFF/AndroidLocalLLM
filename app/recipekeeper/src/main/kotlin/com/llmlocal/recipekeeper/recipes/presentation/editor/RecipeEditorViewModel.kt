package com.llmlocal.recipekeeper.recipes.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.recipekeeper.recipes.domain.EditSection
import com.llmlocal.recipekeeper.recipes.domain.EditableLine
import com.llmlocal.recipekeeper.recipes.domain.EditorError
import com.llmlocal.recipekeeper.recipes.domain.RecipeEdit
import com.llmlocal.recipekeeper.recipes.domain.RecipeEditValidator
import com.llmlocal.recipekeeper.recipes.domain.SaveResult
import com.llmlocal.recipekeeper.recipes.domain.usecase.AddRecipePictureUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.DiscardRecipeEditsUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.LoadRecipeForEditUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.SaveRecipeUseCase
import com.llmlocal.recipekeeper.recipes.domain.usecase.SplitPastedLinesUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * MVI ViewModel of the recipe editor (`recipe/new`, `recipe/{id}/edit`).
 *
 * The draft lives in [state]; every change is mirrored into [savedStateHandle]
 * under [DRAFT_KEY] (JSON, see [RecipeEditorDraftCodec]) so it survives process
 * death, and `init` restores from it instead of reloading the recipe. Temp
 * pictures are grouped under a per-editor session id kept under [SESSION_KEY].
 *
 * @param recipeId `null` creates a new recipe
 * @param savedStateHandle holds the draft across process death
 */
class RecipeEditorViewModel(
    private val recipeId: String?,
    private val savedStateHandle: SavedStateHandle,
    private val loadForEdit: LoadRecipeForEditUseCase,
    private val saveRecipe: SaveRecipeUseCase,
    private val addPicture: AddRecipePictureUseCase,
    private val discardEdits: DiscardRecipeEditsUseCase,
    private val splitPasted: SplitPastedLinesUseCase,
    private val dispatchers: DispatcherProvider,
    private val maxPictures: Int = 8,
) : ViewModel() {

    private val mode = if (recipeId == null) EditorMode.CREATE else EditorMode.EDIT

    private val _state = MutableStateFlow(RecipeEditorState(mode = mode, maxPictures = maxPictures))
    val state: StateFlow<RecipeEditorState> = _state.asStateFlow()

    private val _effects = Channel<RecipeEditorEffect>(Channel.BUFFERED)
    val effects: Flow<RecipeEditorEffect> = _effects.receiveAsFlow()

    private val sessionId: String = savedStateHandle.get<String>(SESSION_KEY)
        ?: UUID.randomUUID().toString().also { savedStateHandle[SESSION_KEY] = it }

    init {
        val restored = savedStateHandle.get<String>(DRAFT_KEY)?.let(RecipeEditorDraftCodec::decode)
        if (restored != null) {
            _state.update { it.copy(edit = restored.first, initial = restored.second, isLoading = false) }
        } else {
            viewModelScope.launch {
                val loaded = loadForEdit(recipeId)
                _state.update { it.copy(edit = loaded, initial = loaded, isLoading = false) }
                if (loaded != null) {
                    persist()
                    if (mode == EditorMode.CREATE) _effects.send(RecipeEditorEffect.FocusTitle)
                }
            }
        }
    }

    /** Single dispatch point for every user action. */
    fun onIntent(intent: RecipeEditorIntent) {
        when (intent) {
            is RecipeEditorIntent.TitleChanged -> edit { it.copy(title = intent.value.take(TITLE_MAX)) }
            is RecipeEditorIntent.DescriptionChanged -> edit { it.copy(description = intent.value.take(DESCRIPTION_MAX)) }
            is RecipeEditorIntent.ServingsChanged -> edit { it.copy(servings = intent.value.take(SERVINGS_MAX)) }
            is RecipeEditorIntent.PrepChanged -> edit { it.copy(prepMinutes = intent.value.toMinutes()) }
            is RecipeEditorIntent.CookChanged -> edit { it.copy(cookMinutes = intent.value.toMinutes()) }
            is RecipeEditorIntent.TotalChanged -> edit { it.copy(totalMinutes = intent.value.toMinutes()) }
            is RecipeEditorIntent.NotesChanged -> edit { it.copy(notes = intent.value.take(NOTES_MAX)) }
            is RecipeEditorIntent.LineChanged -> changeLine(intent)
            is RecipeEditorIntent.AddLine -> addLine(intent.section, intent.afterKey)
            is RecipeEditorIntent.RemoveLine -> edit { e ->
                e.withLines(intent.section) { lines -> lines.filterNot { it.key == intent.key } }
            }
            is RecipeEditorIntent.MoveLine -> edit { e -> e.withLines(intent.section) { it.moved(intent.from, intent.to) } }
            is RecipeEditorIntent.PicturesPicked -> importPictures(intent.uris)
            is RecipeEditorIntent.RemovePicture -> removePicture(intent.key)
            is RecipeEditorIntent.SetCover -> edit { e ->
                val picture = e.pictures.firstOrNull { it.key == intent.key } ?: return@edit e
                e.copy(pictures = listOf(picture) + e.pictures.filterNot { it.key == intent.key })
            }
            RecipeEditorIntent.Save -> save()
            RecipeEditorIntent.RequestClose -> requestClose()
            RecipeEditorIntent.DismissDiscard -> _state.update { it.copy(showDiscardDialog = false) }
            RecipeEditorIntent.ConfirmDiscard -> confirmDiscard()
        }
    }

    // ---- editing -------------------------------------------------------------

    /** Applies [transform] to the draft, re-validates live once errors have been shown, and persists. */
    private fun edit(transform: (RecipeEdit) -> RecipeEdit) {
        _state.update { s ->
            val current = s.edit ?: return@update s
            val next = transform(current)
            s.copy(
                edit = next,
                errors = if (s.errors.isEmpty()) s.errors else RecipeEditValidator.validate(next),
            )
        }
        persist()
    }

    private fun changeLine(intent: RecipeEditorIntent.LineChanged) {
        val section = intent.section
        if (!intent.text.contains('\n') && !intent.text.contains('\r')) {
            edit { e ->
                e.withLines(section) { lines ->
                    lines.map { if (it.key == intent.key) it.copy(text = intent.text.take(lineMax(section))) else it }
                }
            }
            return
        }
        val parts = splitPasted(intent.text, section)
        // A newline typed inside one step is legitimate: keep the raw text.
        val single = section == EditSection.STEPS && parts.size <= 1
        var focus: Long? = null
        edit { e ->
            e.withLines(section) { lines ->
                val index = lines.indexOfFirst { it.key == intent.key }
                if (index < 0) return@withLines lines
                if (single) {
                    return@withLines lines.mapIndexed { i, l ->
                        if (i == index) l.copy(text = intent.text.take(lineMax(section))) else l
                    }
                }
                val first = parts.firstOrNull().orEmpty().take(lineMax(section))
                var next = e.nextLineKey()
                val inserted = parts.drop(1).map { EditableLine(next++, it.take(lineMax(section))) }
                focus = inserted.lastOrNull()?.key
                lines.take(index) + lines[index].copy(text = first) + inserted + lines.drop(index + 1)
            }
        }
        focus?.let { _effects.trySend(RecipeEditorEffect.FocusLine(section, it)) }
    }

    private fun addLine(section: EditSection, afterKey: Long?) {
        var newKey: Long? = null
        edit { e ->
            val key = e.nextLineKey().also { newKey = it }
            e.withLines(section) { lines ->
                val index = afterKey?.let { k -> lines.indexOfFirst { it.key == k } } ?: -1
                val at = if (index < 0) lines.size else index + 1
                lines.take(at) + EditableLine(key, "") + lines.drop(at)
            }
        }
        newKey?.let { _effects.trySend(RecipeEditorEffect.FocusLine(section, it)) }
    }

    // ---- pictures ------------------------------------------------------------

    private fun importPictures(uris: List<String>) {
        val current = _state.value
        val edit = current.edit ?: return
        val free = (maxPictures - edit.pictures.size - current.importingPictures).coerceAtLeast(0)
        if (uris.size > free) _effects.trySend(RecipeEditorEffect.Notify(EditorNotice.PICTURES_LIMIT))
        val accepted = uris.take(free)
        if (accepted.isEmpty()) return
        _state.update { it.copy(importingPictures = it.importingPictures + accepted.size) }
        viewModelScope.launch {
            for (uri in accepted) {
                when (val result = addPicture(sessionId, uri)) {
                    is Outcome.Success -> {
                        _state.update { it.copy(importingPictures = it.importingPictures - 1) }
                        edit { e -> e.copy(pictures = e.pictures + result.value) }
                    }
                    is Outcome.Failure -> {
                        _state.update { it.copy(importingPictures = it.importingPictures - 1) }
                        _effects.send(RecipeEditorEffect.Notify(EditorNotice.PICTURE_FAILED))
                    }
                }
            }
        }
    }

    private fun removePicture(key: Long) {
        val current = _state.value.edit ?: return
        val removed = current.pictures.firstOrNull { it.key == key } ?: return
        edit { e -> e.copy(pictures = e.pictures.filterNot { it.key == key }) }
        // Only temp files are ours to delete now; stored ones go when Save commits.
        if (removed.isNew) viewModelScope.launch { discardEdits(current.copy(pictures = listOf(removed))) }
    }

    // ---- save / close --------------------------------------------------------

    private fun save() {
        val s = _state.value
        val draft = s.edit ?: return
        if (s.isSaving || s.importingPictures > 0) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = saveRecipe(draft)) {
                is SaveResult.Saved -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(RecipeEditorEffect.Saved(result.id, mode))
                }
                is SaveResult.Invalid -> {
                    _state.update { it.copy(isSaving = false, errors = result.errors) }
                    if (EditorError.TitleRequired in result.errors) {
                        _effects.send(RecipeEditorEffect.ScrollTo(EditorTarget.TITLE))
                        _effects.send(RecipeEditorEffect.FocusTitle)
                    } else {
                        _effects.send(RecipeEditorEffect.ScrollTo(EditorTarget.INGREDIENTS))
                    }
                }
                is SaveResult.Failed -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.send(RecipeEditorEffect.Notify(EditorNotice.SAVE_FAILED))
                }
            }
        }
    }

    private fun requestClose() {
        if (_state.value.isDirty) {
            _state.update { it.copy(showDiscardDialog = true) }
        } else {
            _effects.trySend(RecipeEditorEffect.Closed)
        }
    }

    private fun confirmDiscard() {
        val draft = _state.value.edit
        _state.update { it.copy(showDiscardDialog = false) }
        viewModelScope.launch {
            draft?.let { discardEdits(it) }
            _effects.send(RecipeEditorEffect.Closed)
        }
    }

    // ---- helpers -------------------------------------------------------------

    private fun persist() {
        val s = _state.value
        val draft = s.edit ?: return
        savedStateHandle[DRAFT_KEY] = RecipeEditorDraftCodec.encode(draft, s.initial)
    }

    private fun String.toMinutes(): Int? = filter { it.isDigit() }.take(MINUTES_DIGITS).toIntOrNull()

    private fun lineMax(section: EditSection) = if (section == EditSection.INGREDIENTS) INGREDIENT_MAX else STEP_MAX

    private fun RecipeEdit.nextLineKey(): Long =
        ((ingredients + steps).maxOfOrNull { it.key } ?: -1L) + 1

    private fun RecipeEdit.withLines(section: EditSection, transform: (List<EditableLine>) -> List<EditableLine>) =
        when (section) {
            EditSection.INGREDIENTS -> copy(ingredients = transform(ingredients))
            EditSection.STEPS -> copy(steps = transform(steps))
        }

    private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
        if (from !in indices || to !in indices || from == to) return this
        return toMutableList().apply { add(to, removeAt(from)) }
    }

    companion object {
        /** `SavedStateHandle` key of the JSON draft snapshot (`{edit, initial}`). */
        const val DRAFT_KEY = "recipe_editor_draft"

        /** `SavedStateHandle` key of the temp-picture session id. */
        const val SESSION_KEY = "recipe_editor_session"

        const val TITLE_MAX = 200
        const val DESCRIPTION_MAX = 1_000
        const val SERVINGS_MAX = 40
        const val INGREDIENT_MAX = 500
        const val STEP_MAX = 2_000
        const val NOTES_MAX = 4_000
        const val MINUTES_DIGITS = 4
    }
}
