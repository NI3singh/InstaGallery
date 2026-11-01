// InstaGallery v2.1
package com.example.ml

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Landmark-based face alignment for MobileFaceNet.
 *
 * MobileFaceNet (like every ArcFace-family model) is trained on faces warped onto a fixed
 * 112x112 landmark template. Feeding it a bounding-box crop instead means the eyes and mouth
 * land in a different place on every photo, which spreads one person's embeddings apart and
 * pulls different people together.
 *
 * This file is pure Kotlin with no Android dependencies so the math is unit-testable on the JVM.
 * [FaceDetectorProcessor] turns the returned 2x3 matrix into an `android.graphics.Matrix`.
 */
object FaceAlignment {

    data class Point(val x: Float, val y: Float)

    const val TEMPLATE_SIZE = 112

    // Standard ArcFace 112x112 template, in IMAGE coordinates (left = smaller x).
    val TEMPLATE_IMAGE_LEFT_EYE = Point(38.2946f, 51.6963f)
    val TEMPLATE_IMAGE_RIGHT_EYE = Point(73.5318f, 51.5014f)
    val TEMPLATE_NOSE_TIP = Point(56.0252f, 71.7366f)
    val TEMPLATE_IMAGE_LEFT_MOUTH = Point(41.5493f, 92.3655f)
    val TEMPLATE_IMAGE_RIGHT_MOUTH = Point(70.7299f, 92.2041f)

    private val TEMPLATE_EYE_DISTANCE = TEMPLATE_IMAGE_RIGHT_EYE.x - TEMPLATE_IMAGE_LEFT_EYE.x

    /**
     * Sanity window for how wide the detector's bounding box ends up after warping.
     * On the ArcFace template a face box is roughly 70-100px wide; anything far outside that
     * means the landmarks were garbage (e.g. a mis-detected eye) and we fall back to a box crop.
     */
    private const val MIN_WARPED_BOX_WIDTH = 45f
    private const val MAX_WARPED_BOX_WIDTH = 170f

    enum class Method { FOUR_POINT, EYES_AND_NOSE, EYES_ONLY }

    data class Alignment(
        /** Row-major 2x3 similarity matrix [a, -b, tx, b, a, ty] mapping source pixels to template pixels. */
        val matrix: FloatArray,
        val method: Method
    ) {
        val scale: Float get() = hypot(matrix[0], matrix[3])
    }

    /**
     * Picks the best available landmark set and estimates the similarity transform onto the template.
     *
     * Landmarks are passed exactly as ML Kit names them (subject-relative: ML Kit's LEFT_EYE is the
     * subject's left eye, which normally sits on the RIGHT side of the image). We sort each pair by x
     * instead of trusting the labels, so mirrored selfies and normal photos both work.
     *
     * Preference order:
     * 1. Both eyes + both mouth corners (4 points). We deliberately skip ML Kit's NOSE_BASE here: it
     *    sits below the nose tip the template expects, so including it would bias the fit.
     * 2. Both eyes + nose base, when the mouth is missing.
     * 3. Both eyes only.
     *
     * Returns null if landmarks are missing or the result is implausible for the given face box;
     * callers should fall back to a plain box crop in that case.
     */
    fun estimate(
        mlKitLeftEye: Point?,
        mlKitRightEye: Point?,
        noseBase: Point?,
        mlKitMouthLeft: Point?,
        mlKitMouthRight: Point?,
        boxWidth: Float
    ): Alignment? {
        if (mlKitLeftEye == null || mlKitRightEye == null) return null
        val (imgLeftEye, imgRightEye) = sortByX(mlKitLeftEye, mlKitRightEye)
        if (distance(imgLeftEye, imgRightEye) < 4f) return null

        val src = mutableListOf(imgLeftEye, imgRightEye)
        val dst = mutableListOf(TEMPLATE_IMAGE_LEFT_EYE, TEMPLATE_IMAGE_RIGHT_EYE)
        val method: Method

        if (mlKitMouthLeft != null && mlKitMouthRight != null) {
            val (imgLeftMouth, imgRightMouth) = sortByX(mlKitMouthLeft, mlKitMouthRight)
            src += imgLeftMouth
            src += imgRightMouth
            dst += TEMPLATE_IMAGE_LEFT_MOUTH
            dst += TEMPLATE_IMAGE_RIGHT_MOUTH
            method = Method.FOUR_POINT
        } else if (noseBase != null) {
            src += noseBase
            dst += TEMPLATE_NOSE_TIP
            method = Method.EYES_AND_NOSE
        } else {
            method = Method.EYES_ONLY
        }

        val matrix = estimateSimilarity(src, dst) ?: return null
        val alignment = Alignment(matrix, method)

        val warpedBoxWidth = boxWidth * alignment.scale
        if (warpedBoxWidth !in MIN_WARPED_BOX_WIDTH..MAX_WARPED_BOX_WIDTH) return null

        // A similarity fit can still "succeed" on a wildly wrong landmark; make sure the eyes land
        // near where the template expects them before trusting it.
        val warpedLeftEye = apply(matrix, imgLeftEye)
        val warpedRightEye = apply(matrix, imgRightEye)
        val eyeTolerance = TEMPLATE_EYE_DISTANCE * 0.35f
        if (distance(warpedLeftEye, TEMPLATE_IMAGE_LEFT_EYE) > eyeTolerance ||
            distance(warpedRightEye, TEMPLATE_IMAGE_RIGHT_EYE) > eyeTolerance
        ) {
            return null
        }

        return alignment
    }

    /**
     * Closed-form least-squares 2D similarity transform (rotation + uniform scale + translation,
     * no reflection) mapping [src] onto [dst]. This is Umeyama's method specialised to 2D.
     *
     * Returns a row-major 2x3 matrix [a, -b, tx, b, a, ty], or null for degenerate input.
     */
    fun estimateSimilarity(src: List<Point>, dst: List<Point>): FloatArray? {
        require(src.size == dst.size) { "src and dst must have the same number of points" }
        val n = src.size
        if (n < 2) return null

        var srcMeanX = 0.0
        var srcMeanY = 0.0
        var dstMeanX = 0.0
        var dstMeanY = 0.0
        for (i in 0 until n) {
            srcMeanX += src[i].x
            srcMeanY += src[i].y
            dstMeanX += dst[i].x
            dstMeanY += dst[i].y
        }
        srcMeanX /= n
        srcMeanY /= n
        dstMeanX /= n
        dstMeanY /= n

        var srcVar = 0.0
        var dotSum = 0.0
        var crossSum = 0.0
        for (i in 0 until n) {
            val sx = src[i].x - srcMeanX
            val sy = src[i].y - srcMeanY
            val dx = dst[i].x - dstMeanX
            val dy = dst[i].y - dstMeanY
            srcVar += sx * sx + sy * sy
            dotSum += sx * dx + sy * dy
            crossSum += sx * dy - sy * dx
        }
        if (srcVar < 1e-6) return null

        val a = dotSum / srcVar
        val b = crossSum / srcVar
        if (abs(a) < 1e-9 && abs(b) < 1e-9) return null

        val tx = dstMeanX - (a * srcMeanX - b * srcMeanY)
        val ty = dstMeanY - (b * srcMeanX + a * srcMeanY)

        return floatArrayOf(
            a.toFloat(), (-b).toFloat(), tx.toFloat(),
            b.toFloat(), a.toFloat(), ty.toFloat()
        )
    }

    fun apply(matrix: FloatArray, p: Point): Point = Point(
        x = matrix[0] * p.x + matrix[1] * p.y + matrix[2],
        y = matrix[3] * p.x + matrix[4] * p.y + matrix[5]
    )

    private fun sortByX(p1: Point, p2: Point): Pair<Point, Point> =
        if (p1.x <= p2.x) p1 to p2 else p2 to p1

    private fun distance(p1: Point, p2: Point): Float = hypot(p1.x - p2.x, p1.y - p2.y)
}
