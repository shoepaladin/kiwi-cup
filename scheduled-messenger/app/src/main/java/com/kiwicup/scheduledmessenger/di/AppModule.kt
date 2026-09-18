package com.kiwicup.scheduledmessenger.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.kiwicup.scheduledmessenger.core.SchedulingPolicy
import com.kiwicup.scheduledmessenger.core.SystemTimeSource
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.sms.AndroidSmsSender
import com.kiwicup.scheduledmessenger.data.sms.SmsSender
import com.kiwicup.scheduledmessenger.work.SchedulerApi
import com.kiwicup.scheduledmessenger.work.WorkScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSmsSender(impl: AndroidSmsSender): SmsSender

    @Binds
    @Singleton
    abstract fun bindScheduler(impl: WorkScheduler): SchedulerApi

    companion object {
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

        @Provides
        @Singleton
        fun provideTimeSource(): TimeSource = SystemTimeSource

        @Provides
        @Singleton
        fun provideSchedulingPolicy(): SchedulingPolicy = SchedulingPolicy()

        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })
    }
}
