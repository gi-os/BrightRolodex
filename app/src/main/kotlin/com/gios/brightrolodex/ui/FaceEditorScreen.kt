package com.gios.brightrolodex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gios.brightrolodex.face.FACE_SIZE
import com.gios.brightrolodex.face.Face
import com.gios.brightrolodex.face.PartCategory
import com.gios.brightrolodex.face.PartRef
import com.gios.brightrolodex.face.Parts
import com.gios.brightrolodex.face.Stroke
import com.gios.brightrolodex.face.drawFace
import com.gios.brightrolodex.face.drawFaceFitted
import com.gios.brightrolodex.hw.WheelSteps
import com.gios.brightrolodex.ui.theme.LightText
import com.gios.brightrolodex.ui.theme.LightTextVariant
import com.gios.brightrolodex.ui.theme.LightThemeTokens
import com.gios.brightrolodex.ui.theme.gridUnitsAsDp
import com.gios.brightrolodex.ui.theme.lightClickable
import kotlin.math.abs

private enum class Tool { Parts, Pencil }

/**
 * The three pencil widths, in face units, at zoom 1.
 *
 * The stroke actually stored is this divided by the zoom, so the same button draws a 24-unit
 * line zoomed out and a 6-unit line at 4x. That is the entire reason zoom is here, and the
 * reason widths are face-space numbers rather than screen-space ones.
 */
private val BRUSHES = floatArrayOf(24f, 14f, 7f)

private const val MAX_ZOOM = 6f
private const val UNDO_DEPTH = 40

/**
 * Drawing somebody's face.
 *
 * Two tools sharing one finger, so the wheel means something different in each — which is the
 * only way a 3.9" screen affords both:
 *
 *  - **PARTS.** The wheel cycles variants in the selected category, a one-finger drag moves the
 *    placed part, and a two-finger pinch scales and rotates it.
 *  - **PENCIL.** The wheel zooms, one finger draws, two fingers pan.
 *
 * Nothing here is destructive. [UNDO_DEPTH] snapshots cover parts and strokes alike, and the
 * face is vectors all the way down — so opening this screen a year later gives back every part
 * still swappable and every stroke still removable. No step in the app ever turns a face into a
 * flattened image that could only be redrawn from scratch.
 */
@Composable
fun FaceEditorScreen(
    initial: Face,
    onSave: (Face) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LightThemeTokens.colors

    var face by remember { mutableStateOf(initial) }
    var undo by remember { mutableStateOf(listOf<Face>()) }
    var tool by remember { mutableStateOf(Tool.Parts) }
    var category by remember { mutableStateOf(PartCategory.Hair) }
    var brush by remember { mutableStateOf(1) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    /** The stroke being drawn, kept out of [face] so an in-progress line is never undoable. */
    var live by remember { mutableStateOf<List<Float>?>(null) }

    fun push() {
        undo = (undo + face).takeLast(UNDO_DEPTH)
    }

    fun mutate(next: Face) {
        push()
        face = next
    }

    /** Clamps [pan] so the face cannot be scrolled off the canvas at any zoom. */
    fun clampPan(candidate: Offset, z: Float): Offset {
        val visible = FACE_SIZE / z
        val max = (FACE_SIZE - visible).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(0f, max), candidate.y.coerceIn(0f, max))
    }

    /**
     * Zooms about the middle of what is on screen rather than about the face's origin, so
     * turning the wheel magnifies what you are looking at instead of sliding it away.
     */
    fun setZoom(next: Float) {
        val z = next.coerceIn(1f, MAX_ZOOM)
        if (z == zoom) return
        val halfBefore = FACE_SIZE / zoom / 2f
        val halfAfter = FACE_SIZE / z / 2f
        val centre = pan + Offset(halfBefore, halfBefore)
        zoom = z
        pan = clampPan(centre - Offset(halfAfter, halfAfter), z)
    }

    val variants = remember(category) { Parts.inCategory(category) }
    val selected = face.partIn(category)
    val selectedIndex = variants.indexOfFirst { it.id == selected?.partId }

    // One wheel collector for both tools. Two would each receive every notch.
    WheelSteps { delta ->
        if (tool == Tool.Parts) {
            if (variants.isNotEmpty()) {
                // From -1 when nothing is placed, so the first notch selects the first variant
                // rather than the second.
                val next = (selectedIndex + delta).coerceIn(-1, variants.size - 1)
                mutate(
                    if (next < 0) {
                        face.withoutCategory(category)
                    } else {
                        face.withPart(PartRef(variants[next].id))
                    },
                )
            }
        } else {
            setZoom(zoom * if (delta > 0) 1.35f else 1f / 1.35f)
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {

        LightTopBar(
            title = if (tool == Tool.Parts) {
                val counter = if (selectedIndex >= 0) {
                    " " + (selectedIndex + 1) + "/" + variants.size
                } else {
                    ""
                }
                category.label + counter
            } else {
                ("PENCIL " + zoomLabel(zoom)).trim()
            },
            left = "CANCEL",
            right = "SAVE",
            onLeft = onCancel,
            onRight = { onSave(face) },
        )

        Row(Modifier.weight(1f).fillMaxWidth()) {

            // The category rail, only in PARTS. In PENCIL it would be nine dead labels crowding
            // the thing you are trying to draw on.
            if (tool == Tool.Parts) {
                Column(
                    modifier = Modifier
                        .width(4f.gridUnitsAsDp())
                        .fillMaxHeight()
                        .padding(start = lightInset()),
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    PartCategory.entries.forEach { entry ->
                        LightText(
                            text = entry.label,
                            variant = LightTextVariant.Micro,
                            color = if (entry == category) colors.content else colors.contentFaint,
                            modifier = Modifier.lightClickable { category = entry },
                        )
                    }
                }
            }

            FaceCanvas(
                face = face,
                live = live,
                brushWidth = BRUSHES[brush],
                zoom = zoom,
                pan = pan,
                tool = tool,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onPanZoom = { zoomChange, panChange, k ->
                    setZoom(zoom * zoomChange)
                    // The finger moves the face under the window, so the window moves the other
                    // way — and by the pan measured in face units, not pixels.
                    pan = clampPan(pan - panChange / k, zoom)
                },
                onMovePart = { delta, k ->
                    val ref = face.partIn(category)
                    if (ref != null) {
                        // No snapshot per frame of a drag; one per gesture, taken by
                        // onGestureStart. Forty snapshots of one drag would bury everything
                        // before it.
                        face = face.withPart(
                            ref.copy(dx = ref.dx + delta.x / k, dy = ref.dy + delta.y / k),
                        )
                    }
                },
                onTransformPart = { zoomChange, rotation ->
                    val ref = face.partIn(category)
                    if (ref != null) {
                        face = face.withPart(
                            ref.copy(
                                scale = (ref.scale * zoomChange).coerceIn(0.4f, 2.5f),
                                rot = ref.rot + rotation,
                            ),
                        )
                    }
                },
                onGestureStart = { push() },
                onStrokeStart = { point -> live = listOf(point.x, point.y) },
                onStrokePoint = { point ->
                    val current = live
                    if (current != null) {
                        // Points closer than a few face units add file size and nothing else.
                        val lastX = current[current.size - 2]
                        val lastY = current[current.size - 1]
                        if (abs(point.x - lastX) + abs(point.y - lastY) > 3f) {
                            live = current + listOf(point.x, point.y)
                        }
                    }
                },
                onStrokeEnd = {
                    val points = live
                    live = null
                    if (points != null && points.size >= 4) {
                        face = face.copy(
                            strokes = face.strokes + Stroke(
                                width = BRUSHES[brush] / zoom,
                                points = points,
                            ),
                        )
                    }
                },
                onStrokeCancel = { live = null },
            )
        }

        // The strip: variants in PARTS, brush widths in PENCIL.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.4f.gridUnitsAsDp())
                .padding(horizontal = lightInset()),
            contentAlignment = Alignment.Center,
        ) {
            if (tool == Tool.Parts) {
                VariantStrip(
                    category = category,
                    selectedId = selected?.partId,
                    onPick = { partId ->
                        mutate(
                            if (partId == null) {
                                face.withoutCategory(category)
                            } else {
                                face.withPart(PartRef(partId))
                            },
                        )
                    },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BRUSHES.forEachIndexed { index, width ->
                            Box(
                                modifier = Modifier
                                    .size(2.6f.gridUnitsAsDp())
                                    .lightClickable { brush = index },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.size(2.2f.gridUnitsAsDp())) {
                                    val r = width / FACE_SIZE * size.minDimension * 3.4f
                                    drawCircle(
                                        color = if (index == brush) {
                                            colors.content
                                        } else {
                                            colors.contentFaint
                                        },
                                        radius = r.coerceAtLeast(2f),
                                    )
                                }
                            }
                        }
                    }
                    LightText(
                        text = if (zoom > 1.01f) {
                            "TWO FINGERS PAN"
                        } else {
                            "WHEEL ZOOMS IN"
                        },
                        variant = LightTextVariant.Micro,
                        lighten = true,
                        align = TextAlign.End,
                    )
                }
            }
        }

        LightBottomBar(
            items = listOf(
                BarItem("PARTS") { tool = Tool.Parts },
                BarItem("PENCIL") { tool = Tool.Pencil },
                BarItem("UNDO", enabled = undo.isNotEmpty()) {
                    undo.lastOrNull()?.let { previous ->
                        face = previous
                        undo = undo.dropLast(1)
                    }
                },
            ),
        )
    }
}

private fun zoomLabel(zoom: Float): String {
    val rounded = Math.round(zoom * 10f) / 10f
    return if (rounded <= 1.01f) "" else rounded.toString() + "x"
}

/**
 * The canvas, and the only place the two tools' gestures are told apart.
 *
 * One `awaitEachGesture` loop rather than two pointerInput modifiers: two would both see every
 * event and both consume it. Counting pressed pointers inside one loop is what lets one finger
 * mean "draw" while two mean "pan" on the same surface.
 *
 * The face-to-screen mapping is computed here in the composable from a size captured by
 * `onSizeChanged`, **not** written from inside the draw block. Assigning state during draw is
 * the obvious way to get the numbers the renderer used, and it makes every frame schedule a
 * recomposition; this way the mapping the gesture handler reads and the one the canvas draws
 * with are the same value, computed once per layout.
 */
@Composable
private fun FaceCanvas(
    face: Face,
    live: List<Float>?,
    brushWidth: Float,
    zoom: Float,
    pan: Offset,
    tool: Tool,
    modifier: Modifier = Modifier,
    onPanZoom: (zoomChange: Float, panChange: Offset, k: Float) -> Unit,
    onMovePart: (delta: Offset, k: Float) -> Unit,
    onTransformPart: (zoomChange: Float, rotation: Float) -> Unit,
    onGestureStart: () -> Unit,
    onStrokeStart: (Offset) -> Unit,
    onStrokePoint: (Offset) -> Unit,
    onStrokeEnd: () -> Unit,
    onStrokeCancel: () -> Unit,
) {
    val colors = LightThemeTokens.colors
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val side = minOf(canvasSize.width, canvasSize.height).toFloat()
    val k = if (side > 0f) side * zoom / FACE_SIZE else 0f
    val origin = Offset(
        (canvasSize.width - side) / 2f - pan.x * k,
        (canvasSize.height - side) / 2f - pan.y * k,
    )

    fun toFace(screen: Offset): Offset =
        if (k <= 0f) Offset.Zero else Offset((screen.x - origin.x) / k, (screen.y - origin.y) / k)

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(tool, k, origin, brushWidth) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    if (k <= 0f) return@awaitEachGesture

                    var drawing = false
                    var snapshotTaken = false

                    if (tool == Tool.Pencil) {
                        drawing = true
                        onStrokeStart(toFace(first.position))
                    }

                    while (true) {
                        val event: PointerEvent = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            // A second finger landing mid-stroke abandons the stroke rather than
                            // leaving the stray mark that the start of every pinch would be.
                            if (drawing) {
                                onStrokeCancel()
                                drawing = false
                            }
                            if (!snapshotTaken && tool == Tool.Parts) {
                                onGestureStart()
                                snapshotTaken = true
                            }
                            val zoomChange = event.calculateZoom()
                            val rotation = event.calculateRotation()
                            val panChange = event.calculatePan()
                            if (tool == Tool.Pencil) {
                                onPanZoom(zoomChange, panChange, k)
                            } else {
                                onTransformPart(zoomChange, rotation)
                            }
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = pressed.first()
                            if (drawing) {
                                onStrokePoint(toFace(change.position))
                                change.consume()
                            } else if (tool == Tool.Parts) {
                                val delta = change.positionChange()
                                if (delta != Offset.Zero) {
                                    if (!snapshotTaken) {
                                        onGestureStart()
                                        snapshotTaken = true
                                    }
                                    onMovePart(delta, k)
                                    change.consume()
                                }
                            }
                        }
                    }

                    if (drawing) {
                        // The snapshot for a stroke is taken at the end, so a tap that produced
                        // no line does not consume an undo step.
                        onGestureStart()
                        onStrokeEnd()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (k <= 0f) return@Canvas

            // The edge of the card, so it stays obvious what is on the face and what is off it.
            // It matters most when zoomed, where the head's outline may be off screen entirely.
            drawRect(
                color = colors.rule,
                topLeft = origin,
                size = Size(FACE_SIZE * k, FACE_SIZE * k),
                style = DrawStroke(width = 1f),
            )

            drawFace(face = face, origin = origin, k = k, color = colors.content)

            if (live != null && live.size >= 4) {
                val path = Path().apply {
                    moveTo(origin.x + live[0] * k, origin.y + live[1] * k)
                    for (i in 2 until live.size step 2) {
                        lineTo(origin.x + live[i] * k, origin.y + live[i + 1] * k)
                    }
                }
                drawPath(
                    path = path,
                    color = colors.content,
                    // The stored width will be brushWidth/zoom face units, which at this k is
                    // brushWidth*side/FACE_SIZE pixels — a constant. The pencil is a fixed
                    // number of screen pixels wide at every zoom, which is what makes zooming
                    // in the way to draw something small.
                    style = DrawStroke(
                        width = brushWidth * k / zoom,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

/**
 * The variant strip: every part in the category, plus NONE.
 *
 * Each cell draws the part on its own with no head behind it, which reads better than it sounds
 * — a mouth alone is recognisably a mouth, while a head behind eleven cells is eleven identical
 * ovals with a small difference somewhere in the middle.
 */
@Composable
private fun VariantStrip(
    category: PartCategory,
    selectedId: String?,
    onPick: (String?) -> Unit,
) {
    val colors = LightThemeTokens.colors
    val state = rememberLazyListState()
    val variants = remember(category) { Parts.inCategory(category) }

    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No NONE for the head. A face with no head is not a style, it is a bug that would then
        // need handling everywhere else.
        if (category != PartCategory.Head) {
            item {
                Box(
                    modifier = Modifier
                        .size(3.6f.gridUnitsAsDp())
                        .background(if (selectedId == null) colors.content else Color.Transparent)
                        .lightClickable { onPick(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = "NONE",
                        variant = LightTextVariant.Micro,
                        color = if (selectedId == null) {
                            colors.background
                        } else {
                            colors.contentSecondary
                        },
                    )
                }
            }
        }
        items(variants, key = { it.id }) { part ->
            val chosen = part.id == selectedId
            Box(
                modifier = Modifier
                    .size(3.6f.gridUnitsAsDp())
                    .background(if (chosen) colors.content else Color.Transparent)
                    .lightClickable { onPick(part.id) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                    drawFaceFitted(
                        face = Face(parts = listOf(PartRef(part.id))),
                        size = size,
                        color = if (chosen) colors.background else colors.content,
                        minStrokePx = 1.6f,
                    )
                }
            }
        }
    }
}
