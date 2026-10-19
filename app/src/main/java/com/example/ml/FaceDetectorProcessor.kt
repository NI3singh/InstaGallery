// InstaGallery v1.0
package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class FaceDetectorProcessor(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectorProcessor"
        private const val MAX_DIMENSION = 1024
        private const val MIN_PHOTO_FACE_PIXELS = 52
        private const val MIN_VIDEO_FACE_PIXELS = 68
        const val CANONICAL_FACE_SIZE = 112
    }

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.08f)
            .build()
        FaceDetection.getClient(options)
    }

    suspend fun decodePhotoBitmap(uriString: String): Bitmap? {
        return try {
            decodePhoto(Uri.parse(uriString))
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding photo $uriString", e)
            null
        }
    }

    suspend fun decodeVideoKeyframes(uriString: String, durationMs: Long?): List<Bitmap> {
        return try {
            decodeVideoFrames(Uri.parse(uriString), durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding video keyframes $uriString", e)
            emptyList()
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

        // Step 3: Read and apply EXIF orientation safely
        val orientation = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        return rotateBitmap(decodedBitmap, orientation)
    }

    private fun decodeVideoFrames(uri: Uri, durationMs: Long?): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()
        return try {
            retriever.setDataSource(context, uri)
            val dur = durationMs ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2000L
            val timestampsUs = if (dur > 2500L) {
                listOf((dur * 0.25).toLong() * 1000L, (dur * 0.60).toLong() * 1000L)
            } else {
                listOf((dur / 2L).coerceAtLeast(300L) * 1000L)
            }

            for (timeUs in timestampsUs) {
                val rawFrame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue
                val width = rawFrame.width
                val height = rawFrame.height
                val longestEdge = max(width, height)

                val scaled = if (longestEdge > MAX_DIMENSION) {
                    val scale = MAX_DIMENSION.toFloat() / longestEdge
                    val s = Bitmap.createScaledBitmap(
                        rawFrame,
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    if (s != rawFrame) rawFrame.recycle()
                    s
                } else {
                    rawFrame
                }
                frames.add(scaled)
            }
            frames
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve video frames for $uri", e)
            frames
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

    /**
     * Detects and validates clear human faces with verified anatomical landmarks.
     */
    suspend fun detectFaces(bitmap: Bitmap, isVideo: Boolean = false): List<Face> {
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

            val minBox = if (isVideo) MIN_VIDEO_FACE_PIXELS else MIN_PHOTO_FACE_PIXELS
            val maxYaw = if (isVideo) 28f else 40f
            val minEyeDist = if (isVideo) 20f else 14f

            faces.filter { face ->
                val box = face.boundingBox
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position

                if (leftEye == null || rightEye == null || nose == null) {
                    false
                } else {
                    val eyeDist = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y)
                    box.width() >= minBox &&
                        box.height() >= minBox &&
                        eyeDist >= minEyeDist &&
                        abs(face.headEulerAngleY) <= maxYaw &&
                        abs(face.headEulerAngleZ) <= 35f
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Face detection error", e)
            emptyList()
        }
    }

    /**
     * Computes a [0..1] quality score for selecting the best profile avatar crop.
     */
    fun computeFaceQuality(face: Face, bitmapWidth: Int, bitmapHeight: Int): Float {
        val box = face.boundingBox
        val areaRatio = ((box.width().toFloat() * box.height().toFloat()) /
            (bitmapWidth.toFloat() * bitmapHeight.toFloat()).coerceAtLeast(1f)).coerceIn(0f, 0.5f) * 2f
        val yawScore = (1f - (abs(face.headEulerAngleY) / 45f)).coerceIn(0f, 1f)
        val rollScore = (1f - (abs(face.headEulerAngleZ) / 40f)).coerceIn(0f, 1f)
        val leftOpen = (face.leftEyeOpenProbability ?: 0.7f).coerceIn(0f, 1f)
        val rightOpen = (face.rightEyeOpenProbability ?: 0.7f).coerceIn(0f, 1f)
        val smileBonus = ((face.smilingProbability ?: 0.3f).coerceIn(0f, 1f)) * 0.15f

        return (yawScore * 0.40f + rollScore * 0.15f + ((leftOpen + rightOpen) * 0.5f) * 0.25f + areaRatio * 0.20f + smileBonus)
    }

    /**
     * Performs an exact affine similarity warp using LEFT_EYE and RIGHT_EYE landmarks
     * so the left eye lands at (35, 42) and right eye lands at (77, 42) on a 112x112 canvas.
     * This ensures every anatomical region aligns pixel-for-pixel across all photos of a person.
     */
    fun alignFaceCanonical(bitmap: Bitmap, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        if (leftEye != null && rightEye != null) {
            val dx = rightEye.x - leftEye.x
            val dy = rightEye.y - leftEye.y
            val dist = hypot(dx, dy)
            if (dist >= 10f) {
                val targetLeftX = 35f
                val targetRightX = 77f
                val targetEyeY = 42f
                val desiredDist = targetRightX - targetLeftX // 42px
                val scale = desiredDist / dist
                val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                val eyesCenterX = (leftEye.x + rightEye.x) * 0.5f
                val eyesCenterY = (leftEye.y + rightEye.y) * 0.5f
                val targetCenterX = (targetLeftX + targetRightX) * 0.5f // 56f

                val matrix = Matrix().apply {
                    postTranslate(-eyesCenterX, -eyesCenterY)
                    postRotate(-angleDeg)
                    postScale(scale, scale)
                    postTranslate(targetCenterX, targetEyeY)
                }

                return try {
                    val aligned = Bitmap.createBitmap(CANONICAL_FACE_SIZE, CANONICAL_FACE_SIZE, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(aligned)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bitmap, matrix, paint)
                    aligned
                } catch (e: Exception) {
                    Log.e(TAG, "Affine eye alignment fallback", e)
                    cropFaceAvatar(bitmap, face)
                }
            }
        }
        return cropFaceAvatar(bitmap, face)
    }

    /**
     * Crops and deskews the face region with an aesthetic 18% portrait margin for circular avatars.
     */
    fun cropFaceAvatar(bitmap: Bitmap, face: Face): Bitmap? {
        val box = face.boundingBox
        val padX = (box.width() * 0.18f).toInt()
        val padY = (box.height() * 0.18f).toInt()

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
            Log.e(TAG, "Failed to crop face avatar", e)
            null
        }
    }
}
