// InstaGallery v1.0
package com.example.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.data.local.dao.MediaDao
import com.example.data.local.entity.MediaEntity
import com.example.ml.FaceIndexingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreScanner(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    companion object {
        private const val TAG = "MediaStoreScanner"
        const val FACE_INDEXING_WORK_NAME = "face_indexing_work"
        private const val DEFAULT_WIDTH = 1080
        private const val DEFAULT_HEIGHT = 1350
    }

    suspend fun scanAndReconcile(): Int = withContext(Dispatchers.IO) {
        try {
            val volumeNames: Set<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context).ifEmpty {
                    setOf(MediaStore.VOLUME_EXTERNAL)
                }
            } else {
                setOf(MediaStore.VOLUME_EXTERNAL)
            }

            val deduplicatedMedia = LinkedHashMap<Long, MediaEntity>()

            for (volumeName in volumeNames) {
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

                val filesCollectionUri = MediaStore.Files.getContentUri(volumeName)

                // a) Dedicated Images query on this volume
                queryImagesTable(imagesCollectionUri, deduplicatedMedia)

                // b) Dedicated Video query on this volume
                queryVideoTable(videoCollectionUri, deduplicatedMedia)

                // c) Fallback Files query for WebP, HEIC, MKV, MP4, etc. not flagged with standard media_type
                queryFilesFallbackTable(
                    filesCollectionUri = filesCollectionUri,
                    imagesCollectionUri = imagesCollectionUri,
                    videoCollectionUri = videoCollectionUri,
                    deduplicatedMedia = deduplicatedMedia
                )
            }

            // Insert newly discovered files in chunks of 250 using OnConflictStrategy.IGNORE
            var anyNewUnprocessedInserted = false
            val scannedList = deduplicatedMedia.values.toList()
            if (scannedList.isNotEmpty()) {
                scannedList.chunked(250).forEach { chunk ->
                    val rowIds = mediaDao.insertMediaList(chunk)
                    if (rowIds.any { it != -1L }) {
                        anyNewUnprocessedInserted = true
                    }
                }
            }

            // Only delete mediaId rows from Room if the ID is absent from the newly scanned set
            // AND ContentResolver.openFileDescriptor(Uri.parse(uri), "r") throws an exception.
            val existingRecords = mediaDao.getAllMediaIdsAndUris()
            val scannedIds = deduplicatedMedia.keys
            val confirmedDeletedIds = mutableListOf<Long>()

            for (record in existingRecords) {
                if (!scannedIds.contains(record.mediaId)) {
                    val isDeleted = try {
                        context.contentResolver.openFileDescriptor(Uri.parse(record.uri), "r")?.use {
                            false
                        } ?: false
                    } catch (_: Exception) {
                        true
                    }
                    if (isDeleted) {
                        confirmedDeletedIds.add(record.mediaId)
                    }
                }
            }

            if (confirmedDeletedIds.isNotEmpty()) {
                confirmedDeletedIds.chunked(250).forEach { chunk ->
                    mediaDao.deleteMediaByIds(chunk)
                }
            }

            // Trigger FaceIndexingWorker with ExistingWorkPolicy.APPEND_OR_REPLACE if any newly inserted rows
            // (or existing pending rows) have isProcessedForFaces = false
            if (anyNewUnprocessedInserted || mediaDao.getUnindexedCountSync() > 0) {
                enqueueFaceIndexingWorker(ExistingWorkPolicy.APPEND_OR_REPLACE)
            }

            scannedList.size
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan MediaStore", e)
            0
        }
    }

    private fun queryImagesTable(
        imagesCollectionUri: Uri,
        deduplicatedMedia: LinkedHashMap<Long, MediaEntity>
    ) {
        try {
            val projectionList = mutableListOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                projectionList.add(MediaStore.MediaColumns.IS_TRASHED)
                projectionList.add(MediaStore.MediaColumns.IS_PENDING)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projectionList.add(MediaStore.MediaColumns.IS_PENDING)
            }

            val selection = buildTrashAndPendingSelection(null)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                imagesCollectionUri,
                projectionList.toTypedArray(),
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val mimeTypeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val displayNameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                } else -1
                val trashedCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                } else -1

                while (cursor.moveToNext()) {
                    if (isTrashedOrPending(cursor, trashedCol, pendingCol)) continue

                    val id = cursor.getLong(idCol)
                    if (deduplicatedMedia.containsKey(id)) continue

                    val displayName = cursor.getStringOrNull(displayNameCol) ?: "Image_$id.jpg"
                    val rawMime = cursor.getStringOrNull(mimeTypeCol)
                    val mimeType = inferMimeType(rawMime, displayName, isVideoDefault = false)
                    val dateAdded = cursor.getLongOrNull(dateAddedCol) ?: (System.currentTimeMillis() / 1000L)
                    val rawWidth = cursor.getIntOrNull(widthCol) ?: 0
                    val rawHeight = cursor.getIntOrNull(heightCol) ?: 0
                    val width = if (rawWidth > 0) rawWidth else DEFAULT_WIDTH
                    val height = if (rawHeight > 0) rawHeight else DEFAULT_HEIGHT
                    val itemUri = ContentUris.withAppendedId(imagesCollectionUri, id)

                    deduplicatedMedia[id] = MediaEntity(
                        mediaId = id,
                        uri = itemUri.toString(),
                        displayName = displayName,
                        mimeType = mimeType,
                        isVideo = false,
                        dateAddedSeconds = dateAdded,
                        width = width,
                        height = height,
                        durationMs = null,
                        isProcessedForFaces = false,
                        hasFaces = false,
                        isFavorite = false,
                        isBookmarked = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying Images table for $imagesCollectionUri", e)
        }
    }

    private fun queryVideoTable(
        videoCollectionUri: Uri,
        deduplicatedMedia: LinkedHashMap<Long, MediaEntity>
    ) {
        try {
            val projectionList = mutableListOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DISPLAY_NAME
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                projectionList.add(MediaStore.MediaColumns.IS_TRASHED)
                projectionList.add(MediaStore.MediaColumns.IS_PENDING)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projectionList.add(MediaStore.MediaColumns.IS_PENDING)
            }

            val selection = buildTrashAndPendingSelection(null)
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                videoCollectionUri,
                projectionList.toTypedArray(),
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val mimeTypeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val displayNameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                } else -1
                val trashedCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                } else -1

                while (cursor.moveToNext()) {
                    if (isTrashedOrPending(cursor, trashedCol, pendingCol)) continue

                    val id = cursor.getLong(idCol)
                    if (deduplicatedMedia.containsKey(id)) continue

                    val displayName = cursor.getStringOrNull(displayNameCol) ?: "Video_$id.mp4"
                    val rawMime = cursor.getStringOrNull(mimeTypeCol)
                    val mimeType = inferMimeType(rawMime, displayName, isVideoDefault = true)
                    val dateAdded = cursor.getLongOrNull(dateAddedCol) ?: (System.currentTimeMillis() / 1000L)
                    val rawWidth = cursor.getIntOrNull(widthCol) ?: 0
                    val rawHeight = cursor.getIntOrNull(heightCol) ?: 0
                    val width = if (rawWidth > 0) rawWidth else DEFAULT_WIDTH
                    val height = if (rawHeight > 0) rawHeight else DEFAULT_HEIGHT
                    val duration = cursor.getLongOrNull(durationCol)
                    val itemUri = ContentUris.withAppendedId(videoCollectionUri, id)

                    deduplicatedMedia[id] = MediaEntity(
                        mediaId = id,
                        uri = itemUri.toString(),
                        displayName = displayName,
                        mimeType = mimeType,
                        isVideo = true,
                        dateAddedSeconds = dateAdded,
                        width = width,
                        height = height,
                        durationMs = duration,
                        isProcessedForFaces = false,
                        hasFaces = false,
                        isFavorite = false,
                        isBookmarked = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying Video table for $videoCollectionUri", e)
        }
    }

    private fun queryFilesFallbackTable(
        filesCollectionUri: Uri,
        imagesCollectionUri: Uri,
        videoCollectionUri: Uri,
        deduplicatedMedia: LinkedHashMap<Long, MediaEntity>
    ) {
        try {
            val projectionList = mutableListOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Video.VideoColumns.DURATION,
                MediaStore.Files.FileColumns.DISPLAY_NAME
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                projectionList.add(MediaStore.MediaColumns.IS_TRASHED)
                projectionList.add(MediaStore.MediaColumns.IS_PENDING)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projectionList.add(MediaStore.MediaColumns.IS_PENDING)
            }

            val baseFilter = "(" +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO} OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'image/%' OR " +
                "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'video/%'" +
                ")"
            val selection = buildTrashAndPendingSelection(baseFilter)
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

            context.contentResolver.query(
                filesCollectionUri,
                projectionList.toTypedArray(),
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val mediaTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val mimeTypeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                val displayNameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pendingCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                } else -1
                val trashedCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                } else -1

                while (cursor.moveToNext()) {
                    if (isTrashedOrPending(cursor, trashedCol, pendingCol)) continue

                    val id = cursor.getLong(idCol)
                    if (deduplicatedMedia.containsKey(id)) continue

                    val mediaType = cursor.getIntOrNull(mediaTypeCol) ?: MediaStore.Files.FileColumns.MEDIA_TYPE_NONE
                    val displayName = cursor.getStringOrNull(displayNameCol) ?: "Media_$id"
                    val rawMime = cursor.getStringOrNull(mimeTypeCol)

                    val isVideoByTypeOrMime = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ||
                        rawMime?.startsWith("video/", ignoreCase = true) == true ||
                        isVideoExtension(displayName)

                    val mimeType = inferMimeType(rawMime, displayName, isVideoDefault = isVideoByTypeOrMime)
                    val isVideo = isVideoByTypeOrMime || mimeType.startsWith("video/", ignoreCase = true)

                    val dateAdded = cursor.getLongOrNull(dateAddedCol) ?: (System.currentTimeMillis() / 1000L)
                    val rawWidth = cursor.getIntOrNull(widthCol) ?: 0
                    val rawHeight = cursor.getIntOrNull(heightCol) ?: 0
                    val width = if (rawWidth > 0) rawWidth else DEFAULT_WIDTH
                    val height = if (rawHeight > 0) rawHeight else DEFAULT_HEIGHT
                    val duration = if (isVideo) cursor.getLongOrNull(durationCol) else null

                    val itemUri = when (mediaType) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ContentUris.withAppendedId(videoCollectionUri, id)
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ContentUris.withAppendedId(imagesCollectionUri, id)
                        else -> ContentUris.withAppendedId(
                            if (isVideo) videoCollectionUri else imagesCollectionUri,
                            id
                        )
                    }

                    deduplicatedMedia[id] = MediaEntity(
                        mediaId = id,
                        uri = itemUri.toString(),
                        displayName = displayName,
                        mimeType = mimeType,
                        isVideo = isVideo,
                        dateAddedSeconds = dateAdded,
                        width = width,
                        height = height,
                        durationMs = duration,
                        isProcessedForFaces = false,
                        hasFaces = false,
                        isFavorite = false,
                        isBookmarked = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying Files fallback table for $filesCollectionUri", e)
        }
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
        cursor: android.database.Cursor,
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
        if (!rawMime.isNullOrBlank()) return rawMime
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

    private fun isVideoExtension(displayName: String): Boolean {
        val ext = displayName.substringAfterLast('.', "").lowercase()
        return ext == "mp4" || ext == "mkv" || ext == "mov" || ext == "3gp"
    }

    private fun android.database.Cursor.getStringOrNull(colIndex: Int): String? {
        return if (colIndex >= 0 && !isNull(colIndex)) getString(colIndex) else null
    }

    private fun android.database.Cursor.getIntOrNull(colIndex: Int): Int? {
        return if (colIndex >= 0 && !isNull(colIndex)) getInt(colIndex) else null
    }

    private fun android.database.Cursor.getLongOrNull(colIndex: Int): Long? {
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
