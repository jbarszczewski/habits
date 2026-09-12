package com.jbarszczewski.habits.data.stats

import com.jbarszczewski.habits.data.Completion
import com.jbarszczewski.habits.data.CompletionStatus
import com.jbarszczewski.habits.data.DaysMask
import com.jbarszczewski.habits.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HeatmapCalculatorTest {

    private fun d(value: String): LocalDate = LocalDate.parse(value)

    @Test
    fun `current week leaves future days empty`() {
        val task = Task(
            id = 1,
            name = "Drink water",
            daysMask = DaysMask.EVERY_DAY,
            createdAt = d("2026-09-07"),
        )

        val week = HeatmapCalculator.buildCurrentWeek(
            tasks = listOf(task),
            completions = listOf(
                Completion(taskId = 1, date = d("2026-09-07"), status = CompletionStatus.DONE),
                Completion(taskId = 1, date = d("2026-09-09"), status = CompletionStatus.DONE),
            ),
            today = d("2026-09-10"),
        )

        assertEquals(d("2026-09-07"), week.weekStart)
        assertEquals(7, week.days.size)
        assertEquals(HeatmapIntensity.FULL, week.days[0]?.intensity)
        assertEquals(HeatmapIntensity.MISSED, week.days[1]?.intensity)
        assertEquals(HeatmapIntensity.FULL, week.days[2]?.intensity)
        assertEquals(HeatmapIntensity.IN_PROGRESS, week.days[3]?.intensity)
        assertNull(week.days[4])
        assertNull(week.days[5])
        assertNull(week.days[6])
    }

    @Test
    fun `partial timed progress uses weighted intensity`() {
        val task = Task(
            id = 1,
            name = "Practice singing",
            daysMask = DaysMask.EVERY_DAY,
            targetMinutes = 40,
            createdAt = d("2026-09-01"),
        )

        val weeks = HeatmapCalculator.buildWeeks(
            tasks = listOf(task),
            completions = listOf(
                Completion(taskId = 1, date = d("2026-09-08"), status = CompletionStatus.PARTIAL, actualMinutes = 10),
                Completion(taskId = 1, date = d("2026-09-09"), status = CompletionStatus.PARTIAL, actualMinutes = 20),
                Completion(taskId = 1, date = d("2026-09-10"), status = CompletionStatus.PARTIAL, actualMinutes = 30),
                Completion(taskId = 1, date = d("2026-09-11"), status = CompletionStatus.DONE, actualMinutes = 40),
            ),
            gridStart = d("2026-09-07"),
            today = d("2026-09-12"),
        )

        val days = weeks.single().days
        assertEquals(HeatmapIntensity.MISSED, days[0]?.intensity)
        assertEquals(HeatmapIntensity.LOW, days[1]?.intensity)
        assertEquals(HeatmapIntensity.MEDIUM, days[2]?.intensity)
        assertEquals(HeatmapIntensity.HIGH, days[3]?.intensity)
        assertEquals(HeatmapIntensity.FULL, days[4]?.intensity)
    }

    @Test
    fun `today with skipped and pending tasks stays in progress`() {
        val tasks = listOf(
            Task(
                id = 1,
                name = "Drink water",
                daysMask = DaysMask.EVERY_DAY,
                createdAt = d("2026-09-01"),
            ),
            Task(
                id = 2,
                name = "Walk",
                daysMask = DaysMask.EVERY_DAY,
                createdAt = d("2026-09-01"),
            ),
        )

        val week = HeatmapCalculator.buildCurrentWeek(
            tasks = tasks,
            completions = listOf(
                Completion(taskId = 1, date = d("2026-09-10"), status = CompletionStatus.SKIPPED),
            ),
            today = d("2026-09-10"),
        )

        assertEquals(HeatmapIntensity.IN_PROGRESS, week.days[3]?.intensity)
    }
}
