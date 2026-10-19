package com.llmlocal.feature.modelmanagement.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.llmlocal.core.llm.download.ModelDownloadWorker
import com.llmlocal.feature.modelmanagement.domain.DownloadScheduler

/**
 * Thin wrapper around [WorkManager] that enqueues and cancels model
 * downloads. One `unique work` per model id — calling `enqueue` for a
 * model that is already downloading is a no-op (existing work is kept).
 *
 * Kept feature-side (in `data/`) so the work-tag conventions stay private to the
 * model-management feature.
 */
class ModelDownloadScheduler(private val context: Context) : DownloadScheduler {

    /**
     * Starts (or no-ops, if already running) a download for [id].
     * Requires a network connection — the [Constraints] guard ensures we
     * never burn battery on a download that would fail immediately.
     */
    override fun enqueue(id: String) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                uniqueWorkName(id),
                ExistingWorkPolicy.KEEP,
                request,
            )
    }

    /**
     * Cancels the in-flight (or pending) download for [id]. Idempotent.
     * The worker observes `isStopped`, deletes the `.part` file, and reports
     * `markCancelled` to the progress store.
     */
    override fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(id))
    }

    private fun uniqueWorkName(modelId: String): String = "$WORK_NAME_PREFIX$modelId"

    companion object {
        const val WORK_NAME_PREFIX = "model-download-"
        const val WORK_TAG = "model-download"
    }
}