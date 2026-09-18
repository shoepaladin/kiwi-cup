package com.kiwicup.scheduledmessenger.data.local

import androidx.room.TypeConverter
import com.kiwicup.scheduledmessenger.core.MessageStatus
import com.kiwicup.scheduledmessenger.core.SmsStatus

/** Enums are stored by name so the SQL in the DAOs can compare against readable literals. */
class Converters {
    @TypeConverter fun fromMessageStatus(value: MessageStatus): String = value.name
    @TypeConverter fun toMessageStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
    @TypeConverter fun fromSmsStatus(value: SmsStatus): String = value.name
    @TypeConverter fun toSmsStatus(value: String): SmsStatus = SmsStatus.valueOf(value)
}
