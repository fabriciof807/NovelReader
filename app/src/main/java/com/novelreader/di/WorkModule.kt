package com.novelreader.di

import android.content.Context
import androidx.work.WorkManager
import com.novelreader.data.worker.SpecFileStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkModule {
    @Provides
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideSpecFileStore(@ApplicationContext context: Context): SpecFileStore {
        val baseDir = File(context.filesDir, "import_jobs")
        return SpecFileStore(baseDir)
    }
}
