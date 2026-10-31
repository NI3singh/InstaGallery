// InstaGallery v2.1
package com.example.ml

import android.util.Log
import com.example.data.local.dao.MediaDao
import com.example.data.local.entity.MediaEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Indexes one photo end to end: claim -> detect -> embed + cluster -> record result.
 *
 * Shared by [FaceIndexingWorker] and the repository's fast lane so both paths handle failures the
 * same way. The important part is what happens when things go wrong:
 * - If the coroutine is cancelled (WorkManager stopped the worker, app backgrounded), the claim is
 *   released so the photo is picked up again on the next run instead of being recorded as "no faces".
 * - If ML Kit fails on a photo, the claim is released once so it gets one retry. A photo that fails
 *   twice in the same session is recorded as processed so a corrupt file can never loop forever.
 */
class FacePhotoIndexer(
    private val mediaDao: MediaDao,
    private val detector: FaceDetectorProcessor,
    private val embeddingExtractor: FaceEmbeddingExtractor,
    private val clusterManager: FaceClusterManager
) {

    sealed interface Outcome {
        /** Another indexer already claimed this photo. */
        data object AlreadyClaimed : Outcome
        data object SkippedNotAPhoto : Outcome
        data class Indexed(val assignedToProfile: Boolean, val facesDetected: Int) : Outcome
        /** Claim released after a detector failure; the photo will be retried. */
        data object WillRetry : Outcome
        /** Gave up on this photo for now (repeated or non-retryable failure). */
        data object Failed : Outcome
    }

    /**
     * @param failedOnce photo ids that already failed detection once in this session. Mutated.
     */
    suspend fun index(media: MediaEntity, failedOnce: MutableSet<Long>): Outcome {
        if (media.isVideo || media.displayName.startsWith("InstaCard_", ignoreCase = true)) {
            mediaDao.updateMediaProcessed(media.mediaId, isProcessed = true, hasFaces = false)
            return Outcome.SkippedNotAPhoto
        }

        if (mediaDao.atomicallyClaimMediaForIndexing(media.mediaId) == 0) {
            return Outcome.AlreadyClaimed
        }

        var detectedFaces: List<DetectedAlignedFace> = emptyList()
        try {
            detectedFaces = detector.processMedia(media)
            val assigned = if (detectedFaces.isNotEmpty()) {
                clusterManager.processDetectedFacesForMedia(
                    mediaId = media.mediaId,
                    detectedFaces = detectedFaces,
                    embeddingExtractor = embeddingExtractor,
                    isVideo = false
                )
            } else {
                false
            }
            mediaDao.updateMediaHasFaces(media.mediaId, hasFaces = assigned)
            failedOnce.remove(media.mediaId)
            return Outcome.Indexed(assignedToProfile = assigned, facesDetected = detectedFaces.size)
        } catch (c: CancellationException) {
            withContext(NonCancellable) { releaseClaim(media.mediaId) }
            throw c
        } catch (e: FaceDetectionFailedException) {
            return if (failedOnce.add(media.mediaId)) {
                releaseClaim(media.mediaId)
                Outcome.WillRetry
            } else {
                Log.w(TAG, "Face detection failed twice for ${media.mediaId}; skipping for now", e)
                mediaDao.updateMediaHasFaces(media.mediaId, hasFaces = false)
                Outcome.Failed
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error indexing photo ${media.mediaId}", t)
            mediaDao.updateMediaHasFaces(media.mediaId, hasFaces = false)
            return Outcome.Failed
        } finally {
            recycle(detectedFaces)
        }
    }

    private suspend fun releaseClaim(mediaId: Long) {
        runCatching { mediaDao.updateMediaProcessed(mediaId, isProcessed = false, hasFaces = false) }
            .onFailure { Log.e(TAG, "Failed to release indexing claim for $mediaId", it) }
    }

    private fun recycle(faces: List<DetectedAlignedFace>) {
        for (face in faces) {
            if (!face.alignedFaceBitmap.isRecycled) face.alignedFaceBitmap.recycle()
            if (face.avatarCropBitmap != face.alignedFaceBitmap && !face.avatarCropBitmap.isRecycled) {
                face.avatarCropBitmap.recycle()
            }
        }
    }

    private companion object {
        const val TAG = "FacePhotoIndexer"
    }
}
