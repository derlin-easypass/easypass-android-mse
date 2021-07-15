package ch.derlin.easypass

import android.content.Intent
import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import ch.derlin.changelog.Changelog
import ch.derlin.changelog.Changelog.getAppVersion
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.helper.MiscUtils.showIntro
import ch.derlin.easypass.helper.Preferences
import com.dropbox.core.android.Auth
import kotlinx.android.synthetic.main.activity_start.*
import timber.log.Timber

/**
 * This activity is the entry point of the application.
 * It ensures that the app is linked to dropbox and that
 * the dropbox service is instantiated before launching
 * the actual main activity.
 * @author Lucy Linder
 */
class StartActivity : AppCompatActivity() {

    private var isAuthenticating = false

    // ----------------------------------------------------


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        setSupportActionBar(toolbar)

        val prefs = Preferences()
        if (!prefs.introDone) {
            prefs.introDone = true
            // update version on first load
            prefs.versionCode = getAppVersion().first
            showIntro()
        } else {
            checkToken()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == IntroActivity.INTENT_INTRO) {
            checkToken()
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isAuthenticating) {
            finishAuth()
        }
    }

    // ----------------------------------------------------

    private fun checkToken() {
        val token = Preferences(this).dbxAccessToken
        if (token == null) {
            Timber.d("Dropbox token is null")
            isAuthenticating = true
            Auth.startOAuth2Authentication(this, getString(R.string.dbx_app_key))
        } else {
            Timber.d("Dropbox token is ${token}")
            startApp()
        }
    }

    private fun finishAuth() {
        // the dropbox linking happens in another activity.
        val token = Auth.getOAuth2Token() //generate Access Token
        if (token != null) {
            Preferences(this).dbxAccessToken = token //Store accessToken in SharedPreferences
            Timber.d("new Dropbox token is ${token}")
            isAuthenticating = false
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

    private fun startApp() {
        // service up and running, show changelog if needed and start the actual app
        val prefs = Preferences(this)
        try {
            val version = getAppVersion()
            if (prefs.versionCode < version.first) {
                prefs.versionCode = version.first
                val dialog = Changelog.createDialog(this,
                        title = resources.getString(R.string.whatsnew_title),
                        versionCode = getAppVersion().first)
                dialog.setOnDismissListener({ _ -> _startApp() })
                dialog.show()
            } else {
                _startApp()
            }
        } catch (e: Throwable) { // in case the version or xml doesn't load, skip
            Timber.e(e)
            _startApp()
        }
    }

    private fun _startApp() {
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
