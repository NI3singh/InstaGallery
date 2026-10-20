// InstaGallery v1.0
package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.google.mlkit.vision.face.Face
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Extracts flip-augmented L2-normalized facial identity embeddings using MobileFaceNet TFLite
 * (`mobilefacenet.tflite`) with dynamic tensor shape & DataType inspection.
 *
 * For every 112x112 alignedFaceBitmap:
 * 1. Runs TFLite inference on the original alignedFaceBitmap -> v1 -> L2-normalized v1_hat.
 * 2. Horizontally flips alignedFaceBitmap -> runs TFLite inference -> v2 -> L2-normalized v2_hat.
 * 3. Sums v_sum = v1_hat + v2_hat and L2-normalizes to produce the final illumination- and
 *    yaw-invariant identity embedding.
 */
class FaceEmbeddingExtractor(private val context: Context) {

    companion object {
        private const val TAG = "InstaGalleryML"
        private const val MODEL_ASSET_NAME = "mobilefacenet.tflite"
        const val FALLBACK_EMBEDDING_DIM = 192
    }

    private var interpreter: Interpreter? = null
    private var modelInitAttempted = false
    private var inputHeight = 112
    private var inputWidth = 112
    private var inputChannels = 3
    private var inputDataType: DataType = DataType.FLOAT32
    private var outputEmbeddingDim = FALLBACK_EMBEDDING_DIM
    private var outputDataType: DataType = DataType.FLOAT32

    @Synchronized
    private fun getOrInitInterpreter(): Interpreter? {
        if (modelInitAttempted) return interpreter
        modelInitAttempted = true

        return try {
            val modelBuffer = loadModelFile(MODEL_ASSET_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val tflite = Interpreter(modelBuffer, options)

            // Dynamically inspect input tensor [1, inputHeight, inputWidth, 3] and DataType
            val inputTensor = tflite.getInputTensor(0)
            val inShape = inputTensor.shape()
            if (inShape.size >= 4) {
                inputHeight = inShape[1].coerceAtLeast(32)
                inputWidth = inShape[2].coerceAtLeast(32)
                inputChannels = inShape[3].coerceAtLeast(1)
            } else if (inShape.size == 3) {
                inputHeight = inShape[0].coerceAtLeast(32)
                inputWidth = inShape[1].coerceAtLeast(32)
                inputChannels = inShape[2].coerceAtLeast(1)
            }
            inputDataType = inputTensor.dataType()

            // Dynamically inspect output tensor [1, embeddingDim] (e.g., 192 or 512)
            val outputTensor = tflite.getOutputTensor(0)
            val outShape = outputTensor.shape()
            outputEmbeddingDim = if (outShape.size >= 2) {
                outShape.last().coerceAtLeast(64)
            } else if (outShape.isNotEmpty()) {
                outShape[0].coerceAtLeast(64)
            } else {
                FALLBACK_EMBEDDING_DIM
            }
            outputDataType = outputTensor.dataType()

            interpreter = tflite
            tflite
        } catch (fnf: FileNotFoundException) {
            Log.e(TAG, "CRITICAL: mobilefacenet.tflite missing", fnf)
            null
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: mobilefacenet.tflite missing or failed to initialize", e)
            null
        }
    }

    private fun loadModelFile(assetName: String): MappedByteBuffer {
        val assetFd = context.assets.openFd(assetName)
        FileInputStream(assetFd.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                assetFd.startOffset,
                assetFd.declaredLength
            )
        }
    }

    /**
     * Extracts a flip-augmented L2-normalized embedding from a 2D eye-aligned 112x112 face bitmap.
     */
    fun extractEmbedding(alignedFaceBitmap: Bitmap, face: Face? = null): FloatArray {
        val mirroredBitmap = createHorizontallyMirroredBitmap(alignedFaceBitmap)
        return try {
            val tflite = getOrInitInterpreter()
            if (tflite != null) {
                val raw1 = runSingleTfliteInference(tflite, alignedFaceBitmap)
                val v1Hat = l2Normalize(raw1)

                val raw2 = runSingleTfliteInference(tflite, mirroredBitmap)
                val v2Hat = l2Normalize(raw2)

                combineAndNormalize(v1Hat, v2Hat)
            } else {
                val raw1 = extractStructuralLbpHog192D(alignedFaceBitmap)
                val v1Hat = l2Normalize(raw1)

                val raw2 = extractStructuralLbpHog192D(mirroredBitmap)
                val v2Hat = l2Normalize(raw2)

                combineAndNormalize(v1Hat, v2Hat)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TFLite inference error, using 192-D LBP+HOG structural fallback", e)
            val v1Hat = l2Normalize(extractStructuralLbpHog192D(alignedFaceBitmap))
            val v2Hat = l2Normalize(extractStructuralLbpHog192D(mirroredBitmap))
            combineAndNormalize(v1Hat, v2Hat)
        } finally {
            if (mirroredBitmap != alignedFaceBitmap && !mirroredBitmap.isRecycled) {
                mirroredBitmap.recycle()
            }
        }
    }

    private fun createHorizontallyMirroredBitmap(source: Bitmap): Bitmap {
        val matrix = Matrix().apply {
            postScale(-1f, 1f, source.width / 2f, source.height / 2f)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun combineAndNormalize(v1Hat: FloatArray, v2Hat: FloatArray): FloatArray {
        val dim = min(v1Hat.size, v2Hat.size)
        val vSum = FloatArray(dim)
        for (i in 0 until dim) {
            vSum[i] = v1Hat[i] + v2Hat[i]
        }
        return l2Normalize(vSum)
    }

    @Synchronized
    private fun runSingleTfliteInference(tflite: Interpreter, bitmap: Bitmap): FloatArray {
        val scaled = if (bitmap.width == inputWidth && bitmap.height == inputHeight) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        }

        val numPixels = inputWidth * inputHeight
        val pixels = IntArray(numPixels)
        scaled.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }

        val bytesPerChannel = if (inputDataType == DataType.UINT8) 1 else 4
        val inputBuffer = ByteBuffer.allocateDirect(bytesPerChannel * inputHeight * inputWidth * inputChannels)
            .order(ByteOrder.nativeOrder())

        if (inputDataType == DataType.UINT8) {
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                inputBuffer.put(r.toByte())
                if (inputChannels >= 3) {
                    inputBuffer.put(g.toByte())
                    inputBuffer.put(b.toByte())
                }
            }
        } else {
            // FLOAT32: normalize RGB pixels via (channel - 127.5f) / 128.0f
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()
                inputBuffer.putFloat((r - 127.5f) / 128.0f)
                if (inputChannels >= 3) {
                    inputBuffer.putFloat((g - 127.5f) / 128.0f)
                    inputBuffer.putFloat((b - 127.5f) / 128.0f)
                }
            }
        }
        inputBuffer.rewind()

        return if (outputDataType == DataType.UINT8) {
            val outBytes = Array(1) { ByteArray(outputEmbeddingDim) }
            tflite.run(inputBuffer, outBytes)
            val floatOut = FloatArray(outputEmbeddingDim)
            for (i in 0 until outputEmbeddingDim) {
                floatOut[i] = ((outBytes[0][i].toInt() and 0xFF) - 127.5f) / 128.0f
            }
            floatOut
        } else {
            val outFloats = Array(1) { FloatArray(outputEmbeddingDim) }
            tflite.run(inputBuffer, outFloats)
            outFloats[0]
        }
    }

    /**
     * Section 2.3: Deterministic Multi-Scale 192-D Local Binary Pattern (LBP) + HOG facial grid
     * descriptor on the grayscale eye-aligned crop (never uses RGB color histograms).
     * Used ONLY if mobilefacenet.tflite is absent in a web preview environment.
     */
    private fun extractStructuralLbpHog192D(bitmap: Bitmap): FloatArray {
        val gridDim = 96
        val scaled = if (bitmap.width == gridDim && bitmap.height == gridDim) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, gridDim, gridDim, true)
        }

        val pixels = IntArray(gridDim * gridDim)
        scaled.getPixels(pixels, 0, gridDim, 0, 0, gridDim, gridDim)
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }

        // Convert to grayscale luminance [0..1]
        val gray = FloatArray(gridDim * gridDim)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Local contrast normalization (removes illumination gradients)
        val normGray = localContrastNormalize(gray, gridDim, radius = 4)

        // Compute over a 4x4 spatial grid across the inner aligned face region [8..88, 8..88]
        // 16 cells * 12 structural features (8-bin HOG + 4-bin CS-LBP multi-scale) = 192 dimensions
        val gridCells = 4
        val margin = 8
        val cellSize = (gridDim - 2 * margin) / gridCells // 20px per cell
        val featuresPerCell = 12
        val numCells = gridCells * gridCells // 16
        val cellFeatures = Array(featuresPerCell) { FloatArray(numCells) }

        var cellIdx = 0
        for (gy in 0 until gridCells) {
            for (gx in 0 until gridCells) {
                val yStart = margin + gy * cellSize
                val yEnd = yStart + cellSize
                val xStart = margin + gx * cellSize
                val xEnd = xStart + cellSize

                val hogBins = FloatArray(8)
                val cslbpBins = FloatArray(4)
                var sampleCount = 0

                for (y in yStart until yEnd) {
                    val row = y * gridDim
                    for (x in xStart until xEnd) {
                        // 1. 8-orientation HOG accumulation (radius 1 & 2 multi-scale)
                        val dx = (normGray[row + (x + 1)] - normGray[row + (x - 1)]) +
                            0.5f * (normGray[row + (x + 2)] - normGray[row + (x - 2)])
                        val dy = (normGray[(y + 1) * gridDim + x] - normGray[(y - 1) * gridDim + x]) +
                            0.5f * (normGray[(y + 2) * gridDim + x] - normGray[(y - 2) * gridDim + x])
                        val mag = hypot(dx, dy)
                        var angle = atan2(dy, dx) // -pi..pi
                        if (angle < 0f) angle += (2.0 * Math.PI).toFloat()
                        val bin = ((angle / (2.0 * Math.PI).toFloat()) * 8f).toInt().coerceIn(0, 7)
                        hogBins[bin] += mag

                        // 2. Center-Symmetric Local Binary Pattern (CS-LBP) across 4 opposing pairs
                        val tau = 0.04f
                        val n0 = normGray[(y - 2) * gridDim + x] - normGray[(y + 2) * gridDim + x]
                        val n1 = normGray[(y - 2) * gridDim + (x + 2)] - normGray[(y + 2) * gridDim + (x - 2)]
                        val n2 = normGray[row + (x + 2)] - normGray[row + (x - 2)]
                        val n3 = normGray[(y + 2) * gridDim + (x + 2)] - normGray[(y - 2) * gridDim + (x - 2)]

                        cslbpBins[0] += if (abs(n0) > tau) n0 else 0f
                        cslbpBins[1] += if (abs(n1) > tau) n1 else 0f
                        cslbpBins[2] += if (abs(n2) > tau) n2 else 0f
                        cslbpBins[3] += if (abs(n3) > tau) n3 else 0f
                        sampleCount++
                    }
                }

                val invCount = if (sampleCount > 0) 1f / sampleCount.toFloat() else 1f
                for (b in 0 until 8) {
                    cellFeatures[b][cellIdx] = hogBins[b] * invCount
                }
                for (b in 0 until 4) {
                    cellFeatures[8 + b][cellIdx] = cslbpBins[b] * invCount
                }
                cellIdx++
            }
        }

        // Zero-center and Z-score normalize each feature channel across the 16 spatial cells
        val descriptor = FloatArray(FALLBACK_EMBEDDING_DIM) // 192-D
        var outIndex = 0
        for (f in 0 until featuresPerCell) {
            val channel = cellFeatures[f]
            var mean = 0f
            for (v in channel) mean += v
            mean /= numCells.toFloat()

            var varSum = 0f
            for (v in channel) {
                val diff = v - mean
                varSum += diff * diff
            }
            val std = sqrt(varSum / numCells.toFloat()) + 1e-5f
            for (c in 0 until numCells) {
                descriptor[outIndex++] = ((channel[c] - mean) / std).coerceIn(-3.0f, 3.0f)
            }
        }

        return descriptor
    }

    private fun localContrastNormalize(gray: FloatArray, size: Int, radius: Int): FloatArray {
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
                        val v = gray[rowOffset + xx]
                        sum += v
                        sumSq += v * v
                        count++
                    }
                }
                val mean = sum / count
                val variance = (sumSq / count - mean * mean).coerceAtLeast(0f)
                val std = sqrt(variance) + 0.04f
                result[y * size + x] = ((gray[y * size + x] - mean) / std).coerceIn(-3.0f, 3.0f)
            }
        }
        return result
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += (v * v).toDouble()
        }
        val norm = sqrt(sumSquares).toFloat()
        if (norm < 1e-6f) return vector

        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = vector[i] / norm
        }
        return normalized
    }

    @Synchronized
    fun close() {
        try {
            interpreter?.close()
        } catch (ignored: Exception) {
        } finally {
            interpreter = null
            modelInitAttempted = false
        }
    }
}
