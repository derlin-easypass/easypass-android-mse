package ch.derlin.easypass.easypass

/**
 * Created by Lin on 24.11.17.
 */
import android.app.Application
import android.content.Context
import nl.komponents.kovenant.android.startKovenant
import nl.komponents.kovenant.android.stopKovenant

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        startKovenant()
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