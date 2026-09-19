package com.kiwicup.scheduledmessenger.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiwicup.scheduledmessenger.data.local.dao.SmsMessageDao
import com.kiwicup.scheduledmessenger.data.local.dao.ThreadSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.kiwicup.scheduledmessenger.data.system.ContactNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    smsMessageDao: SmsMessageDao,
    private val contacts: ContactNames
) : ViewModel() {
    val threads: StateFlow<List<ThreadSummary>> = smsMessageDao.observeThreadSummaries()
        .map { list -> list.map { it.copy(displayName = contacts.displayName(it.address)) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
