package com.example.drawthrough.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object DrawThroughIcons {

    val Photo: ImageVector by lazy {
        ImageVector.Builder(
            name = "Photo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 4f)
                horizontalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 16.5f)
                lineTo(8.5f, 11.5f)
                lineTo(13f, 16f)
                lineTo(16f, 13f)
                lineTo(20.5f, 17.5f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(15.5f, 8.5f)
                moveToRelative(-1.3f, 0f)
                arcToRelative(1.3f, 1.3f, 0f, true, true, 2.6f, 0f)
                arcToRelative(1.3f, 1.3f, 0f, true, true, -2.6f, 0f)
            }
        }.build()
    }

    val Sun: ImageVector by lazy {
        ImageVector.Builder(
            name = "Sun",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 8f)
                arcToRelative(4f, 4f, 0f, true, true, 0f, 8f)
                arcToRelative(4f, 4f, 0f, true, true, 0f, -8f)
                moveTo(12f, 2.5f); verticalLineTo(5f)
                moveTo(12f, 19f); verticalLineTo(21.5f)
                moveTo(2.5f, 12f); horizontalLineTo(5f)
                moveTo(19f, 12f); horizontalLineTo(21.5f)
                moveTo(5.3f, 5.3f); lineTo(7.1f, 7.1f)
                moveTo(16.9f, 16.9f); lineTo(18.7f, 18.7f)
                moveTo(18.7f, 5.3f); lineTo(16.9f, 7.1f)
                moveTo(7.1f, 16.9f); lineTo(5.3f, 18.7f)
            }
        }.build()
    }

    val Outline: ImageVector by lazy {
        ImageVector.Builder(
            name = "Outline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Sketch frame + stylus stroke matching Concept A
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(11f, 5f)
                horizontalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineTo(17f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineTo(13f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(18.5f, 3.8f)
                arcToRelative(1.9f, 1.9f, 0f, false, true, 2.7f, 2.7f)
                lineTo(12.2f, 15.5f)
                lineTo(8.5f, 16.5f)
                lineTo(9.5f, 12.8f)
                close()
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 11f)
                horizontalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(19f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(13f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(8f, 11f)
                verticalLineTo(7.5f)
                arcToRelative(4f, 4f, 0f, false, true, 8f, 0f)
                verticalLineTo(11f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 15.5f)
                moveToRelative(-1.2f, 0f)
                arcToRelative(1.2f, 1.2f, 0f, true, true, 2.4f, 0f)
                arcToRelative(1.2f, 1.2f, 0f, true, true, -2.4f, 0f)
            }
        }.build()
    }

    val Grid: ImageVector by lazy {
        ImageVector.Builder(
            name = "Grid",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(20f)
                horizontalLineTo(4f)
                close()
                moveTo(9.33f, 4f); verticalLineTo(20f)
                moveTo(14.67f, 4f); verticalLineTo(20f)
                moveTo(4f, 9.33f); horizontalLineTo(20f)
                moveTo(4f, 14.67f); horizontalLineTo(20f)
            }
        }.build()
    }

    val Reset: ImageVector by lazy {
        ImageVector.Builder(
            name = "Reset",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.5f, 12f)
                arcToRelative(7.5f, 7.5f, 0f, true, false, 2.2f, -5.3f)
                lineTo(4.5f, 8.9f)
                moveTo(4.5f, 4.5f)
                verticalLineTo(8.9f)
                horizontalLineTo(8.9f)
            }
        }.build()
    }

    val Opacity: ImageVector by lazy {
        ImageVector.Builder(
            name = "Opacity",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Water drop with half-fill representing translucency
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.2f)
                lineTo(6.6f, 10.6f)
                arcToRelative(6.5f, 6.5f, 0f, true, false, 10.8f, 0f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(5.8f, 14.2f)
                horizontalLineTo(18.2f)
                arcToRelative(6.2f, 6.2f, 0f, false, true, -12.4f, 0f)
                close()
            }
        }.build()
    }

    val Torch: ImageVector by lazy {
        ImageVector.Builder(
            name = "Torch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(13.2f, 2.8f)
                lineTo(5.5f, 13.2f)
                horizontalLineTo(11.6f)
                lineTo(10.8f, 21.2f)
                lineTo(18.5f, 10.8f)
                horizontalLineTo(12.4f)
                close()
            }
        }.build()
    }

    val Camera: ImageVector by lazy {
        ImageVector.Builder(
            name = "Camera",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 7.5f)
                horizontalLineTo(7.2f)
                lineTo(8.8f, 5f)
                horizontalLineTo(15.2f)
                lineTo(16.8f, 7.5f)
                horizontalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(17.5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(4f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(9.5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(12f, 10.2f)
                arcToRelative(3.3f, 3.3f, 0f, true, true, 0f, 6.6f)
                arcToRelative(3.3f, 3.3f, 0f, true, true, 0f, -6.6f)
            }
        }.build()
    }
}
