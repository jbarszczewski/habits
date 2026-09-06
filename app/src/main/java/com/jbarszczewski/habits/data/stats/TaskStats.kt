package com.jbarszczewski.habits.data.stats

/**
 * Derived statistics for one task. Never stored; recomputed from completions + schedule.
 *
 * Rates are in `0.0..1.0`. Both are `0.0` when there is no counted day yet.
 */
data class TaskStats(
    /** Consecutive DONE days ending today (or yesterday if today is still pending). */
    val currentStreak: Int,
    val longestStreak: Int,
    val doneCount: Int,
    val partialCount: Int,
    val skippedCount: Int,
    val missedCount: Int,
    /** `done / (done + partial + missed)`. */
    val strictCompletionRate: Double,
    /**
     * Like the strict rate but a partial day contributes `actual / target` instead of 0.
     * Checkbox tasks have no partial days, so their weighted rate equals the strict one.
     */
    val weightedCompletionRate: Double,
) {
    companion object {
        val EMPTY = TaskStats(0, 0, 0, 0, 0, 0, 0.0, 0.0)
    }
}
