// InstaGallery v1.0
package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class DetectedAlignedFace(
    val alignedFaceBitmap: Bitmap, // 112x112 eye-aligned upright face bitmap for TFLite
    val avatarCropBitmap: Bitmap,  // 180x180 padded square portrait crop for UI avatar
    val boundingBox: Rect,
    val qualityScore: Float,       // 0.0f..1.0f
    val headEulerY: Float,         // Yaw
    val headEulerZ: Float,         // Roll
    val isStrictAnchorCandidate: Boolean, // True if clear frontal face suitable for spawning a new profile
    val rawFace: Face? = null
)

class FaceDetectorProcessor(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetectorProcessor"
        private const val TARGET_MAX_DIMENSION = 1600
        private const val MIN_DECODED_DIMENSION = 1280
        private const val MAX_DECODED_DIMENSION = 1920
        private const val MIN_FACE_BOX_PX = 42
        private const val MIN_EYE_DIST_PX = 12f
        private const val MAX_YAW_DEGREES = 52f
        private const val MAX_ROLL_DEGREES = 45f

        const val CANONICAL_FACE_SIZE = 112
        const val AVATAR_CROP_SIZE = 180
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
     * Unified entry point to detect, filter, and 2D eye-align faces from either a photo or a video Reel.
     */
    suspend fun processMediaItem(
        uriString: String,
        isVideo: Boolean,
        durationMs: Long? = null
    ): List<DetectedAlignedFace> {
        return if (isVideo) {
            detectAndAlignFacesInVideo(uriString, durationMs)
        } else {
            detectAndAlignFacesInPhoto(uriString)
        }
    }

    /**
     * Decodes a photo up to ~1600px (within 1280..1920 longest edge), normalizes EXIF orientation,
     * runs ML Kit ACCURATE detection, applies the 5-landmark quality gate, and performs 2D affine eye alignment.
     */
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

    /**
     * Samples up to 4 keyframes from a video Reel (20%, 45%, 70%, 85% of durationMs, or 500ms, 1500ms, 3000ms
     * fallback), runs ML Kit ACCURATE detection on each frame, and deduplicates faces across frames whose
     * normalized bounding-box centers are within 12% distance and similar size, keeping the highest-quality crop.
     */
    suspend fun detectAndAlignFacesInVideo(
        uriString: String,
        durationMs: Long? = null
    ): List<DetectedAlignedFace> {
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            return emptyList()
        }

        val retriever = MediaMetadataRetriever()
        val deduplicatedCandidates = mutableListOf<VideoFaceCandidate>()

        try {
            retriever.setDataSource(context, uri)
            val resolvedDurationMs = durationMs?.takeIf { it > 0L }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L

            val sampleTimestampsUs: List<Long> = if (resolvedDurationMs >= 2000L) {
                listOf(
                    (resolvedDurationMs * 0.20).toLong() * 1000L,
                    (resolvedDurationMs * 0.45).toLong() * 1000L,
                    (resolvedDurationMs * 0.70).toLong() * 1000L,
                    (resolvedDurationMs * 0.85).toLong() * 1000L
                ).distinct()
            } else {
                listOf(500_000L, 1_500_000L, 3_000_000L)
            }

            for (timeUs in sampleTimestampsUs) {
                val rawFrame = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) {
                    null
                } ?: continue

                val frameBitmap = scaleFrameIfNeeded(rawFrame)
                try {
                    val frameW = frameBitmap.width.toFloat().coerceAtLeast(1f)
                    val frameH = frameBitmap.height.toFloat().coerceAtLeast(1f)
                    val frameDetected = detectAndAlignFacesFromBitmap(frameBitmap)

                    for (alignedFace in frameDetected) {
                        val box = alignedFace.boundingBox
                        val normCenterX = box.exactCenterX() / frameW
                        val normCenterY = box.exactCenterY() / frameH
                        val normWidth = box.width().toFloat() / frameW

                        val existingIndex = deduplicatedCandidates.indexOfFirst { existing ->
                            val centerDist = hypot(
                                (normCenterX - existing.normCenterX).toDouble(),
                                (normCenterY - existing.normCenterY).toDouble()
                            ).toFloat()
                            val sizeRatio = if (existing.normWidth > 0f) {
                                normWidth / existing.normWidth
                            } else 1f
                            centerDist <= 0.12f && sizeRatio in 0.60f..1.67f
                        }

                        if (existingIndex >= 0) {
                            val existing = deduplicatedCandidates[existingIndex]
                            if (alignedFace.qualityScore > existing.detectedFace.qualityScore) {
                                existing.detectedFace.recycleBitmaps()
                                deduplicatedCandidates[existingIndex] = VideoFaceCandidate(
                                    detectedFace = alignedFace,
                                    normCenterX = normCenterX,
                                    normCenterY = normCenterY,
                                    normWidth = normWidth
                                )
                            } else {
                                alignedFace.recycleBitmaps()
                            }
                        } else {
                            deduplicatedCandidates.add(
                                VideoFaceCandidate(
                                    detectedFace = alignedFace,
                                    normCenterX = normCenterX,
                                    normCenterY = normCenterY,
                                    normWidth = normWidth
                                )
                            )
                        }
                    }
                } finally {
                    if (!frameBitmap.isRecycled) {
                        frameBitmap.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sampling multi-frame video faces for $uriString", e)
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }

        return deduplicatedCandidates.map { it.detectedFace }
    }

    private data class VideoFaceCandidate(
        val detectedFace: DetectedAlignedFace,
        val normCenterX: Float,
        val normCenterY: Float,
        val normWidth: Float
    )

    private fun DetectedAlignedFace.recycleBitmaps() {
        if (!alignedFaceBitmap.isRecycled) alignedFaceBitmap.recycle()
        if (avatarCropBitmap != alignedFaceBitmap && !avatarCropBitmap.isRecycled) {
            avatarCropBitmap.recycle()
        }
    }

    /**
     * Runs ML Kit face detection on an upright bitmap, filters out false-positive non-faces,
     * performs 2D eye-aligned affine warping, and computes quality and anchor metrics.
     */
    suspend fun detectAndAlignFacesFromBitmap(bitmap: Bitmap): List<DetectedAlignedFace> {
        val validFaces = detectFaces(bitmap, isVideo = false)
        if (validFaces.isEmpty()) return emptyList()

        val results = mutableListOf<DetectedAlignedFace>()
        for (face in validFaces) {
            val (aligned112, avatar180) = alignAndCropFace(bitmap, face) ?: continue
            val qualityScore = computeFaceQuality(face, bitmap.width, bitmap.height)
            val headYaw = face.headEulerAngleY
            val headRoll = face.headEulerAngleZ
            val isStrictAnchor = abs(headYaw) <= 26f &&
                abs(headRoll) <= 22f &&
                face.boundingBox.width() >= 58 &&
                qualityScore >= 0.62f

            results.add(
                DetectedAlignedFace(
                    alignedFaceBitmap = aligned112,
                    avatarCropBitmap = avatar180,
                    boundingBox = Rect(face.boundingBox),
                    qualityScore = qualityScore,
                    headEulerY = headYaw,
                    headEulerZ = headRoll,
                    isStrictAnchorCandidate = isStrictAnchor,
                    rawFace = face
                )
            )
        }
        return results
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

        // Calculate inSampleSize so the longest edge lands between 1280px and 1920px (1600px target)
        val longestEdge = max(width, height)
        var inSampleSize = 1
        while (longestEdge / (inSampleSize * 2) >= MIN_DECODED_DIMENSION) {
            inSampleSize *= 2
        }

        // Step 2: Decode in ARGB_8888
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var decodedBitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // Ensure longest edge does not exceed MAX_DECODED_DIMENSION (1920px), targeting 1600px
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

        // Step 3: Read and apply EXIF orientation so bitmap is upright before ML Kit
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
            val resolvedDurationMs = durationMs?.takeIf { it > 0L }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L

            val timestampsUs: List<Long> = if (resolvedDurationMs >= 2000L) {
                listOf(
                    (resolvedDurationMs * 0.20).toLong() * 1000L,
                    (resolvedDurationMs * 0.45).toLong() * 1000L,
                    (resolvedDurationMs * 0.70).toLong() * 1000L,
                    (resolvedDurationMs * 0.85).toLong() * 1000L
                ).distinct()
            } else {
                listOf(500_000L, 1_500_000L, 3_000_000L)
            }

            for (timeUs in timestampsUs) {
                val rawFrame = try {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) {
                    null
                } ?: continue
                frames.add(scaleFrameIfNeeded(rawFrame))
            }
            frames
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve video frames for $uri", e)
            frames
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun scaleFrameIfNeeded(rawFrame: Bitmap): Bitmap {
        val width = rawFrame.width
        val height = rawFrame.height
        val longestEdge = max(width, height)
        if (longestEdge <= MAX_DECODED_DIMENSION && rawFrame.config == Bitmap.Config.ARGB_8888) {
            return rawFrame
        }
        val scale = if (longestEdge > MAX_DECODED_DIMENSION) {
            TARGET_MAX_DIMENSION.toFloat() / longestEdge.toFloat()
        } else 1f
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(rawFrame, targetW, targetH, true)
        if (scaled != rawFrame) {
            rawFrame.recycle()
        }
        if (scaled.config != Bitmap.Config.ARGB_8888) {
            val argb = scaled.copy(Bitmap.Config.ARGB_8888, false)
            scaled.recycle()
            return argb
        }
        return scaled
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

    /**
     * Runs ML Kit face detection and applies the Strict False-Positive Filter (Section 2.5):
     * - Rejects bounding box width or height < 42px.
     * - Rejects missing LEFT_EYE, RIGHT_EYE, or NOSE_BASE landmarks.
     * - Rejects extreme pose angles: abs(headEulerAngleY) > 52f or abs(headEulerAngleZ) > 45f.
     * - Rejects invalid landmark geometry: eye-to-eye distance < 12px, or roll-corrected NOSE_BASE
     *   vertical position above the eye line.
     */
    suspend fun detectFaces(bitmap: Bitmap, isVideo: Boolean = false): List<Face> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val rawFaces = suspendCancellableCoroutine<List<Face>> { cont ->
                detector.process(image)
                    .addOnSuccessListener { detected ->
                        if (cont.isActive) cont.resume(detected)
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resumeWithException(e)
                    }
            }

            rawFaces.filter { face -> isValidHumanFace(face) }
        } catch (e: Exception) {
            Log.e(TAG, "Face detection error", e)
            emptyList()
        }
    }

    private fun isValidHumanFace(face: Face): Boolean {
        val box = face.boundingBox
        // 1. Minimum bounding box size check (< 42px rejected)
        if (box.width() < MIN_FACE_BOX_PX || box.height() < MIN_FACE_BOX_PX) {
            return false
        }

        // 2. Required landmarks check (LEFT_EYE, RIGHT_EYE, NOSE_BASE)
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return false
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return false
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return false

        // 3. Pose angle bounds check (abs(Yaw) <= 52f, abs(Roll) <= 45f)
        if (abs(face.headEulerAngleY) > MAX_YAW_DEGREES || abs(face.headEulerAngleZ) > MAX_ROLL_DEGREES) {
            return false
        }

        // 4. Landmark geometry check: eye-to-eye distance >= 12px and roll-corrected NOSE_BASE below eyes
        val dx = (rightEye.x - leftEye.x).toDouble()
        val dy = (rightEye.y - leftEye.y).toDouble()
        val eyeDist = hypot(dx, dy).toFloat()
        if (eyeDist < MIN_EYE_DIST_PX) {
            return false
        }

        val eyeCenterX = (leftEye.x + rightEye.x) * 0.5f
        val eyeCenterY = (leftEye.y + rightEye.y) * 0.5f
        val rollRadians = atan2(dy, dx)
        val cosRoll = cos(-rollRadians).toFloat()
        val sinRoll = sin(-rollRadians).toFloat()

        // Rotate nose vector relative to eye midpoint by -rollRadians
        val relNoseX = noseBase.x - eyeCenterX
        val relNoseY = noseBase.y - eyeCenterY
        val rollCorrectedNoseY = relNoseX * sinRoll + relNoseY * cosRoll

        // In upright coordinates (+Y is down), nose base must lie strictly below the eye midpoint line
        if (rollCorrectedNoseY <= 0f) {
            return false
        }

        return true
    }

    /**
     * Section 2.6: 2D Affine Eye Alignment (alignAndCropFace)
     * Rotates the face region around the eye midpoint by -angleDegrees so both eyes lie horizontally,
     * then extracts:
     * - 112x112 alignedFaceBitmap with 12% padding around facial landmarks/box
     * - 180x180 avatarCropBitmap with 30% padding around facial landmarks/box
     */
    fun alignAndCropFace(bitmap: Bitmap, face: Face): Pair<Bitmap, Bitmap>? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null

        val dy = (rightEye.y - leftEye.y).toDouble()
        val dx = (rightEye.x - leftEye.x).toDouble()
        val angleDegrees = Math.toDegrees(atan2(dy, dx)).toFloat()

        val eyeCenterX = (leftEye.x + rightEye.x) * 0.5f
        val eyeCenterY = (leftEye.y + rightEye.y) * 0.5f

        // Compute landmark-encompassing square bounds in the eye-aligned frame
        val alignedBounds = computeRotatedFaceBounds(face, eyeCenterX, eyeCenterY, -angleDegrees)

        val aligned112 = renderAlignedSquareCrop(
            source = bitmap,
            eyeCenterX = eyeCenterX,
            eyeCenterY = eyeCenterY,
            angleDegrees = angleDegrees,
            alignedFaceBounds = alignedBounds,
            paddingFraction = 0.12f,
            outputSize = CANONICAL_FACE_SIZE
        ) ?: return null

        val avatar180 = renderAlignedSquareCrop(
            source = bitmap,
            eyeCenterX = eyeCenterX,
            eyeCenterY = eyeCenterY,
            angleDegrees = angleDegrees,
            alignedFaceBounds = alignedBounds,
            paddingFraction = 0.30f,
            outputSize = AVATAR_CROP_SIZE
        ) ?: aligned112.copy(Bitmap.Config.ARGB_8888, false)

        return aligned112 to avatar180
    }

    private fun computeRotatedFaceBounds(
        face: Face,
        pivotX: Float,
        pivotY: Float,
        rotationDegrees: Float
    ): RectF {
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()

        fun rotatePoint(x: Float, y: Float): Pair<Float, Float> {
            val rx = (x - pivotX) * cosA - (y - pivotY) * sinA + pivotX
            val ry = (x - pivotX) * sinA + (y - pivotY) * cosA + pivotY
            return rx to ry
        }

        val box = face.boundingBox
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        val corners = listOf(
            box.left.toFloat() to box.top.toFloat(),
            box.right.toFloat() to box.top.toFloat(),
            box.right.toFloat() to box.bottom.toFloat(),
            box.left.toFloat() to box.bottom.toFloat()
        )
        for ((cx, cy) in corners) {
            val (rx, ry) = rotatePoint(cx, cy)
            minX = min(minX, rx)
            minY = min(minY, ry)
            maxX = max(maxX, rx)
            maxY = max(maxY, ry)
        }

        for (landmark in face.allLandmarks) {
            val pos = landmark.position
            val (rx, ry) = rotatePoint(pos.x, pos.y)
            minX = min(minX, rx)
            minY = min(minY, ry)
            maxX = max(maxX, rx)
            maxY = max(maxY, ry)
        }

        // Keep square aspect around the face box center
        val width = (maxX - minX).coerceAtLeast(MIN_FACE_BOX_PX.toFloat())
        val height = (maxY - minY).coerceAtLeast(MIN_FACE_BOX_PX.toFloat())
        val side = max(width, height)
        val centerX = (minX + maxX) * 0.5f
        val centerY = (minY + maxY) * 0.5f

        return RectF(
            centerX - side * 0.5f,
            centerY - side * 0.5f,
            centerX + side * 0.5f,
            centerY + side * 0.5f
        )
    }

    private fun renderAlignedSquareCrop(
        source: Bitmap,
        eyeCenterX: Float,
        eyeCenterY: Float,
        angleDegrees: Float,
        alignedFaceBounds: RectF,
        paddingFraction: Float,
        outputSize: Int
    ): Bitmap? {
        return try {
            val baseSide = max(alignedFaceBounds.width(), alignedFaceBounds.height()).coerceAtLeast(1f)
            val paddedSide = baseSide * (1f + 2f * paddingFraction)
            val scale = outputSize.toFloat() / paddedSide

            val faceCenterX = alignedFaceBounds.centerX()
            val faceCenterY = alignedFaceBounds.centerY()

            val matrix = Matrix().apply {
                // 1. Rotate upright around the eye midpoint by -angleDegrees
                postRotate(-angleDegrees, eyeCenterX, eyeCenterY)
                // 2. Translate so the padded square top-left maps to (0, 0)
                postTranslate(
                    -(faceCenterX - paddedSide * 0.5f),
                    -(faceCenterY - paddedSide * 0.5f)
                )
                // 3. Scale to outputSize x outputSize (112x112 or 180x180)
                postScale(scale, scale)
            }

            val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, matrix, paint)
            output
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render aligned square face crop", e)
            null
        }
    }

    /**
     * Backward-compatible helper returning the 112x112 2D eye-aligned face crop.
     */
    fun alignFaceCanonical(bitmap: Bitmap, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val dy = (rightEye.y - leftEye.y).toDouble()
        val dx = (rightEye.x - leftEye.x).toDouble()
        val angleDegrees = Math.toDegrees(atan2(dy, dx)).toFloat()
        val eyeCenterX = (leftEye.x + rightEye.x) * 0.5f
        val eyeCenterY = (leftEye.y + rightEye.y) * 0.5f
        val alignedBounds = computeRotatedFaceBounds(face, eyeCenterX, eyeCenterY, -angleDegrees)
        return renderAlignedSquareCrop(
            source = bitmap,
            eyeCenterX = eyeCenterX,
            eyeCenterY = eyeCenterY,
            angleDegrees = angleDegrees,
            alignedFaceBounds = alignedBounds,
            paddingFraction = 0.12f,
            outputSize = CANONICAL_FACE_SIZE
        )
    }

    /**
     * Backward-compatible helper returning the 180x180 padded square portrait crop for UI avatar.
     */
    fun cropFaceAvatar(bitmap: Bitmap, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        if (leftEye != null && rightEye != null) {
            val dy = (rightEye.y - leftEye.y).toDouble()
            val dx = (rightEye.x - leftEye.x).toDouble()
            val angleDegrees = Math.toDegrees(atan2(dy, dx)).toFloat()
            val eyeCenterX = (leftEye.x + rightEye.x) * 0.5f
            val eyeCenterY = (leftEye.y + rightEye.y) * 0.5f
            val alignedBounds = computeRotatedFaceBounds(face, eyeCenterX, eyeCenterY, -angleDegrees)
            return renderAlignedSquareCrop(
                source = bitmap,
                eyeCenterX = eyeCenterX,
                eyeCenterY = eyeCenterY,
                angleDegrees = angleDegrees,
                alignedFaceBounds = alignedBounds,
                paddingFraction = 0.30f,
                outputSize = AVATAR_CROP_SIZE
            )
        }
        return null
    }

    /**
     * Section 2.7: Quality Score Calculation
     * - Pose Penalty: yawScore = (1f - (abs(face.headEulerAngleY) / 50f)).coerceIn(0f, 1f)
     * - Roll Penalty: rollScore = (1f - (abs(face.headEulerAngleZ) / 45f)).coerceIn(0f, 1f)
     * - Resolution Score: sizeScore = ((face.boundingBox.width() - 42f) / 140f).coerceIn(0.1f, 1f)
     * - Eye Open Bonus: Average of (face.leftEyeOpenProbability ?: 0.7f) and (face.rightEyeOpenProbability ?: 0.7f)
     * - qualityScore = (yawScore * 0.45f + sizeScore * 0.30f + rollScore * 0.15f + eyeOpenScore * 0.10f).coerceIn(0.05f, 1.0f)
     */
    fun computeFaceQuality(face: Face, bitmapWidth: Int = 0, bitmapHeight: Int = 0): Float {
        val yawScore = (1f - (abs(face.headEulerAngleY) / 50f)).coerceIn(0f, 1f)
        val rollScore = (1f - (abs(face.headEulerAngleZ) / 45f)).coerceIn(0f, 1f)
        val sizeScore = ((face.boundingBox.width() - 42f) / 140f).coerceIn(0.1f, 1f)
        val leftOpen = (face.leftEyeOpenProbability ?: 0.7f).coerceIn(0f, 1f)
        val rightOpen = (face.rightEyeOpenProbability ?: 0.7f).coerceIn(0f, 1f)
        val eyeOpenScore = (leftOpen + rightOpen) * 0.5f

        return (yawScore * 0.45f + sizeScore * 0.30f + rollScore * 0.15f + eyeOpenScore * 0.10f)
            .coerceIn(0.05f, 1.0f)
    }

    fun isStrictAnchorCandidate(face: Face, qualityScore: Float): Boolean {
        return abs(face.headEulerAngleY) <= 26f &&
            abs(face.headEulerAngleZ) <= 22f &&
            face.boundingBox.width() >= 58 &&
            qualityScore >= 0.62f
    }
}
