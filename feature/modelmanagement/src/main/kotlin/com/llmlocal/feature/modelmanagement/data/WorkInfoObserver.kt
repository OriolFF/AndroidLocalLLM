package com.llmlocal.feature.modelmanagement.data

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.llmlocal.feature.modelmanagement.domain.DownloadWorkSource
import com.llmlocal.feature.modelmanagement.domain.DownloadWorkStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Adapts WorkManager's `getWorkInfosByTagFlow` to the domain
 * [DownloadWorkSource]. Every emission carries the *current* status of
 * every model-download job; callers diff on their own.
 */
class WorkInfoObserver(private val context: Context) : DownloadWorkSource {

    override fun observe(): Flow<List<DownloadWorkStatus>> =
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(ModelDownloadScheduler.WORK_TAG)
            .map { infos -> infos.orEmpty().map { it.state.toDomain() } }

    private fun WorkInfo.State.toDomain(): DownloadWorkStatus = when (this) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadWorkStatus.ENQUEUED
        WorkInfo.State.RUNNING -> DownloadWorkStatus.RUNNING
        WorkInfo.State.SUCCEEDED -> DownloadWorkStatus.SUCCEEDED
        WorkInfo.State.FAILED -> DownloadWorkStatus.FAILED
        WorkInfo.State.CANCELLED -> DownloadWorkStatus.CANCELLED
    }
}
