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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class FaceDetectorProcessor(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectorProcessor"
        private const val MAX_DIMENSION = 1024
        private const val MIN_FACE_PIXELS = 44
    }

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.07f)
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

        // Step 1: Decode bounds only
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) return null

        // Calculate power-of-2 inSampleSize
        val longestEdge = max(width, height)
        var inSampleSize = 1
        while (longestEdge / (inSampleSize * 2) >= MAX_DIMENSION) {
            inSampleSize *= 2
        }

        // Step 2: Decode scaled bitmap in ARGB_8888
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decodedBitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // Step 3: Read and apply EXIF orientation
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
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
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
                box.width() >= MIN_FACE_PIXELS &&
                    box.height() >= MIN_FACE_PIXELS &&
                    abs(face.headEulerAngleY) < 60f // Filter extreme side-profile false positives
            }
        } catch (e: Exception) {
            Log.e(TAG, "Face detection error", e)
            emptyList()
        }
    }

    /**
     * Crops and deskews the face region based on head roll angle (headEulerAngleZ)
     * so both avatar thumbnails and embedding extraction receive an aligned face crop.
     */
    fun cropFaceBitmap(bitmap: Bitmap, face: Face): Bitmap? {
        val box = face.boundingBox
        // Add a slight 8% padding for visual avatar framing while keeping focus on the face
        val padX = (box.width() * 0.08f).toInt()
        val padY = (box.height() * 0.08f).toInt()

        val left = max(0, box.left - padX)
        val top = max(0, box.top - padY)
        val right = min(bitmap.width, box.right + padX)
        val bottom = min(bitmap.height, box.bottom + padY)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) return null

        return try {
            val rawCrop = Bitmap.createBitmap(bitmap, left, top, width, height)
            val rollAngle = face.headEulerAngleZ
            if (abs(rollAngle) in 4f..35f) {
                val matrix = Matrix().apply {
                    postRotate(-rollAngle, rawCrop.width / 2f, rawCrop.height / 2f)
                }
                val aligned = Bitmap.createBitmap(rawCrop, 0, 0, rawCrop.width, rawCrop.height, matrix, true)
                if (aligned != rawCrop) {
                    rawCrop.recycle()
                }
                aligned
            } else {
                rawCrop
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop face bitmap", e)
            null
        }
    }
}
