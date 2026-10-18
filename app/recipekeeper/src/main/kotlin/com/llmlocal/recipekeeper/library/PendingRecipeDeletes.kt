package com.llmlocal.recipekeeper.library

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.recipekeeper.domain.RecipeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App-wide undo window for recipe deletion (UX spec §5: "soft delete, pop,
 * snackbar + Undo 5 s, then hard delete").
 *
 * Both the library (long-press → Delete) and the detail screen (overflow →
 * Delete) call [schedule]; the recipe disappears from every list at once
 * because `LibraryViewModel` filters [pending] out of what the repository
 * emits, while the row itself stays in storage until the window elapses.
 * [undo] cancels the timer and the recipe reappears; [commit] deletes
 * immediately (used when the snackbar is dismissed early).
 *
 * The timer runs in an application-scoped [scope] rather than a
 * ViewModel's so leaving the screen never loses a scheduled delete. If the
 * process dies first, the recipe simply survives — the safe failure.
 *
 * Bound as a Koin `single` in `recipeKeeperUiModule`.
 */
class PendingRecipeDeletes(
    private val repository: RecipeRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.default),
    private val undoWindowMs: Long = DEFAULT_UNDO_WINDOW_MS,
) {

    private val _pending = MutableStateFlow<Set<String>>(emptySet())

    /** Ids that are hidden from the UI but not yet deleted from storage. */
    val pending: StateFlow<Set<String>> = _pending

    private val timers = HashMap<String, Job>()

    /** Hides [id] and starts the undo window. No-op if already pending. */
    fun schedule(id: String) {
        synchronized(timers) {
            if (timers.containsKey(id)) return
            _pending.update { it + id }
            timers[id] = scope.launch {
                delay(undoWindowMs)
                hardDelete(id)
            }
        }
    }

    /** Cancels the pending delete for [id]; `false` when the window already closed. */
    fun undo(id: String): Boolean {
        val job = synchronized(timers) { timers.remove(id) } ?: return false
        job.cancel()
        _pending.update { it - id }
        return true
    }

    /** Deletes [id] now (if still pending) instead of waiting for the window. */
    suspend fun commit(id: String) {
        val job = synchronized(timers) { timers.remove(id) } ?: return
        job.cancel()
        withContext(dispatchers.io) { repository.delete(id) }
        _pending.update { it - id }
    }

    private suspend fun hardDelete(id: String) {
        synchronized(timers) { timers.remove(id) }
        withContext(dispatchers.io) { repository.delete(id) }
        _pending.update { it - id }
    }

    companion object {
        const val DEFAULT_UNDO_WINDOW_MS = 5_000L
    }
}
