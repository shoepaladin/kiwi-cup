package com.kiwicup.scheduledmessenger.core

/** The confirmed-recipient half of the compose screen's state: addresses plus known display names. */
data class RecipientSelection(val recipient: String = "", val recipientNames: Map<String, String> = emptyMap()) {
    val addresses: List<String> get() = Recipients.decode(recipient)
}

/**
 * State transitions for picking, removing, and submitting recipients, kept separate from the
 * ViewModel so a mistake here — the kind that only shows up as "I removed a chip and the wrong
 * number stayed" — is something a test can catch without an Android runtime.
 */
object RecipientSelections {

    /** Adding a suggestion already present is a no-op rather than a duplicate chip. */
    fun add(selection: RecipientSelection, suggestion: ContactSuggestion): RecipientSelection {
        if (suggestion.address in selection.addresses) return selection
        // A recent conversation carries a name too when the address book knows the number, and
        // a chip reading "John Smith" beats one reading "5551112222" either way it was picked.
        val name = when (suggestion) {
            is PersonSuggestion -> suggestion.displayName
            is RecentSuggestion -> suggestion.displayName
            is NewNumberSuggestion -> null
        }
        val names = if (name != null) selection.recipientNames + (suggestion.address to name) else selection.recipientNames
        return selection.copy(recipient = Recipients.encode(selection.addresses + suggestion.address), recipientNames = names)
    }

    fun remove(selection: RecipientSelection, address: String): RecipientSelection =
        selection.copy(
            recipient = Recipients.encode(selection.addresses - address),
            recipientNames = selection.recipientNames - address
        )

    /**
     * Folds a still-typed, unconfirmed query into the recipient list for use at submit time.
     * Without this, typing a number and hitting send without first tapping its suggestion row
     * would silently drop it, since only confirmed chips otherwise feed the recipient list.
     */
    fun effectiveRecipient(selection: RecipientSelection, query: String): String =
        if (query.isBlank()) selection.recipient else Recipients.encode(selection.addresses + Recipients.decode(query))
}
