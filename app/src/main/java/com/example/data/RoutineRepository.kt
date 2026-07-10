package com.example.data

import android.content.Context
import com.example.util.PrayerTimesCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class RoutineRepository(
    private val db: AppDatabase,
    private val context: Context
) {
    private val todoDao = db.todoDao()
    private val scheduleDao = db.scheduleDao()
    private val habitDao = db.habitDao()
    private val pomodoroDao = db.pomodoroDao()
    private val prayerDao = db.prayerDao()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    init {
        // Run database seeding on start
        repositoryScope.launch {
            seedDefaultHabits()
            seedDefaultSchedule()
        }
    }

    // --- To-Do Tasks ---
    val allTodoTasks: Flow<List<TodoTask>> = todoDao.getAllTasks()

    suspend fun insertTodo(task: TodoTask): Long = withContext(Dispatchers.IO) {
        val maxIndex = todoDao.getMaxOrderIndex() ?: 0
        todoDao.insertTask(task.copy(orderIndex = maxIndex + 1))
    }

    suspend fun updateTodo(task: TodoTask) = withContext(Dispatchers.IO) {
        todoDao.updateTask(task)
    }

    suspend fun deleteTodo(task: TodoTask) = withContext(Dispatchers.IO) {
        todoDao.deleteTask(task)
    }

    suspend fun deleteTodoById(id: Long) = withContext(Dispatchers.IO) {
        todoDao.deleteById(id)
    }

    suspend fun updateTodoOrder(tasks: List<TodoTask>) = withContext(Dispatchers.IO) {
        tasks.forEachIndexed { index, task ->
            todoDao.updateTask(task.copy(orderIndex = index))
        }
    }


    // --- Schedule Tasks ---
    val allScheduleTasks: Flow<List<ScheduleTask>> = scheduleDao.getAllTasks()

    fun getScheduleTasksForDay(dayOfWeek: Int): Flow<List<ScheduleTask>> {
        return scheduleDao.getTasksForDay(dayOfWeek)
    }

    suspend fun insertScheduleTask(task: ScheduleTask): Long = withContext(Dispatchers.IO) {
        scheduleDao.insertTask(task)
    }

    suspend fun deleteScheduleTask(task: ScheduleTask) = withContext(Dispatchers.IO) {
        scheduleDao.deleteTask(task)
    }

    suspend fun deleteScheduleTaskById(id: Long) = withContext(Dispatchers.IO) {
        scheduleDao.deleteById(id)
    }


    // --- Schedule Completions ---
    fun getScheduleCompletionsForDate(dateString: String): Flow<List<ScheduleTaskCompletion>> {
        return scheduleDao.getCompletionsForDate(dateString)
    }

    suspend fun toggleScheduleTaskCompletion(taskId: Long, dateString: String) = withContext(Dispatchers.IO) {
        val completions = scheduleDao.getCompletionsForDate(dateString).first()
        val exists = completions.any { it.taskId == taskId }
        if (exists) {
            scheduleDao.deleteCompletion(ScheduleTaskCompletion(taskId, dateString))
        } else {
            scheduleDao.insertCompletion(ScheduleTaskCompletion(taskId, dateString))
        }
    }


    // --- Habits ---
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()
    val allHabitCompletions: Flow<List<HabitCompletion>> = habitDao.getAllCompletions()

    fun getHabitCompletionsForDate(dateString: String): Flow<List<HabitCompletion>> {
        return habitDao.getCompletionsForDate(dateString)
    }

    suspend fun insertHabit(habit: Habit): Long = withContext(Dispatchers.IO) {
        habitDao.insertHabit(habit)
    }

    suspend fun deleteHabit(habit: Habit) = withContext(Dispatchers.IO) {
        habitDao.deleteHabit(habit)
    }

    suspend fun deleteHabitById(id: Long) = withContext(Dispatchers.IO) {
        habitDao.deleteById(id)
    }

    suspend fun toggleHabitCompletion(habitId: Long, dateString: String) = withContext(Dispatchers.IO) {
        val completions = habitDao.getCompletionsForDate(dateString).first()
        val exists = completions.any { it.habitId == habitId }
        if (exists) {
            habitDao.deleteCompletion(HabitCompletion(habitId, dateString))
        } else {
            habitDao.insertCompletion(HabitCompletion(habitId, dateString))
        }
    }


    // --- Pomodoro ---
    val allPomodoroSessions: Flow<List<PomodoroSession>> = pomodoroDao.getAllSessions()

    suspend fun insertPomodoroSession(session: PomodoroSession): Long = withContext(Dispatchers.IO) {
        pomodoroDao.insertSession(session)
    }


    // --- Prayer Times (API + Offline Fallback) ---
    suspend fun getPrayerTimes(
        latitude: Double,
        longitude: Double,
        timezoneOffset: Double,
        dateString: String
    ): PrayerTimeCache = withContext(Dispatchers.IO) {
        // 1. Try local database cache
        val cached = prayerDao.getCacheForDate(dateString)
        if (cached != null && (System.currentTimeMillis() - cached.lastUpdated < 24 * 60 * 60 * 1000)) {
            return@withContext cached
        }

        // 2. Try Aladhan API
        val apiTimes = fetchPrayerTimesFromApi(latitude, longitude, dateString)
        if (apiTimes != null) {
            val cacheRecord = PrayerTimeCache(
                dateString = dateString,
                fajr = apiTimes["Fajr"] ?: "04:30",
                sunrise = apiTimes["Sunrise"] ?: "06:00",
                dhuhr = apiTimes["Dhuhr"] ?: "12:30",
                asr = apiTimes["Asr"] ?: "16:30",
                maghrib = apiTimes["Maghrib"] ?: "19:45",
                isha = apiTimes["Isha"] ?: "21:30",
                latitude = latitude,
                longitude = longitude
            )
            prayerDao.insertCache(cacheRecord)
            return@withContext cacheRecord
        }

        // 3. Fallback to 100% precise offline mathematical calculations
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        try {
            val parsedDate = sdf.parse(dateString)
            if (parsedDate != null) {
                calendar.time = parsedDate
            }
        } catch (e: Exception) {
            // Ignore
        }

        val calculated = PrayerTimesCalculator.calculate(latitude, longitude, timezoneOffset, calendar)
        val offlineRecord = PrayerTimeCache(
            dateString = dateString,
            fajr = calculated.fajr,
            sunrise = calculated.sunrise,
            dhuhr = calculated.dhuhr,
            asr = calculated.asr,
            maghrib = calculated.maghrib,
            isha = calculated.isha,
            latitude = latitude,
            longitude = longitude
        )
        prayerDao.insertCache(offlineRecord)
        return@withContext offlineRecord
    }

    private fun fetchPrayerTimesFromApi(
        latitude: Double,
        longitude: Double,
        dateString: String
    ): Map<String, String>? {
        return try {
            // Format date for Aladhan API: DD-MM-YYYY
            val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val apiSdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            val formattedDate = apiSdf.format(inputSdf.parse(dateString) ?: Date())

            val urlString = "https://api.aladhan.com/v1/timings/$formattedDate?latitude=$latitude&longitude=$longitude&method=2"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val data = json.getJSONObject("data")
                val timings = data.getJSONObject("timings")
                
                mapOf(
                    "Fajr" to timings.getString("Fajr"),
                    "Sunrise" to timings.getString("Sunrise"),
                    "Dhuhr" to timings.getString("Dhuhr"),
                    "Asr" to timings.getString("Asr"),
                    "Maghrib" to timings.getString("Maghrib"),
                    "Isha" to timings.getString("Isha")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    // --- Seeding Helpers ---
    private suspend fun seedDefaultHabits() {
        val count = habitDao.getAllHabits().first().size
        if (count == 0) {
            val defaults = listOf(
                Habit(title = "All 5 prayers on time", emoji = "🤲", isDefault = true),
                Habit(title = "Morning run + horizontal bars", emoji = "🏃", isDefault = true),
                Habit(title = "At least 4-5 hours of coding", emoji = "💻", isDefault = true),
                Habit(title = "English study (Mon-Fri 12:00-14:00)", emoji = "🇬🇧", isDefault = true, frequency = "Mon-Fri", customDays = "1,2,3,4,5"),
                Habit(title = "No phone during study blocks", emoji = "📱", isDefault = true),
                Habit(title = "Afternoon nap 30-45 minutes", emoji = "😴", isDefault = true),
                Habit(title = "Evening reading 20-30 minutes", emoji = "📖", isDefault = true),
                Habit(title = "Strict sleep at 22:00", emoji = "🌙", isDefault = true)
            )
            defaults.forEach { habitDao.insertHabit(it) }
        }
    }

    private suspend fun seedDefaultSchedule() {
        val count = scheduleDao.getAllTasks().first().size
        if (count == 0) {
            val tasks = mutableListOf<ScheduleTask>()
            // Days of week: 1 = Monday, 7 = Sunday
            for (day in 1..7) {
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 4, startMinute = 30, endHour = 5, endMinute = 0, title = "Fajr Prayer", category = "Prayer"))
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 6, startMinute = 30, endHour = 7, endMinute = 15, title = "Morning Run & Workout", category = "Run/Workout"))
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 8, startMinute = 30, endHour = 11, endMinute = 30, title = "Coding & Study Block 1", category = "Coding"))
                
                // English study is only Monday to Friday
                if (day in 1..5) {
                    tasks.add(ScheduleTask(dayOfWeek = day, startHour = 12, startMinute = 0, endHour = 14, endMinute = 0, title = "English Language Study", category = "English"))
                }
                
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 13, startMinute = 15, endHour = 13, endMinute = 45, title = "Dhuhr Prayer", category = "Prayer"))
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 14, startMinute = 30, endHour = 15, endMinute = 15, title = "Afternoon Nap", category = "Nap"))
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 15, startMinute = 30, endHour = 18, endMinute = 30, title = "Coding & Study Block 2", category = "Coding"))
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 17, startMinute = 0, endHour = 17, endMinute = 30, title = "Asr Prayer", category = "Prayer"))
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 19, startMinute = 45, endHour = 20, endMinute = 15, title = "Maghrib Prayer", category = "Prayer"))
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 21, startMinute = 0, endHour = 21, endMinute = 30, title = "Reading Book", category = "Reading"))
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 21, startMinute = 30, endHour = 22, endMinute = 0, title = "Isha Prayer", category = "Prayer"))
                tasks.add(ScheduleTask(dayOfWeek = day, startHour = 22, startMinute = 0, endHour = 4, endMinute = 30, title = "Strict Sleeping", category = "Sleep"))
            }
            tasks.forEach { scheduleDao.insertTask(it) }
        }
    }


    // --- Import / Export / Backup ---
    suspend fun exportDataJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        
        // Export Todos
        val todos = todoDao.getAllTasks().first()
        val todosArray = org.json.JSONArray()
        todos.forEach {
            val obj = JSONObject()
            obj.put("title", it.title)
            obj.put("notes", it.notes)
            obj.put("priority", it.priority)
            obj.put("category", it.category)
            obj.put("isCompleted", it.isCompleted)
            obj.put("isPinned", it.isPinned)
            obj.put("deadline", it.deadline ?: JSONObject.NULL)
            obj.put("orderIndex", it.orderIndex)
            todosArray.put(obj)
        }
        root.put("todos", todosArray)

        // Export Pomodoros
        val pomo = pomodoroDao.getAllSessions().first()
        val pomoArray = org.json.JSONArray()
        pomo.forEach {
            val obj = JSONObject()
            obj.put("timestamp", it.timestamp)
            obj.put("durationMinutes", it.durationMinutes)
            obj.put("category", it.category)
            pomoArray.put(obj)
        }
        root.put("pomodoros", pomoArray)

        // Export Habit Completions
        val habitComps = habitDao.getAllCompletions().first()
        val compsArray = org.json.JSONArray()
        habitComps.forEach {
            val obj = JSONObject()
            obj.put("habitId", it.habitId)
            obj.put("dateString", it.dateString)
            compsArray.put(obj)
        }
        root.put("habit_completions", compsArray)

        root.toString(2)
    }

    suspend fun importDataJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            
            // Clear existing and import
            if (root.has("todos")) {
                val todosArray = root.getJSONArray("todos")
                for (i in 0 until todosArray.length()) {
                    val obj = todosArray.getJSONObject(i)
                    val task = TodoTask(
                        title = obj.getString("title"),
                        notes = obj.optString("notes", ""),
                        priority = obj.optString("priority", "Medium"),
                        category = obj.optString("category", "Other"),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        isPinned = obj.optBoolean("isPinned", false),
                        deadline = if (obj.isNull("deadline")) null else obj.getLong("deadline"),
                        orderIndex = obj.optInt("orderIndex", 0)
                    )
                    todoDao.insertTask(task)
                }
            }

            if (root.has("pomodoros")) {
                val pomoArray = root.getJSONArray("pomodoros")
                for (i in 0 until pomoArray.length()) {
                    val obj = pomoArray.getJSONObject(i)
                    val session = PomodoroSession(
                        timestamp = obj.getLong("timestamp"),
                        durationMinutes = obj.getInt("durationMinutes"),
                        category = obj.optString("category", "Coding")
                    )
                    pomodoroDao.insertSession(session)
                }
            }

            if (root.has("habit_completions")) {
                val compsArray = root.getJSONArray("habit_completions")
                for (i in 0 until compsArray.length()) {
                    val obj = compsArray.getJSONObject(i)
                    val completion = HabitCompletion(
                        habitId = obj.getLong("habitId"),
                        dateString = obj.getString("dateString")
                    )
                    habitDao.insertCompletion(completion)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
