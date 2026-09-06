package com.jbarszczewski.habits.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import java.time.LocalDate

enum class CompletionStatus {
    DONE,
    PARTIAL,
    SKIPPED,
}

/**
 * What happened for one task on one local date. Exactly one row per (task, date); a
 * scheduled day with no row is a MISS and is never stored.
 *
 * Deleting a task cascades to its completions (see the foreign key below).
 */
@Entity(
    tableName = "completions",
    primaryKeys = ["task_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Completion(
    @ColumnInfo(name = "task_id")
    val taskId: Long,

    val date: LocalDate,

    /** Room stores enums by name as TEXT. */
    val status: CompletionStatus,

    /** Timed tasks only: minutes accumulated across all sessions on this day. */
    @ColumnInfo(name = "actual_minutes")
    val actualMinutes: Int? = null,
) {
    companion object {
        /**
         * Status of a timed task for a day on which [actualMinutes] were logged against
         * [targetMinutes]. Returns `null` for zero minutes: there is nothing to store yet.
         */
        fun statusForMinutes(actualMinutes: Int, targetMinutes: Int): CompletionStatus? = when {
            actualMinutes <= 0 -> null
            actualMinutes >= targetMinutes -> CompletionStatus.DONE
            else -> CompletionStatus.PARTIAL
        }
    }
}
