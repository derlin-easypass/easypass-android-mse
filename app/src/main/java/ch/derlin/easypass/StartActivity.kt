package ch.derlin.easypass

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ch.derlin.changelog.Changelog
import ch.derlin.changelog.Changelog.getAppVersion
import ch.derlin.easypass.easypass.R
import ch.derlin.easypass.helper.DbxManager
import ch.derlin.easypass.helper.MiscUtils.rootView
import ch.derlin.easypass.helper.MiscUtils.showIntro
import ch.derlin.easypass.helper.Preferences
import com.dropbox.core.android.Auth
import com.google.android.material.snackbar.Snackbar
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

        if (!Preferences.introDone) {
            Preferences.introDone = true
            // update version on first load
            Preferences.versionCode = getAppVersion().first
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
        val token = Preferences.dbxAccessToken
        if (token == null) {
            Timber.d("Dropbox token is null")
            isAuthenticating = true
            Auth.startOAuth2PKCE(this, getString(R.string.dbx_app_key), DbxManager.requestConfig())
        } else {
            Timber.d("Dropbox token is $token")
            startApp()
        }
    }

    private fun finishAuth() {
        // the dropbox linking happens in another activity.
        val token = Auth.getDbxCredential() //generate Access Token
        if (token != null) {
            Preferences.dbxAccessToken = token.toString() //Store accessToken in SharedPreferences
            Timber.d("new Dropbox token is $token")
            isAuthenticating = false
            startApp()
        } else {
            Snackbar.make(rootView(), "Error authenticating with Dropbox", Snackbar.LENGTH_INDEFINITE)
                .setAction("retry") { forceRestart() }
                .show()
            Timber.d("Error authenticating")
        }
    }

    private fun startApp() {
        // service up and running, show changelog if needed and start the actual app
        try {
            val version = getAppVersion()
            if (Preferences.versionCode < version.first) {
                Preferences.versionCode = version.first
                val dialog = Changelog.createDialog(
                    this,
                    title = resources.getString(R.string.whatsnew_title),
                    versionCode = getAppVersion().first
                )
                dialog.setOnDismissListener { _startApp() }
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
