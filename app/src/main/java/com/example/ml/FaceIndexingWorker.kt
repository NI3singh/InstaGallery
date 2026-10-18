package com.example.ml

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class FaceIndexingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "FaceIndexingWorker"
        const val PROGRESS_PROCESSED = "processed"
        const val PROGRESS_TOTAL = "total"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getInstance(applicationContext)
        val mediaDao = database.mediaDao()
        val personDao = database.personDao()

        val detectorProcessor = FaceDetectorProcessor(applicationContext)
        val embeddingExtractor = FaceEmbeddingExtractor(applicationContext)
        val clusterManager = FaceClusterManager(applicationContext, personDao, mediaDao)

        try {
            var processedCount = 0
            val totalUnindexed = mediaDao.getUnindexedCount().firstOrNull() ?: 0
            var remainingTotal = totalUnindexed

            while (!isStopped) {
                val unindexedBatch = mediaDao.getUnindexedMedia(limit = 25)
                if (unindexedBatch.isEmpty()) break

                for (media in unindexedBatch) {
                    if (isStopped) break

                    try {
                        val bitmap = detectorProcessor.decodeBitmap(
                            media.uri,
                            media.isVideo,
                            media.durationMs
                        )

                        var facesFound = false

                        if (bitmap != null) {
                            val detectedFaces = detectorProcessor.detectFaces(bitmap)
                            if (detectedFaces.isNotEmpty()) {
                                facesFound = true
                                for (face in detectedFaces) {
                                    val crop = detectorProcessor.cropFaceBitmap(bitmap, face)
                                    if (crop != null) {
                                        val embedding = embeddingExtractor.extractEmbedding(crop)
                                        clusterManager.processFace(
                                            mediaId = media.mediaId,
                                            face = face,
                                            cropBitmap = crop,
                                            embedding = embedding
                                        )
                                        if (crop != bitmap) {
                                            crop.recycle()
                                        }
                                    }
                                }
                            }
                            bitmap.recycle()
                        }

                        mediaDao.updateMediaProcessed(
                            mediaId = media.mediaId,
                            isProcessed = true,
                            hasFaces = facesFound
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing media ${media.mediaId}", e)
                        // Mark processed to prevent infinite loops
                        mediaDao.updateMediaProcessed(media.mediaId, isProcessed = true, hasFaces = false)
                    }

                    processedCount++
                    setProgress(
                        workDataOf(
                            PROGRESS_PROCESSED to processedCount,
                            PROGRESS_TOTAL to remainingTotal
                        )
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.retry()
        }
    }
}
