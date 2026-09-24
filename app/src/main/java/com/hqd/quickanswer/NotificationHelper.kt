package com.hqd.quickanswer

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

object NotificationHelper {
    const val CHANNEL_ID = "quick_ai_channel"
    const val NOTIFICATION_ID = 240903
    const val KEY_TEXT_REPLY = "quick_ai_text"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showAssistant(context: Context, status: String = "Nhấn “Nhập câu hỏi” để gửi"): Boolean {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(context.getString(R.string.reply_label))
            .setAllowFreeFormInput(true)
            .build()

        val intent = Intent(context, ReplyReceiver::class.java).apply {
            action = "${context.packageName}.REPLY"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            240903,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_quick_ai,
            "Nhập câu hỏi",
            pendingIntent
        ).addRemoteInput(remoteInput).build()

        val openIntent = PendingIntent.getActivity(
            context,
            240904,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quick_ai)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openIntent)
            .addAction(action)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    fun showProcessing(context: Context, questionPreview: String) {
        showAssistant(context, "Đang xử lý… ${questionPreview.take(80)}")
    }

    fun showAnswer(context: Context, answer: String) {
        showAssistant(context, answer.take(1000))
    }

    fun showError(context: Context, message: String) {
        showAssistant(context, "Lỗi: ${message.take(600)}")
    }
}
