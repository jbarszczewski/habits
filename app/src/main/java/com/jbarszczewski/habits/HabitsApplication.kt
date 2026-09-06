package com.jbarszczewski.habits

import android.app.Application

/**
 * Registered in AndroidManifest.xml via `android:name`. Android creates exactly one instance per
 * process, which makes it the natural owner of app-wide singletons.
 */
class HabitsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
