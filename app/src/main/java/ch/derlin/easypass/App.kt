package ch.derlin.easypass

/**
 * Created by Lin on 24.11.17.
 */
import android.app.Application
import android.content.Context
import ch.derlin.easypass.easypass.BuildConfig
import timber.log.Timber
import timber.log.Timber.DebugTree


class App : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        if (BuildConfig.DEBUG) {
            Timber.plant(object : DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String =
                    "lucy:${super.createStackElementTag(element)}:${element.lineNumber}"
            })
            Timber.v("initialised Timber in debug mode")
        }
    }

    companion object {
        lateinit var appContext: Context
            private set
    }

}