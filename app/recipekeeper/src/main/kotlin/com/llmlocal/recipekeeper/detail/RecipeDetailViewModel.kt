package com.llmlocal.recipekeeper.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.feature.webclipper.capture.PictureDownloader
import com.llmlocal.recipekeeper.data.RecipePictureStore
import com.llmlocal.recipekeeper.domain.ClippedRecipe
import com.llmlocal.recipekeeper.domain.RecipeRepository
import com.llmlocal.recipekeeper.library.PendingRecipeDeletes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MVI ViewModel for one recipe (`recipe/{recipeId}`).
 *
 * Observes [RecipeRepository.observe] so edits made elsewhere (another
 * screen ticking ingredients, a re-extraction replacing the row) show up
 * live. Ingredient checks are applied optimistically to the state and
 * persisted with [RecipeRepository.setChecked]; the repository's next
 * emission confirms them.
 *
 * Delete hands the id to [PendingRecipeDeletes] (shared undo window) and
 * emits [RecipeDetailEffect.Deleted]; the host pops the screen and shows
 * the undo snackbar from its own scope so it survives the pop.
 *
 * "Retry pictures" (WS11, after an offline clip) downloads the first
 * [RETRY_PICTURES] of [ClippedRecipe.pictureCandidates] with the same
 * [PictureDownloader] the extraction uses, moves them into the recipe's
 * folder through [RecipePictureStore] and clears `picturesFailed`; the
 * repository's next emission shows the hero.
 *
 * @param tempDir scratch directory for the retry downloads (the clipper's cache dir)
 */
class RecipeDetailViewModel(
    private val recipeId: String,
    private val repository: RecipeRepository,
    private val pendingDeletes: PendingRecipeDeletes,
    private val dispatchers: DispatcherProvider,
    private val pictureDownloader: PictureDownloader,
    private val pictureStore: RecipePictureStore,
    private val tempDir: File,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeDetailState())
    val state: StateFlow<RecipeDetailState> = _state.asStateFlow()

    private val _effects = Channel<RecipeDetailEffect>(Channel.BUFFERED)
    val effects: Flow<RecipeDetailEffect> = _effects.receiveAsFlow()

    init {
        repository.observe(recipeId)
            .flowOn(dispatchers.io)
            .onEach { recipe -> _state.update { it.copy(recipe = recipe, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    /** Single dispatch point for every user action. */
    fun onIntent(intent: RecipeDetailIntent) {
        when (intent) {
            is RecipeDetailIntent.ToggleIngredient -> toggleIngredient(intent.index)
            RecipeDetailIntent.ResetChecks -> setChecked(emptySet())
            is RecipeDetailIntent.SelectStep -> _state.update {
                it.copy(currentStep = if (it.currentStep == intent.index) null else intent.index)
            }
            RecipeDetailIntent.ToggleKeepScreenOn -> _state.update { it.copy(keepScreenOn = !it.keepScreenOn) }
            RecipeDetailIntent.Share -> _state.value.recipe?.let { recipe ->
                emit(RecipeDetailEffect.ShareRecipe(recipe.title, recipe.sourceUrl))
            }
            RecipeDetailIntent.RetryPictures -> retryPictures()
            RecipeDetailIntent.OpenOriginal, RecipeDetailIntent.ReExtract -> _state.value.recipe?.let { recipe ->
                emit(RecipeDetailEffect.NavigateToBrowser(recipe.sourceUrl))
            }
            RecipeDetailIntent.RequestDelete -> _state.update { it.copy(showDeleteDialog = true) }
            RecipeDetailIntent.DismissDelete -> _state.update { it.copy(showDeleteDialog = false) }
            RecipeDetailIntent.ConfirmDelete -> {
                _state.update { it.copy(showDeleteDialog = false) }
                pendingDeletes.schedule(recipeId)
                emit(RecipeDetailEffect.Deleted(recipeId))
            }
        }
    }

    private fun toggleIngredient(index: Int) {
        val recipe = _state.value.recipe ?: return
        if (index !in recipe.ingredients.indices) return
        val checked = recipe.checkedIngredients.toMutableSet().apply {
            if (!add(index)) remove(index)
        }
        setChecked(checked)
    }

    private fun setChecked(checked: Set<Int>) {
        val recipe = _state.value.recipe ?: return
        _state.update { it.copy(recipe = recipe.copy(checkedIngredients = checked)) }
        viewModelScope.launch {
            withContext(dispatchers.io) { repository.setChecked(recipeId, checked) }
        }
    }

    private fun retryPictures() {
        val recipe = _state.value.recipe ?: return
        if (_state.value.retryingPictures) return
        val urls = recipe.pictureCandidates.take(RETRY_PICTURES)
        if (urls.isEmpty()) {
            emit(RecipeDetailEffect.PicturesRetryFailed)
            return
        }
        _state.update { it.copy(retryingPictures = true) }
        viewModelScope.launch {
            try {
                val paths = withContext(dispatchers.io) {
                    val files = downloadAll(urls, referer = recipe.sourceUrl)
                    pictureStore.moveIn(recipe.id, files)
                }
                repository.replacePictures(recipe.id, paths, picturesFailed = paths.isEmpty())
                if (paths.isEmpty()) emit(RecipeDetailEffect.PicturesRetryFailed)
            } finally {
                _state.update { it.copy(retryingPictures = false) }
            }
        }
    }

    /** Downloads [urls] concurrently; a failed one is skipped, cancellation propagates. */
    private suspend fun downloadAll(urls: List<String>, referer: String): List<File> = coroutineScope {
        tempDir.mkdirs()
        urls.mapIndexed { index, url ->
            async {
                val into = File(tempDir, "retry-$recipeId-$index.jpg")
                val outcome = try {
                    pictureDownloader.download(url, referer, into, MAX_LONG_EDGE_PX)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Outcome.Failure(e)
                }
                (outcome as? Outcome.Success)?.value
            }
        }.awaitAll().filterNotNull()
    }

    private fun emit(effect: RecipeDetailEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    companion object {
        /** How many stored candidates a retry downloads (mirrors the extraction screen's default keep). */
        const val RETRY_PICTURES = 3

        /** Same long-edge bound as `ExtractionViewModel.MAX_LONG_EDGE_PX`. */
        const val MAX_LONG_EDGE_PX = 1_280
    }
}
