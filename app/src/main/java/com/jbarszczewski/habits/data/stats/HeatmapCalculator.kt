package com.jbarszczewski.habits.data.stats

import com.jbarszczewski.habits.data.Completion
import com.jbarszczewski.habits.data.CompletionStatus
import com.jbarszczewski.habits.data.Task
import com.jbarszczewski.habits.data.isScheduledOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** How "done" a day was across all scheduled tasks. Drives the calendar / widget cell colour. */
enum class HeatmapIntensity {
    /** No tasks scheduled on this day (or future day). */
    NONE,
    /** Tasks are scheduled today but none completed yet. */
    IN_PROGRESS,
    /** Tasks were scheduled in the past but none completed. */
    MISSED,
    /** 1–25 % of scheduled tasks completed. */
    LOW,
    /** 26–50 % completed. */
    MEDIUM,
    /** 51–75 % completed. */
    HIGH,
    /** 76–100 % completed. */
    FULL,
}

data class HeatmapDay(
    val date: LocalDate,
    val intensity: HeatmapIntensity,
    val scheduledCount: Int,
    val completedCredit: Double,
)

data class HeatmapWeek(
    val weekStart: LocalDate,
    val days: List<HeatmapDay?>, // size == 7, index 0 = Mon … index 6 = Sun
)

object HeatmapCalculator {

    fun buildCurrentWeek(tasks: List<Task>, completions: List<Completion>, today: LocalDate): HeatmapWeek {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return buildWeek(tasks, completions.groupBy { it.date }, weekStart, today)
    }

    fun buildWeeks(
        tasks: List<Task>,
        completions: List<Completion>,
        gridStart: LocalDate,
        today: LocalDate,
    ): List<HeatmapWeek> {
        val completionsByDate = completions.groupBy { it.date }
        val result = mutableListOf<HeatmapWeek>()
        var weekStart = gridStart
        while (!weekStart.isAfter(today)) {
            result += buildWeek(tasks, completionsByDate, weekStart, today)
            weekStart = weekStart.plusWeeks(1)
        }
        return result
    }

    private fun buildWeek(
        tasks: List<Task>,
        completionsByDate: Map<LocalDate, List<Completion>>,
        weekStart: LocalDate,
        today: LocalDate,
    ): HeatmapWeek {
        val days = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            when {
                date.isAfter(today) -> null
                else -> buildDay(tasks, completionsByDate, date, today)
            }
        }
        return HeatmapWeek(weekStart = weekStart, days = days)
    }

    private fun buildDay(
        tasks: List<Task>,
        completionsByDate: Map<LocalDate, List<Completion>>,
        date: LocalDate,
        today: LocalDate,
    ): HeatmapDay {
        val dayCompletions = completionsByDate[date] ?: emptyList()
        val completionByTask = dayCompletions.associateBy { it.taskId }

        var scheduled = 0
        var creditSum = 0.0
        var hasPendingScheduledTask = false
        for (task in tasks) {
            if (task.createdAt.isAfter(date)) continue
            val archivedAt = task.archivedAt
            if (archivedAt != null && archivedAt.isBefore(date)) continue
            if (!task.isScheduledOn(date)) continue
            scheduled++
            val completion = completionByTask[task.id]
            if (date == today && completion == null) hasPendingScheduledTask = true
            creditSum += when (completion?.status) {
                CompletionStatus.DONE -> 1.0
                CompletionStatus.PARTIAL -> {
                    val target = task.targetMinutes
                    val actual = completion.actualMinutes ?: 0
                    if (target != null && target > 0) (actual.toDouble() / target).coerceIn(0.0, 1.0) else 0.5
                }
                else -> 0.0
            }
        }

        val intensity = when {
            scheduled == 0 -> HeatmapIntensity.NONE
            // A skipped row means "the user made a choice", but if any scheduled task is still
            // untouched today the day is still in progress rather than already missed.
            date == today && creditSum == 0.0 && hasPendingScheduledTask -> HeatmapIntensity.IN_PROGRESS
            creditSum == 0.0 -> HeatmapIntensity.MISSED
            else -> {
                // Once any credit exists today, we show how much progress was earned so far
                // instead of collapsing every partial day into the generic IN_PROGRESS state.
                val ratio = creditSum / scheduled
                when {
                    ratio <= 0.25 -> HeatmapIntensity.LOW
                    ratio <= 0.50 -> HeatmapIntensity.MEDIUM
                    ratio <= 0.75 -> HeatmapIntensity.HIGH
                    else -> HeatmapIntensity.FULL
                }
            }
        }

        return HeatmapDay(
            date = date,
            intensity = intensity,
            scheduledCount = scheduled,
            completedCredit = creditSum,
        )
    }
}
