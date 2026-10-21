// InstaGallery v1.0
package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DetectedAlignedFace(
    val alignedFaceBitmap: Bitmap, // 112x112 origin-centered eye-aligned upright face bitmap for TFLite
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

        private const val MIN_FACE_BOX_PX = 68
        private const val MIN_ASPECT_RATIO = 0.68f
        private const val MAX_ASPECT_RATIO = 1.32f
        private const val MAX_YAW_DEGREES = 30f
        private const val MAX_ROLL_DEGREES = 25f
        private const val MAX_PITCH_DEGREES = 25f

        const val CANONICAL_FACE_SIZE = 112
        const val AVATAR_CROP_SIZE = 200
    }

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.065f)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Section 2.1: Photos-Only Rule (Zero Video Scanning).
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
            if (!passesStrictHumanFaceGate(face)) continue

            val (aligned112, avatar200) = cropAndAlignOriginCentered(bitmap, face) ?: continue
            val qualityScore = computeFaceQuality(face, bitmap.width, bitmap.height)

            results.add(
                DetectedAlignedFace(
                    alignedFaceBitmap = aligned112,
                    avatarCropBitmap = avatar200,
                    boundingBox = Rect(face.boundingBox),
                    qualityScore = qualityScore,
                    headEulerY = face.headEulerAngleY,
                    headEulerZ = face.headEulerAngleZ,
                    isStrictAnchorCandidate = true,
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
     * Section 2.2: Strict 5-Landmark Human Face Gate.
     * Rejects any candidate unless ALL 5 criteria pass:
     * 1. Minimum Box Size & Aspect Ratio: width >= 68, height >= 68, aspect in 0.68f..1.32f
     * 2. Strict Pose Limits: abs(Y) <= 30f, abs(Z) <= 25f, abs(X) <= 25f
     * 3. All 5 Facial Landmarks Required: LEFT_EYE, RIGHT_EYE, NOSE_BASE, MOUTH_LEFT, MOUTH_RIGHT
     * 4. Human Eye Classification Required: non-null leftOpen & rightOpen, not both < 0.15f
     * 5. Anatomical Geometry Verification:
     *    - eyeDist in [box.width() * 0.22f .. box.width() * 0.78f]
     *    - eyeMidY < noseBase.y && noseBase.y < mouthMidY
     *    - (mouthMidY - eyeMidY) >= box.height() * 0.22f
     */
    private fun passesStrictHumanFaceGate(face: Face): Boolean {
        val box = face.boundingBox
        if (box.width() < MIN_FACE_BOX_PX || box.height() < MIN_FACE_BOX_PX) {
            return false
        }

        val aspect = box.width().toFloat() / box.height().toFloat()
        if (aspect !in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO) {
            return false
        }

        if (abs(face.headEulerAngleY) > MAX_YAW_DEGREES ||
            abs(face.headEulerAngleZ) > MAX_ROLL_DEGREES ||
            abs(face.headEulerAngleX) > MAX_PITCH_DEGREES
        ) {
            return false
        }

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return false
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return false
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return false
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return false
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return false

        val leftOpen = face.leftEyeOpenProbability
        val rightOpen = face.rightEyeOpenProbability
        if (leftOpen == null || rightOpen == null || (leftOpen < 0.15f && rightOpen < 0.15f)) {
            return false
        }

        val eyeDist = hypot(
            (rightEye.x - leftEye.x).toDouble(),
            (rightEye.y - leftEye.y).toDouble()
        ).toFloat()
        val boxW = box.width().toFloat()
        if (eyeDist < boxW * 0.22f || eyeDist > boxW * 0.78f) {
            return false
        }

        val eyeMidY = (leftEye.y + rightEye.y) * 0.5f
        val mouthMidY = (mouthLeft.y + mouthRight.y) * 0.5f
        val boxH = box.height().toFloat()

        if (!(eyeMidY < noseBase.y && noseBase.y < mouthMidY)) {
            return false
        }
        if ((mouthMidY - eyeMidY) < boxH * 0.22f) {
            return false
        }

        return true
    }

    /**
     * Section 2.3: Origin-Centered Face Crop & Alignment (Fixes Shifted/Hand Crops)
     * - UI Avatar (avatarCropBitmap, 200x200): unrotated square around (box.exactCenterX(), box.exactCenterY())
     *   with halfSide = max(box.width(), box.height()) * 0.68f, clamped to bitmap bounds and scaled to 200x200.
     * - TFLite Input (alignedFaceBitmap, 112x112): origin-centered Matrix translating (-cx, -cy),
     *   rotating (-angleDeg), scaling (112f / cropSize), and translating to (56f, 56f).
     */
    private fun cropAndAlignOriginCentered(bitmap: Bitmap, face: Face): Pair<Bitmap, Bitmap>? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position ?: return null
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position ?: return null

        val box = face.boundingBox

        // 1. UI Avatar (200x200, unrotated)
        val avatar200 = cropUnrotatedAvatar200(bitmap, box) ?: return null

        // 2. TFLite Input (112x112, origin-centered affine warp)
        val eyeMidY = (leftEye.y + rightEye.y) * 0.5f
        val mouthMidY = (mouthLeft.y + mouthRight.y) * 0.5f
        val cx = (leftEye.x + rightEye.x) * 0.5f
        val cy = (eyeMidY * 0.45f + mouthMidY * 0.55f)

        val angleDeg = Math.toDegrees(
            atan2(
                (rightEye.y - leftEye.y).toDouble(),
                (rightEye.x - leftEye.x).toDouble()
            )
        ).toFloat().coerceIn(-20f, 20f)

        val cropSize = (max(box.width(), box.height()) * 1.05f).coerceAtLeast(1f)
        val aligned112 = try {
            val out = Bitmap.createBitmap(CANONICAL_FACE_SIZE, CANONICAL_FACE_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val m = Matrix()
            m.postTranslate(-cx, -cy)
            m.postRotate(-angleDeg)
            val scale = 112f / cropSize
            m.postScale(scale, scale)
            m.postTranslate(56f, 56f)
            canvas.drawBitmap(bitmap, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            out
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create aligned 112x112 bitmap", e)
            if (!avatar200.isRecycled) avatar200.recycle()
            return null
        }

        return aligned112 to avatar200
    }

    private fun cropUnrotatedAvatar200(bitmap: Bitmap, box: Rect): Bitmap? {
        return try {
            val centerX = box.exactCenterX()
            val centerY = box.exactCenterY()
            val halfSide = max(box.width(), box.height()) * 0.68f

            val left = (centerX - halfSide).roundToInt().coerceIn(0, bitmap.width - 1)
            val top = (centerY - halfSide).roundToInt().coerceIn(0, bitmap.height - 1)
            val right = (centerX + halfSide).roundToInt().coerceIn(left + 1, bitmap.width)
            val bottom = (centerY + halfSide).roundToInt().coerceIn(top + 1, bitmap.height)

            val width = right - left
            val height = bottom - top
            if (width <= 0 || height <= 0) return null

            val rawCrop = Bitmap.createBitmap(bitmap, left, top, width, height)
            val scaled200 = Bitmap.createScaledBitmap(rawCrop, AVATAR_CROP_SIZE, AVATAR_CROP_SIZE, true)
            if (scaled200 != rawCrop && !rawCrop.isRecycled) {
                rawCrop.recycle()
            }
            scaled200
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop 200x200 UI avatar", e)
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
        return runMlKitDetection(bitmap).filter { passesStrictHumanFaceGate(it) }
    }

    fun computeFaceQuality(face: Face, bitmapWidth: Int = 0, bitmapHeight: Int = 0): Float {
        val yawScore = (1f - (abs(face.headEulerAngleY) / 30f)).coerceIn(0f, 1f)
        val rollScore = (1f - (abs(face.headEulerAngleZ) / 25f)).coerceIn(0f, 1f)
        val sizeScore = ((face.boundingBox.width() - 68f) / 140f).coerceIn(0.1f, 1f)
        val leftOpen = (face.leftEyeOpenProbability ?: 0.7f).coerceIn(0f, 1f)
        val rightOpen = (face.rightEyeOpenProbability ?: 0.7f).coerceIn(0f, 1f)
        val eyeOpenScore = (leftOpen + rightOpen) * 0.5f

        return (yawScore * 0.45f + sizeScore * 0.30f + rollScore * 0.15f + eyeOpenScore * 0.10f)
            .coerceIn(0.05f, 1.0f)
    }
}
