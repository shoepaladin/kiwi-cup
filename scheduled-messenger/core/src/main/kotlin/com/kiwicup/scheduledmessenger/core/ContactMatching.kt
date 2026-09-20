package com.kiwicup.scheduledmessenger.core

import java.text.Normalizer

/**
 * Matching rules for the recipient search field, mirroring the convention Fossify and QKSMS both
 * use: accent-folded substring on the name, digit-normalized substring on the number. Neither
 * apps restricts to a name *prefix* — a substring anywhere in the name matches, which is what
 * lets "smith" find "John Smith".
 */
object ContactMatching {

    /** Strips diacritics so "jose" matches "José": decompose, then drop the combining marks. */
    fun foldAccents(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")

    fun matchesContactName(displayName: String, query: String): Boolean {
        if (query.isBlank()) return false
        val name = foldAccents(displayName).lowercase()
        val needle = foldAccents(query).lowercase()
        return name.contains(needle)
    }

    /** Compares digits only, so "555-1234" and formatting-free "5551234" match the same query. */
    fun matchesPhoneNumber(phoneNumber: String, query: String): Boolean {
        val needle = query.filter { it.isDigit() }
        if (needle.isEmpty()) return false
        val digits = phoneNumber.filter { it.isDigit() }
        return digits.contains(needle)
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
