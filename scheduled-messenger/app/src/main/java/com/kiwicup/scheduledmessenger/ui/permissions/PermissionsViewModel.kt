package com.kiwicup.scheduledmessenger.ui.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.data.inbox.SmsInboxImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val importer: SmsInboxImporter
) : ViewModel() {
    fun importInbox() {
        viewModelScope.launch { runCatching { importer.importNew() } }
    }
}
