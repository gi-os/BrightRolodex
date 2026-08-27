package com.gios.brightrolodex.data

/**
 * Finding a person by anything you can remember about them.
 *
 * This is the feature, not a convenience. The situation the app exists for is somebody saying
 * hello on the stairs and you having no idea who they are — and what you have to search with in
 * that moment is not their name. It is "4B", or "beagle", or "the super". So every field is
 * searchable, including each fact separately, and the drawn face is what confirms the hit.
 *
 * Pure and Android-free, so the ranking rules are covered by unit tests rather than by tapping.
 */
object Search {

    /**
     * Where a match landed. The order of this enum is the ranking, best first.
     *
     * A name match outranks a fact match because someone typing "ray" almost always means the
     * person called Ray, not the three people whose facts mention X-rays. Beyond that the
     * ordering is by how deliberately the field was filled in: `howIKnow` and `where` are
     * written to be recalled later, facts accumulate.
     */
    enum class Hit {
        NameStart,
        NameWord,
        Name,
        HowIKnow,
        Where,
        Tag,
        Fact,
        Number,
    }

    data class Result(val person: Person, val hit: Hit, val snippet: String)

    /**
     * @param includeHidden archived people. False everywhere except the archive screen: hiding
     *   someone has to take them out of search too, or the deck is the only place it took
     *   effect and the feature does nothing where it matters.
     */
    fun query(
        people: List<Person>,
        raw: String,
        includeHidden: Boolean = false,
    ): List<Result> {
        val pool = if (includeHidden) people else people.filter { !it.hidden }
        val q = raw.trim().lowercase()
        if (q.isEmpty()) {
            return pool
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .map { Result(it, Hit.Name, it.howIKnow) }
        }

        val digits = Phone.digits(q)
        val results = ArrayList<Result>()

        pool.forEach { person ->
            val name = person.name.lowercase()
            val hit: Hit?
            var snippet = person.howIKnow

            hit = when {
                name.startsWith(q) -> Hit.NameStart
                name.split(' ', '-').any { it.startsWith(q) } -> Hit.NameWord
                name.contains(q) -> Hit.Name
                person.howIKnow.lowercase().contains(q) -> {
                    snippet = person.howIKnow
                    Hit.HowIKnow
                }
                person.where.lowercase().contains(q) -> {
                    snippet = person.where
                    Hit.Where
                }
                person.tags.any { it.lowercase().contains(q) } -> {
                    snippet = person.tags.first { it.lowercase().contains(q) }
                    Hit.Tag
                }
                else -> {
                    val fact = person.facts.firstOrNull { it.text.lowercase().contains(q) }
                    if (fact != null) {
                        snippet = fact.text
                        Hit.Fact
                    } else {
                        // Digits only, and at least three of them: a one-digit query matches
                        // half the address book and tells you nothing.
                        val numberMatch = digits.length >= 3 &&
                            person.phones.any { Phone.digits(it).contains(digits) }
                        if (numberMatch) {
                            snippet = person.phones.firstOrNull()?.let { Phone.pretty(it) } ?: ""
                            Hit.Number
                        } else {
                            null
                        }
                    }
                }
            }

            if (hit != null) results += Result(person, hit, snippet)
        }

        return results.sortedWith(
            compareBy<Result> { it.hit.ordinal }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.person.name },
        )
    }

    /** Every tag in use, most-used first, for the deck's scope chip. */
    fun tags(people: List<Person>): List<String> =
        people.filter { !it.hidden }
            .flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
}
