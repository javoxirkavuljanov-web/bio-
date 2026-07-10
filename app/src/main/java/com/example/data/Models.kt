package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_tasks")
data class TodoTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val priority: String = "Medium", // High, Medium, Low
    val category: String = "Other", // Study, Coding, Home, Shopping, Personal, Other
    val isCompleted: Boolean = false,
    val isPinned: Boolean = false,
    val deadline: Long? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "schedule_tasks")
data class ScheduleTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int, // 1 = Monday, 7 = Sunday
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val title: String,
    val category: String, // Study, Coding, English, Prayer, Nap, Reading, Sleep, Run, Workout, Custom
    val isDefault: Boolean = true
)

@Entity(tableName = "schedule_task_completions", primaryKeys = ["taskId", "dateString"])
data class ScheduleTaskCompletion(
    val taskId: Long,
    val dateString: String // "YYYY-MM-DD"
)

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val emoji: String,
    val isDefault: Boolean = false,
    val frequency: String = "Daily", // Daily, Mon-Fri, Custom
    val customDays: String = "" // "1,2,3,4,5"
)

@Entity(tableName = "habit_completions", primaryKeys = ["habitId", "dateString"])
data class HabitCompletion(
    val habitId: Long,
    val dateString: String // "YYYY-MM-DD"
)

@Entity(tableName = "pomodoro_sessions")
data class PomodoroSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val durationMinutes: Int,
    val category: String = "Coding" // Coding, English, Reading, Other
)

@Entity(tableName = "prayer_time_cache")
data class PrayerTimeCache(
    @PrimaryKey val dateString: String, // "YYYY-MM-DD"
    val fajr: String,
    val sunrise: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val latitude: Double,
    val longitude: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)
