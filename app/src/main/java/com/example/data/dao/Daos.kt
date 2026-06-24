package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.EscalatingReminder
import com.example.data.model.ScheduleBlock
import com.example.data.model.Task
import com.example.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY deadline ASC")
    fun getAllTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Int): Task?

    @Query("SELECT * FROM tasks WHERE status != 'COMPLETED' ORDER BY deadline ASC")
    suspend fun getActiveTasks(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)
}

@Dao
interface ScheduleBlockDao {
    @Query("SELECT * FROM schedule_blocks ORDER BY startTime ASC")
    fun getAllScheduleBlocksFlow(): Flow<List<ScheduleBlock>>

    @Query("SELECT * FROM schedule_blocks ORDER BY startTime ASC")
    suspend fun getAllScheduleBlocks(): List<ScheduleBlock>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlock(block: ScheduleBlock): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<ScheduleBlock>)

    @Query("DELETE FROM schedule_blocks")
    suspend fun clearAllBlocks()

    @Delete
    suspend fun deleteBlock(block: ScheduleBlock)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)
}

@Dao
interface EscalatingReminderDao {
    @Query("SELECT * FROM escalating_reminders WHERE isDismissed = 0 ORDER BY timestamp DESC")
    fun getActiveRemindersFlow(): Flow<List<EscalatingReminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: EscalatingReminder): Long

    @Query("UPDATE escalating_reminders SET isDismissed = 1 WHERE id = :id")
    suspend fun dismissReminder(id: Int)

    @Query("DELETE FROM escalating_reminders")
    suspend fun clearAllReminders()
}
