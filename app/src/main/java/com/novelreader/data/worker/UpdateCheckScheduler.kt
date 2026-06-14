package com.novelreader.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.novelreader.data.repository.NovelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCheckScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val novelRepository: NovelRepository
) {
    fun schedule(intervalHours: Long) {
        if (intervalHours <= 0) {
            cancel()
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<ChapterUpdateCheckWorker>(
            intervalHours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(ChapterUpdateCheckWorker.TAG_UPDATE_CHECK)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ChapterUpdateCheckWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(
            ChapterUpdateCheckWorker.UNIQUE_PERIODIC
        )
    }

    suspend fun rescheduleIfNeeded() {
        val hasAutoUpdateNovels = novelRepository.getAutoUpdateNovels().isNotEmpty()
        if (hasAutoUpdateNovels) {
            schedule(DEFAULT_INTERVAL_HOURS)
        } else {
            cancel()
        }
    }

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 6L
    }
}