package com.jbarszczewski.habits.data

import com.jbarszczewski.habits.data.stats.StatsCalculator
import com.jbarszczewski.habits.data.stats.TaskStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** A task together with its completions row for one particular date (null = nothing logged). */
data class TaskWithCompletion(
    val task: Task,
    val completion: Completion?,
)

/**
 * Single entry point for all reads and writes. The app UI, the widget and the timer service
 * all go through this class so the rules below live in exactly one place.
 */
class HabitRepository(
    private val taskDao: TaskDao,
    private val completionDao: CompletionDao,
    private val dateProvider: DateProvider,
) {

    // ---------------------------------------------------------------- tasks

    fun observeActiveTasks(): Flow<List<Task>> = taskDao.observeActive()

    fun observeAllTasks(): Flow<List<Task>> = taskDao.observeAll()

    fun observeTask(id: Long): Flow<Task?> = taskDao.observeById(id)

    suspend fun getTask(id: Long): Task? = taskDao.getById(id)

    /**
     * Active tasks scheduled on [date], each paired with that day's completion.
     * "Today" is resolved once when the flow is collected; callers that stay on screen across
     * midnight should re-collect (the Today screen will do this in a later step).
     */
    fun observeTasksForDate(date: LocalDate = dateProvider.today()): Flow<List<TaskWithCompletion>> =
        combine(
            taskDao.observeScheduledOn(date, DaysMask.bit(date.dayOfWeek)),
            completionDao.observeForDate(date),
        ) { tasks, completions ->
            val byTask = completions.associateBy { it.taskId }
            tasks.map { TaskWithCompletion(it, byTask[it.id]) }
        }

    suspend fun createTask(name: String, daysMask: Int, targetMinutes: Int? = null): Long {
        require(name.isNotBlank()) { "Task name must not be blank" }
        require(DaysMask.isValid(daysMask)) { "days_mask must have at least one weekday set" }
        require(targetMinutes == null || targetMinutes > 0) { "target_minutes must be positive or null" }
        return taskDao.insert(
            Task(
                name = name.trim(),
                daysMask = daysMask,
                targetMinutes = targetMinutes,
                createdAt = dateProvider.today(),
            )
        )
    }

    /**
     * Edits name / schedule / target. Only these three fields are taken from [task]; timer state
     * and dates are left untouched. History is never rewritten: a new schedule simply changes how
     * future days are judged.
     */
    suspend fun updateTask(task: Task) {
        require(task.name.isNotBlank()) { "Task name must not be blank" }
        require(DaysMask.isValid(task.daysMask)) { "days_mask must have at least one weekday set" }
        require(task.targetMinutes == null || task.targetMinutes > 0) { "target_minutes must be positive or null" }
        val stored = taskDao.getById(task.id) ?: error("Task ${task.id} does not exist")
        taskDao.update(
            stored.copy(
                name = task.name.trim(),
                daysMask = task.daysMask,
                targetMinutes = task.targetMinutes,
            )
        )
    }

    /** Hides the task from today's list from now on. Its completions stay for statistics. */
    suspend fun archiveTask(id: Long) {
        taskDao.setArchivedAt(id, dateProvider.today())
    }

    suspend fun unarchiveTask(id: Long) {
        taskDao.setArchivedAt(id, null)
    }

    /** Permanently deletes the task and (via the foreign key cascade) all its completions. */
    suspend fun deleteTask(id: Long) {
        taskDao.deleteById(id)
    }

    // ---------------------------------------------------------------- completions

    suspend fun getCompletion(taskId: Long, date: LocalDate): Completion? = completionDao.get(taskId, date)

    fun observeCompletions(taskId: Long): Flow<List<Completion>> = completionDao.observeForTask(taskId)

    /** Checkbox tasks: mark DONE, or remove the row again when [done] is false. */
    suspend fun setDone(taskId: Long, date: LocalDate, done: Boolean) {
        if (done) {
            completionDao.upsert(Completion(taskId = taskId, date = date, status = CompletionStatus.DONE))
        } else {
            completionDao.delete(taskId, date)
        }
    }

    suspend fun toggleDone(taskId: Long, date: LocalDate = dateProvider.today()) {
        val existing = completionDao.get(taskId, date)
        setDone(taskId, date, done = existing?.status != CompletionStatus.DONE)
    }

    /** Marks the day as deliberately skipped. Any minutes already logged are kept on the row. */
    suspend fun setSkipped(taskId: Long, date: LocalDate) {
        val existing = completionDao.get(taskId, date)
        completionDao.upsert(
            Completion(
                taskId = taskId,
                date = date,
                status = CompletionStatus.SKIPPED,
                actualMinutes = existing?.actualMinutes,
            )
        )
    }

    /** Removes the row, turning the day back into a miss (or pending, if it is today). */
    suspend fun clearCompletion(taskId: Long, date: LocalDate) {
        completionDao.delete(taskId, date)
    }

    /**
     * Timed tasks: adds [minutes] to the day's running total and re-derives the status
     * (`>= target` -> DONE, otherwise PARTIAL). Used by both manual entry and the timer.
     */
    suspend fun addMinutes(taskId: Long, date: LocalDate, minutes: Int): Completion {
        require(minutes > 0) { "minutes must be positive" }
        val task = taskDao.getById(taskId) ?: error("Task $taskId does not exist")
        val target = task.targetMinutes ?: error("Task $taskId is not a timed task")
        val existing = completionDao.get(taskId, date)
        val total = (existing?.actualMinutes ?: 0) + minutes
        val status = Completion.statusForMinutes(total, target) ?: CompletionStatus.PARTIAL
        val updated = Completion(taskId = taskId, date = date, status = status, actualMinutes = total)
        completionDao.upsert(updated)
        return updated
    }

    // ---------------------------------------------------------------- timer

    /**
     * Records the start time on the task. Starting the foreground service that keeps the timer
     * visible is the caller's job (build step 5); this layer only stores state.
     */
    suspend fun startTimer(taskId: Long) {
        val task = taskDao.getById(taskId) ?: error("Task $taskId does not exist")
        check(task.isTimed) { "Task $taskId is not a timed task" }
        if (task.timerStartedAt != null) return // already running
        taskDao.setTimerStartedAt(taskId, dateProvider.nowMillis())
    }

    /**
     * Clears the timer and credits the elapsed whole minutes to today's row.
     * Returns the number of minutes added (0 if no timer was running or it ran under a minute).
     */
    suspend fun stopTimer(taskId: Long): Int {
        val task = taskDao.getById(taskId) ?: return 0
        val startedAt = task.timerStartedAt ?: return 0
        val elapsedMinutes = elapsedWholeMinutes(startedAt, dateProvider.nowMillis())
        taskDao.setTimerStartedAt(taskId, null)
        if (elapsedMinutes > 0) {
            addMinutes(taskId, dateProvider.today(), elapsedMinutes)
        }
        return elapsedMinutes
    }

    suspend fun getTasksWithRunningTimer(): List<Task> = taskDao.getWithRunningTimer()

    // ---------------------------------------------------------------- statistics

    suspend fun getStats(taskId: Long): TaskStats {
        val task = taskDao.getById(taskId) ?: return TaskStats.EMPTY
        return StatsCalculator.compute(task, completionDao.getForTask(taskId), dateProvider.today())
    }

    companion object {
        /** Elapsed time rounded *down* to whole minutes; partial minutes are not credited. */
        fun elapsedWholeMinutes(startedAtMillis: Long, nowMillis: Long): Int =
            ((nowMillis - startedAtMillis).coerceAtLeast(0) / 60_000L).toInt()
    }
}
