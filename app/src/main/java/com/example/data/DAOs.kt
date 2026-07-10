package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_tasks ORDER BY isPinned DESC, orderIndex ASC, createdAt DESC")
    fun getAllTasks(): Flow<List<TodoTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TodoTask): Long

    @Update
    suspend fun updateTask(task: TodoTask)

    @Delete
    suspend fun deleteTask(task: TodoTask)

    @Query("DELETE FROM todo_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT MAX(orderIndex) FROM todo_tasks")
    suspend fun getMaxOrderIndex(): Int?
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_tasks ORDER BY startHour ASC, startMinute ASC")
    fun getAllTasks(): Flow<List<ScheduleTask>>

    @Query("SELECT * FROM schedule_tasks WHERE dayOfWeek = :day ORDER BY startHour ASC, startMinute ASC")
    fun getTasksForDay(day: Int): Flow<List<ScheduleTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: ScheduleTask): Long

    @Delete
    suspend fun deleteTask(task: ScheduleTask)

    @Query("DELETE FROM schedule_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Completion queries
    @Query("SELECT * FROM schedule_task_completions WHERE dateString = :dateString")
    fun getCompletionsForDate(dateString: String): Flow<List<ScheduleTaskCompletion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: ScheduleTaskCompletion)

    @Delete
    suspend fun deleteCompletion(completion: ScheduleTaskCompletion)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits")
    fun getAllHabits(): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Delete
    suspend fun deleteHabit(habit: Habit)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Completion queries
    @Query("SELECT * FROM habit_completions WHERE dateString = :dateString")
    fun getCompletionsForDate(dateString: String): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions")
    fun getAllCompletions(): Flow<List<HabitCompletion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HabitCompletion)

    @Delete
    suspend fun deleteCompletion(completion: HabitCompletion)
}

@Dao
interface PomodoroDao {
    @Query("SELECT * FROM pomodoro_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<PomodoroSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PomodoroSession): Long
}

@Dao
interface PrayerDao {
    @Query("SELECT * FROM prayer_time_cache WHERE dateString = :dateString")
    suspend fun getCacheForDate(dateString: String): PrayerTimeCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: PrayerTimeCache)
}
