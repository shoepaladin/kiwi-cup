package com.kiwicup.scheduledmessenger.core

/** One (contact, number) pair, matching how the system contacts provider naturally returns rows. */
data class Contact(val lookupKey: String, val displayName: String, val phoneNumber: String)

/** One row offered under the recipient search field. */
sealed class ContactSuggestion {
    abstract val address: String
    abstract val label: String
}

data class PersonSuggestion(
    override val address: String,
    override val label: String,
    val displayName: String,
    val lookupKey: String
) : ContactSuggestion()

/** The query itself, offered as a destination when it looks like a number but matches no contact. */
data class NewNumberSuggestion(override val address: String, override val label: String) : ContactSuggestion()

/**
 * Builds the dropdown for the recipient search field.
 *
 * No provider query happens here: [contacts] is expected to be loaded once and held in memory,
 * the same "bulk-load then filter locally" strategy QKSMS and Fossify both use rather than
 * hitting the ContentProvider per keystroke.
 */
object ContactSuggestions {

    private const val MAX_RESULTS = 20

    fun forQuery(
        contacts: List<Contact>,
        recognizer: PhoneNumberRecognizer,
        query: String,
        alreadySelected: Set<String>,
        defaultRegion: String
    ): List<ContactSuggestion> {
        if (query.isBlank()) return emptyList()

        val matches = contacts
            .asSequence()
            .filter { RecipientValidator.normalize(it.phoneNumber) !in alreadySelected }
            .filter { ContactMatching.matchesContactName(it.displayName, query) || ContactMatching.matchesPhoneNumber(it.phoneNumber, query) }
            .distinctBy { it.lookupKey to it.phoneNumber }
            .sortedWith(
                compareByDescending<Contact> { it.displayName.startsWith(query, ignoreCase = true) }
                    .thenBy { it.displayName }
            )
            .take(MAX_RESULTS)
            .map {
                PersonSuggestion(
                    address = RecipientValidator.normalize(it.phoneNumber),
                    label = "${it.displayName} · ${it.phoneNumber}",
                    displayName = it.displayName,
                    lookupKey = it.lookupKey
                )
            }
            .toList()

        // Leads rather than trails: someone typing a number they intend to text is looking for
        // confirmation that it will go through, not scrolling past name matches to find it.
        // Dropped entirely if a match already carries the same address: offering "New number:
        // 555-111-2222" beside "John Smith · 5551112222" for the identical number is not a second
        // option, and duplicate addresses in the same list would collide as a UI list key besides.
        val newNumber = PossibleNumbers.suggestionFor(recognizer, query, defaultRegion, alreadySelected)
            ?.takeIf { candidate -> matches.none { it.address == candidate.address } }
        return if (newNumber != null) listOf(newNumber) + matches else matches
    }
}
