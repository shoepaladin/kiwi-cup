package com.kiwicup.scheduledmessenger.core

/**
 * Recognises and formats phone numbers, without committing to any particular library.
 *
 * `core` depends only on this interface, not on how a query is actually judged to be number-shaped.
 * The app module currently supplies `PlatformPhoneNumberRecognizer`, backed by the platform's
 * `android.telephony.PhoneNumberUtils` rather than a bundled library — see that class's own doc
 * comment for why. Tests supply one backed by the plain (non-Android) Google libphonenumber build,
 * which is perfectly fine off-device and gives these tests a stricter, independent check of the
 * same behaviour. Keeping this as an interface is what made swapping the app's implementation a
 * one-file change rather than a `core` change.
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
