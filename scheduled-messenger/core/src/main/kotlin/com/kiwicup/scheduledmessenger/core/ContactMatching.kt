package com.kiwicup.scheduledmessenger.core

import java.text.Normalizer

/**
 * Matching rules for the recipient search field, mirroring the convention Fossify and QKSMS both
 * use: accent-folded substring on the name, digit-normalized substring on the number. Neither
 * app restricts to a name *prefix* — a substring anywhere in the name matches, which is what
 * lets "smith" find "John Smith".
 */
object ContactMatching {

    /** Strips diacritics so "jose" matches "José": decompose, then drop the combining marks. */
    fun foldAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

    /** The form both sides of a name comparison are reduced to before matching. */
    fun searchKey(text: String): String = foldAccents(text).lowercase()

    /** Just the digits, so "555-1234" and "5551234" compare the same. */
    fun digitsOf(text: String): String = text.filter { it.isDigit() }

    fun matchesContactName(displayName: String, query: String): Boolean {
        if (query.isBlank()) return false
        return searchKey(displayName).contains(searchKey(query))
    }

    /** Compares digits only, so "555-1234" and formatting-free "5551234" match the same query. */
    fun matchesPhoneNumber(phoneNumber: String, query: String): Boolean {
        val needle = digitsOf(query)
        if (needle.isEmpty()) return false
        return digitsOf(phoneNumber).contains(needle)
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
