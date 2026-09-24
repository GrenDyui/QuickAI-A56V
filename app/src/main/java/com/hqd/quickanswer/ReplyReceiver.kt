package com.hqd.quickanswer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (text.isBlank()) {
            NotificationHelper.showError(context, "Không đọc được nội dung nhập.")
            return
        }

        NotificationHelper.showProcessing(context, text)

        // Store the actual text outside WorkManager Data so long pasted passages
        // are not limited by WorkManager's small Data payload size.
        val jobId = UUID.randomUUID().toString()
        context.getSharedPreferences("pending_questions", Context.MODE_PRIVATE)
            .edit().putString(jobId, text).apply()

        val input = Data.Builder().putString(AnswerWorker.KEY_JOB_ID, jobId).build()
        val request = OneTimeWorkRequestBuilder<AnswerWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            AnswerWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
