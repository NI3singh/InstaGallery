// InstaGallery v2.1
package com.example

import com.example.ml.FaceAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure JVM tests for the landmark-based alignment used before MobileFaceNet inference.
 * The alignment object has no Android dependencies on purpose so these run without Robolectric.
 */
class FaceAlignmentTest {

    private val templateEyes = listOf(
        FaceAlignment.TEMPLATE_IMAGE_LEFT_EYE,
        FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE
    )

    @Test
    fun `identity landmarks map onto template with unit scale`() {
        val alignment = FaceAlignment.estimate(
            mlKitLeftEye = FaceAlignment.TEMPLATE_IMAGE_LEFT_EYE,
            mlKitRightEye = FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE,
            noseBase = FaceAlignment.TEMPLATE_NOSE_TIP,
            mlKitMouthLeft = FaceAlignment.TEMPLATE_IMAGE_LEFT_MOUTH,
            mlKitMouthRight = FaceAlignment.TEMPLATE_IMAGE_RIGHT_MOUTH,
            boxWidth = 90f
        )
        assertNotNull(alignment)
        assertEquals(1f, alignment!!.scale, 1e-4f)
        assertEquals(FaceAlignment.Method.FOUR_POINT, alignment.method)
    }

    @Test
    fun `rotated face is straightened onto the template`() {
        // Build a face whose eyes are rotated 15 degrees around the template center.
        val angle = Math.toRadians(15.0)
        val cx = 56f
        val cy = 72f
        fun rotate(p: FaceAlignment.Point): FaceAlignment.Point {
            val dx = p.x - cx
            val dy = p.y - cy
            return FaceAlignment.Point(
                x = (dx * cos(angle) - dy * sin(angle) + cx).toFloat(),
                y = (dx * sin(angle) + dy * cos(angle) + cy).toFloat()
            )
        }

        val alignment = FaceAlignment.estimate(
            mlKitLeftEye = rotate(FaceAlignment.TEMPLATE_IMAGE_LEFT_EYE),
            mlKitRightEye = rotate(FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE),
            noseBase = rotate(FaceAlignment.TEMPLATE_NOSE_TIP),
            mlKitMouthLeft = rotate(FaceAlignment.TEMPLATE_IMAGE_LEFT_MOUTH),
            mlKitMouthRight = rotate(FaceAlignment.TEMPLATE_IMAGE_RIGHT_MOUTH),
            boxWidth = 90f
        )
        assertNotNull(alignment)

        val warpedLeftEye = FaceAlignment.apply(alignment!!.matrix, rotate(FaceAlignment.TEMPLATE_IMAGE_LEFT_EYE))
        val warpedRightEye = FaceAlignment.apply(alignment.matrix, rotate(FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE))
        assertEquals(FaceAlignment.TEMPLATE_IMAGE_LEFT_EYE.x, warpedLeftEye.x, 1.5f)
        assertEquals(FaceAlignment.TEMPLATE_IMAGE_LEFT_EYE.y, warpedLeftEye.y, 1.5f)
        assertEquals(FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE.x, warpedRightEye.x, 1.5f)
        assertEquals(FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE.y, warpedRightEye.y, 1.5f)
    }

    @Test
    fun `ml kit left-right labels do not mirror the result`() {
        // ML Kit's LEFT_EYE is the subject's left eye, which usually sits on the image's right.
        // Swapping the labels must produce the same alignment because we sort by x.
        val a = FaceAlignment.estimate(
            mlKitLeftEye = FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE,
            mlKitRightEye = FaceAlignment.TEMPLATE_IMAGE_LEFT_EYE,
            noseBase = null,
            mlKitMouthLeft = FaceAlignment.TEMPLATE_IMAGE_RIGHT_MOUTH,
            mlKitMouthRight = FaceAlignment.TEMPLATE_IMAGE_LEFT_MOUTH,
            boxWidth = 90f
        )
        val b = FaceAlignment.estimate(
            mlKitLeftEye = FaceAlignment.TEMPLATE_IMAGE_LEFT_EYE,
            mlKitRightEye = FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE,
            noseBase = null,
            mlKitMouthLeft = FaceAlignment.TEMPLATE_IMAGE_LEFT_MOUTH,
            mlKitMouthRight = FaceAlignment.TEMPLATE_IMAGE_RIGHT_MOUTH,
            boxWidth = 90f
        )
        assertNotNull(a)
        assertNotNull(b)
        for (i in 0 until 6) {
            assertEquals(a!!.matrix[i], b!!.matrix[i], 1e-3f)
        }
    }

    @Test
    fun `degenerate inputs return null instead of a garbage transform`() {
        // Missing eyes
        assertNull(
            FaceAlignment.estimate(null, FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE, null, null, null, 90f)
        )
        // Eyes nearly overlapping
        assertNull(
            FaceAlignment.estimate(
                FaceAlignment.Point(50f, 50f),
                FaceAlignment.Point(50.5f, 50f),
                null, null, null, 90f
            )
        )
        // Fewer than 2 points
        assertNull(FaceAlignment.estimateSimilarity(listOf(FaceAlignment.Point(1f, 1f)), listOf(FaceAlignment.Point(2f, 2f))))
        // Duplicate points (zero variance)
        assertNull(
            FaceAlignment.estimateSimilarity(
                listOf(FaceAlignment.Point(1f, 1f), FaceAlignment.Point(1f, 1f)),
                listOf(FaceAlignment.Point(2f, 2f), FaceAlignment.Point(2f, 2f))
            )
        )
    }

    @Test
    fun `implausible landmark fit is rejected via box width sanity window`() {
        // Eyes only 10px apart inside a box that is 90px wide: scale explodes far beyond the window.
        val alignment = FaceAlignment.estimate(
            mlKitLeftEye = FaceAlignment.Point(50f, 51f),
            mlKitRightEye = FaceAlignment.Point(60f, 51f),
            noseBase = null,
            mlKitMouthLeft = null,
            mlKitMouthRight = null,
            boxWidth = 90f
        )
        assertNull(alignment)
    }

    @Test
    fun `uniform similarity transform recovers scale and rotation`() {
        val angle = 0.3
        val scale = 2.0
        val src = listOf(
            FaceAlignment.Point(0f, 0f),
            FaceAlignment.Point(10f, 0f),
            FaceAlignment.Point(5f, 8f)
        )
        val dst = src.map { p ->
            FaceAlignment.Point(
                x = (p.x * scale * cos(angle)).toFloat(),
                y = (p.x * scale * sin(angle)).toFloat()
            )
        }
        val m = FaceAlignment.estimateSimilarity(src, dst)!!
        val recovered = hypot(m[0], m[3])
        assertEquals(scale.toFloat(), recovered, 1e-3f)
        // Apply to src[1]: should land on dst[1]
        val applied = FaceAlignment.apply(m, src[1])
        assertEquals(dst[1].x, applied.x, 1e-2f)
        assertEquals(dst[1].y, applied.y, 1e-2f)
    }

    @Test
    fun `warped box width sanity window accepts typical faces`() {
        // A 90px face box with a standard fit lands ~90px wide on the template: plausible.
        val alignment = FaceAlignment.estimate(
            mlKitLeftEye = FaceAlignment.TEMPLATE_IMAGE_LEFT_EYE,
            mlKitRightEye = FaceAlignment.TEMPLATE_IMAGE_RIGHT_EYE,
            noseBase = FaceAlignment.TEMPLATE_NOSE_TIP,
            mlKitMouthLeft = null,
            mlKitMouthRight = null,
            boxWidth = 90f
        )
        assertNotNull(alignment)
        assertTrue(alignment!!.method == FaceAlignment.Method.EYES_AND_NOSE)
    }
}
