package com.example.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.model.Task

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"

    fun scheduleAlarm(
        context: Context,
        uniqueId: Int,
        taskTitle: String,
        triggerAtMillis: Long,
        alertLevel: String,
        message: String
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "Attempted to schedule alarm in the past. Skipping.")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TASK_ID", uniqueId / 10) // recover original taskId
            putExtra("TASK_TITLE", taskTitle)
            putExtra("MESSAGE", message)
            putExtra("ALERT_LEVEL", alertLevel)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            uniqueId, // unique request code per sub-alarm
            intent,
            pendingIntentFlags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d(TAG, "Successfully scheduled $alertLevel alarm for '$taskTitle' at $triggerAtMillis")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling exact alarm, falling back to inexact.", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }

    fun getNotificationCategoryAndMessage(
        priorityScore: Double,
        timeLeftMillis: Long,
        taskTitle: String
    ): Pair<String, String> {
        val timeLeftMinutes = timeLeftMillis / 60000

        val (category, text) = when {
            timeLeftMillis <= 15 * 60 * 1000L -> { // <= 15 mins left
                when {
                    priorityScore >= 7.5 -> {
                        "RESCUE" to "🚨 CRITICAL RESCUE ALARM: '$taskTitle' is due in only $timeLeftMinutes mins! Priority is extremely high ($priorityScore/10.0). Open the app to launch Deadline Rescue now!"
                    }
                    priorityScore >= 4.0 -> {
                        "URGENT" to "⚠️ URGENT WARNING: '$taskTitle' is due in $timeLeftMinutes mins. Priority index is $priorityScore. Stop procrastinating and complete it!"
                    }
                    else -> {
                        "SUGGESTION" to "💡 Reminder: '$taskTitle' is due in $timeLeftMinutes mins. Even though priority is low ($priorityScore), let's get it out of the way."
                    }
                }
            }
            timeLeftMillis <= 1 * 3600 * 1000L -> { // 15 mins to 1 hour left
                when {
                    priorityScore >= 7.5 -> {
                        "URGENT" to "⚠️ HIGH PRIORITY ALERT: '$taskTitle' is due in $timeLeftMinutes mins. Priority is $priorityScore/10.0. Focus immediately!"
                    }
                    priorityScore >= 4.0 -> {
                        "SUGGESTION" to "⚡ Proactive Suggestion: '${taskTitle}' is due in $timeLeftMinutes mins. Start allocating focus blocks."
                    }
                    else -> {
                        "GENTLE" to "🌸 Gentle reminder: '${taskTitle}' is due in $timeLeftMinutes mins. Take a step whenever you are ready."
                    }
                }
            }
            else -> { // > 1 hour left
                val timeLeftHours = timeLeftMinutes / 60
                val displayTime = if (timeLeftHours > 0) "$timeLeftHours hours" else "$timeLeftMinutes mins"
                when {
                    priorityScore >= 7.5 -> {
                        "SUGGESTION" to "⚡ AI Suggestion: '${taskTitle}' is a top-priority task ($priorityScore/10.0) due in $displayTime. Plan your focus blocks ahead."
                    }
                    else -> {
                        "GENTLE" to "🌸 Gentle Heads-up: '${taskTitle}' (Priority $priorityScore) is due in $displayTime. There's plenty of time, but a slow start helps."
                    }
                }
            }
        }
        return Pair(category, text)
    }

    fun scheduleAlarmsForTask(context: Context, task: Task) {
        if (task.status == "COMPLETED") {
            cancelAlarmsForTask(context, task.id)
            return
        }

        val deadline = task.deadline
        val now = System.currentTimeMillis()
        val timeToDeadline = deadline - now

        if (timeToDeadline > 0) {
            // 1. Gentle Reminder: 1 hour before, or 50% of remaining time if less than 2 hours away
            val gentleTime = if (timeToDeadline > 3600_000 * 2) {
                deadline - 3600_000
            } else {
                now + (timeToDeadline / 2)
            }
            
            val gentleTimeLeft = deadline - gentleTime
            val (gentleCategory, gentleMessage) = getNotificationCategoryAndMessage(task.priorityScore, gentleTimeLeft, task.title)

            scheduleAlarm(
                context,
                task.id * 10 + 1,
                task.title,
                gentleTime,
                gentleCategory,
                gentleMessage
            )

            // 2. Urgent Reminder: 15 minutes before, or 80% of remaining time
            val urgentTime = if (timeToDeadline > 900_000 * 2) {
                deadline - 900_000
            } else {
                now + (timeToDeadline * 4 / 5)
            }

            val urgentTimeLeft = deadline - urgentTime
            val (urgentCategory, urgentMessage) = getNotificationCategoryAndMessage(task.priorityScore, urgentTimeLeft, task.title)

            scheduleAlarm(
                context,
                task.id * 10 + 2,
                task.title,
                urgentTime,
                urgentCategory,
                urgentMessage
            )
        }
    }

    fun cancelAlarmsForTask(context: Context, taskId: Int) {
        cancelAlarm(context, taskId * 10 + 1)
        cancelAlarm(context, taskId * 10 + 2)
    }

    private fun cancelAlarm(context: Context, uniqueId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java)
        
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }

        val pendingIntent = PendingIntent.getBroadcast(context, uniqueId, intent, pendingIntentFlags)
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled scheduled alarm uniqueId $uniqueId")
        }
    }
}
