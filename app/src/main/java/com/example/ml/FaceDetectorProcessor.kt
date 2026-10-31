// InstaGallery v1.0
package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.example.data.local.entity.MediaEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** ML Kit failed on a photo (as opposed to finding no faces). The photo should be retried. */
class FaceDetectionFailedException(cause: Throwable) : Exception("Face detection failed", cause)

data class DetectedAlignedFace(
    val alignedFaceBitmap: Bitmap, // 112x112 face warped onto the ArcFace landmark template for TFLite
    val avatarCropBitmap: Bitmap,  // 200x200 unrotated square portrait crop for UI avatar
    val boundingBox: Rect,
    val qualityScore: Float,       // 0.0f..1.0f
    val headEulerY: Float,         // Yaw
    val headEulerZ: Float,         // Roll
    val isStrictAnchorCandidate: Boolean = true,
    val rawFace: Face? = null,
    val isLandmarkAligned: Boolean = false
)

class FaceDetectorProcessor(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectorProcessor"
        private const val TARGET_MAX_DIMENSION = 1600
        private const val MIN_DECODED_DIMENSION = 1280
        private const val MAX_DECODED_DIMENSION = 1920

        private const val MIN_FACE_BOX_PX = 36
        private const val MAX_YAW_DEGREES = 45f
        private const val MAX_ROLL_DEGREES = 42f

        const val CANONICAL_FACE_SIZE = 112
        const val AVATAR_CROP_SIZE = 200
        private const val SCREENSHOT_MIN_WIDTH_RATIO = 0.22f

        /**
         * A face must clear BOTH bars to start a brand-new profile on its own. Smaller or blurrier
         * faces are still stored and can join an existing profile, or form a new one in Pass-2
         * once the same face recurs across several photos.
         */
        const val NEW_PROFILE_MIN_QUALITY = 0.45f
        const val NEW_PROFILE_MIN_BOX_PX = 52

        fun isStrongEnoughForNewProfile(qualityScore: Float, boxWidthPx: Int): Boolean =
            qualityScore >= NEW_PROFILE_MIN_QUALITY && boxWidthPx >= NEW_PROFILE_MIN_BOX_PX
    }

    private val warpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.035f)
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Photos-Only Rule (Zero Video Scanning).
     * Immediately returns emptyList() if media.isVideo is true or if it is an exported InstaCard template.
     */
    suspend fun processMedia(media: MediaEntity): List<DetectedAlignedFace> {
        if (media.isVideo) return emptyList()
        if (media.displayName.startsWith("InstaCard_", ignoreCase = true)) return emptyList()
        val isScreenshot = media.displayName.contains("screenshot", ignoreCase = true) ||
            media.uri.contains("screenshot", ignoreCase = true)
        return detectAndAlignFacesInPhoto(media.uri, isScreenshot = isScreenshot)
    }

    suspend fun processMediaItem(
        uriString: String,
        isVideo: Boolean,
        durationMs: Long? = null
    ): List<DetectedAlignedFace> {
        if (isVideo) return emptyList()
        val isScreenshot = uriString.contains("screenshot", ignoreCase = true)
        return detectAndAlignFacesInPhoto(uriString, isScreenshot = isScreenshot)
    }

    suspend fun detectAndAlignFacesInPhoto(
        uriString: String,
        isScreenshot: Boolean = uriString.contains("screenshot", ignoreCase = true)
    ): List<DetectedAlignedFace> {
        val bitmap = decodePhotoBitmap(uriString) ?: return emptyList()
        return try {
            detectAndAlignFacesFromBitmap(bitmap, isScreenshot = isScreenshot)
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    suspend fun detectAndAlignFacesFromBitmap(
        bitmap: Bitmap,
        isScreenshot: Boolean = false
    ): List<DetectedAlignedFace> {
        val rawFaces = runMlKitDetectionOrThrow(bitmap)
        if (rawFaces.isEmpty()) return emptyList()

        // If this is a screenshot and ML Kit detects >= 4 small grid-aligned faces
        // (such as a screenshot of the People directory grid), return emptyList() immediately
        // so a screenshot of the People grid never creates 12 clone profiles!
        if (isScreenshot && rawFaces.size >= 4) {
            val smallGridFaces = rawFaces.count { face ->
                face.boundingBox.width().toFloat() / bitmap.width.toFloat() < 0.32f
            }
            if (smallGridFaces >= 4 || rawFaces.size >= 4) {
                return emptyList()
            }
        }

        val results = mutableListOf<DetectedAlignedFace>()
        for (face in rawFaces) {
            // Screenshots: skip tiny UI avatars / grid thumbnails. Real photos rely on the absolute
            // MIN_FACE_BOX_PX gate instead; a relative width cut-off silently dropped most faces in
            // group shots (6.5% of a 1600px photo is a 104px face).
            if (isScreenshot &&
                face.boundingBox.width().toFloat() / bitmap.width.toFloat() < SCREENSHOT_MIN_WIDTH_RATIO
            ) {
                continue
            }
            if (!passesHumanFaceGate(face)) continue

            val cropped = cropFaultTolerantFacePair(bitmap, face) ?: continue
            val qualityScore = computeFaceQuality(face, bitmap.width, bitmap.height)
            val isAnchor = isStrongEnoughForNewProfile(qualityScore, face.boundingBox.width())

            results.add(
                DetectedAlignedFace(
                    alignedFaceBitmap = cropped.aligned112,
                    avatarCropBitmap = cropped.avatar200,
                    boundingBox = Rect(face.boundingBox),
                    qualityScore = qualityScore,
                    headEulerY = face.headEulerAngleY,
                    headEulerZ = face.headEulerAngleZ,
                    isStrictAnchorCandidate = isAnchor,
                    rawFace = face,
                    isLandmarkAligned = cropped.isLandmarkAligned
                )
            )
        }
        return results
    }

    /**
     * Lenient detection for UI helpers: any failure is logged and treated as "no faces".
     * Coroutine cancellation still propagates.
     */
    private suspend fun runMlKitDetection(bitmap: Bitmap): List<Face> {
        return try {
            runMlKitDetectionOrThrow(bitmap)
        } catch (e: FaceDetectionFailedException) {
            emptyList()
        }
    }

    /**
     * Strict detection for indexing. A detector failure throws [FaceDetectionFailedException] so the
     * caller can retry the photo instead of permanently recording it as "no faces".
     * Coroutine cancellation is never swallowed.
     */
    private suspend fun runMlKitDetectionOrThrow(bitmap: Bitmap): List<Face> {
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
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "ML Kit face detection error", t)
            throw FaceDetectionFailedException(t)
        }
    }

    /**
     * Fault-tolerant human face validation gate:
     * 1. Minimum Size: box.width() >= 36 && box.height() >= 36 (supports group & full-body WhatsApp/camera photos).
     * 2. Pose Angles: abs(face.headEulerAngleY) <= 45f && abs(face.headEulerAngleZ) <= 42f.
     * 3. Landmarks: Requires both eyes (or eye + nose base) so real human faces pass reliably.
     * 4. Eye Open Probability: Never rejects null! Only rejects if both eyes are non-null and < 0.05f.
     * 5. Anatomical Order: Verifies eyeMidY < noseBase.y < mouthMidY when upright (abs(roll) <= 28f).
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

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position

        // Require at least both eyes, or one eye + nose base for side-angled faces
        val hasCoreLandmarks = (leftEye != null && rightEye != null) ||
            (noseBase != null && (leftEye != null || rightEye != null))
        if (!hasCoreLandmarks) {
            return false
        }

        val leftOpen = face.leftEyeOpenProbability
        val rightOpen = face.rightEyeOpenProbability
        if (leftOpen != null && rightOpen != null && leftOpen < 0.05f && rightOpen < 0.05f) {
            return false
        }

        // When upright and all landmarks are present, verify vertical anatomical order
        if (leftEye != null && rightEye != null && noseBase != null && abs(face.headEulerAngleZ) <= 28f) {
            val eyeMidY = (leftEye.y + rightEye.y) * 0.5f
            val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
            val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
            val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
            val mouthMidY = when {
                mouthBottom != null -> mouthBottom.y
                mouthLeft != null && mouthRight != null -> (mouthLeft.y + mouthRight.y) * 0.5f
                mouthLeft != null -> mouthLeft.y
                mouthRight != null -> mouthRight.y
                else -> null
            }
            if (mouthMidY != null) {
                val tolerance = box.height() * 0.06f
                if (eyeMidY > noseBase.y + tolerance || noseBase.y > mouthMidY + tolerance) {
                    return false
                }
            }
        }

        return true
    }

    private class CroppedFacePair(
        val aligned112: Bitmap,
        val avatar200: Bitmap,
        val isLandmarkAligned: Boolean
    )

    /**
     * Produces the two bitmaps every detected face needs:
     * - avatar200: an unrotated, padded square portrait (halfSide = 0.65 * box) for the UI.
     * - aligned112: the face warped onto the ArcFace landmark template (see [FaceAlignment]) for the
     *   embedding model. Falls back to a tight box crop only when landmarks are missing or implausible.
     */
    private fun cropFaultTolerantFacePair(bitmap: Bitmap, face: Face): CroppedFacePair? {
        val box = face.boundingBox

        val avatar200 = cropSquareAndScale(
            bitmap = bitmap,
            centerX = box.exactCenterX(),
            centerY = box.exactCenterY(),
            halfSide = max(box.width(), box.height()) * 0.65f,
            targetSize = AVATAR_CROP_SIZE
        ) ?: return null

        val aligned = alignFaceTo112(bitmap, face)
        if (aligned == null) {
            if (!avatar200.isRecycled) avatar200.recycle()
            return null
        }

        return CroppedFacePair(aligned.first, avatar200, aligned.second)
    }

    /**
     * Warps [face] from [bitmap] onto the 112x112 ArcFace template.
     * Returns the aligned bitmap and whether landmark alignment (vs the box-crop fallback) was used.
     */
    fun alignFaceTo112(bitmap: Bitmap, face: Face): Pair<Bitmap, Boolean>? {
        val alignment = FaceAlignment.estimate(
            mlKitLeftEye = face.landmarkPoint(FaceLandmark.LEFT_EYE),
            mlKitRightEye = face.landmarkPoint(FaceLandmark.RIGHT_EYE),
            noseBase = face.landmarkPoint(FaceLandmark.NOSE_BASE),
            mlKitMouthLeft = face.landmarkPoint(FaceLandmark.MOUTH_LEFT),
            mlKitMouthRight = face.landmarkPoint(FaceLandmark.MOUTH_RIGHT),
            boxWidth = face.boundingBox.width().toFloat()
        )
        if (alignment != null) {
            val warped = warpToTemplate(bitmap, alignment.matrix)
            if (warped != null) return warped to true
        }
        val fallback = cropTightBox112(bitmap, face.boundingBox) ?: return null
        return fallback to false
    }

    private fun Face.landmarkPoint(type: Int): FaceAlignment.Point? =
        getLandmark(type)?.position?.let { FaceAlignment.Point(it.x, it.y) }

    private fun warpToTemplate(bitmap: Bitmap, m: FloatArray): Bitmap? {
        return try {
            val size = FaceAlignment.TEMPLATE_SIZE
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val matrix = Matrix().apply {
                setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f))
            }
            Canvas(out).apply {
                drawColor(Color.BLACK)
                drawBitmap(bitmap, matrix, warpPaint)
            }
            out
        } catch (e: Exception) {
            Log.e(TAG, "Failed to warp face onto alignment template", e)
            null
        }
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

    /** Fallback when landmarks are unusable: tight square box crop scaled to 112x112, no rotation. */
    private fun cropTightBox112(bitmap: Bitmap, box: Rect): Bitmap? = cropSquareAndScale(
        bitmap = bitmap,
        centerX = box.exactCenterX(),
        centerY = box.exactCenterY(),
        halfSide = max(box.width(), box.height()) * 0.52f,
        targetSize = CANONICAL_FACE_SIZE
    )

    /**
     * Re-runs detection on a stored 200x200 avatar crop and returns the most central face's aligned
     * 112x112 bitmap. Used to upgrade embeddings for faces indexed by an older pipeline without
     * re-decoding the original photo. Returns null if no face is found in the crop.
     */
    suspend fun realignStoredAvatarCrop(avatarCrop: Bitmap): Pair<Bitmap, Boolean>? {
        val faces = runMlKitDetection(avatarCrop)
        if (faces.isEmpty()) return null
        val cx = avatarCrop.width / 2f
        val cy = avatarCrop.height / 2f
        val central = faces.minByOrNull { f ->
            val dx = f.boundingBox.exactCenterX() - cx
            val dy = f.boundingBox.exactCenterY() - cy
            dx * dx + dy * dy
        } ?: return null
        return alignFaceTo112(avatarCrop, central)
    }

    /**
     * Box-crop fallback for a stored avatar crop when re-detection fails. The avatar was cropped with
     * halfSide = 0.65 * box, so the original face box spans the central ~77% of it.
     */
    fun tightCropFromStoredAvatar(avatarCrop: Bitmap): Bitmap? {
        val side = max(avatarCrop.width, avatarCrop.height).toFloat()
        val boxSide = side * (0.5f / 0.65f)
        return cropSquareAndScale(
            bitmap = avatarCrop,
            centerX = avatarCrop.width / 2f,
            centerY = avatarCrop.height / 2f,
            halfSide = boxSide * 0.52f,
            targetSize = CANONICAL_FACE_SIZE
        )
    }

    suspend fun decodePhotoBitmap(uriString: String): Bitmap? {
        return try {
            decodePhoto(Uri.parse(uriString))
        } catch (t: Throwable) {
            Log.e(TAG, "Error decoding photo $uriString", t)
            null
        }
    }

    private fun decodePhoto(uri: Uri): Bitmap? {
        val contentResolver = context.contentResolver

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
        } catch (_: Throwable) {
        }

        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight

        var decodedBitmap: Bitmap? = null
        if (width > 0 && height > 0) {
            val longestEdge = max(width, height)
            var inSampleSize = 1
            while (longestEdge / (inSampleSize * 2) >= MIN_DECODED_DIMENSION) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            try {
                decodedBitmap = contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            } catch (_: Throwable) {
            }
        }

        if (decodedBitmap != null) {
            val orientation = try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Throwable) {
                ExifInterface.ORIENTATION_NORMAL
            }
            decodedBitmap = rotateBitmap(decodedBitmap, orientation)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Fallback for HEIC / WebP / content streams where BitmapFactory returns null
            try {
                val source = ImageDecoder.createSource(contentResolver, uri)
                decodedBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val srcLongest = max(info.size.width, info.size.height)
                    if (srcLongest > TARGET_MAX_DIMENSION) {
                        val scale = TARGET_MAX_DIMENSION.toFloat() / srcLongest.toFloat()
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val validBitmap = decodedBitmap ?: return null

        val argbBitmap = if (validBitmap.config == Bitmap.Config.ARGB_8888) {
            validBitmap
        } else {
            val copy = validBitmap.copy(Bitmap.Config.ARGB_8888, false)
            if (copy != null && copy != validBitmap && !validBitmap.isRecycled) {
                validBitmap.recycle()
            }
            copy ?: validBitmap
        }

        val decodedLongest = max(argbBitmap.width, argbBitmap.height)
        if (decodedLongest > MAX_DECODED_DIMENSION) {
            val scale = TARGET_MAX_DIMENSION.toFloat() / decodedLongest.toFloat()
            val targetW = (argbBitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (argbBitmap.height * scale).toInt().coerceAtLeast(1)
            val rescaled = Bitmap.createScaledBitmap(argbBitmap, targetW, targetH, true)
            if (rescaled != argbBitmap && !argbBitmap.isRecycled) {
                argbBitmap.recycle()
            }
            return rescaled
        }

        return argbBitmap
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
        val yawScore = (1f - (abs(face.headEulerAngleY) / 45f)).coerceIn(0.15f, 1f)
        val rollScore = (1f - (abs(face.headEulerAngleZ) / 42f)).coerceIn(0.15f, 1f)
        val sizeScore = ((face.boundingBox.width() - 32f) / 120f).coerceIn(0.25f, 1f)
        val leftOpen = (face.leftEyeOpenProbability ?: 0.80f).coerceIn(0f, 1f)
        val rightOpen = (face.rightEyeOpenProbability ?: 0.80f).coerceIn(0f, 1f)
        val eyeOpenScore = (leftOpen + rightOpen) * 0.5f

        return (yawScore * 0.45f + sizeScore * 0.30f + rollScore * 0.15f + eyeOpenScore * 0.10f)
            .coerceIn(0.25f, 1.0f)
    }
}
