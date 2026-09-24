package com.hqd.quickanswer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnswerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString(KEY_JOB_ID).orEmpty()
        val prefs = applicationContext.getSharedPreferences("pending_questions", Context.MODE_PRIVATE)
        val question = prefs.getString(jobId, null).orEmpty()
        if (question.isBlank()) {
            NotificationHelper.showError(applicationContext, "Không tìm thấy nội dung câu hỏi.")
            return@withContext Result.failure()
        }

        try {
            val securePrefs = SecurePrefs(applicationContext)
            val apiKey = securePrefs.getApiKey()
            if (apiKey.isBlank()) {
                NotificationHelper.showError(applicationContext, "Chưa cấu hình Gemini API key trong app.")
                return@withContext Result.failure()
            }

            val answer = AiClient.generateAnswer(apiKey, securePrefs.getModel(), question)
            NotificationHelper.showAnswer(applicationContext, answer)
            prefs.edit().remove(jobId).apply()
            Result.success()
        } catch (e: AiClient.RetryableException) {
            if (runAttemptCount < 2) {
                // Keep the stored question so WorkManager can retry it.
                Result.retry()
            } else {
                NotificationHelper.showError(applicationContext, e.message ?: "Lỗi kết nối tạm thời")
                prefs.edit().remove(jobId).apply()
                Result.failure()
            }
        } catch (e: Exception) {
            NotificationHelper.showError(applicationContext, e.message ?: "Không thể nhận đáp án")
            prefs.edit().remove(jobId).apply()
            Result.failure()
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val UNIQUE_WORK_NAME = "quick_ai_answer"
    }
}
