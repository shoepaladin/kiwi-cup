package com.kiwicup.scheduledmessenger.core

/**
 * Recognises and formats phone numbers, without committing to any particular library.
 *
 * This exists because there is no single library that is both testable outside Android and safe
 * to run on it. Google's own libphonenumber loads its metadata via `Class#getResourceAsStream`,
 * which Google's own documentation says Android apps should not rely on directly — the Android
 * port (`io.michaelrocks:libphonenumber-android`, what QKSMS uses) exists specifically to load
 * that metadata the way Android expects, but it needs a `Context` to construct, which a pure
 * Kotlin/JVM module cannot provide. So `core` depends only on this interface; the app module
 * supplies the Android-safe implementation, and tests supply one backed by the plain library,
 * which is perfectly fine off-device.
 */
interface PhoneNumberRecognizer {
    /** True once the query has enough of a real number in it to be worth suggesting. */
    fun isPossible(query: String, defaultRegion: String): Boolean

    /** Formats for display; implementations should fall back to the raw query if it does not parse. */
    fun format(query: String, defaultRegion: String): String
}

object PossibleNumbers {

    /**
     * A synthetic "send to this number" suggestion for a query that is not a contact match.
     * Requires a handful of digits before bothering the recognizer — a one- or two-digit query is
     * never a real number and rejecting it early avoids a suggestion flickering in in front of
     * someone who has barely started typing.
     */
    fun suggestionFor(
        recognizer: PhoneNumberRecognizer,
        query: String,
        defaultRegion: String,
        alreadySelected: Set<String>
    ): NewNumberSuggestion? {
        if (query.count { it.isDigit() } < MIN_DIGITS_BEFORE_PARSING) return null
        if (!recognizer.isPossible(query, defaultRegion)) return null
        val normalized = RecipientValidator.normalizeOrNull(query) ?: return null
        if (normalized in alreadySelected) return null
        return NewNumberSuggestion(address = normalized, label = "New number: ${recognizer.format(query, defaultRegion)}")
    }

    private const val MIN_DIGITS_BEFORE_PARSING = 3
}
