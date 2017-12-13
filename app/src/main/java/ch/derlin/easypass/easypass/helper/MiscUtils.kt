package ch.derlin.easypass.easypass.helper

import android.app.Activity
import android.content.Context
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.view.inputmethod.InputMethodManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.support.v4.app.Fragment
import android.view.View


/**
 * Created by Lin on 25.11.17.
 */

object MiscUtils {

    fun toSpannable(str: String, vararg args: String): Spanned? {
        val content = if (args.size > 0) str.format(args) else str
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY)
        } else {
            return Html.fromHtml(content)
        }
    }

    fun Fragment.hideKeyboard() {
        activity.hideKeyboard()
    }


    fun Activity.hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (inputMethodManager.isAcceptingText)
            inputMethodManager.hideSoftInputFromWindow(this.currentFocus.windowToken, /*flags:*/ 0)
    }

    fun Activity.restartApp() {
        // see https://stackoverflow.com/questions/17795189/how-to-programmatically-force-a-full-app-restart-e-g-kill-then-start
        val startIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(this, 123456, startIntent,
                PendingIntent.FLAG_CANCEL_CURRENT)

        (getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .set(AlarmManager.RTC, System.currentTimeMillis() + 1, pendingIntent)

        System.exit(0)
    }

    fun Activity.rootView(): View = findViewById(android.R.id.content)

}