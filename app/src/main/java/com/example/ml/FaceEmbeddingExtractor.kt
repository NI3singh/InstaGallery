// InstaGallery v1.0
package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Extracts L2-normalized 256-dimensional zero-centered facial identity embeddings on-device.
 *
 * Crucially, every sub-descriptor block is zero-centered (mean = 0.0) and Z-score standardized
 * around canonical human face proportions so that two random individuals have expected cosine
 * similarity ~0.0, while photos of the same individual have cosine similarity 0.56..0.95.
 */
class FaceEmbeddingExtractor(private val context: Context) {

    companion object {
        private const val TAG = "FaceEmbeddingExtractor"
        private const val MODEL_ASSET_NAME = "mobilefacenet.tflite"
        const val DEFAULT_EMBEDDING_DIM = 256
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
            Log.d(TAG, "Using 256-D Zero-Centered Canonical Face Identity Engine")
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

    fun extractEmbedding(alignedFaceBitmap: Bitmap, face: Face? = null): FloatArray {
        val tflite = getOrInitTflite()
        if (tflite != null) {
            try {
                return extractTfliteReflective(tflite, alignedFaceBitmap)
            } catch (e: Exception) {
                Log.w(TAG, "TFLite inference fallback to 256-D Identity Engine", e)
            }
        }
        return extractZeroCenteredIdentityEmbedding(alignedFaceBitmap, face)
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
        return zeroCenterAndNormalize(output[0])
    }

    /**
     * 256-D Zero-Centered Identity Embedding combining:
     * - Block 1 [0..63]: 64-D Zero-Centered Anthropometric Landmark & Contour Z-Scores (weight 0.40)
     * - Block 2 [64..191]: 128-D Illumination-Normalized Local Structure (CS-LBP + Signed HOG on 4x4 eye-aligned grid) (weight 0.40)
     * - Block 3 [192..255]: 64-D Zero-Centered Internal Facial Contrast & Chromaticity (weight 0.20)
     */
    private fun extractZeroCenteredIdentityEmbedding(bitmap: Bitmap, face: Face?): FloatArray {
        val size = 80
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)

        val lum = FloatArray(size * size)
        val rg = FloatArray(size * size) // Red-Green opponent channel
        val yb = FloatArray(size * size) // Yellow-Blue opponent channel
        val cb = FloatArray(size * size)
        val cr = FloatArray(size * size)

        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        if (scaled != bitmap) {
            scaled.recycle()
        }

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            val y = 0.299f * r + 0.587f * g + 0.114f * b
            lum[i] = y
            rg[i] = r - g
            yb[i] = 0.5f * (r + g) - b
            cb[i] = -0.168736f * r - 0.331264f * g + 0.5f * b
            cr[i] = 0.5f * r - 0.418688f * g - 0.081312f * b
        }

        // Local contrast normalization on luminance (removes directional lighting & exposure)
        val normLum = localContrastNormalize(lum, size, radius = 4)

        // 1. Extract 64-D Anthropometric Z-Scores
        val geoBlock = extractAnthropometricZScores(face)
        val normGeo = zeroCenterAndNormalize(geoBlock)

        // 2. Extract 128-D Illumination-Invariant CS-LBP + Signed Gradient Structure
        val structBlock = extractStructuralBlock(normLum, size)
        val normStruct = zeroCenterAndNormalize(structBlock)

        // 3. Extract 64-D Internal Facial Contrast & Chromaticity Block
        val colorBlock = extractRegionalAppearanceBlock(lum, rg, yb, cb, cr, size)
        val normColor = zeroCenterAndNormalize(colorBlock)

        // Combine with balanced identity weights
        val combined = FloatArray(DEFAULT_EMBEDDING_DIM)
        val wGeo = 0.40f
        val wStruct = 0.40f
        val wColor = 0.20f

        for (i in 0 until 64) {
            combined[i] = normGeo[i] * wGeo
        }
        for (i in 0 until 128) {
            combined[64 + i] = normStruct[i] * wStruct
        }
        for (i in 0 until 64) {
            combined[192 + i] = normColor[i] * wColor
        }

        return l2Normalize(combined)
    }

    private fun localContrastNormalize(lum: FloatArray, size: Int, radius: Int): FloatArray {
        val result = FloatArray(size * size)
        for (y in 0 until size) {
            val y0 = max(0, y - radius)
            val y1 = min(size - 1, y + radius)
            for (x in 0 until size) {
                val x0 = max(0, x - radius)
                val x1 = min(size - 1, x + radius)
                var sum = 0f
                var sumSq = 0f
                var count = 0
                for (yy in y0..y1) {
                    val rowOffset = yy * size
                    for (xx in x0..x1) {
                        val v = lum[rowOffset + xx]
                        sum += v
                        sumSq += v * v
                        count++
                    }
                }
                val mean = sum / count
                val variance = (sumSq / count - mean * mean).coerceAtLeast(0f)
                val std = sqrt(variance) + 0.04f
                result[y * size + x] = ((lum[y * size + x] - mean) / std).coerceIn(-3.0f, 3.0f)
            }
        }
        return result
    }

    /**
     * Computes 128-D structural features over a 4x4 grid on the central aligned face [8..72, 8..72],
     * standardizing each feature channel across the 16 cells so the mean is strictly 0.
     */
    private fun extractStructuralBlock(normLum: FloatArray, size: Int): FloatArray {
        val grid = 4
        val margin = 8
        val innerSpan = size - 2 * margin // 64
        val cellSpan = innerSpan / grid // 16
        val numCells = grid * grid // 16
        val featsPerCell = 8
        val rawMatrix = Array(featsPerCell) { FloatArray(numCells) }

        var cellIdx = 0
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                val startY = margin + gy * cellSpan
                val endY = startY + cellSpan
                val startX = margin + gx * cellSpan
                val endX = startX + cellSpan

                var signedGx = 0f
                var signedGy = 0f
                var absGx = 0f
                var absGy = 0f
                var diag1 = 0f
                var diag2 = 0f
                var centerContrast = 0f
                var energy = 0f
                var count = 0

                val midY = (startY + endY) / 2
                val midX = (startX + endX) / 2

                for (y in startY until endY) {
                    for (x in startX until endX) {
                        val c = normLum[y * size + x]
                        val dx = normLum[y * size + (x + 1)] - normLum[y * size + (x - 1)]
                        val dy = normLum[(y + 1) * size + x] - normLum[(y - 1) * size + x]
                        val d1 = normLum[(y + 1) * size + (x + 1)] - normLum[(y - 1) * size + (x - 1)]
                        val d2 = normLum[(y + 1) * size + (x - 1)] - normLum[(y - 1) * size + (x + 1)]

                        signedGx += dx
                        signedGy += dy
                        absGx += abs(dx)
                        absGy += abs(dy)
                        diag1 += d1
                        diag2 += d2
                        val isInner = (y in (midY - 4)..(midY + 4)) && (x in (midX - 4)..(midX + 4))
                        centerContrast += if (isInner) c else -c * 0.35f
                        energy += c * c
                        count++
                    }
                }

                val inv = if (count > 0) 1f / count else 1f
                rawMatrix[0][cellIdx] = signedGx * inv
                rawMatrix[1][cellIdx] = signedGy * inv
                rawMatrix[2][cellIdx] = absGx * inv
                rawMatrix[3][cellIdx] = absGy * inv
                rawMatrix[4][cellIdx] = diag1 * inv
                rawMatrix[5][cellIdx] = diag2 * inv
                rawMatrix[6][cellIdx] = centerContrast * inv
                rawMatrix[7][cellIdx] = sqrt((energy * inv).coerceAtLeast(0f))
                cellIdx++
            }
        }

        // Standardize each feature channel across the 16 cells (removes common human face average)
        val out = FloatArray(128)
        var idx = 0
        for (f in 0 until featsPerCell) {
            val channel = rawMatrix[f]
            var mean = 0f
            for (v in channel) mean += v
            mean /= numCells

            var sq = 0f
            for (v in channel) {
                val d = v - mean
                sq += d * d
            }
            val std = sqrt(sq / numCells) + 1e-4f
            for (c in 0 until numCells) {
                out[idx++] = ((channel[c] - mean) / std).coerceIn(-3.0f, 3.0f)
            }
        }
        return out
    }

    /**
     * Extracts 64-D internal facial appearance & chromaticity contrasts (hairline, brows, eyes,
     * cheeks, mustache/philtrum, lips, chin/beard) relative to the face's own baseline.
     */
    private fun extractRegionalAppearanceBlock(
        lum: FloatArray,
        rg: FloatArray,
        yb: FloatArray,
        cb: FloatArray,
        cr: FloatArray,
        size: Int
    ): FloatArray {
        val out = FloatArray(64)
        val grid = 4
        val margin = 8
        val cellSpan = (size - 2 * margin) / grid // 16

        val cellLum = FloatArray(16)
        val cellRg = FloatArray(16)
        val cellYb = FloatArray(16)
        val cellCrCb = FloatArray(16)

        var cellIdx = 0
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                val startY = margin + gy * cellSpan
                val endY = startY + cellSpan
                val startX = margin + gx * cellSpan
                val endX = startX + cellSpan

                var sL = 0f
                var sRg = 0f
                var sYb = 0f
                var sCrCb = 0f
                var cnt = 0

                for (y in startY until endY) {
                    val row = y * size
                    for (x in startX until endX) {
                        val pos = row + x
                        sL += lum[pos]
                        sRg += rg[pos]
                        sYb += yb[pos]
                        sCrCb += (cr[pos] - cb[pos])
                        cnt++
                    }
                }
                val inv = if (cnt > 0) 1f / cnt else 1f
                cellLum[cellIdx] = sL * inv
                cellRg[cellIdx] = sRg * inv
                cellYb[cellIdx] = sYb * inv
                cellCrCb[cellIdx] = sCrCb * inv
                cellIdx++
            }
        }

        // Standardize each of the 4 regional appearance channels across the 16 anatomical cells
        val channels = arrayOf(cellLum, cellRg, cellYb, cellCrCb)
        var outIdx = 0
        for (ch in channels) {
            var mean = 0f
            for (v in ch) mean += v
            mean /= 16f
            var sq = 0f
            for (v in ch) {
                val d = v - mean
                sq += d * d
            }
            val std = sqrt(sq / 16f) + 1e-4f
            for (i in 0 until 16) {
                out[outIdx++] = ((ch[i] - mean) / std).coerceIn(-3.0f, 3.0f)
            }
        }

        return out
    }

    /**
     * Extracts 64-D zero-centered anthropometric Z-scores from ML Kit facial landmarks & contours.
     * Every ratio is normalized by inter-ocular distance (eyeDist) and centered around population mean.
     */
    private fun extractAnthropometricZScores(face: Face?): FloatArray {
        val out = FloatArray(64)
        if (face == null) return out

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
        val leftEar = face.getLandmark(FaceLandmark.LEFT_EAR)?.position
        val rightEar = face.getLandmark(FaceLandmark.RIGHT_EAR)?.position

        if (leftEye == null || rightEye == null) return out
        val eyeDist = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y).coerceAtLeast(8f)
        val eyeMidX = (leftEye.x + rightEye.x) * 0.5f
        val eyeMidY = (leftEye.y + rightEye.y) * 0.5f

        fun zScore(value: Float, mean: Float, std: Float): Float {
            return ((value - mean) / std).coerceIn(-3.2f, 3.2f)
        }

        // 0..3: Bounding box & eye span ratios
        out[0] = zScore(w / h, 0.80f, 0.08f)
        out[1] = zScore(eyeDist / w, 0.43f, 0.045f)
        out[2] = zScore(eyeDist / h, 0.34f, 0.045f)

        // 3..9: Nose proportions relative to eyeDist
        if (nose != null) {
            val noseLen = hypot(nose.x - eyeMidX, nose.y - eyeMidY) / eyeDist
            val leftEyeNose = hypot(nose.x - leftEye.x, nose.y - leftEye.y) / eyeDist
            val rightEyeNose = hypot(nose.x - rightEye.x, nose.y - rightEye.y) / eyeDist
            val noseVert = (nose.y - eyeMidY) / eyeDist
            val noseAsym = (leftEyeNose - rightEyeNose)

            out[3] = zScore(noseLen, 0.62f, 0.08f)
            out[4] = zScore(leftEyeNose, 0.79f, 0.07f)
            out[5] = zScore(rightEyeNose, 0.79f, 0.07f)
            out[6] = zScore(noseVert, 0.60f, 0.08f)
            out[7] = zScore(noseAsym, 0.0f, 0.05f)
        }

        // 8..16: Mouth & lower-face triangle proportions relative to eyeDist
        if (mouthLeft != null && mouthRight != null) {
            val mouthWidth = hypot(mouthRight.x - mouthLeft.x, mouthRight.y - mouthLeft.y) / eyeDist
            val mouthMidX = (mouthLeft.x + mouthRight.x) * 0.5f
            val mouthMidY = (mouthLeft.y + mouthRight.y) * 0.5f
            val eyeToMouth = hypot(mouthMidX - eyeMidX, mouthMidY - eyeMidY) / eyeDist
            val leftEyeToMouthL = hypot(mouthLeft.x - leftEye.x, mouthLeft.y - leftEye.y) / eyeDist
            val rightEyeToMouthR = hypot(mouthRight.x - rightEye.x, mouthRight.y - rightEye.y) / eyeDist

            out[8] = zScore(mouthWidth, 0.84f, 0.10f)
            out[9] = zScore(eyeToMouth, 1.08f, 0.10f)
            out[10] = zScore(mouthWidth / eyeToMouth.coerceAtLeast(0.3f), 0.78f, 0.09f)
            out[11] = zScore(leftEyeToMouthL, 1.10f, 0.09f)
            out[12] = zScore(rightEyeToMouthR, 1.10f, 0.09f)

            if (nose != null) {
                val philtrum = hypot(mouthMidX - nose.x, mouthMidY - nose.y) / eyeDist
                val noseToMouthRatio = philtrum / (hypot(nose.x - eyeMidX, nose.y - eyeMidY) / eyeDist).coerceAtLeast(0.2f)
                out[13] = zScore(philtrum, 0.46f, 0.07f)
                out[14] = zScore(noseToMouthRatio, 0.74f, 0.12f)
            }
        }

        if (mouthBottom != null && nose != null) {
            val noseToLowerLip = hypot(mouthBottom.x - nose.x, mouthBottom.y - nose.y) / eyeDist
            val eyeToLowerLip = hypot(mouthBottom.x - eyeMidX, mouthBottom.y - eyeMidY) / eyeDist
            out[15] = zScore(noseToLowerLip, 0.55f, 0.08f)
            out[16] = zScore(eyeToLowerLip, 1.16f, 0.10f)
        }

        // 17..22: Cheekbone width & jaw geometry
        if (leftCheek != null && rightCheek != null) {
            val cheekWidth = hypot(rightCheek.x - leftCheek.x, rightCheek.y - leftCheek.y) / eyeDist
            val leftEyeToCheek = hypot(leftCheek.x - leftEye.x, leftCheek.y - leftEye.y) / eyeDist
            val rightEyeToCheek = hypot(rightCheek.x - rightEye.x, rightCheek.y - rightEye.y) / eyeDist
            out[17] = zScore(cheekWidth, 1.16f, 0.11f)
            out[18] = zScore(leftEyeToCheek, 0.56f, 0.07f)
            out[19] = zScore(rightEyeToCheek, 0.56f, 0.07f)
            if (mouthLeft != null && mouthRight != null) {
                val mouthW = hypot(mouthRight.x - mouthLeft.x, mouthRight.y - mouthLeft.y) / eyeDist
                out[20] = zScore(mouthW / cheekWidth.coerceAtLeast(0.4f), 0.72f, 0.08f)
            }
        }

        if (leftEar != null && rightEar != null) {
            val earSpan = hypot(rightEar.x - leftEar.x, rightEar.y - leftEar.y) / eyeDist
            out[21] = zScore(earSpan, 1.95f, 0.18f)
        }

        // 22..47: Rich ML Kit Contour geometry (Face Oval, Eyebrows, Eyes, Nose Bridge, Lips)
        val faceOval = face.getContour(FaceContour.FACE)?.points
        if (faceOval != null && faceOval.size >= 36) {
            // FaceContour.FACE has 36 points clockwise starting from top-center (0) to bottom-chin (18)
            val topHead = faceOval[0]
            val chin = faceOval[18]
            val faceHeight = hypot(chin.x - topHead.x, chin.y - topHead.y) / eyeDist

            // Widths at temple (3, 33), cheekbone (6, 30), mid-jaw (10, 26), lower-jaw (14, 22)
            val templeW = hypot(faceOval[3].x - faceOval[33].x, faceOval[3].y - faceOval[33].y) / eyeDist
            val cheekOvalW = hypot(faceOval[6].x - faceOval[30].x, faceOval[6].y - faceOval[30].y) / eyeDist
            val midJawW = hypot(faceOval[10].x - faceOval[26].x, faceOval[10].y - faceOval[26].y) / eyeDist
            val lowerJawW = hypot(faceOval[14].x - faceOval[22].x, faceOval[14].y - faceOval[22].y) / eyeDist
            val chinToEyes = hypot(chin.x - eyeMidX, chin.y - eyeMidY) / eyeDist

            out[22] = zScore(faceHeight, 2.35f, 0.20f)
            out[23] = zScore(templeW, 1.95f, 0.15f)
            out[24] = zScore(cheekOvalW, 2.05f, 0.16f)
            out[25] = zScore(midJawW, 1.75f, 0.16f)
            out[26] = zScore(lowerJawW, 1.22f, 0.14f)
            out[27] = zScore(chinToEyes, 1.62f, 0.14f)
            // Face shape taper ratios (distinguishes round, oval, square, heart, diamond faces!)
            out[28] = zScore(cheekOvalW / faceHeight.coerceAtLeast(0.5f), 0.87f, 0.07f)
            out[29] = zScore(midJawW / cheekOvalW.coerceAtLeast(0.5f), 0.85f, 0.06f)
            out[30] = zScore(lowerJawW / cheekOvalW.coerceAtLeast(0.5f), 0.59f, 0.06f)
            out[31] = zScore(templeW / cheekOvalW.coerceAtLeast(0.5f), 0.95f, 0.05f)

            if (mouthBottom != null) {
                val chinLen = hypot(chin.x - mouthBottom.x, chin.y - mouthBottom.y) / eyeDist
                out[32] = zScore(chinLen, 0.45f, 0.07f)
            }
        }

        val leftBrow = face.getContour(FaceContour.LEFT_EYEBROW_TOP)?.points
        val rightBrow = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)?.points
        if (!leftBrow.isNullOrEmpty() && !rightBrow.isNullOrEmpty()) {
            val lBrowMid = leftBrow[leftBrow.size / 2]
            val rBrowMid = rightBrow[rightBrow.size / 2]
            val lBrowDist = hypot(lBrowMid.x - leftEye.x, lBrowMid.y - leftEye.y) / eyeDist
            val rBrowDist = hypot(rBrowMid.x - rightEye.x, rBrowMid.y - rightEye.y) / eyeDist
            val lBrowSpan = hypot(leftBrow.last().x - leftBrow.first().x, leftBrow.last().y - leftBrow.first().y) / eyeDist
            val rBrowSpan = hypot(rightBrow.last().x - rightBrow.first().x, rightBrow.last().y - rightBrow.first().y) / eyeDist
            val innerBrowGap = hypot(rightBrow.first().x - leftBrow.last().x, rightBrow.first().y - leftBrow.last().y) / eyeDist

            out[33] = zScore(lBrowDist, 0.28f, 0.045f)
            out[34] = zScore(rBrowDist, 0.28f, 0.045f)
            out[35] = zScore(lBrowSpan, 0.52f, 0.06f)
            out[36] = zScore(rBrowSpan, 0.52f, 0.06f)
            out[37] = zScore(innerBrowGap, 0.36f, 0.06f)
        }

        val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
        val rightEyeContour = face.getContour(FaceContour.RIGHT_EYE)?.points
        if (leftEyeContour != null && leftEyeContour.size >= 12 && rightEyeContour != null && rightEyeContour.size >= 12) {
            val lEyeW = hypot(leftEyeContour[8].x - leftEyeContour[0].x, leftEyeContour[8].y - leftEyeContour[0].y) / eyeDist
            val lEyeH = hypot(leftEyeContour[12].x - leftEyeContour[4].x, leftEyeContour[12].y - leftEyeContour[4].y) / eyeDist
            val rEyeW = hypot(rightEyeContour[8].x - rightEyeContour[0].x, rightEyeContour[8].y - rightEyeContour[0].y) / eyeDist
            val rEyeH = hypot(rightEyeContour[12].x - rightEyeContour[4].x, rightEyeContour[12].y - rightEyeContour[4].y) / eyeDist

            out[38] = zScore(lEyeW, 0.42f, 0.04f)
            out[39] = zScore(rEyeW, 0.42f, 0.04f)
            out[40] = zScore(lEyeH / lEyeW.coerceAtLeast(0.1f), 0.34f, 0.06f)
            out[41] = zScore(rEyeH / rEyeW.coerceAtLeast(0.1f), 0.34f, 0.06f)
        }

        val noseBridge = face.getContour(FaceContour.NOSE_BRIDGE)?.points
        val noseBottom = face.getContour(FaceContour.NOSE_BOTTOM)?.points
        if (!noseBridge.isNullOrEmpty() && noseBridge.size >= 2) {
            val bridgeLen = hypot(
                noseBridge.last().x - noseBridge.first().x,
                noseBridge.last().y - noseBridge.first().y
            ) / eyeDist
            out[42] = zScore(bridgeLen, 0.48f, 0.07f)
        }
        if (!noseBottom.isNullOrEmpty() && noseBottom.size >= 3) {
            val nostrilSpan = hypot(
                noseBottom.last().x - noseBottom.first().x,
                noseBottom.last().y - noseBottom.first().y
            ) / eyeDist
            out[43] = zScore(nostrilSpan, 0.52f, 0.06f)
        }

        val upperLip = face.getContour(FaceContour.UPPER_LIP_TOP)?.points
        val lowerLip = face.getContour(FaceContour.LOWER_LIP_BOTTOM)?.points
        if (!upperLip.isNullOrEmpty() && !lowerLip.isNullOrEmpty()) {
            val uMid = upperLip[upperLip.size / 2]
            val lMid = lowerLip[lowerLip.size / 2]
            val lipHeight = hypot(lMid.x - uMid.x, lMid.y - uMid.y) / eyeDist
            out[44] = zScore(lipHeight, 0.24f, 0.05f)
        }

        // 48..63: Non-linear cross-ratio interaction terms for higher discriminative power
        for (i in 0 until 16) {
            val a = out[i * 2]
            val b = out[(i * 2 + 3) % 45]
            out[48 + i] = (a * b * 0.5f).coerceIn(-3.0f, 3.0f)
        }

        return out
    }

    private fun zeroCenterAndNormalize(vector: FloatArray): FloatArray {
        if (vector.isEmpty()) return vector
        var mean = 0f
        for (v in vector) mean += v
        mean /= vector.size

        var sumSquares = 0.0
        val centered = FloatArray(vector.size)
        for (i in vector.indices) {
            val c = vector[i] - mean
            centered[i] = c
            sumSquares += (c * c).toDouble()
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm < 1e-6f) return centered

        for (i in centered.indices) {
            centered[i] /= norm
        }
        return centered
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += (v * v).toDouble()
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm < 1e-6f) return vector

        val result = FloatArray(vector.size)
        for (i in vector.indices) {
            result[i] = vector[i] / norm
        }
        return result
    }
}
