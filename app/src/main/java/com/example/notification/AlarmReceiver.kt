package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.model.EscalatingReminder
import com.example.data.repository.LifesaverRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("TASK_ID", -1)
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Task Alert"
        val message = intent.getStringExtra("MESSAGE") ?: "You have an upcoming deadline!"
        val alertLevel = intent.getStringExtra("ALERT_LEVEL") ?: "URGENT"

        Log.d("AlarmReceiver", "Received background alarm alert for task $taskId: $taskTitle")

        // 1. Show standard Android notification
        NotificationHelper.showNotification(context, "$alertLevel Alarm: $taskTitle", message, taskId)

        // 2. Insert into the local database so it is saved in the active Reminders panel
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(appContext)
                val repository = LifesaverRepository(
                    appContext,
                    database.taskDao(),
                    database.scheduleBlockDao(),
                    database.userProfileDao(),
                    database.escalatingReminderDao()
                )

                val reminder = EscalatingReminder(
                    taskId = if (taskId != -1) taskId else 1,
                    taskTitle = taskTitle,
                    message = message,
                    level = alertLevel,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertReminder(reminder)
                Log.d("AlarmReceiver", "Successfully saved background alert to local database.")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error saving reminder to database in background", e)
            }
        }
    }
}
