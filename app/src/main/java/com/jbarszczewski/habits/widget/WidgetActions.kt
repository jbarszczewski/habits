package com.jbarszczewski.habits.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import com.jbarszczewski.habits.HabitsApplication

object WidgetActionKeys {
    val taskId = ActionParameters.Key<Long>("taskId")
}

/**
 * Widget taps arrive here as broadcasts, possibly with the app process not running. Each action
 * writes through the shared repository and then re-renders every placed widget with `updateAll`.
 */
class ToggleDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[WidgetActionKeys.taskId] ?: return
        val repository = (context.applicationContext as HabitsApplication).container.repository
        repository.toggleDone(taskId)
        HabitsWidget().updateAll(context)
    }
}

/** Start the timer if it is stopped, stop it (and credit the minutes) if it is running. */
class ToggleTimerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[WidgetActionKeys.taskId] ?: return
        val repository = (context.applicationContext as HabitsApplication).container.repository
        val task = repository.getTask(taskId) ?: return
        // Build step 5 adds the foreground service + notification around these two calls.
        if (task.timerStartedAt != null) repository.stopTimer(taskId) else repository.startTimer(taskId)
        HabitsWidget().updateAll(context)
    }
}
