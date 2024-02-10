package ch.derlin.easypass

/**
 * Created by Lin on 24.11.17.
 */
import android.app.Application
import android.content.Context
import ch.derlin.easypass.easypass.BuildConfig
import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.android.startKovenant
import nl.komponents.kovenant.android.stopKovenant
import nl.komponents.kovenant.buildDispatcher
import timber.log.Timber
import timber.log.Timber.DebugTree


class App : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // limit background threads to one to avoid
        // concurrency on account update
        Kovenant.context {
            workerContext.dispatcher = buildDispatcher {
                name = "Kovenant worker thread"
                concurrentTasks = 1
            }
        }
        startKovenant()

        if (BuildConfig.DEBUG) {
            Timber.plant(object : DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String =
                        "lucy:${super.createStackElementTag(element)}:${element.lineNumber}"
            })
            Timber.v("initialised Timber in debug mode")
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        stopKovenant()
    }

    companion object {
        lateinit var appContext: Context
            private set
    }

}