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
 * (`mobilefacenet.tflite`) with dynamic batch size (`[2, 112, 112, 3]` or `[1, 112, 112, 3]`),
 * tensor shape, and DataType inspection.
 *
 * When `inputBatchSize >= 2 && outputBatchSize >= 2` (standard MobileFaceNet `[2, 112, 112, 3]` -> `[2, 192]`):
 * - Packs both `scaledOriginal` (slot 0) and `scaledFlipped` (slot 1) into a single batch `ByteBuffer`
 *   and runs both flip-augmented views in a single TFLite pass!
 */
class FaceEmbeddingExtractor(private val context: Context) {

    companion object {
        private const val TAG = "InstaGalleryML"
        private const val MODEL_ASSET_NAME = "mobilefacenet.tflite"
        const val FALLBACK_EMBEDDING_DIM = 192
    }

    private val lock = Any()
    private var interpreter: Interpreter? = null
    private var modelInitAttempted = false
    private var inputBatchSize = 1
    private var inputHeight = 112
    private var inputWidth = 112
    private var inputChannels = 3
    private var isQuantizedInput = false
    private var outputBatchSize = 1
    private var embeddingDim = FALLBACK_EMBEDDING_DIM
    private var outputDataType: DataType = DataType.FLOAT32

    private fun getOrInitInterpreter(): Interpreter? = synchronized(lock) {
        if (modelInitAttempted) return interpreter
        modelInitAttempted = true

        return try {
            val modelBuffer = loadModelFile(MODEL_ASSET_NAME)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val tflite = Interpreter(modelBuffer, options)

            val inputTensor = tflite.getInputTensor(0)
            val inShape = inputTensor.shape() // e.g., [2, 112, 112, 3] or [1, 112, 112, 3]
            inputBatchSize = if (inShape.size == 4) inShape[0].coerceAtLeast(1) else 1
            inputHeight = if (inShape.size == 4) inShape[1].coerceAtLeast(32) else 112
            inputWidth = if (inShape.size == 4) inShape[2].coerceAtLeast(32) else 112
            inputChannels = if (inShape.size == 4) inShape[3].coerceAtLeast(1) else 3
            isQuantizedInput = (inputTensor.dataType() == DataType.UINT8)

            val outputTensor = tflite.getOutputTensor(0)
            val outShape = outputTensor.shape() // e.g., [2, 192] or [1, 192]
            outputBatchSize = if (outShape.size >= 2) outShape[0].coerceAtLeast(1) else 1
            embeddingDim = if (outShape.size >= 2) {
                outShape[1].coerceAtLeast(64)
            } else if (outShape.isNotEmpty()) {
                outShape[0].coerceAtLeast(64)
            } else {
                FALLBACK_EMBEDDING_DIM
            }
            outputDataType = outputTensor.dataType()

            Log.i(
                TAG,
                "Loaded mobilefacenet.tflite: input=[$inputBatchSize, $inputHeight, $inputWidth, $inputChannels] " +
                    "(quantized=$isQuantizedInput), output=[$outputBatchSize, $embeddingDim]"
            )

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
     * Extracts a flip-augmented L2-normalized embedding from a 112x112 aligned face bitmap.
     * Supports both batch-2 (`[2, 112, 112, 3]` -> `[2, 192]`) and batch-1 (`[1, 112, 112, 3]` -> `[1, 192]`)
     * TFLite models seamlessly.
     */
    fun extractEmbedding(alignedFaceBitmap: Bitmap, face: Face? = null): FloatArray {
        val tflite = getOrInitInterpreter()
        val targetW = inputWidth
        val targetH = inputHeight

        val scaledOriginal = if (alignedFaceBitmap.width == targetW && alignedFaceBitmap.height == targetH) {
            alignedFaceBitmap
        } else {
            Bitmap.createScaledBitmap(alignedFaceBitmap, targetW, targetH, true)
        }
        val scaledFlipped = createHorizontallyMirroredBitmap(scaledOriginal)

        return try {
            if (tflite != null) {
                if (inputBatchSize >= 2 && outputBatchSize >= 2) {
                    runBatchTfliteInference(tflite, scaledOriginal, scaledFlipped)
                } else {
                    val raw1 = runSingleSlotTfliteInference(tflite, scaledOriginal)
                    val norm1 = l2Normalize(raw1)
                    val raw2 = runSingleSlotTfliteInference(tflite, scaledFlipped)
                    val norm2 = l2Normalize(raw2)
                    combineAndNormalize(norm1, norm2)
                }
            } else {
                val norm1 = l2Normalize(extractStructuralLbpHog192D(scaledOriginal))
                val norm2 = l2Normalize(extractStructuralLbpHog192D(scaledFlipped))
                combineAndNormalize(norm1, norm2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TFLite inference error, using 192-D LBP+HOG structural fallback", e)
            val norm1 = l2Normalize(extractStructuralLbpHog192D(scaledOriginal))
            val norm2 = l2Normalize(extractStructuralLbpHog192D(scaledFlipped))
            combineAndNormalize(norm1, norm2)
        } finally {
            if (scaledFlipped != scaledOriginal && scaledFlipped != alignedFaceBitmap && !scaledFlipped.isRecycled) {
                scaledFlipped.recycle()
            }
            if (scaledOriginal != alignedFaceBitmap && !scaledOriginal.isRecycled) {
                scaledOriginal.recycle()
            }
        }
    }

    private fun runBatchTfliteInference(
        tflite: Interpreter,
        scaledOriginal: Bitmap,
        scaledFlipped: Bitmap
    ): FloatArray {
        val bytesPerChannel = if (isQuantizedInput) 1 else 4
        val byteBuffer = ByteBuffer.allocateDirect(
            inputBatchSize * inputHeight * inputWidth * inputChannels * bytesPerChannel
        ).order(ByteOrder.nativeOrder())

        val numPixels = inputWidth * inputHeight
        val origPixels = IntArray(numPixels)
        val flipPixels = IntArray(numPixels)
        scaledOriginal.getPixels(origPixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        scaledFlipped.getPixels(flipPixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        // Slot 0: scaledOriginal
        writePixelsToBuffer(byteBuffer, origPixels)
        // Slot 1: scaledFlipped
        writePixelsToBuffer(byteBuffer, flipPixels)
        // Remaining slots if inputBatchSize > 2: repeat scaledOriginal
        for (slot in 2 until inputBatchSize) {
            writePixelsToBuffer(byteBuffer, origPixels)
        }
        byteBuffer.rewind()

        return if (outputDataType == DataType.UINT8) {
            val outBytes = Array(outputBatchSize) { ByteArray(embeddingDim) }
            synchronized(lock) {
                tflite.run(byteBuffer, outBytes)
            }
            val raw0 = dequantizeRow(outBytes[0])
            val raw1 = dequantizeRow(outBytes[1])
            val norm1 = l2Normalize(raw0)
            val norm2 = l2Normalize(raw1)
            combineAndNormalize(norm1, norm2)
        } else {
            val output = Array(outputBatchSize) { FloatArray(embeddingDim) }
            synchronized(lock) {
                tflite.run(byteBuffer, output)
            }
            val norm1 = l2Normalize(output[0])
            val norm2 = l2Normalize(output[1])
            combineAndNormalize(norm1, norm2)
        }
    }

    private fun runSingleSlotTfliteInference(tflite: Interpreter, bitmap: Bitmap): FloatArray {
        val bytesPerChannel = if (isQuantizedInput) 1 else 4
        val byteBuffer = ByteBuffer.allocateDirect(
            inputBatchSize * inputHeight * inputWidth * inputChannels * bytesPerChannel
        ).order(ByteOrder.nativeOrder())

        val numPixels = inputWidth * inputHeight
        val pixels = IntArray(numPixels)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        for (slot in 0 until inputBatchSize) {
            writePixelsToBuffer(byteBuffer, pixels)
        }
        byteBuffer.rewind()

        return if (outputDataType == DataType.UINT8) {
            val outBytes = Array(outputBatchSize) { ByteArray(embeddingDim) }
            synchronized(lock) {
                tflite.run(byteBuffer, outBytes)
            }
            dequantizeRow(outBytes[0])
        } else {
            val output = Array(outputBatchSize) { FloatArray(embeddingDim) }
            synchronized(lock) {
                tflite.run(byteBuffer, output)
            }
            output[0]
        }
    }

    private fun writePixelsToBuffer(buffer: ByteBuffer, pixels: IntArray) {
        if (isQuantizedInput) {
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                buffer.put(r.toByte())
                if (inputChannels >= 3) {
                    buffer.put(g.toByte())
                    buffer.put(b.toByte())
                }
            }
        } else {
            for (pixel in pixels) {
                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()
                buffer.putFloat((r - 127.5f) / 128.0f)
                if (inputChannels >= 3) {
                    buffer.putFloat((g - 127.5f) / 128.0f)
                    buffer.putFloat((b - 127.5f) / 128.0f)
                }
            }
        }
    }

    private fun dequantizeRow(row: ByteArray): FloatArray {
        val floatOut = FloatArray(row.size)
        for (i in row.indices) {
            floatOut[i] = ((row[i].toInt() and 0xFF) - 127.5f) / 128.0f
        }
        return floatOut
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

    /**
     * Deterministic Multi-Scale 192-D Local Binary Pattern (LBP) + HOG facial grid
     * descriptor on the grayscale aligned crop. Used ONLY if mobilefacenet.tflite is absent.
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

        val gray = FloatArray(gridDim * gridDim)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        val normGray = localContrastNormalize(gray, gridDim, radius = 4)

        val gridCells = 4
        val margin = 8
        val cellSize = (gridDim - 2 * margin) / gridCells
        val featuresPerCell = 12
        val numCells = gridCells * gridCells
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
                        val dx = (normGray[row + (x + 1)] - normGray[row + (x - 1)]) +
                            0.5f * (normGray[row + (x + 2)] - normGray[row + (x - 2)])
                        val dy = (normGray[(y + 1) * gridDim + x] - normGray[(y - 1) * gridDim + x]) +
                            0.5f * (normGray[(y + 2) * gridDim + x] - normGray[(y - 2) * gridDim + x])
                        val mag = hypot(dx, dy)
                        var angle = atan2(dy, dx)
                        if (angle < 0f) angle += (2.0 * Math.PI).toFloat()
                        val bin = ((angle / (2.0 * Math.PI).toFloat()) * 8f).toInt().coerceIn(0, 7)
                        hogBins[bin] += mag

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

        val descriptor = FloatArray(FALLBACK_EMBEDDING_DIM)
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

    fun close() = synchronized(lock) {
        try {
            interpreter?.close()
        } catch (ignored: Exception) {
        } finally {
            interpreter = null
            modelInitAttempted = false
        }
    }
}
