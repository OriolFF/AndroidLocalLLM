package com.llmlocal.feature.modelmanagement.di

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.llmlocal.core.llm.download.ModelDownloadWorker
import com.llmlocal.core.model.LlmModelDescriptor

/**
 * Thin wrapper around [WorkManager] that enqueues and cancels model
 * downloads. One `unique work` per model id — calling `enqueue` for a
 * model that is already downloading is a no-op (existing work is kept).
 *
 * Kept feature-side so the work-tag conventions stay private to the
 * model-management feature.
 */
class ModelDownloadScheduler(private val context: Context) {

    /**
     * Starts (or no-ops, if already running) a download for [descriptor].
     * Requires a network connection — the [Constraints] guard ensures we
     * never burn battery on a download that would fail immediately.
     */
    fun enqueue(descriptor: LlmModelDescriptor) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to descriptor.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                uniqueWorkName(descriptor.id),
                ExistingWorkPolicy.KEEP,
                request,
            )
    }

    /**
     * Cancels the in-flight (or pending) download for [modelId]. Idempotent.
     * The worker observes `isStopped`, deletes the `.part` file, and reports
     * `markCancelled` to the progress store.
     */
    fun cancel(modelId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(modelId))
    }

    /** Tag attached to every model-download work request — used for `WorkInfo` queries. */
    fun workTag(): String = WORK_TAG

    private fun uniqueWorkName(modelId: String): String = "$WORK_NAME_PREFIX$modelId"

    companion object {
        const val WORK_NAME_PREFIX = "model-download-"
        const val WORK_TAG = "model-download"
    }
}