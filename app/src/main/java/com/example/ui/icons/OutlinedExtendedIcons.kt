@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object OutlinedExtendedIcons {

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "OutlinedExtended.Home",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Smooth rounded-peak house arch from (3, 10.5) up to rounded apex (12, 3) down to (21, 10.5),
                // vertical walls down to (21, 20) and (3, 20) with rounded corners
                moveTo(3.0f, 10.5f)
                lineTo(10.8f, 3.65f)
                curveTo(11.5f, 3.05f, 12.5f, 3.05f, 13.2f, 3.65f)
                lineTo(21.0f, 10.5f)
                verticalLineTo(19.2f)
                curveTo(21.0f, 20.19f, 20.19f, 21.0f, 19.2f, 21.0f)
                horizontalLineTo(4.8f)
                curveTo(3.81f, 21.0f, 3.0f, 20.19f, 3.0f, 19.2f)
                close()

                // Smooth inner arched doorway from (9.2, 21) up to (9.2, 14.5) with rounded top arc to (14.8, 14.5) and down to (14.8, 21)
                moveTo(9.2f, 21.0f)
                verticalLineTo(14.5f)
                curveTo(9.2f, 12.95f, 10.45f, 11.7f, 12.0f, 11.7f)
                curveTo(13.55f, 11.7f, 14.8f, 12.95f, 14.8f, 14.5f)
                verticalLineTo(21.0f)
            }
        }.build()
    }

    val Movie: ImageVector by lazy {
        ImageVector.Builder(
            name = "OutlinedExtended.Movie",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Smooth rounded square from (3.5, 3.5) to (20.5, 20.5) with 5.5f corner radius
                moveTo(9.0f, 3.5f)
                horizontalLineTo(15.0f)
                curveTo(18.04f, 3.5f, 20.5f, 5.96f, 20.5f, 9.0f)
                verticalLineTo(15.0f)
                curveTo(20.5f, 18.04f, 18.04f, 20.5f, 15.0f, 20.5f)
                horizontalLineTo(9.0f)
                curveTo(5.96f, 20.5f, 3.5f, 18.04f, 3.5f, 15.0f)
                verticalLineTo(9.0f)
                curveTo(3.5f, 5.96f, 5.96f, 3.5f, 9.0f, 3.5f)
                close()

                // Centered rounded play triangle inside: (10.0, 8.5) -> (16.0, 12.0) -> (10.0, 15.5)
                moveTo(10.0f, 8.5f)
                lineTo(16.0f, 12.0f)
                lineTo(10.0f, 15.5f)
                close()
            }
        }.build()
    }

    val Reels: ImageVector
        get() = Movie

    val ChatBubbleOutline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OutlinedExtended.ChatBubbleOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // True circular Instagram speech bubble: M20.656 17.008a9.993 9.993 0 1 0-3.59 3.615L22 22Z
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.656f, 17.008f)
                arcTo(
                    horizontalEllipseRadius = 9.993f,
                    verticalEllipseRadius = 9.993f,
                    theta = 0f,
                    isMoreThanHalf = true,
                    isPositiveArc = false,
                    x1 = 17.066f,
                    y1 = 20.623f
                )
                lineTo(22.0f, 22.0f)
                close()
            }
        }.build()
    }

    val Send: ImageVector by lazy {
        ImageVector.Builder(
            name = "OutlinedExtended.Send",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Instagram Direct paper plane:
            // Outer wing frame from (22, 3) to (2, 10.5) to (11.5, 13.5) to (14.5, 22) back to (22, 3),
            // plus diagonal crease from (22, 3) to (11.5, 13.5)
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(22.0f, 3.0f)
                lineTo(2.0f, 10.5f)
                lineTo(11.5f, 13.5f)
                lineTo(14.5f, 22.0f)
                close()

                moveTo(22.0f, 3.0f)
                lineTo(11.5f, 13.5f)
            }
        }.build()
    }

    val FavoriteBorder: ImageVector by lazy {
        ImageVector.Builder(
            name = "OutlinedExtended.FavoriteBorder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Wide-lobed smooth bezier curves (strokeLineWidth = 2.0f)
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12.0f, 20.6f)
                curveTo(12.0f, 20.6f, 2.5f, 14.7f, 2.5f, 8.6f)
                curveTo(2.5f, 5.4f, 5.0f, 3.1f, 8.0f, 3.1f)
                curveTo(9.85f, 3.1f, 11.35f, 4.05f, 12.0f, 5.35f)
                curveTo(12.65f, 4.05f, 14.15f, 3.1f, 16.0f, 3.1f)
                curveTo(19.0f, 3.1f, 21.5f, 5.4f, 21.5f, 8.6f)
                curveTo(21.5f, 14.7f, 12.0f, 20.6f, 12.0f, 20.6f)
                close()
            }
        }.build()
    }

    val BookmarkBorder: ImageVector by lazy {
        ImageVector.Builder(
            name = "OutlinedExtended.BookmarkBorder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Clean ribbon with rounded V-notch: M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.0f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19.0f, 21.0f)
                lineTo(12.0f, 16.0f)
                lineTo(5.0f, 21.0f)
                verticalLineTo(5.0f)
                curveTo(5.0f, 3.895f, 5.895f, 3.0f, 7.0f, 3.0f)
                horizontalLineTo(17.0f)
                curveTo(18.105f, 3.0f, 19.0f, 3.895f, 19.0f, 5.0f)
                verticalLineTo(21.0f)
                close()
            }
        }.build()
    }
}

val Icons.Outlined.BookmarkBorder: ImageVector
    get() = OutlinedExtendedIcons.BookmarkBorder

val Icons.Outlined.ChatBubbleOutline: ImageVector
    get() = OutlinedExtendedIcons.ChatBubbleOutline

val Icons.Outlined.Movie: ImageVector
    get() = OutlinedExtendedIcons.Movie

val Icons.Outlined.PhotoCamera: ImageVector
    get() {
        if (_photoCamera != null) return _photoCamera!!
        _photoCamera = materialIcon(name = "Outlined.PhotoCamera") {
            materialPath {
                moveTo(14.12f, 4.0f)
                lineToRelative(1.83f, 2.0f)
                horizontalLineTo(20.0f)
                verticalLineToRelative(12.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(4.05f)
                lineToRelative(1.83f, -2.0f)
                horizontalLineToRelative(4.24f)
                moveTo(15.0f, 2.0f)
                horizontalLineTo(9.0f)
                lineTo(7.17f, 4.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-3.17f)
                lineTo(15.0f, 2.0f)
                close()
                moveTo(12.0f, 9.0f)
                curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.35f, 3.0f, -3.0f, 3.0f)
                reflectiveCurveToRelative(-3.0f, -1.35f, -3.0f, -3.0f)
                reflectiveCurveToRelative(1.35f, -3.0f, 3.0f, -3.0f)
                moveTo(12.0f, 7.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveToRelative(-2.24f, -5.0f, -5.0f, -5.0f)
                close()
            }
        }
        return _photoCamera!!
    }
private var _photoCamera: ImageVector? = null

val Icons.Outlined.Photo: ImageVector
    get() {
        if (_photo != null) return _photo!!
        _photo = materialIcon(name = "Outlined.Photo") {
            materialPath {
                moveTo(19.0f, 5.0f)
                verticalLineToRelative(14.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(14.0f)
                moveTo(19.0f, 3.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(14.14f, 11.86f)
                lineToRelative(-3.0f, 3.87f)
                lineTo(9.0f, 13.14f)
                lineTo(6.0f, 17.0f)
                horizontalLineToRelative(12.0f)
                lineToRelative(-3.86f, -5.14f)
                close()
            }
        }
        return _photo!!
    }
private var _photo: ImageVector? = null

val Icons.Outlined.VolumeOff: ImageVector
    get() {
        if (_volumeOff != null) return _volumeOff!!
        _volumeOff = materialIcon(name = "Outlined.VolumeOff") {
            materialPath {
                moveTo(4.34f, 2.93f)
                lineTo(2.93f, 4.34f)
                lineTo(7.29f, 8.7f)
                lineTo(7.0f, 9.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineToRelative(-6.59f)
                lineToRelative(4.18f, 4.18f)
                curveToRelative(-0.65f, 0.49f, -1.38f, 0.88f, -2.18f, 1.11f)
                verticalLineToRelative(2.06f)
                curveToRelative(1.34f, -0.3f, 2.57f, -0.92f, 3.61f, -1.75f)
                lineToRelative(2.05f, 2.05f)
                lineToRelative(1.41f, -1.41f)
                lineTo(4.34f, 2.93f)
                close()
                moveTo(19.0f, 12.0f)
                curveToRelative(0.0f, 0.82f, -0.15f, 1.61f, -0.41f, 2.34f)
                lineToRelative(1.53f, 1.53f)
                curveToRelative(0.56f, -1.17f, 0.88f, -2.48f, 0.88f, -3.87f)
                curveToRelative(0.0f, -4.28f, -2.99f, -7.86f, -7.0f, -8.77f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5.0f, 3.54f, 5.0f, 6.71f)
                close()
                moveTo(12.0f, 4.0f)
                lineToRelative(-1.88f, 1.88f)
                lineTo(12.0f, 7.76f)
                close()
                moveTo(16.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(1.79f)
                lineToRelative(2.48f, 2.48f)
                curveToRelative(0.01f, -0.08f, 0.02f, -0.16f, 0.02f, -0.24f)
                close()
            }
        }
        return _volumeOff!!
    }
private var _volumeOff: ImageVector? = null

val Icons.Outlined.VolumeUp: ImageVector
    get() {
        if (_volumeUp != null) return _volumeUp!!
        _volumeUp = materialIcon(name = "Outlined.VolumeUp") {
            materialPath {
                moveTo(3.0f, 9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(5.0f, 5.0f)
                verticalLineTo(4.0f)
                lineTo(7.0f, 9.0f)
                horizontalLineTo(3.0f)
                close()
                moveTo(10.0f, 8.83f)
                verticalLineToRelative(6.34f)
                lineTo(7.83f, 13.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.83f)
                lineTo(10.0f, 8.83f)
                close()
                moveTo(16.5f, 12.0f)
                curveToRelative(0.0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(14.0f, 3.23f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5.0f, 3.54f, 5.0f, 6.71f)
                reflectiveCurveToRelative(-2.11f, 5.85f, -5.0f, 6.71f)
                verticalLineToRelative(2.06f)
                curveToRelative(4.01f, -0.91f, 7.0f, -4.49f, 7.0f, -8.77f)
                curveToRelative(0.0f, -4.28f, -2.99f, -7.86f, -7.0f, -8.77f)
                close()
            }
        }
        return _volumeUp!!
    }
private var _volumeUp: ImageVector? = null

val Icons.Outlined.MusicNote: ImageVector
    get() {
        if (_musicNote != null) return _musicNote!!
        _musicNote = materialIcon(name = "Outlined.MusicNote") {
            materialPath {
                moveTo(12.0f, 3.0f)
                verticalLineToRelative(10.55f)
                curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2.0f, -0.55f)
                curveToRelative(-2.21f, 0.0f, -4.0f, 1.79f, -4.0f, 4.0f)
                reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(-6.0f)
                close()
            }
        }
        return _musicNote!!
    }
private var _musicNote: ImageVector? = null

val Icons.Outlined.Tune: ImageVector
    get() {
        if (_tune != null) return _tune!!
        _tune = materialIcon(name = "Outlined.Tune") {
            materialPath {
                moveTo(3.0f, 17.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(3.0f)
                close()
                moveTo(3.0f, 5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(10.0f)
                verticalLineTo(5.0f)
                horizontalLineTo(3.0f)
                close()
                moveTo(13.0f, 21.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                close()
                moveTo(7.0f, 9.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(9.0f)
                horizontalLineTo(7.0f)
                close()
                moveTo(21.0f, 13.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(11.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(10.0f)
                close()
                moveTo(15.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(6.0f)
                close()
            }
        }
        return _tune!!
    }
private var _tune: ImageVector? = null

val Icons.Outlined.Link: ImageVector
    get() {
        if (_link != null) return _link!!
        _link = materialIcon(name = "Outlined.Link") {
            materialPath {
                moveTo(17.0f, 7.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.35f, 3.0f, -3.0f, 3.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                curveToRelative(2.76f, 0.0f, 5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveToRelative(-2.24f, -5.0f, -5.0f, -5.0f)
                close()
                moveTo(11.0f, 15.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-1.65f, 0.0f, -3.0f, -1.35f, -3.0f, -3.0f)
                reflectiveCurveToRelative(1.35f, -3.0f, 3.0f, -3.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(8.0f, 11.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(8.0f)
                close()
            }
        }
        return _link!!
    }
private var _link: ImageVector? = null

val Icons.Outlined.Verified: ImageVector
    get() {
        if (_verified != null) return _verified!!
        _verified = materialIcon(name = "Outlined.Verified") {
            materialPath {
                moveTo(23.0f, 12.0f)
                lineToRelative(-2.44f, -2.79f)
                lineToRelative(0.34f, -3.69f)
                lineToRelative(-3.61f, -0.82f)
                lineToRelative(-1.89f, -3.2f)
                lineTo(12.0f, 2.96f)
                lineTo(8.6f, 1.5f)
                lineTo(6.71f, 4.69f)
                lineTo(3.1f, 5.5f)
                lineToRelative(0.34f, 3.7f)
                lineTo(1.0f, 12.0f)
                lineToRelative(2.44f, 2.79f)
                lineToRelative(-0.34f, 3.7f)
                lineToRelative(3.61f, 0.82f)
                lineToRelative(1.89f, 3.2f)
                lineTo(12.0f, 21.04f)
                lineToRelative(3.4f, 1.46f)
                lineToRelative(1.89f, -3.2f)
                lineToRelative(3.61f, -0.82f)
                lineToRelative(-0.34f, -3.69f)
                lineTo(23.0f, 12.0f)
                close()
                moveTo(10.09f, 16.72f)
                lineToRelative(-3.8f, -3.81f)
                lineToRelative(1.48f, -1.48f)
                lineToRelative(2.32f, 2.33f)
                lineToRelative(5.85f, -5.87f)
                lineToRelative(1.48f, 1.48f)
                lineToRelative(-7.33f, 7.35f)
                close()
            }
        }
        return _verified!!
    }
private var _verified: ImageVector? = null

val Icons.Outlined.AutoFixHigh: ImageVector
    get() {
        if (_autoFixHigh != null) return _autoFixHigh!!
        _autoFixHigh = materialIcon(name = "Outlined.AutoFixHigh") {
            materialPath {
                moveTo(20.0f, 7.0f)
                lineToRelative(0.94f, -2.06f)
                lineTo(23.0f, 4.0f)
                lineToRelative(-2.06f, -0.94f)
                lineTo(20.0f, 1.0f)
                lineToRelative(-0.94f, 2.06f)
                lineTo(17.0f, 4.0f)
                lineToRelative(2.06f, 0.94f)
                close()
                moveTo(8.5f, 7.0f)
                lineToRelative(0.94f, -2.06f)
                lineTo(11.5f, 4.0f)
                lineTo(9.44f, 3.06f)
                lineTo(8.5f, 1.0f)
                lineTo(7.56f, 3.06f)
                lineTo(5.5f, 4.0f)
                lineToRelative(2.06f, 0.94f)
                close()
                moveTo(20.0f, 12.5f)
                lineToRelative(-0.94f, 2.06f)
                lineTo(17.0f, 15.5f)
                lineToRelative(2.06f, 0.94f)
                lineTo(20.0f, 18.5f)
                lineToRelative(0.94f, -2.06f)
                lineTo(23.0f, 15.5f)
                lineToRelative(-2.06f, -0.94f)
                close()
                moveTo(17.71f, 9.12f)
                lineToRelative(-2.83f, -2.83f)
                curveTo(14.68f, 6.1f, 14.43f, 6.0f, 14.17f, 6.0f)
                curveToRelative(-0.26f, 0.0f, -0.51f, 0.1f, -0.71f, 0.29f)
                lineTo(2.29f, 17.46f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.02f, 0.0f, 1.41f)
                lineToRelative(2.83f, 2.83f)
                curveTo(5.32f, 21.9f, 5.57f, 22.0f, 5.83f, 22.0f)
                reflectiveCurveToRelative(0.51f, -0.1f, 0.71f, -0.29f)
                lineTo(17.71f, 10.54f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.42f)
                close()
                moveTo(5.83f, 19.59f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(11.59f, 11.0f)
                lineTo(13.0f, 12.41f)
                lineTo(5.83f, 19.59f)
                close()
                moveTo(14.41f, 11.0f)
                lineTo(13.0f, 9.59f)
                lineToRelative(1.17f, -1.17f)
                lineToRelative(1.41f, 1.41f)
                lineTo(14.41f, 11.0f)
                close()
            }
        }
        return _autoFixHigh!!
    }
private var _autoFixHigh: ImageVector? = null

val Icons.Outlined.Group: ImageVector
    get() {
        if (_group != null) return _group!!
        _group = materialIcon(name = "Outlined.Group") {
            materialPath {
                moveTo(9.0f, 13.75f)
                curveToRelative(-2.34f, 0.0f, -7.0f, 1.17f, -7.0f, 3.5f)
                verticalLineTo(19.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-1.75f)
                curveToRelative(0.0f, -2.33f, -4.66f, -3.5f, -7.0f, -3.5f)
                close()
                moveTo(4.34f, 17.0f)
                curveToRelative(0.84f, -0.58f, 2.87f, -1.25f, 4.66f, -1.25f)
                reflectiveCurveToRelative(3.82f, 0.67f, 4.66f, 1.25f)
                horizontalLineTo(4.34f)
                close()
                moveTo(9.0f, 12.0f)
                curveToRelative(1.93f, 0.0f, 3.5f, -1.57f, 3.5f, -3.5f)
                reflectiveCurveTo(10.93f, 5.0f, 9.0f, 5.0f)
                reflectiveCurveTo(5.5f, 6.57f, 5.5f, 8.5f)
                reflectiveCurveTo(7.07f, 12.0f, 9.0f, 12.0f)
                close()
                moveTo(9.0f, 7.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveTo(9.83f, 10.0f, 9.0f, 10.0f)
                reflectiveCurveToRelative(-1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(8.17f, 7.0f, 9.0f, 7.0f)
                close()
                moveTo(16.04f, 13.81f)
                curveToRelative(1.16f, 0.84f, 1.96f, 1.96f, 1.96f, 3.44f)
                verticalLineTo(19.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-1.75f)
                curveToRelative(0.0f, -2.02f, -3.5f, -3.17f, -5.96f, -3.44f)
                close()
                moveTo(15.0f, 12.0f)
                curveToRelative(1.93f, 0.0f, 3.5f, -1.57f, 3.5f, -3.5f)
                reflectiveCurveTo(16.93f, 5.0f, 15.0f, 5.0f)
                curveToRelative(-0.54f, 0.0f, -1.04f, 0.13f, -1.5f, 0.35f)
                curveToRelative(0.63f, 0.89f, 1.0f, 1.98f, 1.0f, 3.15f)
                reflectiveCurveToRelative(-0.37f, 2.26f, -1.0f, 3.15f)
                curveToRelative(0.46f, 0.22f, 0.96f, 0.35f, 1.5f, 0.35f)
                close()
            }
        }
        return _group!!
    }
private var _group: ImageVector? = null

val Icons.Outlined.GroupAdd: ImageVector
    get() {
        if (_groupAdd != null) return _groupAdd!!
        _groupAdd = materialIcon(name = "Outlined.GroupAdd") {
            materialPath {
                moveTo(22.0f, 9.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(9.0f)
                horizontalLineTo(22.0f)
                close()
                moveTo(8.0f, 12.0f)
                curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
                reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
                reflectiveCurveTo(4.0f, 5.79f, 4.0f, 8.0f)
                reflectiveCurveTo(5.79f, 12.0f, 8.0f, 12.0f)
                close()
                moveTo(8.0f, 13.0f)
                curveToRelative(-2.67f, 0.0f, -8.0f, 1.34f, -8.0f, 4.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(16.0f)
                verticalLineToRelative(-3.0f)
                curveTo(16.0f, 14.34f, 10.67f, 13.0f, 8.0f, 13.0f)
                close()
            }
        }
        return _groupAdd!!
    }
private var _groupAdd: ImageVector? = null

val Icons.Outlined.FolderShared: ImageVector
    get() {
        if (_folderShared != null) return _folderShared!!
        _folderShared = materialIcon(name = "Outlined.FolderShared") {
            materialPath {
                moveTo(20.0f, 6.0f)
                horizontalLineToRelative(-8.0f)
                lineToRelative(-2.0f, -2.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(2.0f, 18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(20.0f, 18.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(5.17f)
                lineToRelative(2.0f, 2.0f)
                horizontalLineTo(20.0f)
                verticalLineToRelative(10.0f)
                close()
            }
        }
        return _folderShared!!
    }
private var _folderShared: ImageVector? = null

val Icons.Outlined.Shield: ImageVector
    get() {
        if (_shield != null) return _shield!!
        _shield = materialIcon(name = "Outlined.Shield") {
            materialPath {
                moveTo(12.0f, 2.0f)
                lineTo(4.0f, 5.0f)
                verticalLineToRelative(6.09f)
                curveToRelative(0.0f, 5.05f, 3.41f, 9.76f, 8.0f, 10.91f)
                curveToRelative(4.59f, -1.15f, 8.0f, -5.86f, 8.0f, -10.91f)
                verticalLineTo(5.0f)
                lineTo(12.0f, 2.0f)
                close()
                moveTo(18.0f, 11.09f)
                curveToRelative(0.0f, 4.0f, -2.55f, 7.7f, -6.0f, 8.83f)
                curveToRelative(-3.45f, -1.13f, -6.0f, -4.82f, -6.0f, -8.83f)
                verticalLineToRelative(-4.7f)
                lineToRelative(6.0f, -2.25f)
                lineToRelative(6.0f, 2.25f)
                verticalLineToRelative(4.7f)
                close()
            }
        }
        return _shield!!
    }
private var _shield: ImageVector? = null

val Icons.Outlined.DeleteOutline: ImageVector
    get() {
        if (_deleteOutline != null) return _deleteOutline!!
        _deleteOutline = materialIcon(name = "Outlined.DeleteOutline") {
            materialPath {
                moveTo(6.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(12.0f)
                close()
                moveTo(8.0f, 9.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(10.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(15.5f, 4.0f)
                lineToRelative(-1.0f, -1.0f)
                horizontalLineToRelative(-5.0f)
                lineToRelative(-1.0f, 1.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineTo(4.0f)
                close()
            }
        }
        return _deleteOutline!!
    }
private var _deleteOutline: ImageVector? = null

val Icons.Outlined.AddAPhoto: ImageVector
    get() {
        if (_addAPhoto != null) return _addAPhoto!!
        _addAPhoto = materialIcon(name = "Outlined.AddAPhoto") {
            materialPath {
                moveTo(21.0f, 6.0f)
                horizontalLineToRelative(-3.17f)
                lineTo(16.0f, 4.0f)
                horizontalLineToRelative(-6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(5.12f)
                lineToRelative(1.83f, 2.0f)
                horizontalLineTo(21.0f)
                verticalLineToRelative(12.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-9.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(9.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(8.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(8.0f, 14.0f)
                curveToRelative(0.0f, 2.76f, 2.24f, 5.0f, 5.0f, 5.0f)
                reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveToRelative(-2.24f, -5.0f, -5.0f, -5.0f)
                reflectiveCurveToRelative(-5.0f, 2.24f, -5.0f, 5.0f)
                close()
                moveTo(13.0f, 11.0f)
                curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.35f, 3.0f, -3.0f, 3.0f)
                reflectiveCurveToRelative(-3.0f, -1.35f, -3.0f, -3.0f)
                reflectiveCurveToRelative(1.35f, -3.0f, 3.0f, -3.0f)
                close()
                moveTo(5.0f, 6.0f)
                horizontalLineToRelative(3.0f)
                verticalLineTo(4.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(1.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(3.0f)
                horizontalLineTo(0.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.0f)
                verticalLineToRelative(3.0f)
                horizontalLineToRelative(2.0f)
                close()
            }
        }
        return _addAPhoto!!
    }
private var _addAPhoto: ImageVector? = null
