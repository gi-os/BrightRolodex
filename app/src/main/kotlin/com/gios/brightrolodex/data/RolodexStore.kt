package com.gios.brightrolodex.data

import java.io.File

/**
 * The whole rolodex in one JSON file.
 *
 * No Room and no SQLite, which is a deliberate choice rather than laziness. The data is a few
 * hundred people at the very most, every screen wants all of them in memory anyway (the deck
 * flips through them and search reads every field of every one), and keeping it a plain list
 * means the model, the search and the file format are all testable on the JVM without an
 * instrumented test or a codegen plugin in the build.
 *
 * What that costs: every mutation rewrites the file. At a few hundred people that is tens of
 * kilobytes, which is nothing, and it buys the property below.
 *
 * **Writes are atomic and keep one generation.** This file is irreplaceable in a way most app
 * state is not: a drawn face cannot be re-derived from anything, and neither can "has a beagle
 * named Pete". A half-written file after a kill would lose the lot, so a write goes to a temp
 * file, the previous good file becomes `.bak`, and only then does the temp file take the real
 * name. [read] falls back to `.bak` if the main file will not parse, rather than starting empty
 * — an empty rolodex looks exactly like a working app and is the worst possible failure here.
 */
class RolodexStore(private val dir: File) {

    private val file = File(dir, PEOPLE)
    private val backup = File(dir, "$PEOPLE.bak")
    private val temp = File(dir, "$PEOPLE.tmp")
    private val dismissedFile = File(dir, DISMISSED)

    fun read(): List<Person> {
        val fromMain = file.takeIf { it.exists() }?.let { parse(it) }
        if (fromMain != null) return fromMain
        val fromBackup = backup.takeIf { it.exists() }?.let { parse(it) }
        return fromBackup ?: emptyList()
    }

    /**
     * Returns null for "this file is not usable", which is a different answer from "this file
     * holds an empty rolodex".
     *
     * Hence `decodeAllStrict`, which throws: `decodeAll` turns a parse failure into an empty
     * list, and an empty list is indistinguishable from a new phone — so the fallback to `.bak`
     * below could never fire, and the next write would overwrite the backup with the emptiness.
     */
    private fun parse(f: File): List<Person>? = runCatching {
        val text = f.readText()
        if (text.isBlank()) null else PersonJson.decodeAllStrict(text)
    }.getOrNull()

    fun write(people: List<Person>) {
        runCatching {
            dir.mkdirs()
            temp.writeText(PersonJson.encodeAll(people))
            if (file.exists()) {
                backup.delete()
                file.renameTo(backup)
            }
            temp.renameTo(file)
        }
    }

    /**
     * Address-book rows the user has said are not rolodex people.
     *
     * Keyed by contact lookup key. Stored as a flat newline-separated file rather than inside
     * the people JSON because it is a list about people who are explicitly *not* in the
     * rolodex, and putting them in the same file invites code that treats them as members.
     */
    fun readDismissed(): Set<String> = runCatching {
        dismissedFile.takeIf { it.exists() }
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    }.getOrDefault(emptySet())

    fun writeDismissed(keys: Set<String>) {
        runCatching {
            dir.mkdirs()
            dismissedFile.writeText(keys.joinToString("\n"))
        }
    }

    companion object {
        const val PEOPLE = "people.json"
        const val DISMISSED = "dismissed.txt"
    }
}
