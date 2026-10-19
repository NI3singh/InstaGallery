// InstaGallery v1.0
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class ExampleUnitTest {

    companion object {
        const val EMBEDDING_DIM = 256
    }

    @Test
    fun embeddingSerialization_roundTripPreserves256Dimensions() {
        val original = FloatArray(EMBEDDING_DIM) { i -> sin(i * 0.25f) }
        val buffer = ByteBuffer.allocate(original.size * 4).order(ByteOrder.nativeOrder())
        for (f in original) buffer.putFloat(f)
        val bytes = buffer.array()

        val readBuf = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        val restored = FloatArray(bytes.size / 4) { readBuf.float }

        assertEquals(EMBEDDING_DIM, restored.size)
        for (i in original.indices) {
            assertEquals(original[i], restored[i], 1e-6f)
        }
    }

    @Test
    fun zeroCenteredEmbeddings_separateDifferentIdentitiesClearly() {
        // Construct two orthogonal zero-centered unit vectors representing two different people
        val personA = FloatArray(EMBEDDING_DIM) { i ->
            if (i % 2 == 0) 1f else -1f
        }
        val personB = FloatArray(EMBEDDING_DIM) { i ->
            if ((i / 2) % 2 == 0) 1f else -1f
        }
        normalize(personA)
        normalize(personB)

        // Same person with slight lighting/pose perturbation
        val personAVariant = FloatArray(EMBEDDING_DIM) { i ->
            personA[i] + (if (i % 5 == 0) 0.08f else -0.02f)
        }
        normalize(personAVariant)

        val sameSim = dot(personA, personAVariant)
        val diffSim = dot(personA, personB)

        assertTrue("Same person similarity ($sameSim) should exceed threshold", sameSim > 0.72f)
        assertTrue("Different people similarity ($diffSim) should be near 0", diffSim < 0.25f)
    }

    private fun dot(v1: FloatArray, v2: FloatArray): Float {
        val len = min(v1.size, v2.size)
        var s = 0f
        for (i in 0 until len) s += v1[i] * v2[i]
        return s
    }

    private fun normalize(vec: FloatArray) {
        var normSq = 0f
        for (v in vec) normSq += v * v
        val norm = sqrt(normSq).coerceAtLeast(1e-6f)
        for (i in vec.indices) vec[i] /= norm
    }
}
