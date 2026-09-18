package com.kiwicup.scheduledmessenger.core

/**
 * Normalises and validates phone-number style SMS addresses.
 *
 * Deliberately permissive: carriers accept short codes (5-6 digits) and international
 * numbers with a leading '+'. We only strip formatting characters and reject obvious junk.
 */
object RecipientValidator {

    private val allowed = Regex("^\\+?[0-9]{3,15}$")
    private val formatting = Regex("[\\s\\-().]")

    /** Removes spaces, dashes, dots and parentheses: "(555) 123-4567" -> "5551234567". */
    fun normalize(raw: String): String = raw.trim().replace(formatting, "")

    fun isValid(raw: String): Boolean = allowed.matches(normalize(raw))

    /** Returns the normalised address, or null when it is not a usable SMS address. */
    fun normalizeOrNull(raw: String): String? = normalize(raw).takeIf { allowed.matches(it) }
}
