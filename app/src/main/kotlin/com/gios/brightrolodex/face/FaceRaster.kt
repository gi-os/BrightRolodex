package com.gios.brightrolodex.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.io.ByteArrayOutputStream

/**
 * A face flattened to a PNG, for the things that cannot take vectors: the contact photo, and
 * the provider that serves faces to BrightChat.
 *
 * **This output is always derived and never stored as the source of truth.** The face JSON on
 * the person record is what the editor reads, so a face stays fully re-editable forever — every
 * part still individually swappable, every pencil stroke still individually undoable — no matter
 * how many times it has been rasterised into the address book. Nothing in the app reads a face
 * *back* out of a PNG, and nothing should ever be added that does.
 *
 * Black on white rather than white on black, which is the reverse of the app. A contact photo is
 * shown by LightOS Contacts, the stock dialer and anything else that reads the address book,
 * most of which draw it on a light card and none of which know it came from here. White marks on
 * transparent would be an invisible photo in half of those places.
 */
object FaceRaster {

    /**
     * 256px. Big enough for the largest place a contact photo appears on this phone, small
     * enough that the byte array goes into a ContentProvider transaction without argument —
     * a full-size photo row is a known way to hit the binder limit and fail the whole batch.
     */
    const val CONTACT_PHOTO_PX = 256

    fun render(
        face: Face,
        px: Int,
        inkColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)

        val k = px / FACE_SIZE
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        canvas.save()
        canvas.scale(k, k)

        face.parts.forEach { ref ->
            val part = Parts.byId(ref.partId) ?: return@forEach
            canvas.save()
            canvas.translate(ref.dx, ref.dy)
            if (ref.rot != 0f) canvas.rotate(ref.rot, part.pivotX, part.pivotY)
            val sx = ref.scale * if (ref.mirrored) -1f else 1f
            canvas.scale(sx, ref.scale, part.pivotX, part.pivotY)

            part.paths.forEach { sub ->
                val path = toPath(sub)
                if (sub.filled) {
                    paint.style = Paint.Style.FILL
                } else {
                    paint.style = Paint.Style.STROKE
                    // A floor in face units, so a 48px render still has a visible line.
                    paint.strokeWidth = maxOf(sub.width, MIN_STROKE_PX / k)
                }
                canvas.drawPath(path, paint)
            }
            canvas.restore()
        }

        paint.style = Paint.Style.STROKE
        face.strokes.forEach { stroke ->
            if (stroke.pointCount < 2) return@forEach
            paint.strokeWidth = maxOf(stroke.width, MIN_STROKE_PX / k)
            val path = Path().apply {
                moveTo(stroke.points[0], stroke.points[1])
                for (i in 2 until stroke.points.size step 2) {
                    lineTo(stroke.points[i], stroke.points[i + 1])
                }
            }
            canvas.drawPath(path, paint)
        }

        canvas.restore()
        return bitmap
    }

    fun png(face: Face, px: Int = CONTACT_PHOTO_PX): ByteArray {
        val bitmap = render(face, px)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private const val MIN_STROKE_PX = 1.4f

    /**
     * Built fresh every call rather than cached.
     *
     * This runs once per save, off the main thread, while `FaceDraw`'s cache is read on the main
     * thread every frame. Sharing one cache of mutable `Path` objects between them would be a
     * data race whose only symptom is an occasionally mangled contact photo — the kind of bug
     * that is never reproducible.
     */
    private fun toPath(sub: SubPath): Path = Path().apply {
        sub.cmds.forEach { cmd ->
            when (cmd) {
                is PathCmd.MoveTo -> moveTo(cmd.x, cmd.y)
                is PathCmd.LineTo -> lineTo(cmd.x, cmd.y)
                is PathCmd.QuadTo -> quadTo(cmd.x1, cmd.y1, cmd.x, cmd.y)
                is PathCmd.CubicTo -> cubicTo(cmd.x1, cmd.y1, cmd.x2, cmd.y2, cmd.x, cmd.y)
                is PathCmd.Oval -> addOval(
                    RectF(
                        cmd.cx - cmd.rx,
                        cmd.cy - cmd.ry,
                        cmd.cx + cmd.rx,
                        cmd.cy + cmd.ry,
                    ),
                    Path.Direction.CW,
                )
                PathCmd.Close -> close()
            }
        }
    }
}
