package com.novelreader.data.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.UUID

class ImportCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val idStr = intent.getStringExtra(WorkManagerIntents.EXTRA_JOB_ID) ?: return
        runCatching { UUID.fromString(idStr) }
            .onSuccess { WorkManagerIntents.cancelWork(context, it) }
    }
}
