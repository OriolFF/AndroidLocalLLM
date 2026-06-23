package com.llmlocal.feature.modelmanagement.di

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Adapts WorkManager's `getWorkInfosByTagFlow` to a typed [Flow] of
 * [WorkInfo] updates. The UI subscribes to detect terminal states
 * (SUCCEEDED / FAILED / CANCELLED) and to drive the per-model status pill.
 */
class WorkInfoObserver(private val context: Context) {

    /**
     * Cold flow that re-emits whenever the WorkManager state changes for
     * the supplied [tag]. Each emission contains the *current* snapshot of
     * every work matching the tag — callers diff on their own.
     */
    fun observe(tag: String): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(tag)
            .map { infos -> infos.orEmpty() }
}