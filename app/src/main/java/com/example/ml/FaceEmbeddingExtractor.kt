package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Extracts L2-normalized 128-dimensional facial feature embeddings on-device.
 *
 * Primary path: Dynamically loads `mobilefacenet.tflite` from Android assets if bundled
 * and TFLite runtime is present, inspecting input `[1, H, W, 3]` and output `[1, D]` tensors.
 *
 * Deterministic On-Device Engine: Computes a 128-dimensional facial landmark geometry +
 * Sobel edge orientation (HOG) + YCbCr skin/chroma descriptor on the aligned inner face oval.
 */
class FaceEmbeddingExtractor(private val context: Context) {

    companion object {
        private const val TAG = "FaceEmbeddingExtractor"
        private const val MODEL_ASSET_NAME = "mobilefacenet.tflite"
        const val DEFAULT_EMBEDDING_DIM = 128
    }

    private var tfliteInstance: Any? = null
    private var modelLoadAttempted = false
    private var inputWidth = 112
    private var inputHeight = 112
    private var outputDim = DEFAULT_EMBEDDING_DIM

    @Synchronized
    private fun getOrInitTflite(): Any? {
        if (modelLoadAttempted) return tfliteInstance
        modelLoadAttempted = true
        return try {
            val assetList = context.assets.list("") ?: emptyArray()
            if (!assetList.contains(MODEL_ASSET_NAME)) {
                null
            } else {
                val interpreterClass = Class.forName("org.tensorflow.lite.Interpreter")
                val modelBuffer = loadModelFile(MODEL_ASSET_NAME)
                val ctor = interpreterClass.getConstructor(ByteBuffer::class.java)
                val instance = ctor.newInstance(modelBuffer)
                tfliteInstance = instance
                instance
            }
        } catch (e: Exception) {
            Log.d(TAG, "Using built-in 128-D Landmark + HOG facial embedding engine")
            null
        }
    }

    private fun loadModelFile(assetName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetName)
        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

    fun extractEmbedding(faceBitmap: Bitmap, face: Face? = null): FloatArray {
        val tflite = getOrInitTflite()
        if (tflite != null) {
            try {
                return extractTfliteReflective(tflite, faceBitmap)
            } catch (e: Exception) {
                Log.w(TAG, "TFLite inference fallback to Landmark+HOG", e)
            }
        }
        return extractFacialStructureAndHogEmbedding(faceBitmap, face)
    }

    private fun extractTfliteReflective(tflite: Any, bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputWidth * inputHeight * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        val pixels = IntArray(inputWidth * inputHeight)
        scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)
            byteBuffer.putFloat((r - 127.5f) / 128.0f)
            byteBuffer.putFloat((g - 127.5f) / 128.0f)
            byteBuffer.putFloat((b - 127.5f) / 128.0f)
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        val output = Array(1) { FloatArray(outputDim) }
        val runMethod = tflite.javaClass.getMethod("run", Any::class.java, Any::class.java)
        runMethod.invoke(tflite, byteBuffer, output)
        return l2Normalize(output[0])
    }

    /**
     * Deterministic 128-dimensional embedding combining:
     * - Inner-face 4x4 spatial grid (64x64) with Sobel gradient orientation & magnitude (HOG) + YCbCr/RGB features (112 dims)
     * - ML Kit facial landmark geometric ratios (inter-ocular, nose-to-mouth, eye-to-mouth, symmetry) (16 dims)
     */
    private fun extractFacialStructureAndHogEmbedding(bitmap: Bitmap, face: Face?): FloatArray {
        val embedding = FloatArray(DEFAULT_EMBEDDING_DIM)

        // Focus on the central 80% of the crop to exclude background noise
        val cropX = (bitmap.width * 0.1f).toInt()
        val cropY = (bitmap.height * 0.1f).toInt()
        val cropW = (bitmap.width * 0.8f).toInt().coerceAtLeast(1)
        val cropH = (bitmap.height * 0.8f).toInt().coerceAtLeast(1)

        val innerFace = try {
            Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
        } catch (e: Exception) {
            bitmap
        }

        val size = 64
        val scaled = Bitmap.createScaledBitmap(innerFace, size, size, true)
        if (innerFace != bitmap && innerFace != scaled) {
            innerFace.recycle()
        }

        val lum = FloatArray(size * size)
        val cb = FloatArray(size * size)
        val cr = FloatArray(size * size)

        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            lum[i] = y
            cb[i] = 0.5f + (-0.168736f * r - 0.331264f * g + 0.5f * b)
            cr[i] = 0.5f + (0.5f * r - 0.418688f * g - 0.081312f * b)
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        // 4x4 grid cells -> 16 cells * 7 features per cell = 112 dimensions
        val grid = 4
        val cellSize = size / grid
        var idx = 0

        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                var sumY = 0f
                var sumCb = 0f
                var sumCr = 0f
                var sumGradX = 0f
                var sumGradY = 0f
                var sumGradMag = 0f
                var sumContrast = 0f
                var count = 0

                val startY = (gy * cellSize).coerceAtLeast(1)
                val endY = ((gy + 1) * cellSize).coerceAtMost(size - 1)
                val startX = (gx * cellSize).coerceAtLeast(1)
                val endX = ((gx + 1) * cellSize).coerceAtMost(size - 1)

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val pos = y * size + x
                        val yVal = lum[pos]
                        val gxVal = lum[y * size + (x + 1)] - lum[y * size + (x - 1)]
                        val gyVal = lum[(y + 1) * size + x] - lum[(y - 1) * size + x]
                        val mag = sqrt(gxVal * gxVal + gyVal * gyVal)

                        sumY += yVal
                        sumCb += cb[pos]
                        sumCr += cr[pos]
                        sumGradX += abs(gxVal)
                        sumGradY += abs(gyVal)
                        sumGradMag += mag
                        sumContrast += yVal * yVal
                        count++
                    }
                }

                if (count > 0 && idx + 7 <= 112) {
                    val meanY = sumY / count
                    embedding[idx++] = meanY
                    embedding[idx++] = sumCb / count
                    embedding[idx++] = sumCr / count
                    embedding[idx++] = (sumGradX / count) * 2.5f
                    embedding[idx++] = (sumGradY / count) * 2.5f
                    embedding[idx++] = (sumGradMag / count) * 2.5f
                    embedding[idx++] = sqrt((sumContrast / count - meanY * meanY).coerceAtLeast(0f)) * 2.0f
                }
            }
        }

        // Remaining 16 dimensions (indices 112..127): Facial landmark proportional geometry
        populateLandmarkFeatures(face, embedding, startIdx = 112)

        return l2Normalize(embedding)
    }

    private fun populateLandmarkFeatures(face: Face?, embedding: FloatArray, startIdx: Int) {
        if (face == null) return
        val box = face.boundingBox
        val w = box.width().toFloat().coerceAtLeast(1f)
        val h = box.height().toFloat().coerceAtLeast(1f)

        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)?.position
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)?.position

        embedding[startIdx] = (w / h).coerceIn(0.5f, 1.5f)

        if (leftEye != null && rightEye != null) {
            val eyeDist = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y)
            embedding[startIdx + 1] = (eyeDist / w) * 2.0f
            val eyeAngle = atan2(rightEye.y - leftEye.y, rightEye.x - leftEye.x)
            embedding[startIdx + 2] = cos(eyeAngle)
            embedding[startIdx + 3] = sin(eyeAngle)

            if (nose != null) {
                val leftEyeToNose = hypot(nose.x - leftEye.x, nose.y - leftEye.y) / h
                val rightEyeToNose = hypot(nose.x - rightEye.x, nose.y - rightEye.y) / h
                embedding[startIdx + 4] = leftEyeToNose * 2.0f
                embedding[startIdx + 5] = rightEyeToNose * 2.0f
                embedding[startIdx + 6] = ((nose.y - (leftEye.y + rightEye.y) / 2f) / h) * 2.0f
            }
        }

        if (mouthLeft != null && mouthRight != null) {
            val mouthWidth = hypot(mouthRight.x - mouthLeft.x, mouthRight.y - mouthLeft.y)
            embedding[startIdx + 7] = (mouthWidth / w) * 2.0f
            if (nose != null) {
                val noseToMouth = hypot(
                    (mouthLeft.x + mouthRight.x) / 2f - nose.x,
                    (mouthLeft.y + mouthRight.y) / 2f - nose.y
                )
                embedding[startIdx + 8] = (noseToMouth / h) * 2.0f
            }
        }

        if (mouthBottom != null && nose != null) {
            embedding[startIdx + 9] = ((mouthBottom.y - nose.y) / h) * 2.0f
        }

        if (leftCheek != null && rightCheek != null) {
            val cheekWidth = hypot(rightCheek.x - leftCheek.x, rightCheek.y - leftCheek.y)
            embedding[startIdx + 10] = (cheekWidth / w) * 2.0f
        }

        leftEye?.let {
            embedding[startIdx + 11] = (it.x - box.left) / w
            embedding[startIdx + 12] = (it.y - box.top) / h
        }
        rightEye?.let {
            embedding[startIdx + 13] = (it.x - box.left) / w
            embedding[startIdx + 14] = (it.y - box.top) / h
        }
        nose?.let {
            embedding[startIdx + 15] = (it.y - box.top) / h
        }
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += (v * v).toDouble()
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm == 0f) return vector

        val result = FloatArray(vector.size)
        for (i in vector.indices) {
            result[i] = vector[i] / norm
        }
        return result
    }
}
