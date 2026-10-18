package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

class FaceDetectorProcessor(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectorProcessor"
        private const val MAX_DIMENSION = 1024
        private const val MIN_FACE_PIXELS = 48
    }

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.08f)
            .build()
        FaceDetection.getClient(options)
    }

    suspend fun decodeBitmap(uriString: String, isVideo: Boolean, durationMs: Long?): Bitmap? {
        val uri = Uri.parse(uriString)
        return try {
            if (isVideo) {
                decodeVideoFrame(uri, durationMs)
            } else {
                decodePhoto(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap for $uriString", e)
            null
        }
    }

    private fun decodePhoto(uri: Uri): Bitmap? {
        val contentResolver = context.contentResolver

        // Step 1: Decode bounds
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) return null

        // Calculate inSampleSize
        val longestEdge = max(width, height)
        var inSampleSize = 1
        while (longestEdge / (inSampleSize * 2) >= MAX_DIMENSION) {
            inSampleSize *= 2
        }

        // Step 2: Decode scaled bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decodedBitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // Step 3: Handle EXIF orientation
        val orientation = contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        return rotateBitmap(decodedBitmap, orientation)
    }

    private fun decodeVideoFrame(uri: Uri, durationMs: Long?): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val timeUs = if (durationMs != null && durationMs > 0) {
                (durationMs / 3L) * 1000L
            } else {
                1_000_000L // 1 second into video
            }

            val rawFrame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime ?: return null

            val width = rawFrame.width
            val height = rawFrame.height
            val longestEdge = max(width, height)

            if (longestEdge > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / longestEdge
                val scaled = Bitmap.createScaledBitmap(
                    rawFrame,
                    (width * scale).toInt(),
                    (height * scale).toInt(),
                    true
                )
                if (scaled != rawFrame) {
                    rawFrame.recycle()
                }
                scaled
            } else {
                rawFrame
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve video frame for $uri", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    suspend fun detectFaces(bitmap: Bitmap): List<Face> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = suspendCancellableCoroutine<List<Face>> { cont ->
                detector.process(image)
                    .addOnSuccessListener { detected ->
                        if (cont.isActive) cont.resume(detected)
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            }
            faces.filter { face ->
                val box = face.boundingBox
                box.width() >= MIN_FACE_PIXELS && box.height() >= MIN_FACE_PIXELS
            }
        } catch (e: Exception) {
            Log.e(TAG, "Face detection error", e)
            emptyList()
        }
    }

    fun cropFaceBitmap(bitmap: Bitmap, face: Face): Bitmap? {
        val box = face.boundingBox
        val left = max(0, box.left)
        val top = max(0, box.top)
        val right = kotlin.math.min(bitmap.width, box.right)
        val bottom = kotlin.math.min(bitmap.height, box.bottom)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) return null

        return try {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop face bitmap", e)
            null
        }
    }
}
