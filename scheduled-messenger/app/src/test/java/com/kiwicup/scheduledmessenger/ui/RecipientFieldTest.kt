package com.kiwicup.scheduledmessenger.ui

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kiwicup.scheduledmessenger.core.ContactSuggestion
import com.kiwicup.scheduledmessenger.core.NewNumberSuggestion
import com.kiwicup.scheduledmessenger.core.PersonSuggestion
import com.kiwicup.scheduledmessenger.ui.components.RecipientField
import com.kiwicup.scheduledmessenger.ui.compose.RecipientChip
import com.kiwicup.scheduledmessenger.ui.theme.ScheduledMessengerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipientFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        chips: List<RecipientChip> = emptyList(),
        query: String = "",
        onQueryChange: (String) -> Unit = {},
        suggestions: List<ContactSuggestion> = emptyList(),
        onPickSuggestion: (ContactSuggestion) -> Unit = {},
        onRemoveChip: (String) -> Unit = {}
    ) {
        compose.setContent {
            ScheduledMessengerTheme(dynamicColor = false) {
                RecipientField(
                    chips = chips,
                    query = query,
                    onQueryChange = onQueryChange,
                    suggestions = suggestions,
                    onPickSuggestion = onPickSuggestion,
                    onRemoveChip = onRemoveChip,
                    isError = false
                )
            }
        }
    }

    @Test
    fun typingSurfacesTheTypedTextThroughTheCallback() {
        // A KeyboardType.Phone field could not accept this in the first place; this confirms the
        // typed value round-trips through the callback the way the phone-only field never did.
        var typed = ""
        show(onQueryChange = { typed = it })
        compose.onNodeWithTag("recipient_input").performTextInput("John")
        assertEquals("John", typed)
    }

    @Test
    fun aConfirmedRecipientShowsAsAChip() {
        show(chips = listOf(RecipientChip("5551234567", "John Smith")))
        compose.onNodeWithText("John Smith").assertIsDisplayed()
    }

    @Test
    fun aChipWithNoContactNameShowsTheRawAddress() {
        show(chips = listOf(RecipientChip("5551234567", null)))
        compose.onNodeWithText("5551234567").assertIsDisplayed()
    }

    @Test
    fun removingAChipFiresTheCallbackWithItsAddress() {
        var removed: String? = null
        show(chips = listOf(RecipientChip("5551234567", "John Smith")), onRemoveChip = { removed = it })
        compose.onNodeWithTag("remove_chip_5551234567").performClick()
        assertEquals("5551234567", removed)
    }

    @Test
    fun tappingAPersonSuggestionFiresTheCallback() {
        var picked: ContactSuggestion? = null
        val suggestion = PersonSuggestion("5551234567", "John Smith · 5551234567", "John Smith", "lookup-1")
        show(query = "john", suggestions = listOf(suggestion), onPickSuggestion = { picked = it })
        compose.onNodeWithTag("suggestion_5551234567").performClick()
        assertEquals(suggestion, picked)
    }

    @Test
    fun tappingTheNewNumberSuggestionFiresTheCallback() {
        var picked: ContactSuggestion? = null
        val suggestion = NewNumberSuggestion("5551234567", "New number: (555) 123-4567")
        show(query = "5551234567", suggestions = listOf(suggestion), onPickSuggestion = { picked = it })
        compose.onNodeWithText("New number: (555) 123-4567").performClick()
        assertEquals(suggestion, picked)
    }

    @Test
    fun noSuggestionsMeansNoDropdown() {
        show(query = "xyz", suggestions = emptyList())
        compose.onNodeWithTag("recipient_suggestions").assertDoesNotExist()
    }
}
