package ch.derlin.easypass.helper

import android.content.Intent
import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import ch.derlin.easypass.LoadSessionActivity
import java.util.concurrent.TimeUnit

/**
 * Created by Lin on 25.11.17.
 */

abstract class SecureActivity : AppCompatActivity() {

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //makeSecure()
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        lastActiveTime = System.currentTimeMillis()
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        lastActiveTime?.let {
            if (System.currentTimeMillis() - it > secureTimeoutMillis && shouldAskCredentials())
                backToLoadingScreen()
        }
    }

    fun shouldAskCredentials(): Boolean {
        if (CachedCredentials.isPasswordCached) {
            try {
                CachedCredentials.getPassword()
                return false
            } catch (e: Exception) { }
        }
        return true
    }

    fun backToLoadingScreen() {
        val intent = Intent(this, LoadSessionActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    fun makeSecure() {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
    }

    companion object {
        val secureTimeoutMillis = TimeUnit.MINUTES.toMillis(5)
        private var lastActiveTime: Long? = null
    }
}