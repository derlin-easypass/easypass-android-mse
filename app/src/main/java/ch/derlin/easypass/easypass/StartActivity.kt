package ch.derlin.easypass.easypass

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import ch.derlin.easypass.easypass.dropbox.DbxService

/**
 * This activity is the entry point of the application.
 * It ensures that the app is linked to dropbox and that
 * the dropbox service is instantiated before launching
 * the actual main activity.
 * @author Lucy Linder
 */
class StartActivity : AppCompatActivity() {

    private var mIsAuthenticating = false
    // ----------------------------------------------------

    // this allows us to detect when the service is up.
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            onServiceBound()
        }


        override fun onServiceDisconnected(name: ComponentName) {
        }
    }


    // ----------------------------------------------------


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
    }


    override fun onStart() {
        super.onStart()
        // start the dropbox service
        startService(Intent(applicationContext, DbxService::class.java))
        bindService(Intent(applicationContext, DbxService::class.java), //
                mServiceConnection, Context.BIND_AUTO_CREATE)
    }


    override fun onDestroy() {
        unbindService(mServiceConnection)
        super.onDestroy()
    }


    override fun onResume() {
        super.onResume()
        // the dropbox linking happens in another activity.
        if (mIsAuthenticating) {
            DbxService.instance.finishAuth()
            mIsAuthenticating = false
        }
    }

    // ----------------------------------------------------


    private fun onServiceBound() {
        mIsAuthenticating = true
        if (DbxService.instance.startAuth()) {
            // returns true only if already authenticated
            startApp()
        } else {
            // else, a dbx activity will be launched --> see the on resume
        }
    }


    private fun startApp() {
        // service up and running, start the actual app
        val intent = Intent(this, LoadSessionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
        startActivity(intent)
        this.finish()
    }


}
