package com.llmlocal.core.llm.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.llmlocal.core.llm.model.DownloadProgress
import com.llmlocal.core.llm.model.LlmModelCatalog
import com.llmlocal.core.model.LlmModelDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * WorkManager worker that downloads a single LLM model file.
 *
 * The worker:
 *  1. Looks up the [LlmModelDescriptor] for the id passed in [InputData].
 *  2. Promotes itself to a foreground service with a sticky notification so
 *     the OS does not kill the download when the user backgrounds the app.
 *  3. Streams the file via [LlmModelManager.download], forwarding progress
 *     to both the WorkManager `setProgress(...)` payload (for `WorkInfo`
 *     observers) and the [ModelDownloadProgressStore] (for UI subscribers).
 *  4. Periodically refreshes the foreground notification text so the user
 *     sees live bytes / percent / speed.
 *
 * On cancellation the worker deletes the partial `.part` file and reports
 * [ModelDownloadProgressStore.markCancelled]. On failure it does the same
 * via [ModelDownloadProgressStore.markFailed] with the throwable message.
 *
 * The class is instantiated by [ModelDownloadWorkerFactory] — it expects
 * its dependencies to be passed via the factory, NOT through `inputData`.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val manager: LlmModelManager,
    private val progressStore: ModelDownloadProgressStore,
) : CoroutineWorker(appContext, params) {

    private val tag = "ModelDownloadWorker"

    override suspend fun doWork(): Result {
        val descriptorId = inputData.getString(KEY_MODEL_ID)
        if (descriptorId.isNullOrBlank()) {
            Log.w(tag, "Missing $KEY_MODEL_ID in inputData; cannot start download")
            return Result.failure()
        }
        val descriptor = LlmModelCatalog.findById(descriptorId)
        if (descriptor == null) {
            Log.w(tag, "Unknown model id '$descriptorId' — not in catalog")
            return Result.failure()
        }

        ensureNotificationChannel(applicationContext)

        // Promote to a foreground service IMMEDIATELY. The foreground
        // service is what makes "close the app, download keeps going
        // with just a notification" work — without it the OS is free to
        // kill the worker the moment the user backgrounds the app.
        //
        // We make this mandatory. If the foreground service can't be
        // started (typically because battery optimization / an OEM ROM
        // is restricting the app from running foreground services), fail
        // the worker up front with a clear reason. Silently degrading
        // to a notification-less download is what produced the original
        // "Download failed: Software caused connection abort" symptom:
        // the worker kept running without protection, the OS killed it
        // on app close, and the socket tear-down surfaced as a generic
        // IO failure.
        val initialInfo = buildForegroundInfo(descriptor, bytesRead = 0L, totalBytes = null)
        try {
            setForeground(initialInfo)
            Log.i(
                tag,
                "Foreground service started for ${descriptor.displayName} — " +
                    "download will continue after the app is closed.",
            )
        } catch (t: Throwable) {
            Log.e(
                tag,
                "Could not start foreground service for ${descriptor.displayName}. " +
                    "Background downloads require foreground-service permission " +
                    "(Android may revoke it under battery optimization or OEM " +
                    "task killers).",
                t,
            )
            progressStore.markFailed(
                descriptor.id,
                "Background download needs a foreground service. Disable battery " +
                    "optimization for this app and try again.",
            )
            return Result.failure(
                workDataOf(
                    KEY_FAILURE_REASON to
                        "Could not start foreground service. Disable battery " +
                        "optimization for this app and retry.",
                ),
            )
        }

        return try {
            var lastNotificationRefresh = 0L
            manager.download(descriptor).collect { progress ->
                // Forward progress to WorkManager (visible via WorkInfo).
                setProgress(buildProgressData(progress))

                // Forward progress to the store (visible to UI observers).
                progressStore.update(descriptor.id, progress)

                // Refresh the foreground notification at most every
                // NOTIFICATION_REFRESH_MS to avoid system throttling.
                val now = System.currentTimeMillis()
                if (now - lastNotificationRefresh >= NOTIFICATION_REFRESH_MS) {
                    lastNotificationRefresh = now
                    runCatching {
                        setForegroundAsync(buildForegroundInfo(descriptor, progress.bytesRead, progress.totalBytes))
                    }
                }
                currentCoroutineContext().ensureActive()
            }
            progressStore.markInstalled(descriptor.id)
            runCatching {
                setForegroundAsync(buildInstalledForegroundInfo(descriptor))
            }
            Result.success()
        } catch (ce: CancellationException) {
            Log.i(tag, "Download cancelled for ${descriptor.id}")
            progressStore.markCancelled(descriptor.id)
            runCatching {
                manager.remove(descriptor)
            }
            Result.failure()
        } catch (t: Throwable) {
            Log.w(tag, "Download failed for ${descriptor.id}: ${t.message}", t)
            progressStore.markFailed(descriptor.id, t.message ?: "Download failed")
            Result.failure(workDataOf(KEY_FAILURE_REASON to (t.message ?: "Download failed")))
        }
    }

    /**
     * Build a [ForegroundInfo] for the current progress. Used both for the
     * initial `setForeground` and for periodic refreshes.
     */
    private fun buildForegroundInfo(
        descriptor: LlmModelDescriptor,
        bytesRead: Long,
        totalBytes: Long?,
    ): ForegroundInfo {
        val percent = totalBytes?.takeIf { it > 0 }
            ?.let { ((bytesRead * 100) / it).toInt().coerceIn(0, 100) }
            ?: 0
        val text = if (totalBytes != null && totalBytes > 0) {
            "${LlmModelManager.humanReadableBytes(bytesRead)} / " +
                LlmModelManager.humanReadableBytes(totalBytes)
        } else {
            LlmModelManager.humanReadableBytes(bytesRead)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading ${descriptor.displayName}")
            .setContentText("$text · $percent%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, totalBytes == null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(launchAppPendingIntent())
            .build()
        return foregroundInfo(notification)
    }

    /** Notification shown once the download completes (sticky for a few seconds). */
    private fun buildInstalledForegroundInfo(descriptor: LlmModelDescriptor): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("${descriptor.displayName} downloaded")
            .setContentText("Ready to use")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(launchAppPendingIntent())
            .build()
        return foregroundInfo(notification)
    }

    private fun foregroundInfo(notification: android.app.Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                notificationIdFor(descriptorIdSnapshot),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationIdFor(descriptorIdSnapshot), notification)
        }

    /**
     * Best-effort snapshot of the descriptor id at construction time. The
     * `foregroundInfo` helper above uses this so all notifications for the
     * same model update the same slot (rather than spawning new ones).
     */
    private val descriptorIdSnapshot: String =
        inputData.getString(KEY_MODEL_ID) ?: ""

    private fun launchAppPendingIntent(): PendingIntent {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?: Intent(applicationContext, applicationContext.javaClass)
        return PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildProgressData(progress: DownloadProgress): Data = workDataOf(
        KEY_BYTES_READ to progress.bytesRead,
        KEY_TOTAL_BYTES to (progress.totalBytes ?: -1L),
        KEY_SPEED to progress.speedBytesPerSec,
    )

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_BYTES_READ = "bytes_read"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_SPEED = "speed_bytes_per_sec"
        const val KEY_FAILURE_REASON = "failure_reason"

        const val CHANNEL_ID = "model_downloads"
        const val NOTIFICATION_REFRESH_MS = 1_500L

        /**
         * Notification ids must be stable per model so refreshes replace
         * the previous notification rather than appending a new one.
         */
        fun notificationIdFor(modelId: String): Int =
            NOTIFICATION_ID_BASE + modelId.hashCode()

        private const val NOTIFICATION_ID_BASE = 1_000_000

        /**
         * Registers the [CHANNEL_ID] channel. Idempotent — calling multiple
         * times is harmless.
         */
        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress for on-device LLM model downloads"
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(channel)
        }
    }
}