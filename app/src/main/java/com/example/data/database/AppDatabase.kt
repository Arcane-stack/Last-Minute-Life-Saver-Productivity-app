package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.EscalatingReminderDao
import com.example.data.dao.ScheduleBlockDao
import com.example.data.dao.TaskDao
import com.example.data.dao.UserProfileDao
import com.example.data.model.EscalatingReminder
import com.example.data.model.ScheduleBlock
import com.example.data.model.Task
import com.example.data.model.UserProfile

@Database(
    entities = [
        Task::class,
        ScheduleBlock::class,
        UserProfile::class,
        EscalatingReminder::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun scheduleBlockDao(): ScheduleBlockDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun escalatingReminderDao(): EscalatingReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "last_minute_lifesaver_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
