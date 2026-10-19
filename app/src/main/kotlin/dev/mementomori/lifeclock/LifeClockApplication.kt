package dev.mementomori.lifeclock

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * The application entry point.
 *
 * `@HiltAndroidApp` tells Hilt to generate the dependency-injection container that lives
 * for the whole process. Everything injected anywhere in the app (view models, workers,
 * the widget layer via EntryPointAccessors) ultimately comes from here.
 */
@HiltAndroidApp
class LifeClockApplication : Application()
