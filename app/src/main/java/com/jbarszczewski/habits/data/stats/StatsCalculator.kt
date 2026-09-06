package com.jbarszczewski.habits.data.stats

import com.jbarszczewski.habits.data.Completion
import com.jbarszczewski.habits.data.CompletionStatus
import com.jbarszczewski.habits.data.Task
import com.jbarszczewski.habits.data.isScheduledOn
import com.jbarszczewski.habits.data.isTimed
import java.time.LocalDate

/**
 * Pure functions that turn a task, its completions and "today" into statistics.
 * No Android or Room dependencies, so everything here is covered by plain JVM unit tests.
 *
 * Rules (the schema only stores the *current* schedule, so past days are judged by it):
 * - A day counts from `created_at` up to and including today, or up to the day *before*
 *   `archived_at` for archived tasks.
 * - A scheduled day with no row is MISSED, except today which is PENDING.
 * - SKIPPED and PENDING days are neutral: they neither extend nor break a streak and are
 *   left out of completion rates.
 * - PARTIAL and MISSED days break a streak.
 * - A DONE row on an unscheduled day still counts (a bonus day). Any other row on an
 *   unscheduled day is ignored.
 */
object StatsCalculator {

    fun compute(task: Task, completions: List<Completion>, today: LocalDate): TaskStats {
        val days = classifyDays(task, completions, today)
        if (days.isEmpty()) return TaskStats.EMPTY

        val byDate = completions.associateBy { it.date }

        var done = 0
        var partial = 0
        var skipped = 0
        var missed = 0
        var weightedSum = 0.0
        var longest = 0
        var run = 0

        for ((date, status) in days) {
            when (status) {
                DayStatus.DONE -> {
                    done++
                    weightedSum += 1.0
                    run++
                    if (run > longest) longest = run
                }
                DayStatus.PARTIAL -> {
                    partial++
                    weightedSum += weightedCredit(task, byDate.getValue(date))
                    run = 0
                }
                DayStatus.MISSED -> {
                    missed++
                    run = 0
                }
                DayStatus.SKIPPED -> skipped++
                DayStatus.PENDING, DayStatus.UNSCHEDULED -> Unit
            }
        }

        // Current streak: walk backwards from the most recent day until something breaks it.
        var current = 0
        for ((_, status) in days.asReversed()) {
            when (status) {
                DayStatus.DONE -> current++
                DayStatus.SKIPPED, DayStatus.PENDING, DayStatus.UNSCHEDULED -> continue
                DayStatus.PARTIAL, DayStatus.MISSED -> break
            }
        }

        val counted = done + partial + missed
        return TaskStats(
            currentStreak = current,
            longestStreak = longest,
            doneCount = done,
            partialCount = partial,
            skippedCount = skipped,
            missedCount = missed,
            strictCompletionRate = if (counted == 0) 0.0 else done.toDouble() / counted,
            weightedCompletionRate = if (counted == 0) 0.0 else weightedSum / counted,
        )
    }

    /**
     * Every day in the task's active range with its [DayStatus], oldest first.
     * The stats screen's calendar heatmap will build on this too.
     */
    fun classifyDays(task: Task, completions: List<Completion>, today: LocalDate): List<Pair<LocalDate, DayStatus>> {
        val byDate = completions.associateBy { it.date }
        val lastDay = lastCountedDay(task, today) ?: return emptyList()
        if (lastDay.isBefore(task.createdAt)) return emptyList()

        val result = ArrayList<Pair<LocalDate, DayStatus>>()
        var date = task.createdAt
        while (!date.isAfter(lastDay)) {
            result += date to dayStatus(task, byDate[date], date, today)
            date = date.plusDays(1)
        }
        return result
    }

    fun dayStatus(task: Task, completion: Completion?, date: LocalDate, today: LocalDate): DayStatus {
        val scheduled = task.isScheduledOn(date)
        return when {
            completion?.status == CompletionStatus.DONE -> DayStatus.DONE
            completion != null && !scheduled -> DayStatus.UNSCHEDULED
            completion?.status == CompletionStatus.PARTIAL -> DayStatus.PARTIAL
            completion?.status == CompletionStatus.SKIPPED -> DayStatus.SKIPPED
            !scheduled -> DayStatus.UNSCHEDULED
            date == today -> DayStatus.PENDING
            else -> DayStatus.MISSED
        }
    }

    /** Last day that takes part in statistics, or `null` if the range is empty. */
    private fun lastCountedDay(task: Task, today: LocalDate): LocalDate? {
        val archivedAt = task.archivedAt ?: return today
        val dayBeforeArchive = archivedAt.minusDays(1)
        return if (dayBeforeArchive.isBefore(today)) dayBeforeArchive else today
    }

    /** `actual / target` capped at 1; checkbox tasks are all-or-nothing. */
    private fun weightedCredit(task: Task, completion: Completion): Double {
        val target = task.targetMinutes
        if (!task.isTimed || target == null || target <= 0) {
            return if (completion.status == CompletionStatus.DONE) 1.0 else 0.0
        }
        val actual = completion.actualMinutes ?: 0
        return (actual.toDouble() / target).coerceIn(0.0, 1.0)
    }
}
