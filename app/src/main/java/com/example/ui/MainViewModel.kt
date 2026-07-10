package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.util.NotificationHelper
import com.example.util.NotificationWorker
import com.example.util.PrayerTimesCalculator
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getDatabase(context)
    val repository = RoutineRepository(db, context)
    private val settingsManager = SettingsManager(context)

    // --- State: Date and Navigation ---
    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _currentRoute = MutableStateFlow("home")
    val currentRoute: StateFlow<String> = _currentRoute.asStateFlow()

    // --- State: User Settings (DataStore) ---
    val appTheme: StateFlow<String> = settingsManager.themeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val appLanguage: StateFlow<String> = settingsManager.langFlow.stateIn(viewModelScope, SharingStarted.Eagerly, "ru")
    val notificationsEnabled: StateFlow<Boolean> = settingsManager.notificationsEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val wakeupAlarmEnabled: StateFlow<Boolean> = settingsManager.wakeupAlarmEnabledFlow.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val wakeupAlarmHour: StateFlow<Int> = settingsManager.wakeupAlarmHourFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 4)
    val wakeupAlarmMinute: StateFlow<Int> = settingsManager.wakeupAlarmMinuteFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    val pomoWorkMin: StateFlow<Int> = settingsManager.pomodoroWorkFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 25)
    val pomoShortBreakMin: StateFlow<Int> = settingsManager.pomodoroShortBreakFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 5)
    val pomoLongBreakMin: StateFlow<Int> = settingsManager.pomodoroLongBreakFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 15)

    // --- State: To-Do List ---
    private val _todoSearchQuery = MutableStateFlow("")
    val todoSearchQuery: StateFlow<String> = _todoSearchQuery.asStateFlow()

    private val _todoCategoryFilter = MutableStateFlow("All")
    val todoCategoryFilter: StateFlow<String> = _todoCategoryFilter.asStateFlow()

    private val _todoPriorityFilter = MutableStateFlow("All")
    val todoPriorityFilter: StateFlow<String> = _todoPriorityFilter.asStateFlow()

    val filteredTodos: StateFlow<List<TodoTask>> = combine(
        repository.allTodoTasks,
        _todoSearchQuery,
        _todoCategoryFilter,
        _todoPriorityFilter
    ) { tasks, query, cat, priority ->
        tasks.filter { task ->
            val matchQuery = task.title.contains(query, ignoreCase = true) || task.notes.contains(query, ignoreCase = true)
            val matchCat = cat == "All" || task.category == cat
            val matchPriority = priority == "All" || task.priority == priority
            matchQuery && matchCat && matchPriority
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- State: Daily Routines & Completions for Selected Date ---
    val currentDayOfWeek: StateFlow<Int> = _selectedDate.map { dateStr ->
        getDayOfWeekFromDateString(dateStr)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    val scheduleTasksForSelectedDay: StateFlow<List<ScheduleTask>> = currentDayOfWeek.flatMapLatest { day ->
        repository.getScheduleTasksForDay(day)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleCompletionsForSelectedDay: StateFlow<List<ScheduleTaskCompletion>> = _selectedDate.flatMapLatest { date ->
        repository.getScheduleCompletionsForDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- State: Habits for Selected Date ---
    val habitsForSelectedDay: StateFlow<List<Habit>> = combine(
        repository.allHabits,
        currentDayOfWeek
    ) { habits, day ->
        habits.filter { habit ->
            if (habit.frequency == "Mon-Fri") {
                day in 1..5
            } else {
                true
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitCompletionsForSelectedDay: StateFlow<List<HabitCompletion>> = _selectedDate.flatMapLatest { date ->
        repository.getHabitCompletionsForDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    // --- State: Pomodoro Timer ---
    private val _pomoTimeRemaining = MutableStateFlow(25 * 60)
    val pomoTimeRemaining: StateFlow<Int> = _pomoTimeRemaining.asStateFlow()

    private val _pomoState = MutableStateFlow("stopped") // stopped, running, paused
    val pomoState: StateFlow<String> = _pomoState.asStateFlow()

    private val _pomoIsBreak = MutableStateFlow(false)
    val pomoIsBreak: StateFlow<Boolean> = _pomoIsBreak.asStateFlow()

    private val _pomoCompletedCountToday = MutableStateFlow(0)
    val pomoCompletedCountToday: StateFlow<Int> = _pomoCompletedCountToday.asStateFlow()

    private var pomoJob: Job? = null

    // --- State: Location and Prayer Times ---
    private val _latitude = MutableStateFlow(41.2995) // Default Tashkent
    val latitude: StateFlow<Double> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow(69.2401)
    val longitude: StateFlow<Double> = _longitude.asStateFlow()

    private val _isGpsActive = MutableStateFlow(false)
    val isGpsActive: StateFlow<Boolean> = _isGpsActive.asStateFlow()

    private val _prayerTimesState = MutableStateFlow<PrayerTimeCache?>(null)
    val prayerTimesState: StateFlow<PrayerTimeCache?> = _prayerTimesState.asStateFlow()

    private val _nextPrayerName = MutableStateFlow("Fajr")
    val nextPrayerName: StateFlow<String> = _nextPrayerName.asStateFlow()

    private val _nextPrayerTimeRemaining = MutableStateFlow("")
    val nextPrayerTimeRemaining: StateFlow<String> = _nextPrayerTimeRemaining.asStateFlow()


    // --- Live System Time (Home) ---
    private val _currentTimeString = MutableStateFlow("12:00:00")
    val currentTimeString: StateFlow<String> = _currentTimeString.asStateFlow()


    // --- State: Activity Graph (Heatmap) and Stats ---
    private val _allHabitCompletions = repository.allHabitCompletions
    private val _allTodoTasks = repository.allTodoTasks
    private val _allScheduleTasks = repository.allScheduleTasks

    private val _heatmapSelectedDayDetails = MutableStateFlow<HeatmapDayDetails?>(null)
    val heatmapSelectedDayDetails: StateFlow<HeatmapDayDetails?> = _heatmapSelectedDayDetails.asStateFlow()

    data class HeatmapDayDetails(
        val dateString: String,
        val completionPercentage: Int,
        val tasksCompleted: Int,
        val tasksMissed: Int,
        val hoursCoding: Double,
        val hoursEnglish: Double,
        val pomodorosCount: Int,
        val allPrayersCompleted: Boolean
    )

    data class HeatmapDay(
        val dateString: String,
        val completionPct: Int, // 0 to 100
        val dayOfWeekIndex: Int, // 0 = Mon, 6 = Sun
        val weekIndex: Int // 0 to 11 (12-column grid)
    )

    val heatmapData: StateFlow<List<HeatmapDay>> = combine(
        _allHabitCompletions,
        repository.allScheduleTasks,
        db.scheduleDao().getAllTasks(), // Flow to keep reactive
        _allTodoTasks
    ) { completions, schedTasks, _, todoTasks ->
        calculateHeatmapData()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitsCompletionStreak: StateFlow<Int> = _allHabitCompletions.map {
        calculateStreak()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)


    init {
        // Run background loops
        viewModelScope.launch {
            // Live clock update
            while (isActive) {
                val cal = Calendar.getInstance()
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
                _currentTimeString.value = sdf.format(cal.time)
                
                // Update prayer countdown
                updatePrayerCountdown()
                
                delay(1000)
            }
        }

        // Keep Pomodoro timer starting value synchronized with Settings DataStore
        viewModelScope.launch {
            combine(pomoWorkMin, pomoShortBreakMin, pomoIsBreak) { work, brk, isBrk ->
                if (_pomoState.value == "stopped") {
                    _pomoTimeRemaining.value = if (isBrk) brk * 60 else work * 60
                }
            }.collect()
        }

        // Refresh prayer times on startup and coordinates change
        viewModelScope.launch {
            combine(_latitude, _longitude, _selectedDate) { lat, lng, date ->
                loadPrayerTimes(lat, lng, date)
            }.collect()
        }

        // Register WorkManager triggers based on schedules
        viewModelScope.launch {
            combine(repository.allScheduleTasks, notificationsEnabled) { tasks, enabled ->
                NotificationHelper.scheduleRoutineAlerts(context, tasks, enabled)
            }.collect()
        }

        // Initialize/reschedule wakeup alarm when settings change or on startup
        viewModelScope.launch {
            combine(wakeupAlarmEnabled, wakeupAlarmHour, wakeupAlarmMinute) { enabled, hour, min ->
                com.example.util.AlarmHelper.scheduleWakeupAlarm(context, hour, min, enabled)
            }.collect()
        }
    }

    // --- Helper date calculations ---
    fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun getDayOfWeekFromDateString(dateStr: String): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        try {
            val d = sdf.parse(dateStr)
            if (d != null) cal.time = d
        } catch (e: Exception) {
            // ignore
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }


    // --- Controller: Navigation & Date Select ---
    fun selectDate(dateString: String) {
        _selectedDate.value = dateString
    }

    fun navigateTo(route: String) {
        _currentRoute.value = route
    }


    // --- Controller: Settings ---
    fun setTheme(theme: String) {
        viewModelScope.launch { settingsManager.setTheme(theme) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { settingsManager.setLanguage(lang) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setNotificationsEnabled(enabled) }
    }

    fun setWakeupAlarmEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setWakeupAlarmEnabled(enabled) }
    }

    fun setWakeupAlarmTime(hour: Int, minute: Int) {
        viewModelScope.launch { settingsManager.setWakeupAlarmTime(hour, minute) }
    }

    fun setPomodoroDurations(work: Int, short: Int, long: Int) {
        viewModelScope.launch {
            settingsManager.setPomodoroDurations(work, short, long)
            if (_pomoState.value == "stopped") {
                _pomoTimeRemaining.value = if (_pomoIsBreak.value) short * 60 else work * 60
            }
        }
    }


    // --- Controller: To-Do Tasks ---
    fun setSearchQuery(query: String) {
        _todoSearchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        _todoCategoryFilter.value = category
    }

    fun setPriorityFilter(priority: String) {
        _todoPriorityFilter.value = priority
    }

    fun addTodo(title: String, notes: String, priority: String, category: String, deadline: Long? = null) {
        viewModelScope.launch {
            val task = TodoTask(
                title = title,
                notes = notes,
                priority = priority,
                category = category,
                deadline = deadline
            )
            repository.insertTodo(task)
        }
    }

    fun toggleTodoCompleted(task: TodoTask) {
        viewModelScope.launch {
            repository.updateTodo(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun toggleTodoPinned(task: TodoTask) {
        viewModelScope.launch {
            repository.updateTodo(task.copy(isPinned = !task.isPinned))
        }
    }

    fun deleteTodoTask(task: TodoTask) {
        viewModelScope.launch {
            repository.deleteTodo(task)
        }
    }

    fun reorderTodos(reordered: List<TodoTask>) {
        viewModelScope.launch {
            repository.updateTodoOrder(reordered)
        }
    }


    // --- Controller: Routine Schedule Completion ---
    fun toggleScheduleTask(taskId: Long) {
        viewModelScope.launch {
            repository.toggleScheduleTaskCompletion(taskId, _selectedDate.value)
        }
    }


    // --- Controller: Habit Completion ---
    fun toggleHabit(habitId: Long) {
        viewModelScope.launch {
            repository.toggleHabitCompletion(habitId, _selectedDate.value)
        }
    }

    fun addCustomHabit(title: String, emoji: String) {
        viewModelScope.launch {
            repository.insertHabit(Habit(title = title, emoji = emoji, isDefault = false))
        }
    }

    fun deleteCustomHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }


    // --- Controller: Pomodoro Timer Logic ---
    fun startPomodoro() {
        if (pomoJob != null) return
        _pomoState.value = "running"
        pomoJob = viewModelScope.launch {
            while (_pomoTimeRemaining.value > 0) {
                delay(1000)
                _pomoTimeRemaining.value -= 1
            }
            onPomodoroFinished()
        }
    }

    fun pausePomodoro() {
        pomoJob?.cancel()
        pomoJob = null
        _pomoState.value = "paused"
    }

    fun resetPomodoro() {
        pomoJob?.cancel()
        pomoJob = null
        _pomoState.value = "stopped"
        _pomoTimeRemaining.value = if (_pomoIsBreak.value) pomoShortBreakMin.value * 60 else pomoWorkMin.value * 60
    }

    private suspend fun onPomodoroFinished() {
        pomoJob = null
        _pomoState.value = "stopped"

        if (!_pomoIsBreak.value) {
            // Completed a work session
            _pomoCompletedCountToday.value += 1
            repository.insertPomodoroSession(
                PomodoroSession(
                    timestamp = System.currentTimeMillis(),
                    durationMinutes = pomoWorkMin.value,
                    category = "Coding"
                )
            )

            // Auto transition to breaks
            if (_pomoCompletedCountToday.value % 4 == 0) {
                // Time for long break
                _pomoIsBreak.value = true
                _pomoTimeRemaining.value = pomoLongBreakMin.value * 60
            } else {
                _pomoIsBreak.value = true
                _pomoTimeRemaining.value = pomoShortBreakMin.value * 60
            }
        } else {
            // Finished a break
            _pomoIsBreak.value = false
            _pomoTimeRemaining.value = pomoWorkMin.value * 60
        }

        // Auto trigger alerts
        if (notificationsEnabled.value) {
            val title = if (_pomoIsBreak.value) "Break Time!" else "Focus Time!"
            val text = if (_pomoIsBreak.value) "Take a well-deserved rest!" else "Let's code & focus!"
            val data = Data.Builder().putString("title", title).putString("message", text).build()
            val req = OneTimeWorkRequestBuilder<NotificationWorker>().setInputData(data).build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }


    // --- Controller: Prayer Times GPS Loader ---
    fun updateLocation(lat: Double, lng: Double) {
        _latitude.value = lat
        _longitude.value = lng
        _isGpsActive.value = true
    }

    private suspend fun loadPrayerTimes(lat: Double, lng: Double, date: String) {
        val tzone = TimeZone.getDefault()
        val offsetHours = tzone.getOffset(System.currentTimeMillis()) / 3600000.0
        val times = repository.getPrayerTimes(lat, lng, offsetHours, date)
        _prayerTimesState.value = times
    }

    private fun updatePrayerCountdown() {
        val times = _prayerTimesState.value ?: return
        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val s = now.get(Calendar.SECOND)
        val currentSeconds = h * 3600 + m * 60 + s

        val parsedPrayers = listOf(
            "Fajr" to parseTime(times.fajr),
            "Dhuhr" to parseTime(times.dhuhr),
            "Asr" to parseTime(times.asr),
            "Maghrib" to parseTime(times.maghrib),
            "Isha" to parseTime(times.isha)
        )

        // Find next prayer today
        var next = parsedPrayers.firstOrNull { it.second > currentSeconds }
        var isNextDay = false

        if (next == null) {
            // Next is Fajr tomorrow
            next = parsedPrayers.first()
            isNextDay = true
        }

        _nextPrayerName.value = next.first
        val nextSec = next.second
        val diffSeconds = if (isNextDay) {
            (24 * 3600 - currentSeconds) + nextSec
        } else {
            nextSec - currentSeconds
        }

        val hours = diffSeconds / 3600
        val minutes = (diffSeconds % 3600) / 60
        val secs = diffSeconds % 60
        _nextPrayerTimeRemaining.value = String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun parseTime(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size >= 2) {
            val hh = parts[0].toIntOrNull() ?: 0
            val mm = parts[1].toIntOrNull() ?: 0
            return hh * 3600 + mm * 60
        }
        return 0
    }


    // --- Controller: Heatmap Grid Details Clicking ---
    fun selectHeatmapDay(dateString: String) {
        viewModelScope.launch {
            val habitsList = repository.allHabits.first()
            val compsList = repository.allHabitCompletions.first().filter { it.dateString == dateString }
            val scheduleTasksList = repository.getScheduleTasksForDay(getDayOfWeekFromDateString(dateString)).first()
            val schedComps = repository.getScheduleCompletionsForDate(dateString).first()
            val pomoToday = repository.allPomodoroSessions.first().filter {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.format(Date(it.timestamp)) == dateString
            }

            val totalTasks = habitsList.size + scheduleTasksList.size
            val completedCount = compsList.size + schedComps.size
            val pct = if (totalTasks > 0) (completedCount * 100) / totalTasks else 0

            val hoursCoding = pomoToday.filter { it.category == "Coding" }.sumOf { it.durationMinutes }.toDouble() / 60.0
            val hoursEnglish = pomoToday.filter { it.category == "English" }.sumOf { it.durationMinutes }.toDouble() / 60.0

            val allPrayersDone = schedComps.any { comp ->
                val task = scheduleTasksList.find { it.id == comp.taskId }
                task?.category == "Prayer"
            }

            _heatmapSelectedDayDetails.value = HeatmapDayDetails(
                dateString = dateString,
                completionPercentage = pct,
                tasksCompleted = completedCount,
                tasksMissed = maxOf(0, totalTasks - completedCount),
                hoursCoding = hoursCoding,
                hoursEnglish = hoursEnglish,
                pomodorosCount = pomoToday.size,
                allPrayersCompleted = allPrayersDone
            )
        }
    }


    // --- Calculations: Heatmap and Streaks ---
    private suspend fun calculateHeatmapData(): List<HeatmapDay> {
        val heatmap = mutableListOf<HeatmapDay>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        val habitsList = repository.allHabits.first()
        val allCompletions = repository.allHabitCompletions.first()
        val allSchedCompletions = db.scheduleDao().getCompletionsForDate("").first() // wait, query all by empty or fetch directly

        // Let's generate last 12 weeks of data (84 days) ending today
        cal.add(Calendar.DAY_OF_YEAR, -83)
        // Align to starting Monday of that week
        val diffToMon = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        cal.add(Calendar.DAY_OF_YEAR, -diffToMon)

        for (week in 0..11) {
            for (day in 0..6) {
                val dateStr = sdf.format(cal.time)
                val dayOfWeekVal = getDayOfWeekFromDateString(dateStr)
                
                // Fetch schedule tasks count for that day of week
                val schedTasksCount = db.scheduleDao().getTasksForDay(dayOfWeekVal).first().size
                val habitsActiveCount = habitsList.filter {
                    if (it.frequency == "Mon-Fri") dayOfWeekVal in 1..5 else true
                }.size

                val totalExpected = schedTasksCount + habitsActiveCount

                val completedHabitsCount = allCompletions.filter { it.dateString == dateStr }.size
                val completedSchedCount = db.scheduleDao().getCompletionsForDate(dateStr).first().size
                val totalCompleted = completedHabitsCount + completedSchedCount

                val pct = if (totalExpected > 0) (totalCompleted * 100) / totalExpected else 0

                heatmap.add(
                    HeatmapDay(
                        dateString = dateStr,
                        completionPct = pct,
                        dayOfWeekIndex = day,
                        weekIndex = week
                    )
                )
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return heatmap
    }

    private suspend fun calculateStreak(): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val completions = repository.allHabitCompletions.first()
        val habits = repository.allHabits.first()
        if (habits.isEmpty()) return 0

        var streak = 0
        while (true) {
            val dateStr = sdf.format(cal.time)
            val dayOfWeekVal = getDayOfWeekFromDateString(dateStr)
            val habitsToComplete = habits.filter {
                if (it.frequency == "Mon-Fri") dayOfWeekVal in 1..5 else true
            }

            if (habitsToComplete.isEmpty()) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                continue
            }

            val completedOnDay = completions.filter { it.dateString == dateStr }.map { it.habitId }.toSet()
            val allCompleted = habitsToComplete.all { completedOnDay.contains(it.id) }

            if (allCompleted) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                // Streak is broken
                break
            }
        }
        return streak
    }
}
