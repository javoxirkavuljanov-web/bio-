package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val THEME_KEY = stringPreferencesKey("theme") // "system", "light", "dark"
        val LANG_KEY = stringPreferencesKey("language") // "ru", "en", "uz"
        val NOTIFS_KEY = booleanPreferencesKey("notifications_enabled")
        val POMODORO_WORK_KEY = intPreferencesKey("pomodoro_work")
        val POMODORO_SHORT_BREAK_KEY = intPreferencesKey("pomodoro_short_break")
        val POMODORO_LONG_BREAK_KEY = intPreferencesKey("pomodoro_long_break")
        val WAKEUP_ALARM_ENABLED_KEY = booleanPreferencesKey("wakeup_alarm_enabled")
        val WAKEUP_ALARM_HOUR_KEY = intPreferencesKey("wakeup_alarm_hour")
        val WAKEUP_ALARM_MINUTE_KEY = intPreferencesKey("wakeup_alarm_minute")
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: "system"
    }

    val langFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANG_KEY] ?: "ru"
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFS_KEY] ?: true
    }

    val pomodoroWorkFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[POMODORO_WORK_KEY] ?: 25
    }

    val pomodoroShortBreakFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[POMODORO_SHORT_BREAK_KEY] ?: 5
    }

    val pomodoroLongBreakFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[POMODORO_LONG_BREAK_KEY] ?: 15
    }

    val wakeupAlarmEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WAKEUP_ALARM_ENABLED_KEY] ?: true
    }

    val wakeupAlarmHourFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[WAKEUP_ALARM_HOUR_KEY] ?: 4
    }

    val wakeupAlarmMinuteFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[WAKEUP_ALARM_MINUTE_KEY] ?: 30
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[LANG_KEY] = lang
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFS_KEY] = enabled
        }
    }

    suspend fun setPomodoroDurations(work: Int, shortBreak: Int, longBreak: Int) {
        context.dataStore.edit { preferences ->
            preferences[POMODORO_WORK_KEY] = work
            preferences[POMODORO_SHORT_BREAK_KEY] = shortBreak
            preferences[POMODORO_LONG_BREAK_KEY] = longBreak
        }
    }

    suspend fun setWakeupAlarmEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WAKEUP_ALARM_ENABLED_KEY] = enabled
        }
    }

    suspend fun setWakeupAlarmTime(hour: Int, minute: Int) {
        context.dataStore.edit { preferences ->
            preferences[WAKEUP_ALARM_HOUR_KEY] = hour
            preferences[WAKEUP_ALARM_MINUTE_KEY] = minute
        }
    }
}
