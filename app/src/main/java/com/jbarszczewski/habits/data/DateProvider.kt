package com.jbarszczewski.habits.data

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The one and only source of "what is today" in the app.
 *
 * The user can move the start of their day (e.g. 4:00 so that finishing a task at 1 am still
 * counts for the previous date). [today] applies that offset; nothing else in the app may
 * call `LocalDate.now()` directly.
 *
 * [clock] is injectable so tests can pin the current time with `Clock.fixed(...)`.
 * [dayStartHour] is a function rather than a value so the settings screen can back it with a
 * cached DataStore preference later without changing this class.
 */
class DateProvider(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val dayStartHour: () -> Int = { DEFAULT_DAY_START_HOUR },
) {
    /** The logical local date right now, taking the configured day-start hour into account. */
    fun today(): LocalDate = logicalDate(LocalDateTime.now(clock))

    /** Current epoch millis; used for `timer_started_at` and elapsed-time maths. */
    fun nowMillis(): Long = clock.millis()

    /** Maps a wall-clock date-time onto the logical date it belongs to. */
    fun logicalDate(dateTime: LocalDateTime): LocalDate {
        val startHour = dayStartHour().coerceIn(0, 23)
        return if (dateTime.hour < startHour) dateTime.toLocalDate().minusDays(1) else dateTime.toLocalDate()
    }

    /**
     * Millis from now until the logical date next changes, i.e. tomorrow at the day-start hour.
     * Used to schedule the widget's day-rollover refresh.
     */
    fun millisUntilNextDayStart(): Long {
        val startHour = dayStartHour().coerceIn(0, 23)
        val nextStart = today().plusDays(1).atTime(startHour, 0).atZone(clock.zone).toInstant()
        return (nextStart.toEpochMilli() - clock.millis()).coerceAtLeast(0)
    }

    companion object {
        const val DEFAULT_DAY_START_HOUR = 0
    }
}
