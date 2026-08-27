package com.gios.brightrolodex.data

/**
 * Phone number handling, deliberately minimal.
 *
 * There is no attempt at libphonenumber here and no formatting of foreign numbers. Two jobs
 * only: make a stable key so the same person found through the address book, through a face
 * lookup from BrightChat and through a typed-in number all resolve to one row; and print a US
 * number so it reads like a phone number on a card.
 */
object Phone {

    fun digits(raw: String): String = raw.filter { it.isDigit() }

    /**
     * The key two numbers are compared on: the last ten digits.
     *
     * Ten and not the whole string, because the same person is stored as `+1 212 555 0147` in
     * one place and `(212) 555-0147` in another, and BrightChat's handles are E.164 while the
     * address book's are whatever was typed. Ten digits is the North American number, which is
     * what every number on this phone is; a shorter string (a short code, an extension) keys as
     * itself rather than being padded, so it can still match itself and nothing else.
     */
    fun key(raw: String): String {
        val d = digits(raw)
        return if (d.length > 10) d.takeLast(10) else d
    }

    fun sameNumber(a: String, b: String): Boolean {
        val ka = key(a)
        val kb = key(b)
        return ka.isNotEmpty() && ka == kb
    }

    /**
     * `(212) 555-0147`, or the raw string untouched if it is not a plain US number.
     *
     * Returning the input unchanged rather than best-effort formatting: a half-formatted
     * international number looks like a bug, and an unformatted one looks like a number.
     */
    fun pretty(raw: String): String {
        val d = digits(raw)
        val ten = when {
            d.length == 10 -> d
            d.length == 11 && d.startsWith("1") -> d.drop(1)
            else -> return raw.trim()
        }
        return "(" + ten.substring(0, 3) + ") " + ten.substring(3, 6) + "-" + ten.substring(6)
    }

    /**
     * What gets handed to BrightChat and to the dialer.
     *
     * E.164 where the number is recognisably North American, because BrightChat constructs
     * `iMessage;-;<handle>` from it and iMessage handles are E.164. Anything else goes through
     * as its own digits — wrong guesses about a country code are worse than none.
     */
    fun dialable(raw: String): String {
        val d = digits(raw)
        return when {
            d.length == 10 -> "+1$d"
            d.length == 11 && d.startsWith("1") -> "+$d"
            raw.trim().startsWith("+") -> "+$d"
            else -> d
        }
    }
}
