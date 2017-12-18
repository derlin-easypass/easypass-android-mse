package ch.derlin.easypass.easypass

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import ch.derlin.easypass.easypass.helper.Preferences
import com.dropbox.core.android.Auth
import timber.log.Timber

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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        Preferences().introDone =false
        if(!Preferences().introDone){
            lauchIntro()
            return
        }

        val token = Preferences(this).dbxAccessToken
        if (token == null) {
            mIsAuthenticating = true
            Auth.startOAuth2Authentication(this, getString(R.string.dbx_app_key))
        } else {
            startApp()
        }
    }


    override fun onResume() {
        super.onResume()
        // the dropbox linking happens in another activity.
        if (mIsAuthenticating) {
            val token = Auth.getOAuth2Token() //generate Access Token
            if (token != null) {
                Preferences(this).dbxAccessToken = token //Store accessToken in SharedPreferences
                mIsAuthenticating = false
                startApp()
            } else {
                Snackbar.make(findViewById(android.R.id.content), "Error authenticating with Dropbox", Snackbar.LENGTH_INDEFINITE)
                        .setAction("retry", { _ ->
                            forceRestart()
                        })
                        .show()
                Timber.d("Error authenticating")
            }
        }
    }

    // ----------------------------------------------------

    private fun lauchIntro() {
        // service up and running, start the actual app
        val intent = Intent(this, IntroActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
        startActivity(intent)
        this.finish()
    }

    private fun startApp() {
        // service up and running, start the actual app
        val intent = Intent(this, LoadSessionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME
        startActivity(intent)
        this.finish()
    }

    private fun forceRestart() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent!!.flags = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(launchIntent)
    }


}
