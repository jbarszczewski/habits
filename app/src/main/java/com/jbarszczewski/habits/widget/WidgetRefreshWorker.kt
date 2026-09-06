package com.jbarszczewski.habits.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jbarszczewski.habits.HabitsApplication
import java.util.concurrent.TimeUnit

/**
 * Re-renders the widget when the logical day changes so it stops showing yesterday's list.
 * WorkManager survives process death and reboots, which a plain coroutine timer would not.
 * The job re-schedules itself for the following day after each run.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        HabitsWidget().updateAll(applicationContext)
        WidgetRefreshScheduler.scheduleNextDayStart(applicationContext)
        return Result.success()
    }
}

object WidgetRefreshScheduler {
    private const val WORK_NAME = "widget-day-rollover"

    /** (Re)schedules one run at the next day-start boundary, replacing any pending one. */
    fun scheduleNextDayStart(context: Context) {
        val dateProvider = (context.applicationContext as HabitsApplication).container.dateProvider
        // Small margin so the worker runs just after the boundary, never just before it.
        val delayMillis = dateProvider.millisUntilNextDayStart() + 5_000
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
