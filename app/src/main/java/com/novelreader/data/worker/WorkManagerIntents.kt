package com.novelreader.data.worker

import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import java.util.UUID

object WorkManagerIntents {
    private const val ACTION_CANCEL = "com.novelreader.action.CANCEL_IMPORT"

    fun cancelIntent(context: Context, id: UUID): Intent {
        return Intent(context, ImportCancelReceiver::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_JOB_ID, id.toString())
        }
    }

    const val EXTRA_JOB_ID = "job_id"
    const val ACTION_CANCEL_IMPORT = ACTION_CANCEL

    fun cancelWork(context: Context, id: UUID) {
        WorkManager.getInstance(context).cancelWorkById(id)
    }
}
