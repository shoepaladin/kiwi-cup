package com.kiwicup.scheduledmessenger.core

/** One (contact, number) pair, matching how the system contacts provider naturally returns rows. */
data class Contact(
    val lookupKey: String,
    val displayName: String,
    val phoneNumber: String,
    /** Contacts the phone's own list marks as favourites; offered first on an empty field. */
    val starred: Boolean = false
)

/** A conversation this phone has already had, newest first, used to fill an empty field. */
data class RecentConversation(val address: String, val displayName: String?)

/** One row offered under the recipient search field. */
sealed class ContactSuggestion {
    abstract val address: String
    abstract val label: String
}

data class PersonSuggestion(
    override val address: String,
    override val label: String,
    val displayName: String,
    val lookupKey: String,
    val starred: Boolean = false
) : ContactSuggestion()

/** Someone already texted from this phone, shown before the address book on an empty field. */
data class RecentSuggestion(
    override val address: String,
    override val label: String,
    val displayName: String?
) : ContactSuggestion()

/** The query itself, offered as a destination when it looks like a number but matches no contact. */
data class NewNumberSuggestion(override val address: String, override val label: String) : ContactSuggestion()

/**
 * Builds the dropdown for the recipient search field.
 *
 * No provider query happens here: the index is expected to be built once and held in memory, the
 * same "bulk-load then filter locally" strategy QKSMS and Fossify both use rather than hitting the
 * ContentProvider per keystroke.
 */
object ContactSuggestions {

    private const val MAX_RESULTS = 20
    private const val MAX_RECENTS = 5

    /**
     * What an untouched recipient field offers before anything is typed.
     *
     * An empty field used to offer nothing, which is not what either reference app does: QKSMS
     * lists recent conversations, then starred contacts, then everyone; Fossify shows a row of
     * recent contacts above the full address book. Recents lead because the person you are most
     * likely to text is the one you texted last.
     */
    fun forEmptyField(
        recents: List<RecentConversation>,
        index: ContactIndex,
        alreadySelected: Set<String>
    ): List<ContactSuggestion> {
        val recentRows = recents
            .asSequence()
            .map { it.copy(address = RecipientValidator.normalize(it.address)) }
            .filter { it.address.isNotEmpty() && it.address !in alreadySelected }
            .distinctBy { it.address }
            .take(MAX_RECENTS)
            .map { recent ->
                val name = recent.displayName ?: index.nameFor(recent.address)
                RecentSuggestion(
                    address = recent.address,
                    label = if (name != null) "$name · ${recent.address}" else recent.address,
                    displayName = name
                )
            }
            .toList()

        val taken = recentRows.map { it.address }.toSet() + alreadySelected
        val contactRows = index.entries
            .asSequence()
            .filter { it.address !in taken }
            // By address, not (lookupKey, phoneNumber): a contact synced from two accounts, or
            // stored with the same number typed two different ways, produces two IndexedContact
            // rows with different lookup keys or raw numbers but the identical normalized
            // address — deduping on the raw fields let both through as separate PersonSuggestions
            // sharing one address, which crashed the dropdown's LazyColumn the first time this
            // path (an unfiltered list, not a narrowed search) put two of them in the same top 20.
            .distinctBy { it.address }
            // Favourites first, then alphabetical, the order QKSMS builds its empty-query list in.
            .sortedWith(compareByDescending<IndexedContact> { it.contact.starred }.thenBy { it.searchKey })
            .take(MAX_RESULTS)
            .map { it.toSuggestion() }
            .toList()

        return recentRows + contactRows
    }

    fun forQuery(
        index: ContactIndex,
        recognizer: PhoneNumberRecognizer,
        query: String,
        alreadySelected: Set<String>,
        defaultRegion: String
    ): List<ContactSuggestion> {
        if (query.isBlank()) return emptyList()

        val nameNeedle = ContactMatching.searchKey(query)
        val digitNeedle = ContactMatching.digitsOf(query)

        val matches = index.entries
            .asSequence()
            .filter { it.address !in alreadySelected }
            .filter { it.searchKey.contains(nameNeedle) || (digitNeedle.isNotEmpty() && it.digits.contains(digitNeedle)) }
            // Same reasoning as forEmptyField above: dedupe on the address the UI actually keys
            // its list by, not on the raw (lookupKey, phoneNumber) pair, or a contact synced twice
            // can surface as two suggestions sharing one address.
            .distinctBy { it.address }
            // Fossify sorts new-conversation results the same way: name-prefix matches first,
            // then alphabetical. Favourites break the tie within each group.
            .sortedWith(
                compareByDescending<IndexedContact> { it.searchKey.startsWith(nameNeedle) }
                    .thenByDescending { it.contact.starred }
                    .thenBy { it.searchKey }
            )
            .take(MAX_RESULTS)
            .map { it.toSuggestion() }
            .toList()

        // Leads rather than trails: someone typing a number they intend to text is looking for
        // confirmation that it will go through, not scrolling past name matches to find it.
        // QKSMS puts its equivalent row first for the same reason. Dropped entirely if a match
        // already carries the same address: offering "New number: 555-111-2222" beside "John
        // Smith · 5551112222" for the identical number is not a second option, and duplicate
        // addresses in the same list would collide as a UI list key besides.
        val newNumber = PossibleNumbers.suggestionFor(recognizer, query, defaultRegion, alreadySelected)
            ?.takeIf { candidate -> matches.none { it.address == candidate.address } }
        return if (newNumber != null) listOf(newNumber) + matches else matches
    }

    private fun IndexedContact.toSuggestion() = PersonSuggestion(
        address = address,
        label = "${contact.displayName} · ${contact.phoneNumber}",
        displayName = contact.displayName,
        lookupKey = contact.lookupKey,
        starred = contact.starred
    )
}
