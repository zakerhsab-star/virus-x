package com.virusx.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.virusx.app.data.local.dao.ChatDao
import com.virusx.app.data.local.dao.MessageDao
import com.virusx.app.data.local.dao.ProfileDao
import com.virusx.app.data.local.entity.ChatEntity
import com.virusx.app.data.local.entity.MessageEntity
import com.virusx.app.data.local.entity.ProfileEntity

@Database(
    entities = [
        ProfileEntity::class,
        ChatEntity::class,
        MessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class VirusXDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
}
