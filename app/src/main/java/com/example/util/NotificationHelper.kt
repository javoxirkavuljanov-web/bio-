package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.data.ScheduleTask
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val title = inputData.getString("title") ?: "Daily Routine"
        val message = inputData.getString("message") ?: "Time for your scheduled task!"
        val channelId = "daily_routine_alerts"

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Routine Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Task and prayer reminder alerts"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        return Result.success()
    }
}

object NotificationHelper {

    fun cancelAllAlerts(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("routine_alert")
    }

    fun scheduleRoutineAlerts(context: Context, tasks: List<ScheduleTask>, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("routine_alert")

        if (!enabled) return

        val now = Calendar.getInstance()
        val currentDayOfWeek = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        tasks.forEach { task ->
            // If the task is for today
            if (task.dayOfWeek == currentDayOfWeek) {
                val taskCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, task.startHour)
                    set(Calendar.MINUTE, task.startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val diffMs = taskCalendar.timeInMillis - now.timeInMillis
                if (diffMs > 0) {
                    // 1. Alert at start moment
                    scheduleWork(context, task.title, "Starting now!", diffMs, "start_${task.id}")

                    // 2. Alert 5 minutes before
                    val fiveMinMs = 5 * 60 * 1000
                    if (diffMs > fiveMinMs) {
                        scheduleWork(context, task.title, "Starting in 5 minutes!", diffMs - fiveMinMs, "5min_${task.id}")
                    }

                    // 3. Alert 10 minutes before
                    val tenMinMs = 10 * 60 * 1000
                    if (diffMs > tenMinMs) {
                        scheduleWork(context, task.title, "Starting in 10 minutes!", diffMs - tenMinMs, "10min_${task.id}")
                    }
                }
            }
        }
    }

    private fun scheduleWork(context: Context, title: String, message: String, delayMs: Long, uniqueTag: String) {
        val data = Data.Builder()
            .putString("title", title)
            .putString("message", message)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("routine_alert")
            .addTag(uniqueTag)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
