package com.llmlocal.feature.webclipper.domain.usecase

import com.llmlocal.core.common.coroutines.DispatcherProvider
import com.llmlocal.core.common.result.Outcome
import com.llmlocal.core.web.ImportedImage
import com.llmlocal.core.web.LocalImageImporter
import com.llmlocal.feature.webclipper.domain.ImagePolicy
import com.llmlocal.feature.webclipper.domain.TempPictureStore
import com.llmlocal.feature.webclipper.domain.input.ClipImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Turns picked content URIs into [ClipImage]s: sequential import on `io`
 * (one bitmap in memory at a time), tiling of tall pictures when the policy
 * asks for it, then a cap at `policy.maxImages` *after* tiling. Every file
 * it leaves behind was handed out by the [TempPictureStore], so
 * [DiscardPreparedImagesUseCase] and the clip pipeline own it from here.
 *
 * A URI that cannot be read is counted in [Result.skipped]; the rest goes
 * on. Cancelling deletes whatever was produced so far.
 */
class PrepareImagesUseCase(
    private val importer: LocalImageImporter,
    private val tempPictures: TempPictureStore,
    private val dispatchers: DispatcherProvider,
) {

    /**
     * @property skipped URIs that could not be read
     * @property capped pictures were dropped to respect the policy cap
     */
    data class Result(val images: List<ClipImage>, val skipped: Int, val capped: Boolean)

    suspend operator fun invoke(uris: List<String>, policy: ImagePolicy): Result =
        withContext(dispatchers.io) {
            val runId = "images-" + UUID.randomUUID().toString().take(8)
            val created = mutableListOf<File>()
            var next = 0
            fun newFile(): File = tempPictures.newFile(runId, next++).also { created += it }

            val images = mutableListOf<ClipImage>()
            var skipped = 0
            var capped = false
            try {
                for ((position, uri) in uris.withIndex()) {
                    coroutineContext.ensureActive()
                    if (images.size >= policy.maxImages) {
                        capped = true
                        break
                    }
                    val imported = importOne(uri, ::newFile, policy)
                    if (imported == null) {
                        skipped++
                        continue
                    }
                    val room = policy.maxImages - images.size
                    val pieces = tilesOf(imported, policy, ::newFile)
                    val tileOf = position.takeIf { pieces.size > 1 }
                    pieces.take(room).forEach { images += it.toClipImage(tileOf) }
                    if (pieces.size > room) {
                        pieces.drop(room).forEach { tempPictures.delete(it.file) }
                        capped = true
                    }
                }
            } catch (e: CancellationException) {
                created.forEach(tempPictures::delete)
                throw e
            }
            // The untiled source of a tall picture is not part of the result.
            val kept = images.map { it.file }.toSet()
            created.filterNot { it in kept }.forEach(tempPictures::delete)
            Result(images, skipped, capped)
        }

    private suspend fun importOne(uri: String, newFile: () -> File, policy: ImagePolicy): ImportedImage? {
        val into = newFile()
        val outcome = try {
            importer.import(uri, into, policy.maxLongEdgePx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Failure(e)
        }
        return when (outcome) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> null
        }
    }

    /** The picture itself, or its tiles when it is a tall strip and tiling is on. */
    private suspend fun tilesOf(source: ImportedImage, policy: ImagePolicy, newFile: () -> File): List<ImportedImage> {
        val tall = source.height > policy.tallAspect * source.width
        if (!policy.tileImages || !tall) return listOf(source)
        val outcome = try {
            importer.tile(source, { newFile() }, policy.tallAspect)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Outcome.Failure(e)
        }
        return when (outcome) {
            is Outcome.Success -> outcome.value
            is Outcome.Failure -> listOf(source)
        }
    }

    private fun ImportedImage.toClipImage(tileOf: Int?) =
        ClipImage(file = file, width = width, height = height, sha256 = sha256, tileOf = tileOf)
}
