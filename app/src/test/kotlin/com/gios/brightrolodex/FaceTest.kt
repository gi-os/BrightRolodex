package com.gios.brightrolodex

import com.gios.brightrolodex.face.FACE_SIZE
import com.gios.brightrolodex.face.Face
import com.gios.brightrolodex.face.FaceJson
import com.gios.brightrolodex.face.PartCategory
import com.gios.brightrolodex.face.PartRef
import com.gios.brightrolodex.face.PathSpec
import com.gios.brightrolodex.face.Parts
import com.gios.brightrolodex.face.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts table and the face format.
 *
 * The first test here is the reason the path language exists as a parsed string rather than as
 * drawing code: a typo in one of sixty-eight curves is a test failure on a laptop instead of a
 * blank face discovered on the phone a week later.
 */
class FaceTest {

    @Test
    fun `every part in the library parses`() {
        Parts.all.forEach { part ->
            part.paths.forEachIndexed { index, sub ->
                val cmds = sub.cmds
                assertTrue(part.id + " path " + index + " is empty", cmds.isNotEmpty())
            }
        }
    }

    @Test
    fun `every part sits inside the face`() {
        Parts.all.forEach { part ->
            part.paths.forEach { sub ->
                val b = PathSpec.bounds(sub.cmds)
                // A margin either side, because a control point may legitimately sit slightly
                // outside the drawn curve — a cap brim, for one, reaches past the head.
                assertTrue(part.id + " starts left of the card", b[0] > -200f)
                assertTrue(part.id + " ends right of the card", b[2] < FACE_SIZE + 200f)
                assertTrue(part.id + " starts above the card", b[1] > -200f)
                assertTrue(part.id + " ends below the card", b[3] < FACE_SIZE + 200f)
            }
        }
    }

    @Test
    fun `every category has variants and every id is unique`() {
        PartCategory.entries.forEach { category ->
            assertTrue(
                category.label + " has no variants",
                Parts.inCategory(category).isNotEmpty(),
            )
        }
        val ids = Parts.all.map { it.id }
        assertEquals("duplicate part ids", ids.size, ids.toSet().size)
        assertNotNull("the default head must exist", Parts.byId(Parts.DEFAULT_HEAD))
    }

    @Test
    fun `pivot is inside the part's own bounds`() {
        Parts.all.forEach { part ->
            val xs = part.paths.map { PathSpec.bounds(it.cmds) }
            val minX = xs.minOf { it[0] }
            val maxX = xs.maxOf { it[2] }
            assertTrue(part.id + " pivot x", part.pivotX in minX..maxX)
        }
    }

    @Test
    fun `unknown commands are rejected`() {
        val failed = runCatching { PathSpec.parse("M 0 0 W 5 5") }.isFailure
        assertTrue("an unknown command should not parse", failed)
        assertTrue(runCatching { PathSpec.parse("") }.isFailure)
        assertTrue("a truncated command should not parse", runCatching { PathSpec.parse("M 0") }.isFailure)
    }

    @Test
    fun `a face survives a round trip`() {
        val face = Face(
            parts = listOf(
                PartRef(Parts.DEFAULT_HEAD),
                PartRef("eyes_dots", dx = 12f, dy = -4f, scale = 1.2f, rot = 5f, mirrored = true),
            ),
            strokes = listOf(Stroke(width = 8f, points = listOf(10f, 20f, 30f, 40f))),
        )
        val decoded = FaceJson.decode(FaceJson.encode(face))

        assertEquals(2, decoded.parts.size)
        val eyes = decoded.parts.first { it.partId == "eyes_dots" }
        assertEquals(12f, eyes.dx, 0.01f)
        assertEquals(1.2f, eyes.scale, 0.01f)
        assertTrue(eyes.mirrored)
        assertEquals(1, decoded.strokes.size)
        assertEquals(2, decoded.strokes[0].pointCount)
    }

    @Test
    fun `a part id that no longer exists is dropped rather than kept dangling`() {
        val json = """{"v":1,"parts":[{"id":"eyes_from_2019"},{"id":"eyes_dots"}],"strokes":[]}"""
        val decoded = FaceJson.decode(json)
        assertEquals(1, decoded.parts.size)
        assertEquals("eyes_dots", decoded.parts[0].partId)
    }

    @Test
    fun `a stroke with an odd number of coordinates is dropped`() {
        val json = """{"v":1,"parts":[],"strokes":[{"w":8,"p":[1,2,3]},{"w":8,"p":[1,2,3,4]}]}"""
        val decoded = FaceJson.decode(json)
        assertEquals(1, decoded.strokes.size)
    }

    @Test
    fun `garbage decodes to an empty face rather than throwing`() {
        assertTrue(FaceJson.decode("not json at all").isBlank)
        assertTrue(FaceJson.decode(null).isBlank)
        assertTrue(FaceJson.decode("").isBlank)
    }

    @Test
    fun `one part per category, and the newest wins`() {
        val face = Face.blank()
            .withPart(PartRef("eyes_dots"))
            .withPart(PartRef("eyes_circles"))
        val eyes = face.parts.filter { Parts.byId(it.partId)?.category == PartCategory.Eyes }
        assertEquals(1, eyes.size)
        assertEquals("eyes_circles", eyes[0].partId)
    }

    @Test
    fun `parts are stored in draw order so glasses land over eyes`() {
        val face = Face.blank()
            .withPart(PartRef("extra_glasses_round"))
            .withPart(PartRef("eyes_dots"))
            .withPart(PartRef("hair_bob"))
        val order = face.parts.mapNotNull { Parts.byId(it.partId)?.category?.ordinal }
        assertEquals(order.sorted(), order)
        assertEquals(PartCategory.Extra, Parts.byId(face.parts.last().partId)?.category)
    }

    @Test
    fun `a bare head is not a drawn face`() {
        assertFalse("a new person has not been drawn yet", Face.blank().isDrawn)
        assertFalse(Face.blank().isBlank)
        assertTrue(Face.blank().withPart(PartRef("nose_dot")).isDrawn)
        assertTrue(
            Face(strokes = listOf(Stroke(8f, listOf(1f, 2f, 3f, 4f)))).isDrawn,
        )
    }

    @Test
    fun `removing a category leaves the rest alone`() {
        val face = Face.blank()
            .withPart(PartRef("eyes_dots"))
            .withPart(PartRef("mouth_smile"))
            .withoutCategory(PartCategory.Eyes)
        assertNull(face.partIn(PartCategory.Eyes))
        assertNotNull(face.partIn(PartCategory.Mouth))
        assertNotNull(face.partIn(PartCategory.Head))
    }
}
