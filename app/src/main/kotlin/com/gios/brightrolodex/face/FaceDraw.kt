package com.gios.brightrolodex.face

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.gios.brightrolodex.ui.theme.LightThemeTokens

/**
 * Drawing a [Face] in Compose — list rows, cards, and the editor, which all want the same
 * marks at wildly different sizes.
 *
 * Everything goes through one mapping: `screen = origin + facePoint * k`. A card passes the
 * origin and scale that fit the face to its box; the editor passes a pan offset and a scale
 * multiplied by its zoom. That is the whole reason zooming in the editor needs no separate
 * renderer and cannot drift out of agreement with what a card shows.
 */

/**
 * Compose `Path` objects for a part, built once and kept.
 *
 * Main thread only, and a plain HashMap on purpose — this is read while drawing, which happens
 * on the UI thread. `FaceRaster` builds its own `android.graphics.Path` objects rather than
 * sharing this cache, because it runs on an IO dispatcher and a shared mutable map between the
 * two would be a data race that only shows up as a corrupted contact photo.
 */
private val pathCache = HashMap<String, List<Path>>()

private fun pathsFor(part: Part): List<Path> = pathCache.getOrPut(part.id) {
    part.paths.map { sub ->
        Path().apply {
            if (sub.filled) fillType = PathFillType.NonZero
            sub.cmds.forEach { cmd ->
                when (cmd) {
                    is PathCmd.MoveTo -> moveTo(cmd.x, cmd.y)
                    is PathCmd.LineTo -> lineTo(cmd.x, cmd.y)
                    is PathCmd.QuadTo -> quadraticBezierTo(cmd.x1, cmd.y1, cmd.x, cmd.y)
                    is PathCmd.CubicTo ->
                        cubicTo(cmd.x1, cmd.y1, cmd.x2, cmd.y2, cmd.x, cmd.y)
                    is PathCmd.Oval -> addOval(
                        Rect(
                            left = cmd.cx - cmd.rx,
                            top = cmd.cy - cmd.ry,
                            right = cmd.cx + cmd.rx,
                            bottom = cmd.cy + cmd.ry,
                        ),
                    )
                    PathCmd.Close -> close()
                }
            }
        }
    }
}

/**
 * Draws [face] with face-space point (0,0) landing at [origin] and one face unit measuring [k]
 * pixels.
 *
 * [minStrokePx] is not cosmetic. At a 24px row avatar, `k` is 0.024 and a 16-unit line comes
 * out at 0.38px — which the rasteriser renders as a barely-there grey, and grey is the one
 * thing a monochrome panel cannot show. Clamping the *drawn* width (never the stored one)
 * keeps a face legible as a thumbnail.
 */
fun DrawScope.drawFace(
    face: Face,
    origin: Offset,
    k: Float,
    color: Color,
    minStrokePx: Float = 1.2f,
) {
    withTransform({
        translate(origin.x, origin.y)
        scale(k, k, Offset.Zero)
    }) {
        face.parts.forEach { ref ->
            val part = Parts.byId(ref.partId) ?: return@forEach
            val pivot = Offset(part.pivotX, part.pivotY)
            withTransform({
                translate(ref.dx, ref.dy)
                if (ref.rot != 0f) rotate(ref.rot, pivot)
                val sx = ref.scale * if (ref.mirrored) -1f else 1f
                if (sx != 1f || ref.scale != 1f) scale(sx, ref.scale, pivot)
            }) {
                pathsFor(part).forEachIndexed { index, path ->
                    val sub = part.paths[index]
                    if (sub.filled) {
                        drawPath(path, color, style = Fill)
                    } else {
                        // Divided by k because the transform already scales the stroke, and
                        // the width wanted here is "sub.width face units" — then floored so a
                        // thumbnail keeps a visible line.
                        val w = maxOf(sub.width, minStrokePx / k)
                        drawPath(
                            path,
                            color,
                            style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
        }

        // The pencil layer, always on top of the parts — a detail added by hand is a
        // correction to what the parts got wrong, so it has to win.
        face.strokes.forEach { stroke ->
            if (stroke.pointCount < 2) return@forEach
            val path = Path().apply {
                moveTo(stroke.points[0], stroke.points[1])
                for (i in 2 until stroke.points.size step 2) {
                    lineTo(stroke.points[i], stroke.points[i + 1])
                }
            }
            drawPath(
                path,
                color,
                style = Stroke(
                    width = maxOf(stroke.width, minStrokePx / k),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/** Fits a face into [size], centred, and draws it. */
fun DrawScope.drawFaceFitted(face: Face, size: Size, color: Color, minStrokePx: Float = 1.2f) {
    val side = minOf(size.width, size.height)
    val k = side / FACE_SIZE
    drawFace(
        face = face,
        origin = Offset((size.width - side) / 2f, (size.height - side) / 2f),
        k = k,
        color = color,
        minStrokePx = minStrokePx,
    )
}

/**
 * A face at any size — a row avatar, a card, a preview.
 *
 * An empty face draws nothing at all rather than a placeholder silhouette. A dashed outline
 * where a face should be reads as a loading state; nothing reads as "not drawn yet", which is
 * what it is. The list rows put the initials there instead.
 */
@Composable
fun FaceView(
    face: Face,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val resolved = color ?: LightThemeTokens.colors.content
    Canvas(modifier = modifier) {
        if (face.isBlank) return@Canvas
        drawFaceFitted(face, size, resolved)
    }
}
