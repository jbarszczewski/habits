package com.jbarszczewski.habits.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A recurring task ("habit"). One row per task; the schedule lives in [daysMask].
 *
 * Column names match the data model in CLAUDE.md exactly. Date columns are stored as
 * ISO local date strings ("yyyy-MM-dd") through [DateConverters]; Kotlin code only ever
 * sees [LocalDate].
 */
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    /** 7-bit weekday mask, see [DaysMask]. */
    @ColumnInfo(name = "days_mask")
    val daysMask: Int,

    /** Daily time target in minutes. `null` means a plain checkbox task. */
    @ColumnInfo(name = "target_minutes")
    val targetMinutes: Int? = null,

    /** Whether the noon/4pm reminder checks this task. Opt-out lives per task, not globally. */
    @ColumnInfo(name = "notifications_enabled")
    val notificationsEnabled: Boolean = true,

    /** Epoch millis of when the running timer was started, or `null` when no timer runs. */
    @ColumnInfo(name = "timer_started_at")
    val timerStartedAt: Long? = null,

    /** Local date the task was created. Days before this are never "scheduled". */
    @ColumnInfo(name = "created_at")
    val createdAt: LocalDate,

    /** Local date the task was archived, or `null` while active. Archived tasks keep their history. */
    @ColumnInfo(name = "archived_at")
    val archivedAt: LocalDate? = null,
)

/** `true` for tasks with a time target (as opposed to a plain checkbox). */
val Task.isTimed: Boolean get() = targetMinutes != null

val Task.isTimerRunning: Boolean get() = timerStartedAt != null

val Task.isArchived: Boolean get() = archivedAt != null

/** Whether the *current* schedule includes the weekday of [date]. Ignores created/archived bounds. */
fun Task.isScheduledOn(date: LocalDate): Boolean = DaysMask.contains(daysMask, date.dayOfWeek)
