package com.virusx.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :userId LIMIT 1")
    suspend fun getProfileById(userId: String): ProfileEntity?

    @Query(
        """
        SELECT * FROM profiles
        WHERE username LIKE '%' || :query || '%'
           OR display_name LIKE '%' || :query || '%'
           OR phone_number LIKE '%' || :query || '%'
        ORDER BY display_name ASC
        """
    )
    suspend fun searchProfilesLocally(query: String): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(profiles: List<ProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun clearAll()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE is_hidden_in_vault = 0 ORDER BY COALESCE(last_message_time, created_at) DESC")
    fun observeVisibleChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE is_hidden_in_vault = 1 ORDER BY COALESCE(last_message_time, created_at) DESC")
    fun observeVaultChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT is_hidden_in_vault FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun isChatHidden(chatId: String): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChats(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity)

    @Query("UPDATE chats SET is_hidden_in_vault = :hidden WHERE id = :chatId")
    suspend fun setChatHiddenInVault(chatId: String, hidden: Boolean)

    @Query("DELETE FROM chats")
    suspend fun clearAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY created_at ASC")
    fun observeMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE chat_id = :chatId")
    suspend fun clearChatMessages(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun clearAll()
}
