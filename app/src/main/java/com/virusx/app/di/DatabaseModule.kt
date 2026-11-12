package com.virusx.app.di

import android.content.Context
import androidx.room.Room
import com.virusx.app.data.local.VirusXDatabase
import com.virusx.app.data.local.dao.ChatDao
import com.virusx.app.data.local.dao.MessageDao
import com.virusx.app.data.local.dao.ProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVirusXDatabase(@ApplicationContext context: Context): VirusXDatabase {
        return Room.databaseBuilder(
            context,
            VirusXDatabase::class.java,
            "virusx_local.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideProfileDao(db: VirusXDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideChatDao(db: VirusXDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: VirusXDatabase): MessageDao = db.messageDao()
}
