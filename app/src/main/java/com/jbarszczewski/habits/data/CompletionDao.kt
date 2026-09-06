package com.jbarszczewski.habits.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CompletionDao {

    @Query("SELECT * FROM completions WHERE task_id = :taskId AND date = :date")
    suspend fun get(taskId: Long, date: LocalDate): Completion?

    @Query("SELECT * FROM completions WHERE task_id = :taskId ORDER BY date")
    suspend fun getForTask(taskId: Long): List<Completion>

    @Query("SELECT * FROM completions WHERE task_id = :taskId ORDER BY date")
    fun observeForTask(taskId: Long): Flow<List<Completion>>

    @Query("SELECT * FROM completions WHERE date = :date")
    fun observeForDate(date: LocalDate): Flow<List<Completion>>

    /** All completions with `from <= date <= to`, for all tasks. */
    @Query("SELECT * FROM completions WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun getBetween(from: LocalDate, to: LocalDate): List<Completion>

    /** Insert, or replace the existing row with the same (task_id, date). */
    @Upsert
    suspend fun upsert(completion: Completion)

    @Query("DELETE FROM completions WHERE task_id = :taskId AND date = :date")
    suspend fun delete(taskId: Long, date: LocalDate)
}
