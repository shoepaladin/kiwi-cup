package com.kiwicup.scheduledmessenger.core

/** A contact with the two comparison forms precomputed, so a keystroke does no text normalization. */
data class IndexedContact(
    val contact: Contact,
    val searchKey: String,
    val digits: String,
    val address: String
)

/**
 * The contact list prepared once for repeated searching.
 *
 * Accent folding runs `java.text.Normalizer` and a regex per name. QKSMS calls that "an expensive
 * operation" and caches it for the query side, but still folds every contact name on every
 * keystroke; doing it once at load instead is the same idea carried to the side that actually has
 * thousands of entries.
 */
class ContactIndex(contacts: List<Contact>) {

    val entries: List<IndexedContact> = contacts.map {
        IndexedContact(
            contact = it,
            searchKey = ContactMatching.searchKey(it.displayName),
            digits = ContactMatching.digitsOf(it.phoneNumber),
            address = RecipientValidator.normalize(it.phoneNumber)
        )
    }

    /** Display name for [address], matched on normalized digits; null when no contact has it. */
    fun nameFor(address: String): String? {
        val wanted = RecipientValidator.normalize(address)
        return entries.firstOrNull { it.address == wanted }?.contact?.displayName
    }

    companion object {
        val EMPTY = ContactIndex(emptyList())
    }
}
