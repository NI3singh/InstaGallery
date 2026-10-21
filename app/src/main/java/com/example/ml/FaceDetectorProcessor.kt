// InstaGallery v1.0
package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.data.local.entity.MediaEntity
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
import kotlin.math.max
import kotlin.math.roundToInt

data class DetectedAlignedFace(
    val alignedFaceBitmap: Bitmap, // 112x112 tight face crop for TFLite
    val avatarCropBitmap: Bitmap,  // 200x200 unrotated square portrait crop for UI avatar
    val boundingBox: Rect,
    val qualityScore: Float,       // 0.0f..1.0f
    val headEulerY: Float,         // Yaw
    val headEulerZ: Float,         // Roll
    val isStrictAnchorCandidate: Boolean = true,
    val rawFace: Face? = null
)

class FaceDetectorProcessor(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectorProcessor"
        private const val TARGET_MAX_DIMENSION = 1600
        private const val MIN_DECODED_DIMENSION = 1280
        private const val MAX_DECODED_DIMENSION = 1920

        private const val MIN_FACE_BOX_PX = 48
        private const val MAX_YAW_DEGREES = 38f
        private const val MAX_ROLL_DEGREES = 32f

        const val CANONICAL_FACE_SIZE = 112
        const val AVATAR_CROP_SIZE = 200
    }

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.045f)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Photos-Only Rule (Zero Video Scanning).
     * Immediately returns emptyList() if media.isVideo is true.
     */
    suspend fun processMedia(media: MediaEntity): List<DetectedAlignedFace> {
        if (media.isVideo) return emptyList()
        return detectAndAlignFacesInPhoto(media.uri)
    }

    suspend fun processMediaItem(
        uriString: String,
        isVideo: Boolean,
        durationMs: Long? = null
    ): List<DetectedAlignedFace> {
        if (isVideo) return emptyList()
        return detectAndAlignFacesInPhoto(uriString)
    }

    suspend fun detectAndAlignFacesInPhoto(uriString: String): List<DetectedAlignedFace> {
        val bitmap = decodePhotoBitmap(uriString) ?: return emptyList()
        return try {
            detectAndAlignFacesFromBitmap(bitmap)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    suspend fun detectAndAlignFacesFromBitmap(bitmap: Bitmap): List<DetectedAlignedFace> {
        val rawFaces = runMlKitDetection(bitmap)
        if (rawFaces.isEmpty()) return emptyList()

        val results = mutableListOf<DetectedAlignedFace>()
        for (face in rawFaces) {
            if (!passesHumanFaceGate(face)) continue

            val (aligned112, avatar200) = cropFaultTolerantFacePair(bitmap, face) ?: continue
            val qualityScore = computeFaceQuality(face, bitmap.width, bitmap.height)
            val isAnchor = qualityScore >= 0.45f && face.boundingBox.width() >= 52

            results.add(
                DetectedAlignedFace(
                    alignedFaceBitmap = aligned112,
                    avatarCropBitmap = avatar200,
                    boundingBox = Rect(face.boundingBox),
                    qualityScore = qualityScore,
                    headEulerY = face.headEulerAngleY,
                    headEulerZ = face.headEulerAngleZ,
                    isStrictAnchorCandidate = isAnchor,
                    rawFace = face
                )
            )
        }
        return results
    }

    private suspend fun runMlKitDetection(bitmap: Bitmap): List<Face> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { cont ->
                detector.process(image)
                    .addOnSuccessListener { detected ->
                        if (cont.isActive) cont.resume(detected)
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit face detection error", e)
            emptyList()
        }
    }

    /**
     * Section 2: Fault-tolerant human face validation gate so real family photos and group shots are NOT dropped:
     * 1. Minimum Size: box.width() >= 48 && box.height() >= 48 on the 1600px decoded bitmap.
     * 2. Pose Angles: abs(face.headEulerAngleY) <= 38f && abs(face.headEulerAngleZ) <= 32f (no filter on X).
     * 3. Landmarks Required: LEFT_EYE != null && RIGHT_EYE != null && NOSE_BASE != null AND at least one mouth landmark.
     * 4. Eye Open Probability: Do NOT reject when null! Only reject if BOTH are non-null and both < 0.08f.
     * 5. Anatomical Order: Verify eyeMidY < noseBase.y && noseBase.y < mouthMidY.
     */
    private fun passesHumanFaceGate(face: Face): Boolean {
        val box = face.boundingBox
        if (box.width() < MIN_FACE_BOX_PX || box.height() < MIN_FACE_BOX_PX) {
            return false
        }

        if (abs(face.headEulerAngleY) > MAX_YAW_DEGREES ||
            abs(face.headEulerAngleZ) > MAX_ROLL_DEGREES
        ) {
            return false
        }

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return false
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return false
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return false

        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        if (mouthBottom == null && mouthLeft == null && mouthRight == null) {
            return false
        }

        val leftOpen = face.leftEyeOpenProbability
        val rightOpen = face.rightEyeOpenProbability
        if (leftOpen != null && rightOpen != null && leftOpen < 0.08f && rightOpen < 0.08f) {
            return false
        }

        val eyeMidY = (leftEye.y + rightEye.y) * 0.5f
        val mouthMidY = when {
            mouthBottom != null -> mouthBottom.y
            mouthLeft != null && mouthRight != null -> (mouthLeft.y + mouthRight.y) * 0.5f
            mouthLeft != null -> mouthLeft.y
            mouthRight != null -> mouthRight.y
            else -> return false
        }

        if (!(eyeMidY < noseBase.y && noseBase.y < mouthMidY)) {
            return false
        }

        return true
    }

    /**
     * Simple, Fault-Tolerant Cropping:
     * - For avatarCropBitmap (200x200): crop a padded square around (box.exactCenterX(), box.exactCenterY())
     *   with halfSide = max(box.width(), box.height()) * 0.65f, clamped to [0, bitmap.width] and [0, bitmap.height],
     *   and scale to 200x200.
     * - For alignedFaceBitmap (112x112): crop the tight face square around (box.exactCenterX(), box.exactCenterY())
     *   with halfSide = max(box.width(), box.height()) * 0.52f, clamped to [0, bitmap.width] and [0, bitmap.height],
     *   and scale to 112x112 using Bitmap.createScaledBitmap(tightCrop, 112, 112, true).
     *   (If abs(angleDeg) in 5f..25f, rotate tightCrop around (tightCrop.width / 2f, tightCrop.height / 2f)
     *   and take the inner 85% square before scaling to 112x112).
     */
    private fun cropFaultTolerantFacePair(bitmap: Bitmap, face: Face): Pair<Bitmap, Bitmap>? {
        val box = face.boundingBox

        val avatar200 = cropSquareAndScale(
            bitmap = bitmap,
            centerX = box.exactCenterX(),
            centerY = box.exactCenterY(),
            halfSide = max(box.width(), box.height()) * 0.65f,
            targetSize = AVATAR_CROP_SIZE
        ) ?: return null

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val angleDeg = if (leftEye != null && rightEye != null && rightEye.x > leftEye.x) {
            Math.toDegrees(
                atan2(
                    (rightEye.y - leftEye.y).toDouble(),
                    (rightEye.x - leftEye.x).toDouble()
                )
            ).toFloat()
        } else {
            0f
        }

        val aligned112 = cropTightAligned112(bitmap, box, angleDeg)
        if (aligned112 == null) {
            if (!avatar200.isRecycled) avatar200.recycle()
            return null
        }

        return aligned112 to avatar200
    }

    private fun cropSquareAndScale(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        halfSide: Float,
        targetSize: Int
    ): Bitmap? {
        return try {
            val left = (centerX - halfSide).roundToInt().coerceIn(0, bitmap.width - 1)
            val top = (centerY - halfSide).roundToInt().coerceIn(0, bitmap.height - 1)
            val right = (centerX + halfSide).roundToInt().coerceIn(left + 1, bitmap.width)
            val bottom = (centerY + halfSide).roundToInt().coerceIn(top + 1, bitmap.height)

            val width = right - left
            val height = bottom - top
            if (width <= 0 || height <= 0) return null

            val rawCrop = Bitmap.createBitmap(bitmap, left, top, width, height)
            val scaled = Bitmap.createScaledBitmap(rawCrop, targetSize, targetSize, true)
            if (scaled != rawCrop && !rawCrop.isRecycled) {
                rawCrop.recycle()
            }
            scaled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop square bitmap", e)
            null
        }
    }

    private fun cropTightAligned112(bitmap: Bitmap, box: Rect, angleDeg: Float): Bitmap? {
        return try {
            val centerX = box.exactCenterX()
            val centerY = box.exactCenterY()
            val halfSide = max(box.width(), box.height()) * 0.52f

            val left = (centerX - halfSide).roundToInt().coerceIn(0, bitmap.width - 1)
            val top = (centerY - halfSide).roundToInt().coerceIn(0, bitmap.height - 1)
            val right = (centerX + halfSide).roundToInt().coerceIn(left + 1, bitmap.width)
            val bottom = (centerY + halfSide).roundToInt().coerceIn(top + 1, bitmap.height)

            val width = right - left
            val height = bottom - top
            if (width <= 0 || height <= 0) return null

            val tightCrop = Bitmap.createBitmap(bitmap, left, top, width, height)

            val absAngle = abs(angleDeg)
            if (absAngle in 5f..25f) {
                val m = Matrix().apply {
                    postRotate(-angleDeg, tightCrop.width / 2f, tightCrop.height / 2f)
                }
                val rotated = Bitmap.createBitmap(tightCrop, 0, 0, tightCrop.width, tightCrop.height, m, true)
                if (rotated != tightCrop && !tightCrop.isRecycled) {
                    tightCrop.recycle()
                }
                val innerW = (rotated.width * 0.85f).roundToInt().coerceAtLeast(1)
                val innerH = (rotated.height * 0.85f).roundToInt().coerceAtLeast(1)
                val innerX = ((rotated.width - innerW) / 2).coerceAtLeast(0)
                val innerY = ((rotated.height - innerH) / 2).coerceAtLeast(0)
                val innerCrop = Bitmap.createBitmap(
                    rotated,
                    innerX,
                    innerY,
                    innerW.coerceAtMost(rotated.width - innerX),
                    innerH.coerceAtMost(rotated.height - innerY)
                )
                if (innerCrop != rotated && !rotated.isRecycled) {
                    rotated.recycle()
                }
                val scaled112 = Bitmap.createScaledBitmap(innerCrop, CANONICAL_FACE_SIZE, CANONICAL_FACE_SIZE, true)
                if (scaled112 != innerCrop && !innerCrop.isRecycled) {
                    innerCrop.recycle()
                }
                scaled112
            } else {
                val scaled112 = Bitmap.createScaledBitmap(tightCrop, CANONICAL_FACE_SIZE, CANONICAL_FACE_SIZE, true)
                if (scaled112 != tightCrop && !tightCrop.isRecycled) {
                    tightCrop.recycle()
                }
                scaled112
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop tight 112x112 aligned face", e)
            null
        }
    }

    suspend fun decodePhotoBitmap(uriString: String): Bitmap? {
        return try {
            decodePhoto(Uri.parse(uriString))
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding photo $uriString", e)
            null
        }
    }

    private fun decodePhoto(uri: Uri): Bitmap? {
        val contentResolver = context.contentResolver

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width <= 0 || height <= 0) return null

        val longestEdge = max(width, height)
        var inSampleSize = 1
        while (longestEdge / (inSampleSize * 2) >= MIN_DECODED_DIMENSION) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var decodedBitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val decodedLongest = max(decodedBitmap.width, decodedBitmap.height)
        if (decodedLongest > MAX_DECODED_DIMENSION) {
            val scale = TARGET_MAX_DIMENSION.toFloat() / decodedLongest.toFloat()
            val targetW = (decodedBitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decodedBitmap.height * scale).toInt().coerceAtLeast(1)
            val rescaled = Bitmap.createScaledBitmap(decodedBitmap, targetW, targetH, true)
            if (rescaled != decodedBitmap) {
                decodedBitmap.recycle()
                decodedBitmap = rescaled
            }
        }

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

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }

        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    suspend fun detectFaces(bitmap: Bitmap, isVideo: Boolean = false): List<Face> {
        if (isVideo) return emptyList()
        return runMlKitDetection(bitmap).filter { passesHumanFaceGate(it) }
    }

    fun computeFaceQuality(face: Face, bitmapWidth: Int = 0, bitmapHeight: Int = 0): Float {
        val yawScore = (1f - (abs(face.headEulerAngleY) / 38f)).coerceIn(0f, 1f)
        val rollScore = (1f - (abs(face.headEulerAngleZ) / 32f)).coerceIn(0f, 1f)
        val sizeScore = ((face.boundingBox.width() - 48f) / 140f).coerceIn(0.15f, 1f)
        val leftOpen = (face.leftEyeOpenProbability ?: 0.75f).coerceIn(0f, 1f)
        val rightOpen = (face.rightEyeOpenProbability ?: 0.75f).coerceIn(0f, 1f)
        val eyeOpenScore = (leftOpen + rightOpen) * 0.5f

        return (yawScore * 0.45f + sizeScore * 0.30f + rollScore * 0.15f + eyeOpenScore * 0.10f)
            .coerceIn(0.1f, 1.0f)
    }
}
