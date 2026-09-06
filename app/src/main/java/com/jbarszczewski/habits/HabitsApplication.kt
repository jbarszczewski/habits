package com.jbarszczewski.habits

import android.app.Application
import com.jbarszczewski.habits.data.DaysMask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
