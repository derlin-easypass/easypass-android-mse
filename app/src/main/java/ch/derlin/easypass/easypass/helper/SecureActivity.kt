package ch.derlin.easypass.easypass.helper

import android.os.Bundle
import android.support.annotation.CallSuper
import android.support.v7.app.AppCompatActivity
import android.view.Window
import android.view.WindowManager

/**
 * Created by Lin on 25.11.17.
 */

abstract class SecureActivity : AppCompatActivity() {

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        makeSecure()
    }

    fun makeSecure() {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);
    }
}