package com.jbarszczewski.habits.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jbarszczewski.habits.HabitsApplication
import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Checks, at noon and again at 4pm, which of today's scheduled habits are still unfinished and
 * shows one grouped notification if any are. Self-reschedules for the next trigger time after
 * each run, the same pattern [com.jbarszczewski.habits.widget.WidgetRefreshWorker] uses for the
 * midnight widget rollover. WorkManager (rather than AlarmManager) is used here too so both
 * background jobs share one boring, reboot-safe mechanism.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HabitsApplication
        val unfinished = app.container.repository.getUnfinishedReminders()
        ReminderNotifier.notifyUnfinished(applicationContext, unfinished)
        ReminderScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "habit-reminder-check"
    private val TRIGGER_TIMES = listOf(LocalTime.of(12, 0), LocalTime.of(16, 0))

    /** (Re)schedules one run at the next 12:00 or 16:00, replacing any pending one. */
    fun scheduleNext(context: Context, clock: Clock = Clock.systemDefaultZone()) {
        enqueue(context, ExistingWorkPolicy.REPLACE, clock)
    }

    /** Schedules only if nothing is pending yet, so repeated app starts don't push the run back. */
    fun ensureScheduled(context: Context, clock: Clock = Clock.systemDefaultZone()) {
        enqueue(context, ExistingWorkPolicy.KEEP, clock)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun enqueue(context: Context, policy: ExistingWorkPolicy, clock: Clock) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(millisUntilNextTrigger(clock), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /** Millis from now until the next 12:00 or 16:00, today or tomorrow. */
    internal fun millisUntilNextTrigger(clock: Clock = Clock.systemDefaultZone()): Long {
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()
        val next = TRIGGER_TIMES.map { today.atTime(it).atZone(clock.zone) }.firstOrNull { it.isAfter(now) }
            ?: today.plusDays(1).atTime(TRIGGER_TIMES.first()).atZone(clock.zone)
        return (next.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()).coerceAtLeast(0)
    }
}
