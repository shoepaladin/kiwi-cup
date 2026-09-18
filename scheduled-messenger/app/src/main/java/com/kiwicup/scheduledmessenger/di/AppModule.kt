package com.kiwicup.scheduledmessenger.di

import android.content.Context
import androidx.work.WorkManager
import com.kiwicup.scheduledmessenger.core.SchedulingPolicy
import com.kiwicup.scheduledmessenger.core.SystemTimeSource
import com.kiwicup.scheduledmessenger.core.TimeSource
import com.kiwicup.scheduledmessenger.data.sms.AndroidSmsSender
import com.kiwicup.scheduledmessenger.data.sms.SmsSender
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
    }
}
