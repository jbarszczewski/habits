package com.jbarszczewski.habits.data.stats

/** How a single day is classified for one task once schedule and completions are combined. */
enum class DayStatus {
    DONE,
    PARTIAL,
    SKIPPED,
    /** Scheduled day in the past with no completions row. */
    MISSED,
    /** Today, scheduled, nothing logged yet. Neither counts nor breaks anything. */
    PENDING,
    /** Not in the schedule (and not DONE); ignored by every statistic. */
    UNSCHEDULED,
}
