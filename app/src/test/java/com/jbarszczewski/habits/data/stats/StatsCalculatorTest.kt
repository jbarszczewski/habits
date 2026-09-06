package com.jbarszczewski.habits.data.stats

import com.jbarszczewski.habits.data.Completion
import com.jbarszczewski.habits.data.CompletionStatus
import com.jbarszczewski.habits.data.DaysMask
import com.jbarszczewski.habits.data.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Calendar used throughout (2026):
 *   Mon 08-31, Tue 09-01, Wed 09-02, Thu 09-03, Fri 09-04, Sat 09-05, Sun 09-06 (= "today")
 */
class StatsCalculatorTest {

    private val today: LocalDate = LocalDate.parse("2026-09-06") // Sunday

    private fun d(s: String) = LocalDate.parse(s)

    private fun task(
        createdAt: String,
        daysMask: Int = DaysMask.EVERY_DAY,
        targetMinutes: Int? = null,
        archivedAt: String? = null,
    ) = Task(
        id = 1,
        name = "test",
        daysMask = daysMask,
        targetMinutes = targetMinutes,
        createdAt = d(createdAt),
        archivedAt = archivedAt?.let(::d),
    )

    private fun done(date: String, minutes: Int? = null) =
        Completion(taskId = 1, date = d(date), status = CompletionStatus.DONE, actualMinutes = minutes)

    private fun partial(date: String, minutes: Int) =
        Completion(taskId = 1, date = d(date), status = CompletionStatus.PARTIAL, actualMinutes = minutes)

    private fun skipped(date: String) =
        Completion(taskId = 1, date = d(date), status = CompletionStatus.SKIPPED)

    private val mwf = DaysMask.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    // ------------------------------------------------------------ streaks

    @Test
    fun `a miss breaks the streak and today pending is ignored`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-08-31"),
            listOf(done("2026-08-31"), done("2026-09-01"), done("2026-09-02"), done("2026-09-04"), done("2026-09-05")),
            today,
        )
        assertEquals(2, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
        assertEquals(5, stats.doneCount)
        assertEquals(1, stats.missedCount)
        assertEquals(0, stats.partialCount)
        assertEquals(0, stats.skippedCount)
        assertEquals(5.0 / 6.0, stats.strictCompletionRate, 1e-9)
        assertEquals(5.0 / 6.0, stats.weightedCompletionRate, 1e-9)
    }

    @Test
    fun `today done extends the current streak`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-04"),
            listOf(done("2026-09-04"), done("2026-09-05"), done("2026-09-06")),
            today,
        )
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
        assertEquals(1.0, stats.strictCompletionRate, 1e-9)
    }

    @Test
    fun `a skipped day neither breaks nor extends the streak`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-01"),
            listOf(done("2026-09-01"), done("2026-09-02"), skipped("2026-09-03"), done("2026-09-04"), done("2026-09-05")),
            today,
        )
        assertEquals(4, stats.currentStreak)
        assertEquals(4, stats.longestStreak)
        assertEquals(1, stats.skippedCount)
        assertEquals(4, stats.doneCount)
        assertEquals(0, stats.missedCount)
        assertEquals(1.0, stats.strictCompletionRate, 1e-9) // skipped days are not in the denominator
    }

    @Test
    fun `a partial day breaks the streak but earns weighted credit`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-01", targetMinutes = 30),
            listOf(
                done("2026-09-01", 30), done("2026-09-02", 40),
                partial("2026-09-03", 15),
                done("2026-09-04", 30), done("2026-09-05", 30),
            ),
            today,
        )
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
        assertEquals(4, stats.doneCount)
        assertEquals(1, stats.partialCount)
        assertEquals(4.0 / 5.0, stats.strictCompletionRate, 1e-9)
        assertEquals((1 + 1 + 0.5 + 1 + 1) / 5.0, stats.weightedCompletionRate, 1e-9)
    }

    @Test
    fun `current streak is zero when yesterday was missed`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-01"),
            listOf(done("2026-09-01"), done("2026-09-02"), done("2026-09-03"), done("2026-09-04")),
            today,
        )
        assertEquals(0, stats.currentStreak) // 09-05 missed
        assertEquals(4, stats.longestStreak)
        assertEquals(1, stats.missedCount)
    }

    @Test
    fun `current streak is zero when today is partial`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-04", targetMinutes = 30),
            listOf(done("2026-09-04", 30), done("2026-09-05", 30), partial("2026-09-06", 10)),
            today,
        )
        assertEquals(0, stats.currentStreak)
        assertEquals(2, stats.longestStreak)
    }

    // ------------------------------------------------------------ weekday schedules

    @Test
    fun `unscheduled days are ignored`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-08-31", daysMask = mwf),
            listOf(done("2026-08-31"), done("2026-09-02"), done("2026-09-04")),
            today,
        )
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.longestStreak)
        assertEquals(3, stats.doneCount)
        assertEquals(0, stats.missedCount)
        assertEquals(1.0, stats.strictCompletionRate, 1e-9)
    }

    @Test
    fun `a missed scheduled weekday breaks the streak across unscheduled days`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-08-31", daysMask = mwf),
            listOf(done("2026-08-31"), done("2026-09-04")),
            today,
        )
        assertEquals(1, stats.currentStreak) // Wed 09-02 missed
        assertEquals(1, stats.longestStreak)
        assertEquals(1, stats.missedCount)
        assertEquals(2.0 / 3.0, stats.strictCompletionRate, 1e-9)
    }

    @Test
    fun `a done row on an unscheduled day counts as a bonus`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-08-31", daysMask = mwf),
            listOf(done("2026-08-31"), done("2026-09-01"), done("2026-09-02"), done("2026-09-04")),
            today,
        )
        assertEquals(4, stats.doneCount)
        assertEquals(4, stats.currentStreak)
        assertEquals(1.0, stats.strictCompletionRate, 1e-9)
    }

    @Test
    fun `a partial row on an unscheduled day is ignored`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-08-31", daysMask = mwf, targetMinutes = 30),
            listOf(done("2026-08-31", 30), partial("2026-09-01", 5), done("2026-09-02", 30), done("2026-09-04", 30)),
            today,
        )
        assertEquals(0, stats.partialCount)
        assertEquals(3, stats.currentStreak)
    }

    // ------------------------------------------------------------ ranges

    @Test
    fun `days before creation are not counted`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-03"),
            listOf(done("2026-09-03"), done("2026-09-04"), done("2026-09-05")),
            today,
        )
        assertEquals(0, stats.missedCount)
        assertEquals(3, stats.currentStreak)
    }

    @Test
    fun `archived tasks stop counting the day before archiving`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-01", archivedAt = "2026-09-04"),
            listOf(done("2026-09-01"), done("2026-09-02"), done("2026-09-03")),
            today,
        )
        assertEquals(3, stats.currentStreak)
        assertEquals(3, stats.doneCount)
        assertEquals(0, stats.missedCount)
    }

    @Test
    fun `task archived on its creation day has no stats`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-04", archivedAt = "2026-09-04"),
            emptyList(),
            today,
        )
        assertEquals(TaskStats.EMPTY, stats)
    }

    @Test
    fun `brand new task with nothing logged is all zeros`() {
        val stats = StatsCalculator.compute(task(createdAt = "2026-09-06"), emptyList(), today)
        assertEquals(TaskStats.EMPTY, stats)
        assertEquals(listOf(today to DayStatus.PENDING), StatsCalculator.classifyDays(task(createdAt = "2026-09-06"), emptyList(), today))
    }

    @Test
    fun `task created in the future has no stats`() {
        assertEquals(TaskStats.EMPTY, StatsCalculator.compute(task(createdAt = "2026-09-07"), emptyList(), today))
    }

    // ------------------------------------------------------------ weighted rate details

    @Test
    fun `weighted credit is capped at one`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-04", targetMinutes = 30),
            listOf(done("2026-09-04", 90), partial("2026-09-05", 10)),
            today,
        )
        assertEquals((1.0 + 10.0 / 30.0) / 2.0, stats.weightedCompletionRate, 1e-9)
        assertEquals(0.5, stats.strictCompletionRate, 1e-9)
    }

    @Test
    fun `weighted rate equals strict rate for checkbox tasks`() {
        val stats = StatsCalculator.compute(
            task(createdAt = "2026-09-01"),
            listOf(done("2026-09-01"), done("2026-09-03"), done("2026-09-05")),
            today,
        )
        assertEquals(stats.strictCompletionRate, stats.weightedCompletionRate, 1e-9)
        assertEquals(3.0 / 5.0, stats.strictCompletionRate, 1e-9)
    }

    // ------------------------------------------------------------ day classification

    @Test
    fun `classifyDays labels every day in range`() {
        val days = StatsCalculator.classifyDays(
            task(createdAt = "2026-08-31", daysMask = mwf, targetMinutes = 30),
            listOf(done("2026-08-31", 30), partial("2026-09-02", 10), skipped("2026-09-04")),
            today,
        )
        val expected = listOf(
            d("2026-08-31") to DayStatus.DONE,
            d("2026-09-01") to DayStatus.UNSCHEDULED,
            d("2026-09-02") to DayStatus.PARTIAL,
            d("2026-09-03") to DayStatus.UNSCHEDULED,
            d("2026-09-04") to DayStatus.SKIPPED,
            d("2026-09-05") to DayStatus.UNSCHEDULED,
            d("2026-09-06") to DayStatus.UNSCHEDULED,
        )
        assertEquals(expected, days)
    }

    @Test
    fun `classifyDays marks past scheduled days without rows as missed and today as pending`() {
        val days = StatsCalculator.classifyDays(task(createdAt = "2026-09-05"), emptyList(), today)
        assertEquals(listOf(d("2026-09-05") to DayStatus.MISSED, d("2026-09-06") to DayStatus.PENDING), days)
    }
}
