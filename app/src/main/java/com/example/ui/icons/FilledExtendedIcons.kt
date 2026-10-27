@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.material.icons.filled

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object FilledExtendedIcons {

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "FilledExtended.Home",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(3.0f, 10.8f)
                curveTo(3.0f, 10.1f, 3.35f, 9.45f, 3.9f, 9.0f)
                lineTo(10.65f, 3.45f)
                curveTo(11.43f, 2.81f, 12.57f, 2.81f, 13.35f, 3.45f)
                lineTo(20.1f, 9.0f)
                curveTo(20.65f, 9.45f, 21.0f, 10.1f, 21.0f, 10.8f)
                verticalLineTo(19.0f)
                curveTo(21.0f, 20.1f, 20.1f, 21.0f, 19.0f, 21.0f)
                horizontalLineTo(5.0f)
                curveTo(3.9f, 21.0f, 3.0f, 20.1f, 3.0f, 19.0f)
                close()

                moveTo(9.2f, 21.0f)
                verticalLineTo(14.5f)
                curveTo(9.2f, 12.95f, 10.45f, 11.7f, 12.0f, 11.7f)
                curveTo(13.55f, 11.7f, 14.8f, 12.95f, 14.8f, 14.5f)
                verticalLineTo(21.0f)
                close()
            }
        }.build()
    }

    val Movie: ImageVector by lazy {
        ImageVector.Builder(
            name = "FilledExtended.Movie",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.EvenOdd
            ) {
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

                moveTo(10.0f, 8.5f)
                lineTo(16.0f, 12.0f)
                lineTo(10.0f, 15.5f)
                close()
            }
        }.build()
    }

    val Reels: ImageVector
        get() = Movie

    val Favorite: ImageVector by lazy {
        ImageVector.Builder(
            name = "FilledExtended.Favorite",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
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

    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "FilledExtended.Bookmark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
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

val Icons.Filled.Bookmark: ImageVector
    get() = FilledExtendedIcons.Bookmark

val Icons.Filled.Movie: ImageVector
    get() = FilledExtendedIcons.Movie

val Icons.Filled.GridOn: ImageVector
    get() {
        if (_gridOn != null) return _gridOn!!
        _gridOn = materialIcon(name = "Filled.GridOn") {
            materialPath {
                moveTo(20.0f, 2.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(16.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(8.0f, 20.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(8.0f, 14.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(8.0f, 8.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(14.0f, 20.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(14.0f, 14.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(14.0f, 8.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(20.0f, 20.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(20.0f, 14.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                close()
                moveTo(20.0f, 8.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(4.0f)
                close()
            }
        }
        return _gridOn!!
    }
private var _gridOn: ImageVector? = null

val Icons.Filled.AutoAwesome: ImageVector
    get() {
        if (_autoAwesome != null) return _autoAwesome!!
        _autoAwesome = materialIcon(name = "Filled.AutoAwesome") {
            materialPath {
                moveTo(19.0f, 9.0f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23.0f, 5.0f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19.0f, 1.0f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15.0f, 5.0f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19.0f, 9.0f)
                close()
                moveTo(11.5f, 9.5f)
                lineTo(9.0f, 4.0f)
                lineTo(6.5f, 9.5f)
                lineTo(1.0f, 12.0f)
                lineToRelative(5.5f, 2.5f)
                lineTo(9.0f, 20.0f)
                lineToRelative(2.5f, -5.5f)
                lineTo(17.0f, 12.0f)
                lineToRelative(-5.5f, -2.5f)
                close()
                moveTo(19.0f, 15.0f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15.0f, 19.0f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19.0f, 23.0f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23.0f, 19.0f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19.0f, 15.0f)
                close()
            }
        }
        return _autoAwesome!!
    }
private var _autoAwesome: ImageVector? = null

val Icons.Filled.MoreHoriz: ImageVector
    get() {
        if (_moreHoriz != null) return _moreHoriz!!
        _moreHoriz = materialIcon(name = "Filled.MoreHoriz") {
            materialPath {
                moveTo(6.0f, 10.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(18.0f, 10.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(12.0f, 10.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                reflectiveCurveToRelative(0.9f, 2.0f, 2.0f, 2.0f)
                reflectiveCurveToRelative(2.0f, -0.9f, 2.0f, -2.0f)
                reflectiveCurveToRelative(-0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }
        return _moreHoriz!!
    }
private var _moreHoriz: ImageVector? = null
