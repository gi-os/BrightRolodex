package com.gios.brightrolodex.face

import org.json.JSONArray
import org.json.JSONObject

/** Face space is a square, so a face renders identically into a list row and a contact photo. */
const val FACE_SIZE = 1000f

/**
 * One placed part.
 *
 * [dx] and [dy] are an *offset* from where the part is authored in [Parts], not an absolute
 * position, and [scale] and [rot] are applied about the part's own pivot. So a face with no
 * adjustments at all is `PartRef(id)` and looks the way it was drawn — which matters, because
 * most faces will never be nudged and the ones that are should not have to store a full
 * transform to say "a bit lower".
 */
data class PartRef(
    val partId: String,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val rot: Float = 0f,
    val mirrored: Boolean = false,
)

/**
 * One freehand pencil stroke: a polyline in face space.
 *
 * [width] is in face units too, and that is the whole reason zooming in to draw detail works.
 * The pencil is a fixed number of *screen* pixels wide, so at 4x zoom the stroke it lays down
 * is a quarter as wide in face space — a genuinely finer line, not the same fat line drawn
 * bigger. A stroke stored in screen units could not do that.
 */
data class Stroke(
    val width: Float,
    /** Flat x, y, x, y… so a stroke is one allocation rather than one per point. */
    val points: List<Float>,
) {
    val pointCount: Int get() = points.size / 2
}

data class Face(
    val parts: List<PartRef> = emptyList(),
    val strokes: List<Stroke> = emptyList(),
) {
    val isBlank: Boolean get() = parts.isEmpty() && strokes.isEmpty()

    /**
     * Whether anyone has actually drawn this person, as opposed to it holding the bare head
     * outline every new person starts with.
     *
     * The distinction earns its keep in three places: a list row shows initials until a face is
     * drawn, because an identical empty oval on forty rows identifies nobody; the contact photo
     * is only written once there is a face, because an empty oval as a contact photo is worse
     * than no photo; and the editor can tell "untouched" from "deliberately left as an outline".
     */
    val isDrawn: Boolean
        get() = strokes.isNotEmpty() ||
            parts.any { Parts.byId(it.partId)?.category != PartCategory.Head }

    /** The part currently occupying [category], if any. */
    fun partIn(category: PartCategory): PartRef? =
        parts.firstOrNull { Parts.byId(it.partId)?.category == category }

    /**
     * Replaces whatever is in [ref]'s category with [ref], keeping the list in draw order.
     *
     * One part per category is enforced here rather than by the model being a map, because
     * draw order is a property of the category list and a map would leave it implicit.
     */
    fun withPart(ref: PartRef): Face {
        val category = Parts.byId(ref.partId)?.category ?: return this
        val kept = parts.filter { Parts.byId(it.partId)?.category != category }
        return copy(parts = (kept + ref).sortedBy { Parts.byId(it.partId)?.category?.ordinal ?: 0 })
    }

    fun withoutCategory(category: PartCategory): Face =
        copy(parts = parts.filter { Parts.byId(it.partId)?.category != category })

    companion object {
        /**
         * What a new person starts as: a head outline and nothing else.
         *
         * The blank head is a real part rather than a drawn-on guide, so the first decision in
         * the editor is which head — and so a face that is never edited still reads as a face
         * rather than as a missing image.
         */
        fun blank(): Face = Face(parts = listOf(PartRef(Parts.DEFAULT_HEAD)))
    }
}

/**
 * Faces are stored as JSON inside the person record, not as PNGs.
 *
 * About 300 bytes each. The reasons it is worth keeping the vectors rather than a bitmap: a
 * face renders crisply at 24px in a list row and 900px in the editor from the same data; it
 * stays editable a year later, including undoing a part chosen in the first minute; and there
 * is no anti-aliased grey to lose on a monochrome panel. The PNG is a derived artifact — see
 * `FaceRaster` — and is regenerated, never stored as the source of truth.
 */
object FaceJson {

    fun encode(face: Face): String = JSONObject().apply {
        put("v", 1)
        put(
            "parts",
            JSONArray().apply {
                face.parts.forEach { ref ->
                    put(
                        JSONObject().apply {
                            put("id", ref.partId)
                            // Only what differs from the default, so the common case — a part
                            // placed where it was drawn — costs one key instead of six.
                            if (ref.dx != 0f) put("dx", ref.dx.toDouble())
                            if (ref.dy != 0f) put("dy", ref.dy.toDouble())
                            if (ref.scale != 1f) put("s", ref.scale.toDouble())
                            if (ref.rot != 0f) put("r", ref.rot.toDouble())
                            if (ref.mirrored) put("m", true)
                        },
                    )
                }
            },
        )
        put(
            "strokes",
            JSONArray().apply {
                face.strokes.forEach { stroke ->
                    put(
                        JSONObject().apply {
                            put("w", stroke.width.toDouble())
                            put(
                                "p",
                                JSONArray().apply {
                                    // Rounded to whole face units. Face space is 1000 wide, so
                                    // a unit is finer than the panel can show, and the decimals
                                    // were most of the file size.
                                    stroke.points.forEach { put(Math.round(it)) }
                                },
                            )
                        },
                    )
                }
            },
        )
    }.toString()

    fun decode(json: String?): Face {
        if (json.isNullOrBlank()) return Face()
        return runCatching {
            val root = JSONObject(json)
            val parts = ArrayList<PartRef>()
            val partArray = root.optJSONArray("parts") ?: JSONArray()
            for (i in 0 until partArray.length()) {
                val o = partArray.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                // A part id that no longer exists in the table is dropped rather than kept as
                // a dangling reference: the alternative is a face that renders as a hole and a
                // list that cannot say why. Renaming a part id is therefore a data migration.
                if (Parts.byId(id) == null) continue
                parts += PartRef(
                    partId = id,
                    dx = o.optDouble("dx", 0.0).toFloat(),
                    dy = o.optDouble("dy", 0.0).toFloat(),
                    scale = o.optDouble("s", 1.0).toFloat(),
                    rot = o.optDouble("r", 0.0).toFloat(),
                    mirrored = o.optBoolean("m", false),
                )
            }
            val strokes = ArrayList<Stroke>()
            val strokeArray = root.optJSONArray("strokes") ?: JSONArray()
            for (i in 0 until strokeArray.length()) {
                val o = strokeArray.optJSONObject(i) ?: continue
                val pointArray = o.optJSONArray("p") ?: continue
                val points = ArrayList<Float>(pointArray.length())
                for (j in 0 until pointArray.length()) {
                    points += pointArray.optDouble(j, 0.0).toFloat()
                }
                // An odd number of coordinates is not half a point, it is a corrupt stroke.
                if (points.size < 4 || points.size % 2 != 0) continue
                strokes += Stroke(
                    width = o.optDouble("w", 16.0).toFloat(),
                    points = points,
                )
            }
            Face(parts = parts, strokes = strokes)
        }.getOrDefault(Face())
    }
}
