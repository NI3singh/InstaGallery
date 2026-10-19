// InstaGallery v1.0

package com.example.data.media

import android.content.ContentUris
import android.content.Context
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
    }

    suspend fun scanAndReconcile(): Int = withContext(Dispatchers.IO) {
        try {
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projectionList = mutableListOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.WIDTH,
                MediaStore.Files.FileColumns.HEIGHT,
                MediaStore.Video.VideoColumns.DURATION,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projectionList.add(MediaStore.MediaColumns.IS_PENDING)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                projectionList.add(MediaStore.MediaColumns.IS_TRASHED)
            }

            val projection = projectionList.toTypedArray()

            val selection = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) AND ${MediaStore.Files.FileColumns.SIZE} > 1024"
            val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

            val scannedMedia = mutableListOf<MediaEntity>()
            val activeIds = mutableListOf<Long>()

            context.contentResolver.query(
                collectionUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
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
                    if (pendingCol >= 0 && !cursor.isNull(pendingCol) && cursor.getInt(pendingCol) == 1) {
                        continue
                    }
                    if (trashedCol >= 0 && !cursor.isNull(trashedCol) && cursor.getInt(trashedCol) == 1) {
                        continue
                    }

                    val id = cursor.getLong(idCol)
                    val mediaType = cursor.getInt(mediaTypeCol)
                    val isVideo = (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)

                    val duration = if (isVideo && durationCol >= 0 && !cursor.isNull(durationCol)) {
                        cursor.getLong(durationCol)
                    } else null

                    // Skip corrupted or 0-second stub video files that would render black in Reels
                    if (isVideo && (duration == null || duration < 400L)) {
                        continue
                    }

                    activeIds.add(id)

                    val contentUri = if (isVideo) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }

                    val mimeType = if (mimeTypeCol >= 0 && !cursor.isNull(mimeTypeCol)) {
                        cursor.getString(mimeTypeCol) ?: if (isVideo) "video/mp4" else "image/jpeg"
                    } else {
                        if (isVideo) "video/mp4" else "image/jpeg"
                    }

                    val dateAdded = if (dateAddedCol >= 0 && !cursor.isNull(dateAddedCol)) {
                        cursor.getLong(dateAddedCol)
                    } else {
                        System.currentTimeMillis() / 1000L
                    }

                    val width = if (widthCol >= 0 && !cursor.isNull(widthCol)) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0 && !cursor.isNull(heightCol)) cursor.getInt(heightCol) else 0

                    val displayName = if (displayNameCol >= 0 && !cursor.isNull(displayNameCol)) {
                        cursor.getString(displayNameCol) ?: "Media_$id"
                    } else "Media_$id"

                    scannedMedia.add(
                        MediaEntity(
                            mediaId = id,
                            uri = contentUri.toString(),
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
                    )
                }
            }

            // Insert newly discovered files in chunks of 250 to avoid SQLite variable limits
            if (scannedMedia.isNotEmpty()) {
                scannedMedia.chunked(250).forEach { chunk ->
                    mediaDao.insertMediaList(chunk)
                }
            }

            // Clean up deleted items from Room safely
            val existingIds = mediaDao.getAllMediaIds()
            val activeIdSet = activeIds.toHashSet()
            val deletedIds = existingIds.filter { !activeIdSet.contains(it) }
            if (deletedIds.isNotEmpty()) {
                deletedIds.chunked(250).forEach { chunk ->
                    mediaDao.deleteMediaByIds(chunk)
                }
            }

            // Enqueue expedited FaceIndexingWorker to process unindexed items
            enqueueFaceIndexingWorker(replace = false)

            scannedMedia.size
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan MediaStore", e)
            0
        }
    }

    fun enqueueFaceIndexingWorker(replace: Boolean = false) {
        val workRequest = OneTimeWorkRequestBuilder<FaceIndexingWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            FACE_INDEXING_WORK_NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
}
