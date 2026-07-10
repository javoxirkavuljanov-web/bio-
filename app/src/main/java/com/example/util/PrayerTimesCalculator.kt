package com.example.util

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

object PrayerTimesCalculator {

    data class PrayerTimes(
        val fajr: String,
        val sunrise: String,
        val dhuhr: String,
        val asr: String,
        val maghrib: String,
        val isha: String
    )

    fun calculate(
        latitude: Double,
        longitude: Double,
        timezoneOffset: Double,
        calendar: Calendar = Calendar.getInstance()
    ): PrayerTimes {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        // 1. Calculate Day of Year
        val d = getDayOfYear(year, month, day)

        // 2. Solar equations
        val g = 357.529 + 0.98560028 * d
        val q = 280.459 + 0.98564736 * d
        val L = q + 1.915 * sin(Math.toRadians(g)) + 0.020 * sin(Math.toRadians(2 * g))
        
        val e = 23.439 - 0.00000036 * d
        val declination = Math.toDegrees(asin(sin(Math.toRadians(e)) * sin(Math.toRadians(L))))

        // 3. Equation of Time (in minutes)
        val b = 2 * PI * (d - 81) / 365
        val equationOfTime = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)

        // 4. Local Noon (Dhuhr)
        val dhuhrTime = 12.0 - (longitude / 15.0) + timezoneOffset - (equationOfTime / 60.0)

        // Radian conversions for latitude and declination
        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(declination)

        // 5. Hour Angles
        // Fajr (standard angle is -18.0 or -15.0, let's use -18.0)
        val fajrAngle = -18.0
        val fajrHA = getHourAngle(fajrAngle, latRad, decRad)
        val fajrTime = if (fajrHA != null) dhuhrTime - fajrHA / 15.0 else dhuhrTime - 1.5

        // Sunrise (angle -0.833)
        val sunriseAngle = -0.833
        val sunriseHA = getHourAngle(sunriseAngle, latRad, decRad)
        val sunriseTime = if (sunriseHA != null) dhuhrTime - sunriseHA / 15.0 else dhuhrTime - 1.0

        // Asr (Shafi'i shadow ratio = 1.0, Hanafi = 2.0. Let's use 1.0)
        val asrShadowRatio = 1.0
        val asrAngleRad = atan(1.0 / (asrShadowRatio + tan(abs(latRad - decRad))))
        val asrAngle = Math.toDegrees(asrAngleRad)
        val asrHA = getHourAngle(asrAngle, latRad, decRad)
        val asrTime = if (asrHA != null) dhuhrTime + asrHA / 15.0 else dhuhrTime + 1.5

        // Maghrib (Sunset, angle -0.833)
        val maghribTime = if (sunriseHA != null) dhuhrTime + sunriseHA / 15.0 else dhuhrTime + 1.0

        // Isha (standard angle is -17.0 or -18.0, let's use -17.0 or Fajr's equivalent)
        val ishaAngle = -17.0
        val ishaHA = getHourAngle(ishaAngle, latRad, decRad)
        val ishaTime = if (ishaHA != null) dhuhrTime + ishaHA / 15.0 else dhuhrTime + 1.5

        return PrayerTimes(
            fajr = formatTime(fajrTime),
            sunrise = formatTime(sunriseTime),
            dhuhr = formatTime(dhuhrTime),
            asr = formatTime(asrTime),
            maghrib = formatTime(maghribTime),
            isha = formatTime(ishaTime)
        )
    }

    private fun getDayOfYear(year: Int, month: Int, day: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day)
        return cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun getHourAngle(angle: Double, latRad: Double, decRad: Double): Double? {
        val angleRad = Math.toRadians(angle)
        val cosH = (sin(angleRad) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if (cosH < -1.0 || cosH > 1.0) return null
        return Math.toDegrees(acos(cosH))
    }

    private fun formatTime(hours: Double): String {
        var h = hours
        if (h < 0) h += 24.0
        if (h >= 24) h -= 24.0

        val totalMinutes = (h * 60.0).roundToInt()
        val hour = (totalMinutes / 60) % 24
        val minute = totalMinutes % 60
        return String.format("%02d:%02d", hour, minute)
    }
}
