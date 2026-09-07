package com.jbarszczewski.habits

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.jbarszczewski.habits.data.DaysMask
import com.jbarszczewski.habits.notifications.ReminderNotifier
import com.jbarszczewski.habits.notifications.ReminderScheduler
import com.jbarszczewski.habits.widget.HabitsWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Registered in AndroidManifest.xml via `android:name`. Android creates exactly one instance per
 * process, which makes it the natural owner of app-wide singletons.
 */
class HabitsApplication : Application() {
    lateinit var container: AppContainer
        private set

    /**
     * Scope for background work that should outlive any single screen (seeding, later the
     * widget's writes). A SupervisorJob means one failed child does not cancel the others.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        if (BuildConfig.DEBUG) seedDebugData()
        keepWidgetInSync()
        ReminderNotifier.ensureChannel(this)
        ReminderScheduler.ensureScheduled(this)
    }

    /**
     * The widget is a static snapshot, so it must be re-rendered after the app writes to the
     * database. Room's InvalidationTracker tells us whenever the two tables change; a short
     * debounce coalesces bursts of writes into one refresh. `updateAll` is a no-op when no widget
     * is placed.
     */
    @OptIn(FlowPreview::class)
    private fun keepWidgetInSync() {
        applicationScope.launch {
            container.database.invalidationTracker
                .createFlow("tasks", "completions", emitInitialState = false)
                .debounce(300)
                .collect { HabitsWidget().updateAll(this@HabitsApplication) }
        }
    }

    /** Debug builds only: insert one task the first time the app starts with an empty database. */
    private fun seedDebugData() {
        applicationScope.launch {
            val repository = container.repository
            if (repository.observeAllTasks().first().isEmpty()) {
                repository.createTask(name = "Drink water", daysMask = DaysMask.EVERY_DAY)
            }
        }
    }
}
