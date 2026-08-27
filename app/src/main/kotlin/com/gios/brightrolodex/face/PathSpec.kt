package com.gios.brightrolodex.face

/**
 * A very small path language, so a face part is a string in [Parts] rather than a hundred
 * lines of drawing code.
 *
 * There is deliberately no dependency on `android.graphics.Path` or on Compose's `Path` here.
 * The same parts have to be drawn three ways — live in the editor, live on a card, and
 * rasterised offscreen for the contact photo — and a parsed command list is the one shape all
 * three can walk. It also means the whole parts table is testable on the JVM: a typo in a
 * curve is a parse failure in a unit test rather than a blank face on the phone.
 *
 * Commands, all absolute, whitespace or comma separated:
 *
 *  - `M x y` — move to
 *  - `L x y` — line to
 *  - `C x1 y1 x2 y2 x y` — cubic
 *  - `Q x1 y1 x y` — quadratic
 *  - `E cx cy rx ry` — a whole ellipse; SVG arcs are not worth the parser and every rounded
 *    thing in a face is an ellipse
 *  - `Z` — close
 *
 * Coordinates are in face space: [FACE_SIZE] square, origin top-left. See [Face].
 */
sealed interface PathCmd {
    data class MoveTo(val x: Float, val y: Float) : PathCmd
    data class LineTo(val x: Float, val y: Float) : PathCmd
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
    ) : PathCmd

    data class QuadTo(val x1: Float, val y1: Float, val x: Float, val y: Float) : PathCmd
    data class Oval(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : PathCmd
    data object Close : PathCmd
}

object PathSpec {

    class SpecException(message: String) : IllegalArgumentException(message)

    fun parse(spec: String): List<PathCmd> {
        val tokens = spec.split(' ', ',', '\n', '\t').filter { it.isNotBlank() }
        val out = ArrayList<PathCmd>()
        var i = 0

        fun number(): Float {
            if (i >= tokens.size) throw SpecException("ran out of numbers in: $spec")
            val token = tokens[i++]
            return token.toFloatOrNull() ?: throw SpecException("not a number: $token in: $spec")
        }

        while (i < tokens.size) {
            val op = tokens[i++]
            when (op.uppercase()) {
                "M" -> out += PathCmd.MoveTo(number(), number())
                "L" -> out += PathCmd.LineTo(number(), number())
                "C" -> out += PathCmd.CubicTo(
                    number(), number(), number(), number(), number(), number(),
                )
                "Q" -> out += PathCmd.QuadTo(number(), number(), number(), number())
                "E" -> out += PathCmd.Oval(number(), number(), number(), number())
                "Z" -> out += PathCmd.Close
                else -> throw SpecException("unknown command '$op' in: $spec")
            }
        }
        if (out.isEmpty()) throw SpecException("empty path: $spec")
        return out
    }

    /**
     * The bounding box of a spec, used to give each part a sensible pivot without hand-writing
     * one for all sixty-eight of them.
     *
     * Control points are included rather than solved for the true curve extent. A Bézier is
     * contained by the hull of its control points, so this box is never too small — and a
     * pivot that is a few units off the visual centre is invisible, while a wrong one that
     * makes a part orbit when scaled is not.
     */
    fun bounds(cmds: List<PathCmd>): FloatArray {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        fun point(x: Float, y: Float) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        cmds.forEach { cmd ->
            when (cmd) {
                is PathCmd.MoveTo -> point(cmd.x, cmd.y)
                is PathCmd.LineTo -> point(cmd.x, cmd.y)
                is PathCmd.QuadTo -> {
                    point(cmd.x1, cmd.y1); point(cmd.x, cmd.y)
                }
                is PathCmd.CubicTo -> {
                    point(cmd.x1, cmd.y1); point(cmd.x2, cmd.y2); point(cmd.x, cmd.y)
                }
                is PathCmd.Oval -> {
                    point(cmd.cx - cmd.rx, cmd.cy - cmd.ry)
                    point(cmd.cx + cmd.rx, cmd.cy + cmd.ry)
                }
                PathCmd.Close -> Unit
            }
        }
        if (minX > maxX) return floatArrayOf(0f, 0f, 0f, 0f)
        return floatArrayOf(minX, minY, maxX, maxY)
    }
}
