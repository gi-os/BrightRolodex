package com.gios.brightrolodex

import com.gios.brightrolodex.contacts.ContactRow
import com.gios.brightrolodex.data.Fact
import com.gios.brightrolodex.data.Person
import com.gios.brightrolodex.data.PersonJson
import com.gios.brightrolodex.data.Phone
import com.gios.brightrolodex.data.RolodexStore
import com.gios.brightrolodex.data.Search
import com.gios.brightrolodex.data.candidatesOf
import com.gios.brightrolodex.data.deckOrder
import com.gios.brightrolodex.data.isSameHuman
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun person(
        name: String,
        how: String = "",
        where: String = "",
        phone: String? = null,
        facts: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        hidden: Boolean = false,
    ) = Person(
        id = name.lowercase(),
        name = name,
        howIKnow = how,
        where = where,
        phones = listOfNotNull(phone),
        facts = facts.mapIndexed { i, text -> Fact(name + i, text, 0L) },
        tags = tags,
        hidden = hidden,
    )

    private val marco = person(
        "Marco",
        how = "across the hall",
        where = "4B",
        phone = "(212) 555-0147",
        facts = listOf("Beagle named Pete", "Sister in Astoria"),
        tags = listOf("neighbours"),
    )
    private val angela = person("Angela", how = "bodega night shift", tags = listOf("bodega"))
    private val ray = person("Ray Ortiz", how = "the super", phone = "+1 212 555 0199")
    private val thirdGrade = person("Kevin", how = "third grade", hidden = true)
    private val everyone = listOf(marco, angela, ray, thirdGrade)

    /* ------------------------------------------------------------------ phone */

    @Test
    fun `the same number written two ways is one number`() {
        assertTrue(Phone.sameNumber("(212) 555-0147", "+1 212 555 0147"))
        assertTrue(Phone.sameNumber("2125550147", "1-212-555-0147"))
        assertFalse(Phone.sameNumber("2125550147", "2125550148"))
        // Two empties are not a match. Otherwise every person with no number is the same person
        // as every other, which would collapse the whole rolodex into one card.
        assertFalse(Phone.sameNumber("", ""))
    }

    @Test
    fun `formatting leaves anything that is not a plain US number alone`() {
        assertEquals("(212) 555-0147", Phone.pretty("2125550147"))
        assertEquals("(212) 555-0147", Phone.pretty("+1 212 555 0147"))
        assertEquals("+44 20 7946 0958", Phone.pretty("+44 20 7946 0958"))
        assertEquals("611", Phone.pretty("611"))
    }

    @Test
    fun `dialable numbers are E164 where that is knowable`() {
        assertEquals("+12125550147", Phone.dialable("(212) 555-0147"))
        assertEquals("+12125550147", Phone.dialable("1-212-555-0147"))
        assertEquals("+442079460958", Phone.dialable("+44 20 7946 0958"))
        assertEquals("611", Phone.dialable("611"))
    }

    /* ------------------------------------------------------------------ search */

    @Test
    fun `a fact finds the person`() {
        val results = Search.query(everyone, "beagle")
        assertEquals(1, results.size)
        assertEquals("Marco", results[0].person.name)
        assertEquals(Search.Hit.Fact, results[0].hit)
        // The snippet has to be the fact that matched, or the row gives no clue why it is there.
        assertEquals("Beagle named Pete", results[0].snippet)
    }

    @Test
    fun `where and how-I-know are searchable`() {
        assertEquals("Marco", Search.query(everyone, "4b").single().person.name)
        assertEquals("Ray Ortiz", Search.query(everyone, "super").single().person.name)
        assertEquals("Angela", Search.query(everyone, "bodega").first().person.name)
    }

    @Test
    fun `a name beats a fact`() {
        val people = listOf(
            person("Pete", how = "gym"),
            person("Marco", facts = listOf("Beagle named Pete")),
        )
        val results = Search.query(people, "pete")
        assertEquals("Pete", results[0].person.name)
        assertEquals(2, results.size)
    }

    @Test
    fun `archived people are out of search as well as out of the deck`() {
        assertTrue(Search.query(everyone, "third grade").isEmpty())
        assertEquals(1, Search.query(everyone, "third grade", includeHidden = true).size)
        assertFalse(deckOrder(everyone, null).any { it.name == "Kevin" })
    }

    @Test
    fun `a number is searchable but not by one digit`() {
        assertEquals("Marco", Search.query(everyone, "5550147").single().person.name)
        // "1" appears in both numbers; matching on it would return the address book.
        assertTrue(Search.query(everyone, "1").isEmpty())
    }

    @Test
    fun `an empty query is the whole deck in alphabetical order`() {
        val results = Search.query(everyone, "  ")
        assertEquals(listOf("Angela", "Marco", "Ray Ortiz"), results.map { it.person.name })
    }

    @Test
    fun `tags are counted most-used first`() {
        val people = listOf(
            person("A", tags = listOf("work")),
            person("B", tags = listOf("work")),
            person("C", tags = listOf("gym")),
        )
        assertEquals(listOf("work", "gym"), Search.tags(people))
    }

    /* ------------------------------------------------------------------ deck */

    @Test
    fun `the deck can be scoped to a tag, case insensitively`() {
        assertEquals(1, deckOrder(everyone, "Neighbours").size)
        assertEquals("Marco", deckOrder(everyone, "neighbours")[0].name)
        assertEquals(3, deckOrder(everyone, null).size)
    }

    /* ------------------------------------------------------------------ contacts */

    @Test
    fun `a contact already in the rolodex is not offered again`() {
        val rows = listOf(
            ContactRow("lk-marco", 1L, "Marco", listOf("+1 212 555 0147")),
            ContactRow("lk-new", 2L, "Someone New", listOf("+1 646 555 0001")),
        )
        val candidates = candidatesOf(rows, everyone, emptySet())
        assertEquals(listOf("Someone New"), candidates.map { it.name })
    }

    @Test
    fun `matching on the number catches somebody typed in before the permission was granted`() {
        val typedIn = person("M.", phone = "2125550147")
        val row = ContactRow("lk-marco", 1L, "Marco Rossi", listOf("+1 (212) 555-0147"))
        assertTrue(isSameHuman(typedIn, row))
        assertTrue(candidatesOf(listOf(row), listOf(typedIn), emptySet()).isEmpty())
    }

    @Test
    fun `a dismissed contact stays out of the picker`() {
        val row = ContactRow("lk-kevin", 9L, "Kevin From Third Grade", listOf("5555555555"))
        assertEquals(1, candidatesOf(listOf(row), emptyList(), emptySet()).size)
        assertTrue(candidatesOf(listOf(row), emptyList(), setOf("lk-kevin")).isEmpty())
    }

    /* ------------------------------------------------------------------ the store */

    @Test
    fun `a person survives a round trip through the file`() {
        val store = RolodexStore(folder.newFolder())
        store.write(everyone)
        val read = store.read()
        assertEquals(4, read.size)
        val back = read.first { it.name == "Marco" }
        assertEquals("across the hall", back.howIKnow)
        assertEquals("4B", back.where)
        assertEquals(2, back.facts.size)
        assertEquals(listOf("neighbours"), back.tags)
        assertTrue(read.first { it.name == "Kevin" }.hidden)
    }

    @Test
    fun `a corrupt file falls back to the backup rather than to an empty rolodex`() {
        val dir = folder.newFolder()
        val store = RolodexStore(dir)
        store.write(everyone)
        // A second write is what creates the backup: the previous good file becomes .bak.
        store.write(everyone.filter { it.name != "Kevin" })
        java.io.File(dir, RolodexStore.PEOPLE).writeText("{ this is not json")

        val recovered = store.read()
        // The backup is the *previous* generation, so it still has everybody. Losing one edit
        // beats losing the whole rolodex, which is the trade this is here to make.
        assertEquals(4, recovered.size)
    }

    @Test
    fun `an empty rolodex reads back as empty and not as corrupt`() {
        val store = RolodexStore(folder.newFolder())
        store.write(emptyList())
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `dismissals are kept separately from people`() {
        val store = RolodexStore(folder.newFolder())
        store.writeDismissed(setOf("lk-a", "lk-b"))
        assertEquals(setOf("lk-a", "lk-b"), store.readDismissed())
        store.writeDismissed(emptySet())
        assertTrue(store.readDismissed().isEmpty())
    }

    @Test
    fun `initials take the first and last word`() {
        assertEquals("GV", person("Guy with the van").initials)
        assertEquals("M", person("Marco").initials)
        assertEquals("RO", person("Ray Ortiz").initials)
        assertEquals("?", person("").initials)
    }

    @Test
    fun `json leaves out everything that is at its default`() {
        val encoded = PersonJson.encode(person("Plain")).toString()
        assertFalse(encoded.contains("\"how\""))
        assertFalse(encoded.contains("\"hidden\""))
        assertFalse(encoded.contains("\"phones\""))
        assertTrue(encoded.contains("\"name\""))
    }
}
