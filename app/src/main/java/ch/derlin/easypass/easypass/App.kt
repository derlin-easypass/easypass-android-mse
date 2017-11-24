package ch.derlin.easypass.easypass

/**
 * Created by Lin on 24.11.17.
 */
import android.app.Application
import android.content.Context

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
            private set
    }

}