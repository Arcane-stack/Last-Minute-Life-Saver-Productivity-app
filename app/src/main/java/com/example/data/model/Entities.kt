package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "tasks")
@JsonClass(generateAdapter = true)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val deadline: Long, // Epoch timestamp in milliseconds
    val priorityScore: Double = 0.0,
    val status: String = "PENDING", // PENDING, IN_PROGRESS, COMPLETED
    val estimatedTimeMinutes: Int = 30,
    val microStepsJson: String = "[]", // Serialized list of MicroStep
    val isEmergencyPlanGenerated: Boolean = false,
    val energyRequired: String = "MEDIUM" // HIGH, MEDIUM, LOW
) {
    val isOverdue: Boolean
        get() = deadline < System.currentTimeMillis() && status != "COMPLETED"
}

@JsonClass(generateAdapter = true)
data class MicroStep(
    val title: String,
    var isCompleted: Boolean = false
)

@Entity(tableName = "schedule_blocks")
data class ScheduleBlock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int? = null,
    val taskTitle: String,
    val startTime: Long, // Epoch timestamp in milliseconds
    val endTime: Long, // Epoch timestamp in milliseconds
    val label: String // "Focus Work", "Break Activity", "Adaptive Buffer"
)

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Singleton row
    val name: String = "Life Saver User",
    val energyLevel: String = "MEDIUM", // HIGH, MEDIUM, LOW
    val workingHoursStart: Int = 9, // 9 AM (hour of day 0-23)
    val workingHoursEnd: Int = 18, // 6 PM
    val procrastinationScore: Double = 0.4, // 0.0 (diligent) to 1.0 (chronic)
    val totalTasksCompleted: Int = 0,
    val totalDeadlinesMissed: Int = 0
)

@Entity(tableName = "escalating_reminders")
data class EscalatingReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int,
    val taskTitle: String,
    val message: String,
    val level: String, // GENTLE, SUGGESTION, URGENT, RESCUE
    val timestamp: Long,
    val isDismissed: Boolean = false
)
