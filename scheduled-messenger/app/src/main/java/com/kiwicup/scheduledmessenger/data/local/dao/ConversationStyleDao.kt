package com.kiwicup.scheduledmessenger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kiwicup.scheduledmessenger.data.local.entity.ConversationStyle
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationStyleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(style: ConversationStyle)

    @Query("SELECT * FROM conversation_styles WHERE address = :address")
    suspend fun get(address: String): ConversationStyle?

    @Query("SELECT * FROM conversation_styles WHERE address = :address")
    fun observe(address: String): Flow<ConversationStyle?>

    @Query("SELECT * FROM conversation_styles")
    suspend fun getAll(): List<ConversationStyle>

    @Query("DELETE FROM conversation_styles WHERE address = :address")
    suspend fun delete(address: String): Int
}
