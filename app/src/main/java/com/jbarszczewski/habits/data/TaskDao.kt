package com.jbarszczewski.habits.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Room DAO for the `tasks` table. Room generates the implementation at build time.
 *
 * Methods returning [Flow] re-emit whenever the underlying table changes; `suspend`
 * methods run once on Room's background executor.
 */
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE archived_at IS NULL ORDER BY id")
    fun observeActive(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY id")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): Task?

    @Query("SELECT * FROM tasks ORDER BY id")
    suspend fun getAll(): List<Task>

    /**
     * Active tasks whose schedule includes [date]. [dayBit] must be `DaysMask.bit(date.dayOfWeek)`;
     * SQLite's `&` operator does the mask test directly in the query, which keeps the widget's
     * query cheap.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE archived_at IS NULL
          AND created_at <= :date
          AND (days_mask & :dayBit) != 0
        ORDER BY id
        """
    )
    fun observeScheduledOn(date: LocalDate, dayBit: Int): Flow<List<Task>>

    /** One-shot counterpart of [observeScheduledOn], used by the reminder check. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE archived_at IS NULL
          AND created_at <= :date
          AND (days_mask & :dayBit) != 0
        ORDER BY id
        """
    )
    suspend fun getScheduledOn(date: LocalDate, dayBit: Int): List<Task>

    @Query("SELECT * FROM tasks WHERE timer_started_at IS NOT NULL")
    suspend fun getWithRunningTimer(): List<Task>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("UPDATE tasks SET timer_started_at = :startedAt WHERE id = :id")
    suspend fun setTimerStartedAt(id: Long, startedAt: Long?)

    @Query("UPDATE tasks SET archived_at = :date WHERE id = :id")
    suspend fun setArchivedAt(id: Long, date: LocalDate?)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
