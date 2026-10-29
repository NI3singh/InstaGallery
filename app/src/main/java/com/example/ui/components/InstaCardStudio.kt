// InstaGallery v1.0
package com.example.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class InstaCardTemplate(val label: String) {
    OLED_DARK("OLED Dark"),
    CHAMELEON_GLASS("Ambient Glass"),
    POLAROID_STAMP("Polaroid EXIF")
}

/**
 * Reads a concise 1-line camera/EXIF summary for the Polaroid EXIF template and inspector.
 */
fun readExifSummary(context: Context, uriString: String): String {
    return try {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val parts = mutableListOf<String>()

            // 1. Focal length
            val focal35 = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
            if (focal35 > 0) {
                parts.add("${focal35}mm")
            } else {
                val focalRaw = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                if (focalRaw > 0.0) {
                    val formattedFocal = if (focalRaw % 1.0 == 0.0) {
                        "${focalRaw.toInt()}mm"
                    } else {
                        String.format(Locale.US, "%.1fmm", focalRaw)
                    }
                    parts.add(formattedFocal)
                }
            }

            // 2. Aperture
            val fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
                ?: exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)
            if (!fNumber.isNullOrBlank()) {
                val parsedF = parseRationalOrDouble(fNumber)
                if (parsedF != null && parsedF > 0.0) {
                    parts.add(String.format(Locale.US, "f/%.1f", parsedF))
                }
            }

            // 3. Shutter speed
            val exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            if (!exposureTime.isNullOrBlank()) {
                val expSec = exposureTime.toDoubleOrNull()
                if (expSec != null && expSec > 0.0) {
                    val shutterStr = if (expSec < 1.0) {
                        val denom = (1.0 / expSec).roundToInt().coerceAtLeast(1)
                        "1/${denom}s"
                    } else {
                        String.format(Locale.US, "%.1fs", expSec)
                    }
                    parts.add(shutterStr)
                }
            }

            // 4. ISO
            val iso = exif.getAttribute("PhotographicSensitivity")
                ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            if (!iso.isNullOrBlank()) {
                parts.add("ISO ${iso.trim()}")
            }

            if (parts.isEmpty()) {
                val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                if (!model.isNullOrBlank()) {
                    parts.add(model)
                }
            }

            if (parts.isNotEmpty()) {
                parts.joinToString("  •  ")
            } else {
                "24mm  •  f/1.8  •  ISO 100"
            }
        } ?: "24mm  •  f/1.8  •  ISO 100"
    } catch (_: Exception) {
        "24mm  •  f/1.8  •  ISO 100"
    }
}

/**
 * Custom 24x24 2.0dp rounded-stroke Snapshot Studio viewfinder/card icon.
 */
val SnapshotStudioIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "SnapshotStudioIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        val stroke = androidx.compose.ui.graphics.SolidColor(Color.White)
        addPath(
            pathData = androidx.compose.ui.graphics.vector.PathBuilder().apply {
                moveTo(4f, 8.5f)
                lineTo(4f, 6f)
                quadTo(4f, 4f, 6f, 4f)
                lineTo(8.5f, 4f)
                moveTo(15.5f, 4f)
                lineTo(18f, 4f)
                quadTo(20f, 4f, 20f, 6f)
                lineTo(20f, 8.5f)
                moveTo(20f, 15.5f)
                lineTo(20f, 18f)
                quadTo(20f, 20f, 18f, 20f)
                lineTo(15.5f, 20f)
                moveTo(8.5f, 20f)
                lineTo(6f, 20f)
                quadTo(4f, 20f, 4f, 18f)
                lineTo(4f, 15.5f)
                moveTo(15.2f, 12f)
                arcTo(3.2f, 3.2f, 0f, true, true, 8.8f, 12f)
                arcTo(3.2f, 3.2f, 0f, true, true, 15.2f, 12f)
            }.nodes,
            fill = null,
            stroke = stroke,
            strokeLineWidth = 2.0f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        )
    }.build()
}

private fun parseRationalOrDouble(raw: String): Double? {
    val clean = raw.trim()
    if (clean.contains("/")) {
        val tokens = clean.split("/")
        if (tokens.size == 2) {
            val num = tokens[0].toDoubleOrNull()
            val den = tokens[1].toDoubleOrNull()
            if (num != null && den != null && den != 0.0) {
                return num / den
            }
        }
    }
    return clean.toDoubleOrNull()
}

/**
 * Decodes an upright, high-clarity bitmap from a MediaStore URI with EXIF orientation applied.
 */
private fun decodeUprightBitmap(context: Context, media: MediaEntity, maxEdge: Int = 1440): Bitmap? {
    val uri = runCatching { Uri.parse(media.uri) }.getOrNull() ?: return null

    if (media.isVideo) {
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            if (frame != null) return frame
        }
    }

    return try {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        var sampleSize = 1
        val rawW = boundsOptions.outWidth
        val rawH = boundsOptions.outHeight
        if (rawW > 0 && rawH > 0) {
            val longest = max(rawW, rawH)
            while (longest / (sampleSize * 2) >= maxEdge) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        }

        val rotated = if (!matrix.isIdentity) {
            val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (result != decoded) decoded.recycle()
            result
        } else {
            decoded
        }

        val currentMax = max(rotated.width, rotated.height)
        if (currentMax > maxEdge) {
            val scale = maxEdge.toFloat() / currentMax.toFloat()
            val scaledW = (rotated.width * scale).roundToInt().coerceAtLeast(1)
            val scaledH = (rotated.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(rotated, scaledW, scaledH, true)
            if (scaled != rotated) rotated.recycle()
            scaled
        } else {
            rotated
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Samples a region average color from a Bitmap and boosts its saturation for Chameleon Ambient Glass.
 */
private fun sampleBoostedColor(bitmap: Bitmap, centerXRatio: Float, centerYRatio: Float): Int {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return 0xFF1F1C2C.toInt()

    val cx = (w * centerXRatio).toInt().coerceIn(0, w - 1)
    val cy = (h * centerYRatio).toInt().coerceIn(0, h - 1)
    val radius = (min(w, h) * 0.08f).toInt().coerceAtLeast(2)

    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0L

    val step = max(1, radius / 4)
    for (y in (cy - radius).coerceAtLeast(0)..(cy + radius).coerceAtMost(h - 1) step step) {
        for (x in (cx - radius).coerceAtLeast(0)..(cx + radius).coerceAtMost(w - 1) step step) {
            val pixel = bitmap.getPixel(x, y)
            rSum += android.graphics.Color.red(pixel)
            gSum += android.graphics.Color.green(pixel)
            bSum += android.graphics.Color.blue(pixel)
            count++
        }
    }

    if (count == 0L) return 0xFF28243D.toInt()
    val r = (rSum / count).toInt()
    val g = (gSum / count).toInt()
    val b = (bSum / count).toInt()

    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(r, g, b, hsv)
    hsv[1] = (hsv[1] * 1.38f).coerceIn(0.28f, 0.92f)
    hsv[2] = hsv[2].coerceIn(0.26f, 0.72f)
    return android.graphics.Color.HSVToColor(hsv)
}

/**
 * Generates a razor-sharp 1080p Instagram-styled snapshot card Bitmap on Dispatchers.Default.
 */
suspend fun generateInstaCardBitmap(
    context: Context,
    media: MediaEntity,
    primaryAuthorName: String,
    authorAvatarPath: String?,
    template: InstaCardTemplate,
    heartColor: Color,
    showWatermark: Boolean
): Bitmap? = withContext(Dispatchers.Default) {
    val sourceBitmap = decodeUprightBitmap(context, media, maxEdge = 1440) ?: return@withContext null

    val canvasWidth = 1080
    val photoRatio = (sourceBitmap.width.toFloat() / sourceBitmap.height.toFloat()).coerceIn(0.65f, 1.77f)
    val innerImageWidth = 920
    val innerImageHeight = (innerImageWidth.toFloat() / photoRatio).roundToInt()
    val totalHeight = (innerImageHeight + 460).coerceIn(1350, 1820)

    val outBitmap = Bitmap.createBitmap(canvasWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)

    val cleanAuthor = primaryAuthorName.trim().removePrefix("@").ifBlank { "Other" }
    val formattedDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        .format(Date(media.dateAddedSeconds * 1000L))

    val customCaptionText = media.customCaption?.trim()?.takeIf { it.isNotEmpty() }

    // 1. Draw Background Canvas (1080 x totalHeight)
    when (template) {
        InstaCardTemplate.OLED_DARK -> {
            // Softly diffused ambient photo backdrop + deep OLED scrim + dual radial aurora glows
            val tiny = Bitmap.createScaledBitmap(sourceBitmap, 36, 48, true)
            val blurredBg = Bitmap.createScaledBitmap(tiny, canvasWidth, totalHeight, true)
            tiny.recycle()
            canvas.drawBitmap(blurredBg, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            blurredBg.recycle()

            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xD108080C.toInt() // 82% alpha #08080C
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), totalHeight.toFloat(), scrimPaint)

            val topGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    canvasWidth * 0.18f,
                    totalHeight * 0.14f,
                    canvasWidth * 0.72f,
                    intArrayOf(0x388134AF, 0x008134AF),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), totalHeight.toFloat(), topGlowPaint)

            val bottomGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    canvasWidth * 0.85f,
                    totalHeight * 0.86f,
                    canvasWidth * 0.68f,
                    intArrayOf(0x2EF58529, 0x00F58529),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), totalHeight.toFloat(), bottomGlowPaint)
        }

        InstaCardTemplate.CHAMELEON_GLASS -> {
            val c1 = sampleBoostedColor(sourceBitmap, 0.25f, 0.25f)
            val c2 = sampleBoostedColor(sourceBitmap, 0.50f, 0.50f)
            val c3 = sampleBoostedColor(sourceBitmap, 0.75f, 0.75f)

            val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f,
                    0f,
                    canvasWidth.toFloat(),
                    totalHeight.toFloat(),
                    intArrayOf(c1, c2, c3),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), totalHeight.toFloat(), gradPaint)

            // Subtle ambient blur of source photo at 25% alpha
            val tiny = Bitmap.createScaledBitmap(sourceBitmap, 32, 42, true)
            val blurredBg = Bitmap.createScaledBitmap(tiny, canvasWidth, totalHeight, true)
            tiny.recycle()
            val blendPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 64 }
            canvas.drawBitmap(blurredBg, 0f, 0f, blendPaint)
            blurredBg.recycle()

            // 45% dark scrim
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x73000000
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), totalHeight.toFloat(), scrimPaint)
        }

        InstaCardTemplate.POLAROID_STAMP -> {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0E0E12.toInt()
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), totalHeight.toFloat(), bgPaint)

            val spotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    canvasWidth * 0.5f,
                    totalHeight * 0.5f,
                    canvasWidth * 0.75f,
                    intArrayOf(0xFF1A1A22.toInt(), 0xFF0E0E12.toInt()),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, canvasWidth.toFloat(), totalHeight.toFloat(), spotPaint)
        }
    }

    // 2. Draw Center Card Container (left = 60f, right = 1020f)
    when (template) {
        InstaCardTemplate.OLED_DARK, InstaCardTemplate.CHAMELEON_GLASS -> {
            val headerHeight = 116f
            val hasCaption = !customCaptionText.isNullOrBlank()
            val footerHeight = if (hasCaption) 182f else 118f
            val cardTotalHeight = headerHeight + innerImageHeight + footerHeight
            val cardTop = ((totalHeight - cardTotalHeight) / 2f).coerceAtLeast(42f)
            val cardBottom = cardTop + cardTotalHeight
            val cardRect = RectF(60f, cardTop, 1020f, cardBottom)

            // Card drop shadow + surface fill
            val cardSurfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (template == InstaCardTemplate.OLED_DARK) {
                    0xFF121216.toInt()
                } else {
                    0xEB18181E.toInt() // 92% alpha #18181E
                }
                setShadowLayer(36f, 0f, 18f, 0xAA000000.toInt())
            }
            canvas.drawRoundRect(cardRect, 44f, 44f, cardSurfacePaint)

            // Specular border (2.5f)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = 0xFF2E2E38.toInt()
            }
            canvas.drawRoundRect(cardRect, 44f, 44f, borderPaint)

            // Card Header (height = 116px)
            val avatarCenterX = 118f
            val avatarCenterY = cardTop + 58f
            val avatarRadius = 34f

            drawAuthorAvatarWithStoryRing(
                context = context,
                canvas = canvas,
                avatarPath = authorAvatarPath,
                centerX = avatarCenterX,
                centerY = avatarCenterY,
                radius = avatarRadius,
                ringStrokeWidth = 4f
            )

            // Handle text & date
            val handlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF8E8E93.toInt()
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val handleStr = "@$cleanAuthor"
            val handleX = 172f
            val handleBaselineY = avatarCenterY + 12f
            canvas.drawText(handleStr, handleX, handleBaselineY, handlePaint)

            val handleWidth = handlePaint.measureText(handleStr)
            canvas.drawText("  •  $formattedDate", handleX + handleWidth, handleBaselineY, datePaint)

            // Card Photo (left = 80f, right = 1000f, 24f rounded corners)
            val photoTop = cardTop + headerHeight
            val photoBottom = photoTop + innerImageHeight
            val photoRect = RectF(80f, photoTop, 1000f, photoBottom)

            drawCenterCroppedRoundedBitmap(
                canvas = canvas,
                bitmap = sourceBitmap,
                destRect = photoRect,
                cornerRadius = 24f
            )

            // Card Action Row
            val iconRowCenterY = photoBottom + 56f
            drawInstaActionIconsRow(
                canvas = canvas,
                centerY = iconRowCenterY,
                heartColorArgb = heartColor.toArgb(),
                showWatermarkOnRight = showWatermark && !hasCaption
            )

            // Caption Footer (if customCaption exists)
            if (hasCaption && customCaptionText != null) {
                val captionBaselineY = iconRowCenterY + 68f
                val authorPrefix = "@$cleanAuthor  "
                val boldPrefixPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 30f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val bodyCaptionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFE5E5EA.toInt()
                    textSize = 30f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }

                canvas.drawText(authorPrefix, 86f, captionBaselineY, boldPrefixPaint)
                val prefixW = boldPrefixPaint.measureText(authorPrefix)
                val maxCaptionWidth = if (showWatermark) 690f - prefixW else 900f - prefixW
                val ellipsized = TextUtils.ellipsize(
                    customCaptionText,
                    bodyCaptionPaint,
                    maxCaptionWidth.coerceAtLeast(180f),
                    TextUtils.TruncateAt.END
                ).toString()
                canvas.drawText(ellipsized, 86f + prefixW, captionBaselineY, bodyCaptionPaint)

                if (showWatermark) {
                    val wmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF737373.toInt()
                        textSize = 24f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.RIGHT
                    }
                    canvas.drawText("InstaGallery", 994f, captionBaselineY, wmPaint)
                }
            }
        }

        InstaCardTemplate.POLAROID_STAMP -> {
            // Classic warm Polaroid card (#F4F4F6 off-white surface, 28f corner radius)
            // with 28px side/top borders and a 210px bottom chin
            val borderPad = 28f
            val chinHeight = 210f
            val cardTotalHeight = borderPad + innerImageHeight + chinHeight
            val cardTop = ((totalHeight - cardTotalHeight) / 2f).coerceAtLeast(42f)
            val cardBottom = cardTop + cardTotalHeight
            val cardRect = RectF(60f, cardTop, 1020f, cardBottom)

            val polaroidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF4F4F6.toInt()
                setShadowLayer(40f, 0f, 20f, 0xAA000000.toInt())
            }
            canvas.drawRoundRect(cardRect, 28f, 28f, polaroidPaint)

            val photoRect = RectF(
                60f + borderPad,
                cardTop + borderPad,
                1020f - borderPad,
                cardTop + borderPad + innerImageHeight
            )
            drawCenterCroppedRoundedBitmap(
                canvas = canvas,
                bitmap = sourceBitmap,
                destRect = photoRect,
                cornerRadius = 12f
            )

            // Bottom Chin content
            val chinTop = photoRect.bottom
            val leftTextX = 94f

            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF121214.toInt()
                textSize = 34f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF52525B.toInt()
                textSize = 24f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF3F3F46.toInt()
                textSize = 25f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            val titleLine = if (!customCaptionText.isNullOrBlank()) {
                "@$cleanAuthor  •  $formattedDate"
            } else {
                "@$cleanAuthor"
            }
            canvas.drawText(titleLine, leftTextX, chinTop + 72f, titlePaint)

            if (!customCaptionText.isNullOrBlank()) {
                val clippedNote = TextUtils.ellipsize(
                    customCaptionText,
                    notePaint,
                    740f,
                    TextUtils.TruncateAt.END
                ).toString()
                canvas.drawText(clippedNote, leftTextX, chinTop + 116f, notePaint)
            } else {
                canvas.drawText(formattedDate, leftTextX, chinTop + 114f, notePaint)
            }

            val exifSummary = readExifSummary(context, media.uri)
            val stampLine = if (showWatermark) {
                "$exifSummary  •  InstaGallery"
            } else {
                exifSummary
            }
            canvas.drawText(stampLine, leftTextX, chinTop + 162f, subtitlePaint)

            // Circular author face avatar on the right side of the chin
            drawAuthorAvatarWithStoryRing(
                context = context,
                canvas = canvas,
                avatarPath = authorAvatarPath,
                centerX = 942f,
                centerY = chinTop + (chinHeight / 2f),
                radius = 42f,
                ringStrokeWidth = 4f
            )
        }
    }

    sourceBitmap.recycle()
    outBitmap
}

private fun drawCenterCroppedRoundedBitmap(
    canvas: Canvas,
    bitmap: Bitmap,
    destRect: RectF,
    cornerRadius: Float
) {
    val saveCount = canvas.save()
    val clipPath = Path().apply {
        addRoundRect(destRect, cornerRadius, cornerRadius, Path.Direction.CW)
    }
    canvas.clipPath(clipPath)

    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val dw = destRect.width()
    val dh = destRect.height()

    val scale = max(dw / bw, dh / bh)
    val scaledW = bw * scale
    val scaledH = bh * scale
    val left = destRect.left + (dw - scaledW) / 2f
    val top = destRect.top + (dh - scaledH) / 2f
    val targetRect = RectF(left, top, left + scaledW, top + scaledH)

    canvas.drawBitmap(bitmap, null, targetRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restoreToCount(saveCount)
}

private fun drawAuthorAvatarWithStoryRing(
    context: Context,
    canvas: Canvas,
    avatarPath: String?,
    centerX: Float,
    centerY: Float,
    radius: Float,
    ringStrokeWidth: Float
) {
    // Sunset gradient ring
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringStrokeWidth
        shader = LinearGradient(
            centerX - radius,
            centerY + radius,
            centerX + radius,
            centerY - radius,
            intArrayOf(
                0xFFFEDA77.toInt(),
                0xFFF58529.toInt(),
                0xFFDD2A7B.toInt(),
                0xFF8134AF.toInt()
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawCircle(centerX, centerY, radius, ringPaint)

    val innerRadius = radius - ringStrokeWidth - 2f
    val avatarBmp: Bitmap? = avatarPath?.trim()?.takeIf { it.isNotEmpty() }?.let { rawPath ->
        runCatching {
            val fileObj = File(rawPath)
            when {
                fileObj.exists() -> BitmapFactory.decodeFile(fileObj.absolutePath)
                rawPath.startsWith("content://") || rawPath.startsWith("file://") -> {
                    val parsedUri = Uri.parse(rawPath)
                    context.contentResolver.openInputStream(parsedUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: parsedUri.path?.let { p ->
                        val f = File(p)
                        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                    }
                }
                else -> null
            }
        }.getOrNull()
    }

    if (avatarBmp != null) {
        val minEdge = min(avatarBmp.width, avatarBmp.height).coerceAtLeast(1)
        val cropX = ((avatarBmp.width - minEdge) / 2).coerceAtLeast(0)
        val cropY = ((avatarBmp.height - minEdge) / 2).coerceAtLeast(0)
        val squareCrop = Bitmap.createBitmap(avatarBmp, cropX, cropY, minEdge, minEdge)
        val targetDiameter = (innerRadius * 2f).roundToInt().coerceAtLeast(2)
        val scaledSquare = Bitmap.createScaledBitmap(squareCrop, targetDiameter, targetDiameter, true)

        val save = canvas.save()
        val circlePath = Path().apply {
            addCircle(centerX, centerY, innerRadius, Path.Direction.CW)
        }
        canvas.clipPath(circlePath)
        val dest = RectF(
            centerX - innerRadius,
            centerY - innerRadius,
            centerX + innerRadius,
            centerY + innerRadius
        )
        canvas.drawBitmap(
            scaledSquare,
            null,
            dest,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        canvas.restoreToCount(save)
        if (scaledSquare != squareCrop && scaledSquare != avatarBmp) scaledSquare.recycle()
        if (squareCrop != avatarBmp) squareCrop.recycle()
        avatarBmp.recycle()
    } else {
        // Dark #16161C inner circle inside the Instagram Sunset gradient ring
        val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFF16161C.toInt()
        }
        canvas.drawCircle(centerX, centerY, innerRadius, fallbackPaint)

        // Crisp white 4-point sparkles star Path centered at (centerX, centerY)
        val starRadius = innerRadius * 0.55f
        val starPath = Path().apply {
            moveTo(centerX, centerY - starRadius)
            quadTo(centerX, centerY, centerX + starRadius, centerY)
            quadTo(centerX, centerY, centerX, centerY + starRadius)
            quadTo(centerX, centerY, centerX - starRadius, centerY)
            quadTo(centerX, centerY, centerX, centerY - starRadius)
            close()
        }
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }
        canvas.drawPath(starPath, starPaint)
    }
}

fun drawCommentBubbleIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
    val path = android.graphics.Path()
    val oval = android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius)
    // Start at bottom-right ~55 degrees, sweep 300 degrees around the circle
    path.arcTo(oval, 55f, 300f)
    // Draw the bottom-right speech tail tip and connect back
    path.lineTo(cx + radius * 1.02f, cy + radius * 1.02f)
    path.close()
    canvas.drawPath(path, paint)
}

private fun drawInstaActionIconsRow(
    canvas: Canvas,
    centerY: Float,
    heartColorArgb: Int,
    showWatermarkOnRight: Boolean
) {
    // 1. Filled Heart icon at x = 104f
    val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = heartColorArgb
    }
    val heartSize = 42f
    val hx = 86f
    val hy = centerY - heartSize / 2f
    val s = heartSize / 24f
    val heartPath = Path().apply {
        moveTo(hx + 12f * s, hy + 21.35f * s)
        lineTo(hx + 10.55f * s, hy + 20.03f * s)
        cubicTo(hx + 5.4f * s, hy + 15.36f * s, hx + 2f * s, hy + 12.28f * s, hx + 2f * s, hy + 8.5f * s)
        cubicTo(hx + 2f * s, hy + 5.42f * s, hx + 4.42f * s, hy + 3f * s, hx + 7.5f * s, hy + 3f * s)
        cubicTo(hx + 9.24f * s, hy + 3f * s, hx + 10.91f * s, hy + 3.81f * s, hx + 12f * s, hy + 5.09f * s)
        cubicTo(hx + 13.09f * s, hy + 3.81f * s, hx + 14.76f * s, hy + 3f * s, hx + 16.5f * s, hy + 3f * s)
        cubicTo(hx + 19.58f * s, hy + 3f * s, hx + 22f * s, hy + 5.42f * s, hx + 22f * s, hy + 8.5f * s)
        cubicTo(hx + 22f * s, hy + 12.28f * s, hx + 18.6f * s, hy + 15.36f * s, hx + 13.45f * s, hy + 20.04f * s)
        close()
    }
    canvas.drawPath(heartPath, heartPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = android.graphics.Color.WHITE
    }

    // 2. True Instagram speech-bubble comment outline at x = 182f
    drawCommentBubbleIcon(canvas, cx = 182f, cy = centerY, radius = 17f, paint = strokePaint)

    // 3. Share paper plane outline at x = 256f
    val sx = 240f
    val sy = centerY - 17f
    val planePath = Path().apply {
        moveTo(sx + 2f, sy + 6f)
        lineTo(sx + 34f, sy + 6f)
        lineTo(sx + 18f, sy + 34f)
        lineTo(sx + 13f, sy + 19f)
        close()
    }
    canvas.drawPath(planePath, strokePaint)

    if (showWatermarkOnRight) {
        val wmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF737373.toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("InstaGallery", 994f, centerY + 8f, wmPaint)
    } else {
        // 4. Right-aligned Bookmark outline
        val bx = 958f
        val by = centerY - 18f
        val bookmarkPath = Path().apply {
            moveTo(bx, by)
            lineTo(bx + 28f, by)
            lineTo(bx + 28f, by + 36f)
            lineTo(bx + 14f, by + 26f)
            lineTo(bx, by + 36f)
            close()
        }
        canvas.drawPath(bookmarkPath, strokePaint)
    }
}

/**
 * Saves the generated 1080p InstaCard bitmap into Pictures/InstaGallery_Cards.
 */
suspend fun saveInstaCardToGallery(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
    try {
        val filename = "InstaCard_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/InstaGallery_Cards")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, values) ?: return@withContext null
        resolver.openOutputStream(itemUri)?.use { outStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val completeValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(itemUri, completeValues, null, null)
        }
        itemUri
    } catch (_: Exception) {
        null
    }
}

/**
 * Saves and opens the native Android share sheet for the generated 1080p InstaCard bitmap.
 */
suspend fun shareInstaCardBitmap(context: Context, bitmap: Bitmap) {
    val uri = saveInstaCardToGallery(context, bitmap)
    if (uri != null) {
        withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share InstaCard"))
        }
    } else {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Could not export InstaCard", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Interactive ModalBottomSheet for previewing, customizing, saving, and sharing 1080p InstaCards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstaCardStudioSheet(
    media: MediaEntity,
    primaryAuthorName: String,
    authorAvatarPath: String?,
    heartColor: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    remember(context) {
        com.example.data.local.OnboardingPreferences.init(context)
        true
    }
    val defaultTemplateName = com.example.data.local.OnboardingPreferences.defaultInstaCardTemplateFlow.value
    val initialTemplate = remember(defaultTemplateName) {
        InstaCardTemplate.entries.firstOrNull { it.name == defaultTemplateName }
            ?: InstaCardTemplate.OLED_DARK
    }

    var selectedTemplate by remember(initialTemplate) { mutableStateOf(initialTemplate) }
    var showWatermark by remember { mutableStateOf(true) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    LaunchedEffect(media.mediaId, media.customCaption, selectedTemplate, showWatermark, heartColor) {
        isRendering = true
        val bmp = generateInstaCardBitmap(
            context = context,
            media = media,
            primaryAuthorName = primaryAuthorName,
            authorAvatarPath = authorAvatarPath,
            template = selectedTemplate,
            heartColor = heartColor,
            showWatermark = showWatermark
        )
        renderedBitmap = bmp
        isRendering = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121216)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("instacard_studio_sheet"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "InstaCard Snapshot Studio",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "1080p high-clarity social card export",
                        fontSize = 12.sp,
                        color = Color(0xFF8E8E93)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E24))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "1080P HD",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0095F6)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live 1080p Bitmap Preview Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF09090C))
                    .border(1.dp, Color(0xFF26262E), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = renderedBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "InstaCard Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                }

                if (isRendering) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Template Selector Row: 3 sleek squircle pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InstaCardTemplate.entries.forEach { template ->
                    val isSelected = selectedTemplate == template
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color(0xFF1E1E22),
                        label = "template_pill_bg"
                    )
                    val pillText by animateColorAsState(
                        targetValue = if (isSelected) Color.Black else Color.White,
                        label = "template_pill_text"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(pillBg)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.White else Color(0xFF2C2C34),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedTemplate = template }
                            .testTag("template_pill_${template.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = template.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = pillText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact Watermark Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18181E))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Include InstaGallery badge",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE4E4E7)
                )
                Switch(
                    checked = showWatermark,
                    onCheckedChange = { showWatermark = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color(0xFF8E8E93),
                        uncheckedTrackColor = Color(0xFF26262E)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Action Row (2 Buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val bmp = renderedBitmap ?: return@Button
                        if (isExporting) return@Button
                        isExporting = true
                        scope.launch {
                            val savedUri = saveInstaCardToGallery(context, bmp)
                            isExporting = false
                            if (savedUri != null) {
                                Toast.makeText(
                                    context,
                                    "Saved to Pictures/InstaGallery_Cards",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to save InstaCard",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = renderedBitmap != null && !isExporting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E22),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_instacard_button")
                ) {
                    Text(
                        text = "Save to Gallery",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = {
                        val bmp = renderedBitmap ?: return@Button
                        if (isExporting) return@Button
                        isExporting = true
                        scope.launch {
                            shareInstaCardBitmap(context, bmp)
                            isExporting = false
                            onDismiss()
                        }
                    },
                    enabled = renderedBitmap != null && !isExporting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_instacard_button")
                ) {
                    Text(
                        text = "Share InstaCard ↗",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
