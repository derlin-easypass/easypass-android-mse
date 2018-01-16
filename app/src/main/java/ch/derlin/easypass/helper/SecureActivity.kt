package ch.derlin.easypass.helper

import android.content.Intent
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import ch.derlin.easypass.LoadSessionActivity
import java.util.concurrent.TimeUnit

/**
 * The base class for any activity that displays sensitive information like
 * password or account lists.
 *
 * It will:
 *  - set the window flags so that screenshots and overview are forbidden
 *  - restart the activity in case:
 *      * the list of accounts in not in memory anymore ([DbxManager] killed)
 *      * the activity went more than 5 minutes in the background and
 *          the keyguard is not secure
 *
 * date 25.11.17
 * @author Lucy Linder
 */

abstract class SecureActivity : AppCompatActivity() {

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeSecure() // set the flags
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        // record the timestamp
        lastActiveTime = System.currentTimeMillis()
    }

    @CallSuper
    override fun onResume() {
        super.onResume()

        if (DbxManager.accounts == null) {
            // object might have been reinitialised
            backToLoadingScreen()
        } else {
            lastActiveTime?.let {
                if (System.currentTimeMillis() - it > secureTimeoutMillis && shouldAskCredentials())
                    backToLoadingScreen()
            }
        }
    }

    /** @return false if the accounts list is still in memory and that the keyguard is secure.*/
    fun shouldAskCredentials(): Boolean {
        if (CachedCredentials.isPasswordCached) {
            try {
                CachedCredentials.getPassword()
                return false
            } catch (e: Exception) {
            }
        }
        return true
    }

    /** replace the current activity with an instance of [LoadSessionActivity] */
    fun backToLoadingScreen() {
        val intent = Intent(this, LoadSessionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    /** Set the window flags to secure */
    fun makeSecure() {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
    }

    companion object {
        /** Minimum time required in background before eventually restarting */
        val secureTimeoutMillis = TimeUnit.MINUTES.toMillis(5)
        /** (Static) Time the last secure activity went in background */
        private var lastActiveTime: Long? = null
    }
}