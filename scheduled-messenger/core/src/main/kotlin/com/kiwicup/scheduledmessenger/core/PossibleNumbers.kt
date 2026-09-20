package com.kiwicup.scheduledmessenger.core

import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Recognises and formats a phone number typed into the recipient search field, so a number that
 * matches no contact can still be offered as a tappable suggestion rather than silently accepted
 * only on submit. QKSMS does the same with the Android port of this library; we use the plain
 * Java one so the logic can be unit tested here without an Android runtime.
 */
object PossibleNumbers {

    private val util = PhoneNumberUtil.getInstance()

    /** True once the query has enough of a real number in it to be worth suggesting. */
    fun isPossible(query: String, defaultRegion: String): Boolean =
        runCatching { util.isPossibleNumber(query, defaultRegion) }.getOrDefault(false)

    /** Formats for display; falls back to the raw query if it does not parse. */
    fun format(query: String, defaultRegion: String): String = runCatching {
        util.format(util.parse(query, defaultRegion), PhoneNumberUtil.PhoneNumberFormat.NATIONAL)
    }.getOrElse { query }

    /**
     * A synthetic "send to this number" suggestion for a query that is not a contact match.
     * Requires a handful of digits before bothering the parser — a one- or two-digit query is
     * never a real number and rejecting it early avoids a suggestion flickering in in front of
     * someone who has barely started typing.
     */
    fun suggestionFor(query: String, defaultRegion: String, alreadySelected: Set<String>): NewNumberSuggestion? {
        if (query.count { it.isDigit() } < MIN_DIGITS_BEFORE_PARSING) return null
        if (!isPossible(query, defaultRegion)) return null
        val normalized = RecipientValidator.normalizeOrNull(query) ?: return null
        if (normalized in alreadySelected) return null
        return NewNumberSuggestion(address = normalized, label = "New number: ${format(query, defaultRegion)}")
    }

    private const val MIN_DIGITS_BEFORE_PARSING = 3
}
