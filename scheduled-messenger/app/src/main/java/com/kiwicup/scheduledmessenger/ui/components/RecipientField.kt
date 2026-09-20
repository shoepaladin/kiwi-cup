package com.kiwicup.scheduledmessenger.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kiwicup.scheduledmessenger.core.ContactSuggestion
import com.kiwicup.scheduledmessenger.core.PersonSuggestion
import com.kiwicup.scheduledmessenger.core.RecentSuggestion
import com.kiwicup.scheduledmessenger.ui.compose.RecipientChip

/**
 * The "To" field for a new message.
 *
 * Confirmed recipients render as removable chips above a search box that accepts letters as well
 * as digits — a bare `KeyboardType.Phone` field cannot find anyone by name, which is the gap this
 * replaces. Matching contacts, and a synthetic suggestion when the query itself looks like a
 * sendable number, appear in a dropdown below the field while it has text in it.
 *
 * Chips sit above a plain search field rather than embedded inline with the cursor the way a
 * Gmail-style "To" field does: that would need a hand-built text field with custom decoration to
 * get chip wrapping and cursor placement working together, which is a lot of surface area to get
 * right in a change that cannot be run against a real device before it ships. This costs a little
 * visual polish for a lot of certainty.
 */
@Composable
fun RecipientField(
    chips: List<RecipientChip>,
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<ContactSuggestion>,
    onPickSuggestion: (ContactSuggestion) -> Unit,
    onRemoveChip: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (chips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("recipient_chips")
            ) {
                chips.forEach { chip -> RecipientChipView(chip, onRemove = { onRemoveChip(chip.address) }) }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(if (chips.isEmpty()) "To (name or number)" else "Add another") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = isError,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recipient_input")
        )
        if (suggestions.isNotEmpty()) {
            // Bounded height with its own scroll rather than letting the list grow the screen:
            // this keeps the field self-contained regardless of how many contacts match, and
            // regardless of whether the enclosing screen itself scrolls.
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .testTag("recipient_suggestions")
            ) {
                val recents = suggestions.filterIsInstance<RecentSuggestion>()
                val rest = suggestions.filter { it !is RecentSuggestion }
                LazyColumn {
                    // Headers only appear on the untouched field, where the list is two lists;
                    // a search result is one list and labelling it would be noise.
                    if (recents.isNotEmpty()) {
                        item(key = "header_recent") { SectionHeader("Recent") }
                        items(recents, key = { it.address }) { SuggestionRow(it, onPickSuggestion) }
                        if (rest.isNotEmpty()) item(key = "header_contacts") { SectionHeader("Contacts") }
                    }
                    items(rest, key = { it.address }) { SuggestionRow(it, onPickSuggestion) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SuggestionRow(suggestion: ContactSuggestion, onPick: (ContactSuggestion) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPick(suggestion) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("suggestion_${suggestion.address}")
    ) {
        Text(text = suggestion.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (suggestion is PersonSuggestion && suggestion.starred) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Favourite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun RecipientChipView(chip: RecipientChip, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.testTag("chip_${chip.address}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 2.dp)) {
            Text(chip.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("remove_chip_${chip.address}")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove ${chip.label}", modifier = Modifier.size(16.dp))
            }
        }
    }
}
