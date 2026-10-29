// InstaGallery v2.0
package com.example.data.media

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.InstaGalleryApp
import com.example.data.local.AppDatabase
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.entity.MediaEntity
import com.example.ml.FaceIndexingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SyncDiffResult(
    val addedPhotosCount: Int,
    val addedVideosCount: Int,
    val deletedCount: Int,
    val newlyInsertedPhotos: List<MediaEntity>
)

class MediaStoreScanner(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val personDao: PersonDao = (context.applicationContext as? InstaGalleryApp)?.database?.personDao()
        ?: AppDatabase.getInstance(context).personDao()
) {
    companion object {
        private const val TAG = "MediaStoreScanner"
        const val FACE_INDEXING_WORK_NAME = "face_indexing_work"
        private const val DEFAULT_WIDTH = 1080
        private const val DEFAULT_HEIGHT = 1350

        private val MEDIA_AND_WHATSAPP_FILTER = """
            (
                ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR
                ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO} OR
                ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%' OR
                ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%' OR
                LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE '%.jpg' OR
                LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE '%.jpeg' OR
                LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE '%.png' OR
                LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE '%.webp' OR
                LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE '%.heic' OR
                LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE '%.mp4' OR
                LOWER(${MediaStore.Files.FileColumns.DISPLAY_NAME}) LIKE '%.mov'
            )
        """.trimIndent()
    }

    /**
     * 2.1 Surgical Single-ID Sync (< 5ms).
     * Triggered when ContentObserver.onChange provides a specific numeric mediaId.
     * Handles IS_PENDING camera locks, instant single-item additions, and instant single-item deletions.
     */
    suspend fun syncSingleMediaId(mediaId: Long): SyncDiffResult = withContext(Dispatchers.IO) {
        if (mediaId <= 0L) {
            return@withContext SyncDiffResult(0, 0, 0, emptyList())
        }

        try {
            val externalFilesUri = MediaStore.Files.getContentUri("external")
            val readySelection = buildTrashAndPendingSelection("${MediaStore.Files.FileColumns._ID} = ?")
            val selectionArgs = arrayOf(mediaId.toString())

            val projection = buildFilesProjection()

            var foundReadyEntity: MediaEntity? = null
            var rowPresentReady = false

            context.contentResolver.query(
                externalFilesUri,
                projection,
                readySelection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    rowPresentReady = true
                    foundReadyEntity = parseEntityFromFilesCursor(
                        cursor = cursor,
                        volumeName = MediaStore.VOLUME_EXTERNAL,
                        imagesCollectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        videoCollectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    )
                }
            }

            if (!rowPresentReady) {
                // Case A — Cursor is empty (Row was deleted or is still IS_PENDING == 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    var isPendingInCamera = false
                    val pendingCheckProjection = mutableListOf(
                        MediaStore.Files.FileColumns._ID,
                        MediaStore.MediaColumns.IS_PENDING
                    ).apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            add(MediaStore.MediaColumns.IS_TRASHED)
                        }
                    }.toTypedArray()

                    context.contentResolver.query(
                        externalFilesUri,
                        pendingCheckProjection,
                        "${MediaStore.Files.FileColumns._ID} = ?",
                        selectionArgs,
                        null
                    )?.use { pendingCursor ->
                        if (pendingCursor.moveToFirst()) {
                            val pendingCol = pendingCursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                            val trashedCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                pendingCursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                            } else -1
                            val isPending = pendingCol >= 0 && !pendingCursor.isNull(pendingCol) && pendingCursor.getInt(pendingCol) == 1
                            val isTrashed = trashedCol >= 0 && !pendingCursor.isNull(trashedCol) && pendingCursor.getInt(trashedCol) == 1
                            if (isPending && !isTrashed) {
                                isPendingInCamera = true
                            }
                        }
                    }

                    if (isPendingInCamera) {
                        // Wait for the camera's IS_PENDING = 0 completion update
                        return@withContext SyncDiffResult(0, 0, 0, emptyList())
                    }
                }

                // If the row does not exist in MediaStore at all (or is trashed) AND exists in Room:
                if (mediaDao.existsMediaId(mediaId)) {
                    val deletedFaceCropPaths = collectFaceCropPathsForDeletedMedia(listOf(mediaId))
                    mediaDao.deleteMediaById(mediaId)
                    personDao.reconcilePeopleAfterMediaDeletion()
                    repairAvatarsAndCleanupCrops(deletedFaceCropPaths)
                    return@withContext SyncDiffResult(
                        addedPhotosCount = 0,
                        addedVideosCount = 0,
                        deletedCount = 1,
                        newlyInsertedPhotos = emptyList()
                    )
                }

                return@withContext SyncDiffResult(0, 0, 0, emptyList())
            }

            // Case B — Cursor has the item (IS_PENDING == 0)
            val entity = foundReadyEntity ?: return@withContext SyncDiffResult(0, 0, 0, emptyList())
            if (!mediaDao.existsMediaId(mediaId)) {
                val insertedRowIds = mediaDao.insertMediaItems(listOf(entity))
                val wasInserted = insertedRowIds.firstOrNull() != -1L
                if (wasInserted) {
                    return@withContext SyncDiffResult(
                        addedPhotosCount = if (entity.isVideo) 0 else 1,
                        addedVideosCount = if (entity.isVideo) 1 else 0,
                        deletedCount = 0,
                        newlyInsertedPhotos = if (entity.isVideo) emptyList() else listOf(entity)
                    )
                }
            }

            SyncDiffResult(0, 0, 0, emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncSingleMediaId for mediaId=$mediaId", e)
            SyncDiffResult(0, 0, 0, emptyList())
        }
    }

    /**
     * 2.2 Zero-I/O HashSet ID-Diff Sync (< 15ms).
     * Compares Room IDs against MediaStore IDs in memory without opening file descriptors,
     * instantly deleting removed items and fetching full metadata only for newly added IDs.
     */
    suspend fun syncFastIdDiff(): SyncDiffResult = withContext(Dispatchers.IO) {
        try {
            val roomIds = mediaDao.getAllMediaIdsSync().toHashSet()
            val volumeNames = getExternalVolumeNames()
            val storeIds = HashSet<Long>(roomIds.size + 64)
            val idToVolumeMap = HashMap<Long, String>(64)

            val idOnlyProjection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection = buildTrashAndPendingSelection(MEDIA_AND_WHATSAPP_FILTER)

            for (volumeName in volumeNames) {
                val filesUri = MediaStore.Files.getContentUri(volumeName)
                try {
                    context.contentResolver.query(
                        filesUri,
                        idOnlyProjection,
                        selection,
                        null,
                        null
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            if (storeIds.add(id) && id !in roomIds) {
                                idToVolumeMap[id] = volumeName
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error querying IDs on volume $volumeName", e)
                }
            }

            // 1. Instant Deletion Detection (deletedIds = roomIds - storeIds)
            val deletedIds = roomIds.filter { it !in storeIds }
            if (deletedIds.isNotEmpty()) {
                val deletedFaceCropPaths = collectFaceCropPathsForDeletedMedia(deletedIds)
                deletedIds.chunked(500).forEach { chunk ->
                    mediaDao.deleteMediaByIds(chunk)
                }
                personDao.reconcilePeopleAfterMediaDeletion()
                repairAvatarsAndCleanupCrops(deletedFaceCropPaths)
            }

            // 2. Instant Addition Detection (addedIds = storeIds - roomIds)
            val addedIds = storeIds.filter { it !in roomIds }
            val addedPhotos = mutableListOf<MediaEntity>()
            val addedVideos = mutableListOf<MediaEntity>()

            if (addedIds.isNotEmpty()) {
                val idsByVolume = addedIds.groupBy { idToVolumeMap[it] ?: MediaStore.VOLUME_EXTERNAL }
                val fullProjection = buildFilesProjection()

                for ((volumeName, volumeAddedIds) in idsByVolume) {
                    val filesUri = MediaStore.Files.getContentUri(volumeName)
                    val imagesCollectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(volumeName)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val videoCollectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(volumeName)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }

                    volumeAddedIds.chunked(400).forEach { idChunk ->
                        val idListSql = idChunk.joinToString(",")
                        val chunkSelection = buildTrashAndPendingSelection(
                            "${MediaStore.Files.FileColumns._ID} IN ($idListSql)"
                        )
                        val chunkEntities = mutableListOf<MediaEntity>()

                        try {
                            context.contentResolver.query(
                                filesUri,
                                fullProjection,
                                chunkSelection,
                                null,
                                "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
                            )?.use { cursor ->
                                while (cursor.moveToNext()) {
                                    val entity = parseEntityFromFilesCursor(
                                        cursor = cursor,
                                        volumeName = volumeName,
                                        imagesCollectionUri = imagesCollectionUri,
                                        videoCollectionUri = videoCollectionUri
                                    )
                                    if (entity != null) {
                                        chunkEntities.add(entity)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error querying full metadata for added IDs on $volumeName", e)
                        }

                        if (chunkEntities.isNotEmpty()) {
                            val rowIds = mediaDao.insertMediaItems(chunkEntities)
                            for (i in rowIds.indices) {
                                if (rowIds[i] != -1L) {
                                    val item = chunkEntities[i]
                                    if (item.isVideo) {
                                        addedVideos.add(item)
                                    } else {
                                        addedPhotos.add(item)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SyncDiffResult(
                addedPhotosCount = addedPhotos.size,
                addedVideosCount = addedVideos.size,
                deletedCount = deletedIds.size,
                newlyInsertedPhotos = addedPhotos
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed syncFastIdDiff", e)
            SyncDiffResult(0, 0, 0, emptyList())
        }
    }

    /**
     * Fast Delta Scan compatible wrapper around syncFastIdDiff.
     */
    suspend fun scanDeltaMedia(): Int = withContext(Dispatchers.IO) {
        val diff = syncFastIdDiff()
        if (diff.addedPhotosCount > 0) {
            enqueueFaceIndexingWorker(ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        diff.addedPhotosCount
    }

    /**
     * Full reconciliation & self-healing scan.
     * Uses syncFastIdDiff() for zero-I/O addition/deletion reconciliation, then ensures video flags
     * and self-healing face indexing state are consistent.
     */
    suspend fun scanFullAndReconcile(): Int = withContext(Dispatchers.IO) {
        try {
            val diff = syncFastIdDiff()

            // Purge any video-to-person links and ensure videos are marked processed (never face-indexed)
            mediaDao.deleteAllVideoPersonLinks()
            mediaDao.markAllVideosProcessed()

            val totalPhotos = mediaDao.getTotalMediaCountSync()
            val unindexedPhotos = mediaDao.getUnindexedCountSync()

            if (totalPhotos > 0 &&
                unindexedPhotos == 0 &&
                personDao.getAllPeopleIncludeHiddenSync().isEmpty()
            ) {
                Log.i(TAG, "Detected 0 profiles with processed photos; resetting photo processed flags for re-indexing")
                mediaDao.resetAllMediaProcessed()
                mediaDao.markAllVideosProcessed()
                enqueueFaceIndexingWorker(ExistingWorkPolicy.REPLACE)
            } else if (diff.addedPhotosCount > 0 || unindexedPhotos > 0) {
                enqueueFaceIndexingWorker(ExistingWorkPolicy.APPEND_OR_REPLACE)
            }

            totalPhotos
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan MediaStore", e)
            0
        }
    }

    suspend fun scanAndReconcile(): Int = scanFullAndReconcile()

    private suspend fun collectFaceCropPathsForDeletedMedia(deletedMediaIds: List<Long>): HashSet<String> {
        val deletedFaceCropPaths = HashSet<String>()
        for (deletedId in deletedMediaIds) {
            val faces = personDao.getFacesForMediaSync(deletedId)
            for (face in faces) {
                if (face.cropPath.isNotBlank()) {
                    deletedFaceCropPaths.add(face.cropPath)
                }
            }
        }
        return deletedFaceCropPaths
    }

    private suspend fun repairAvatarsAndCleanupCrops(deletedFaceCropPaths: Set<String>) {
        // 1. Repair broken or deleted avatarCropPath references on remaining PersonEntity rows
        val remainingPeople = personDao.getAllPeopleIncludeHiddenSync()
        for (person in remainingPeople) {
            val avatarMissingOrDeleted = person.avatarCropPath.isBlank() ||
                deletedFaceCropPaths.contains(person.avatarCropPath) ||
                !File(person.avatarCropPath).exists()

            if (avatarMissingOrDeleted) {
                val bestFace = personDao.getHighestQualityFaceForPersonSync(person.personId)
                if (bestFace != null && bestFace.cropPath.isNotBlank() && File(bestFace.cropPath).exists()) {
                    personDao.updateAvatar(person.personId, bestFace.cropPath)
                }
            }
        }

        // 2. Clean up deleted face crop files on disk that are no longer referenced
        for (cropPath in deletedFaceCropPaths) {
            runCatching {
                val file = File(cropPath)
                if (file.exists()) file.delete()
            }
        }

        // 3. Sync media hasFaces flags and invalidate in-memory cluster cache
        mediaDao.syncAllMediaHasFaces()
        (context.applicationContext as? InstaGalleryApp)?.faceClusterManager?.invalidateCache()
    }

    private fun getExternalVolumeNames(): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context).ifEmpty {
                setOf(MediaStore.VOLUME_EXTERNAL)
            }
        } else {
            setOf(MediaStore.VOLUME_EXTERNAL)
        }
    }

    private fun buildFilesProjection(): Array<String> {
        val projectionList = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.MediaColumns.SIZE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            projectionList.add(MediaStore.MediaColumns.IS_TRASHED)
            projectionList.add(MediaStore.MediaColumns.IS_PENDING)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.MediaColumns.IS_PENDING)
        }
        return projectionList.toTypedArray()
    }

    private fun parseEntityFromFilesCursor(
        cursor: Cursor,
        volumeName: String,
        imagesCollectionUri: Uri,
        videoCollectionUri: Uri
    ): MediaEntity? {
        val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
        if (idCol < 0) return null

        val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
        } else -1
        val trashedCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
        } else -1

        if (isTrashedOrPending(cursor, trashedCol, pendingCol)) return null

        val id = cursor.getLong(idCol)
        val mediaTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val mimeTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
        val dateAddedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
        val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
        val displayNameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)

        val mediaType = cursor.getIntOrNull(mediaTypeCol) ?: MediaStore.Files.FileColumns.MEDIA_TYPE_NONE
        val displayName = cursor.getStringOrNull(displayNameCol) ?: "Media_$id"
        val rawMime = cursor.getStringOrNull(mimeTypeCol)

        val isSupportedRow = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ||
            mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ||
            rawMime?.startsWith("image/", ignoreCase = true) == true ||
            rawMime?.startsWith("video/", ignoreCase = true) == true ||
            isImageOrVideoExtension(displayName)

        if (!isSupportedRow) return null

        val isVideoByTypeOrMime = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ||
            rawMime?.startsWith("video/", ignoreCase = true) == true ||
            isVideoExtension(displayName)

        val mimeType = inferMimeType(rawMime, displayName, isVideoDefault = isVideoByTypeOrMime)
        val isVideo = isVideoByTypeOrMime || mimeType.startsWith("video/", ignoreCase = true)

        val rawWidth = cursor.getIntOrNull(widthCol) ?: 0
        val rawHeight = cursor.getIntOrNull(heightCol) ?: 0
        val sizeBytes = cursor.getLongOrNull(sizeCol) ?: 0L

        if (isTinyStickerOrIconNoise(isVideo = isVideo, sizeBytes = sizeBytes, rawWidth = rawWidth, rawHeight = rawHeight)) {
            return null
        }

        val dateAdded = cursor.getLongOrNull(dateAddedCol) ?: (System.currentTimeMillis() / 1000L)
        val width = if (rawWidth > 0) rawWidth else DEFAULT_WIDTH
        val height = if (rawHeight > 0) rawHeight else DEFAULT_HEIGHT
        val duration = if (isVideo) cursor.getLongOrNull(durationCol) else null

        val itemUri = when {
            mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO || isVideo ->
                ContentUris.withAppendedId(videoCollectionUri, id)
            mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                ContentUris.withAppendedId(imagesCollectionUri, id)
            else ->
                MediaStore.Files.getContentUri(volumeName, id)
        }

        return MediaEntity(
            mediaId = id,
            uri = itemUri.toString(),
            displayName = displayName,
            mimeType = mimeType,
            isVideo = isVideo,
            dateAddedSeconds = dateAdded,
            width = width,
            height = height,
            durationMs = duration,
            isProcessedForFaces = isVideo,
            hasFaces = false,
            isFavorite = false,
            isBookmarked = false
        )
    }

    private fun isTinyStickerOrIconNoise(
        isVideo: Boolean,
        sizeBytes: Long,
        rawWidth: Int,
        rawHeight: Int
    ): Boolean {
        if (isVideo) return false
        return sizeBytes in 1L..15360L && (rawWidth in 1..200 || rawHeight in 1..200)
    }

    private fun buildTrashAndPendingSelection(baseCondition: String?): String? {
        val clauses = mutableListOf<String>()
        if (!baseCondition.isNullOrBlank()) {
            clauses.add(baseCondition)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            clauses.add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
            clauses.add("${MediaStore.MediaColumns.IS_PENDING} = 0")
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            clauses.add("${MediaStore.MediaColumns.IS_PENDING} = 0")
        }
        return if (clauses.isEmpty()) null else clauses.joinToString(" AND ")
    }

    private fun isTrashedOrPending(
        cursor: Cursor,
        trashedCol: Int,
        pendingCol: Int
    ): Boolean {
        if (pendingCol >= 0 && !cursor.isNull(pendingCol) && cursor.getInt(pendingCol) == 1) {
            return true
        }
        if (trashedCol >= 0 && !cursor.isNull(trashedCol) && cursor.getInt(trashedCol) == 1) {
            return true
        }
        return false
    }

    private fun inferMimeType(
        rawMime: String?,
        displayName: String,
        isVideoDefault: Boolean
    ): String {
        if (!rawMime.isNullOrBlank() &&
            (rawMime.startsWith("image/", ignoreCase = true) || rawMime.startsWith("video/", ignoreCase = true))
        ) {
            return rawMime
        }
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            else -> if (isVideoDefault) "video/mp4" else "image/jpeg"
        }
    }

    private fun isImageOrVideoExtension(displayName: String): Boolean {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp" ||
            ext == "heic" || ext == "mp4" || ext == "mkv" || ext == "mov" || ext == "3gp"
    }

    private fun isVideoExtension(displayName: String): Boolean {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return ext == "mp4" || ext == "mkv" || ext == "mov" || ext == "3gp"
    }

    private fun Cursor.getStringOrNull(colIndex: Int): String? {
        return if (colIndex >= 0 && !isNull(colIndex)) getString(colIndex) else null
    }

    private fun Cursor.getIntOrNull(colIndex: Int): Int? {
        return if (colIndex >= 0 && !isNull(colIndex)) getInt(colIndex) else null
    }

    private fun Cursor.getLongOrNull(colIndex: Int): Long? {
        return if (colIndex >= 0 && !isNull(colIndex)) getLong(colIndex) else null
    }

    fun enqueueFaceIndexingWorker(
        policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE
    ) {
        val workRequest = OneTimeWorkRequestBuilder<FaceIndexingWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FACE_INDEXING_WORK_NAME,
            policy,
            workRequest
        )
    }

    fun enqueueFaceIndexingWorker(replace: Boolean) {
        enqueueFaceIndexingWorker(
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
        )
    }
}
